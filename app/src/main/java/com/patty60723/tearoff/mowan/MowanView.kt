package com.patty60723.tearoff.mowan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import java.time.LocalDate
import kotlin.random.Random

/**
 * 會動的墨丸 V3。時間怎麼走由 [MowanTimeline] 決定，這裡只負責什麼時候重畫。
 *
 * - 一般站姿只有 line boil：5 張影格的 Path 先算好存起來，每 1/6 秒換一張，中間不重畫。
 * - 天氣姿勢一直在變，每 33 毫秒（約 30 FPS，跟原型一樣）重算一張；筆觸還是 6 FPS。
 * - 換頁、換紙帽或包袱不會讓動作重來；只有換天氣會。
 * - 系統關掉動畫（Reduced Motion）時：固定第一張筆觸影格、停在代表姿勢、不打雷，不排任何重畫。
 * - 看不到的時候（App 在背景、被蓋住、離開畫面）不排重畫；回來時錯過的打雷不補播。
 * - App 停在前景時系統動畫被打開（例如分割畫面裡改設定），Android 13 以上會通知，墨丸馬上恢復動作。
 *
 * 節日（P6-4a）：設定 [today] 以後，今天是節日就用節日站姿、服裝與道具（[FestivalScene]），並由 [FestivalSession] 演出：
 * - 墨丸至少一半在螢幕上、App 在前景且視窗有焦點時（D-40），第一次演今天的主演出；開始就記錄（D-33），
 *   之後滑出滑回、切背景、重建畫面、重開 App 都不重播。可見性只在事件（捲動、版面、可見度、視窗焦點）時重新判斷，不輪詢。
 * - 演出與待機中每 33 毫秒重畫；完全滑出畫面時停止重畫，滑回來接著演（不重來）。
 * - 待機每 20–40 秒一次，只有看得到時才會跑、也只算看得到的時間；從背景或畫面外回來時重新倒數，不補播（D-46）。
 * - 節日當天整天不打雷（D-31）。
 * - 關閉動畫時主演出停在代表姿勢，演出時間到了再重畫一次回到站姿（D-34）。
 * - 沒有節日（或 [today] 是 null）時，畫法與重畫排程跟以前完全一樣。
 */
class MowanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 已解鎖的部位，見 [Mowan.partsOf] */
    var parts: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; redraw() } }

    var hatOn: Boolean = true
        set(value) { if (field != value) { field = value; redraw() } }

    /** 包袱是否背著（第 14 頁前不管這個值都不畫） */
    var bundleOn: Boolean = true
        set(value) { if (field != value) { field = value; redraw() } }

    /** 換天氣時姿勢從頭開始（伸懶腰從站直開始）、打雷排程重來 */
    var weather: Mowan.Weather
        get() = timeline.weather
        set(value) {
            val from = timeline.weather
            if (value == from) return
            timeline.setWeather(value, now())
            // 節日：天氣只換環境，不重播主演出、不改鎖定的層級
            if (festival != null) sessionOrNull?.weatherChanged(from, value)
            redraw()
        }

    /**
     * 今天（App 傳 `AppClock.today`，D-36）。null 表示不判斷節日。
     * 日期換了（跨午夜、除錯假日期）時，正在演的主演出演完才換成新的日期（[FestivalSession.enter]）。
     */
    var today: LocalDate? = null
        set(value) {
            if (field == value) return
            field = value
            festival = value?.let(FestivalTheme::of)
            redraw()
            checkVisible()
        }

    /** 美術設定（節日頭飾開關，見 [MowanSettings.art]）；只影響節日的畫法 */
    var art: ArtConfig = ArtConfig.DEFAULT
        set(value) { if (field != value) { field = value; redraw() } }

    /** 今天的節日（由 [today] 算出來） */
    var festival: Festival? = null
        private set

    /** 除錯用：固定某一張筆觸影格，null 表示跟著時間跑 */
    var fixedVariant: Int? = null
        set(value) { field = value; invalidate() }

    /** 現在在演什麼；節日演出開始、結束時會更新這裡（雷雨驚嚇看這個讓開） */
    val director = MowanDirector()

    /** 時間軸（測試用可以讀）。有更高優先的演出時，雷雨的驚嚇跳過；節日當天整天不打雷 */
    internal val timeline = MowanTimeline(higherPriorityPlaying = { director.aboveWeather() }).also {
        it.thunderAllowed = { festival == null }
        it.setWeather(Mowan.Weather.NONE, SystemClock.uptimeMillis())
    }

    /** 系統的「移除動畫」設定（測試可以換掉） */
    internal var reducedMotion: () -> Boolean = { !ValueAnimator.areAnimatorsEnabled() }

    /** 現在時間（測試可以換掉） */
    internal var clock: () -> Long = { SystemClock.uptimeMillis() }

    /** 墨丸有多少比例在螢幕上（測試可以換掉） */
    internal var visibleFraction: () -> Float = { onScreenFraction() }

    /** 視窗有沒有焦點（測試可以換掉） */
    internal var windowFocused: () -> Boolean = { hasWindowFocus() }

    /** 「主演出演過了」存在哪裡（預設 SharedPreferences；測試可以在第一次用到以前換掉） */
    internal var mainLog: FestivalSession.MainLog? = null

    /** 節日待機間隔的亂數（測試可以換掉） */
    internal var random: Random = Random.Default

    private var sessionOrNull: FestivalSession? = null

    /** 節日的狀態機（第一次用到才建立） */
    internal val session: FestivalSession
        get() = sessionOrNull ?: FestivalSession(mainLog ?: MowanSettings.FestivalLog(context), random).also { sessionOrNull = it }

    private fun now() = clock()

    private val boil = arrayOfNulls<MowanPainter>(Mowan.FRAMES)
    private val tick = Runnable { isTicking = false; invalidate() }

    /** 現在有沒有排著下一次重畫（測試用） */
    internal var isTicking = false
        private set

    /** 下一次重畫排在幾毫秒後（測試用） */
    internal var lastDelayMs = 0L
        private set

    private fun stop() {
        removeCallbacks(tick)
        isTicking = false
    }

    /** 看不到而停過重畫：下次畫的時候節日待機從那時重新倒數 */
    private var paused = false

    private fun pause() {
        stop()
        paused = true
    }

    private fun schedule(delay: Long) {
        lastDelayMs = delay
        isTicking = postDelayed(tick, delay)
    }

    /** 外觀變了：丟掉快取的筆觸，但時鐘不動 */
    private fun redraw() {
        boil.fill(null)
        invalidate()
    }

    private fun scene(f: Festival) = FestivalScene.resolve(parts, f, weather, hatOn, bundleOn, art)

    private fun onScreenFraction(): Float {
        if (!isShown || width == 0 || height == 0) return 0f
        val r = Rect()
        if (!getGlobalVisibleRect(r)) return 0f
        return r.width().toFloat() * r.height() / (width.toFloat() * height)
    }

    /** D-40：至少一半在螢幕上、App 在前景（視窗可見）、視窗有焦點 */
    internal fun foregroundVisible(): Boolean =
        isShown && windowVisibility == VISIBLE && windowFocused() && visibleFraction() >= VISIBLE_ENOUGH

    /**
     * 可見性改變時（事件，不輪詢）：看得到就把今天交給狀態機（今天第一次會開始主演出），
     * 完全看不到就停止重畫。沒有設定 [today] 時什麼都不做。
     */
    internal fun checkVisible() {
        val day = today ?: return
        if (festival != null) {
            // 完全滑出畫面：停止重畫；滑回來一點點：接著畫（演出照實際經過的時間，不重來）
            if (visibleFraction() <= 0f) {
                pause()
                return
            }
            if (!isTicking) invalidate()
        }
        if (!foregroundVisible()) return
        val f = festival
        val before = sessionOrNull?.mode
        session.enter(day, f, now(), f?.let(::scene), reducedMotion())
        syncDirector()
        if (sessionOrNull?.mode != before || !isTicking) invalidate()
    }

    /** 除錯選單的重播（D-33）：不改「演過了」的紀錄 */
    fun replayFestival(): Boolean {
        val f = festival ?: return false
        val ok = session.replay(now(), scene(f), reducedMotion())
        syncDirector()
        invalidate()
        return ok
    }

    private var playing: MowanDirector.Activity? = null

    /** 狀態機的模式 → [director]（雷雨驚嚇看這個） */
    private fun syncDirector() {
        val a = sessionOrNull?.mode?.activity
        if (a == playing) return
        playing?.let(director::finish)
        a?.let(director::start)
        playing = a
    }

    private var lastReduced: Boolean? = null

    override fun onDraw(canvas: Canvas) {
        stop()
        val now = now()
        val reduced = reducedMotion()
        val sample = timeline.sample(now, reduced)
        val variant = fixedVariant ?: sample.variant
        val f = festival
        var performing = false
        var heldUntil: Long? = null
        val painter = if (f == null) {
            if (weather == Mowan.Weather.NONE) {
                boil[variant] ?: MowanPainter(Mowan.frame(parts, variant, hatOn, bundleOn = bundleOn)).also { boil[variant] = it }
            } else {
                MowanPainter(Mowan.frame(
                    parts, variant, hatOn, weather, sample.clockMs, sample.rainStep,
                    sinceStrikeMs = sample.sinceStrikeMs, still = sample.still, bundleOn = bundleOn,
                ))
            }
        } else {
            val scene = scene(f)
            val s = session
            if (lastReduced != null && lastReduced != reduced) s.reducedChanged(reduced, now)
            lastReduced = reduced
            if (paused) s.resumed(now, reduced)
            paused = false
            s.update(now, scene, reduced)
            syncDirector()
            val action = s.action
            val perf = action?.let { FestivalActions.Performance(it, s.level!!, (now - s.startMs).toDouble(), s.held) }
            performing = perf != null && !s.held
            if (perf != null && s.held) heldUntil = s.startMs + action.durMs
            if (perf == null && weather == Mowan.Weather.NONE) {
                boil[variant] ?: MowanPainter(Mowan.frame(parts, variant, hatOn, bundleOn = bundleOn, festival = f, art = art)).also { boil[variant] = it }
            } else {
                MowanPainter(Mowan.frame(
                    parts, variant, hatOn, weather, sample.clockMs, sample.rainStep,
                    still = sample.still, bundleOn = bundleOn, festival = f, art = art, performance = perf,
                ))
            }
        }
        painter.draw(
            canvas, paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingLeft - paddingRight).toFloat(), (height - paddingTop - paddingBottom).toFloat(),
        )
        if (!isShown || windowVisibility != VISIBLE || f != null && visibleFraction() <= 0f) {
            // 看不到就不重畫（節日：完全滑出畫面也是），滑回來（捲動事件）再接著演
            paused = true
            return
        }
        if (sample.still) {
            // 關閉動畫：主演出停在代表姿勢，演出時間到了再畫一次回到站姿
            heldUntil?.let { schedule((it - now).coerceAtLeast(1)) }
            return
        }
        val delay = if (performing || weather != Mowan.Weather.NONE) {
            POSE_FRAME_MS
        } else {
            // 到下一張筆觸影格才重畫
            (Mowan.BOIL_MS - sample.clockMs % Mowan.BOIL_MS).toLong().coerceAtLeast(1)
        }
        schedule(delay)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            invalidate()
            checkVisible()
        } else {
            pause()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        checkVisible()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) checkVisible()
    }

    /** 捲動、版面改變：重新判斷可見性（只有設定了 [today] 才有事做） */
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { checkVisible() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { checkVisible() }

    /** 系統動畫設定改變：停住的墨丸要重畫一次才知道可以動了（通知可能來自其他執行緒） */
    private val scaleListener = ValueAnimator.DurationScaleChangeListener { post { invalidate() } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ValueAnimator.registerDurationScaleChangeListener(scaleListener)
    }

    override fun onDetachedFromWindow() {
        pause()
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ValueAnimator.unregisterDurationScaleChangeListener(scaleListener)
        super.onDetachedFromWindow()
    }

    companion object {
        /** 姿勢動畫的重畫間隔，跟原型一樣約 30 FPS */
        const val POSE_FRAME_MS = 33L

        /** D-40：至少一半在螢幕上才算看得到 */
        const val VISIBLE_ENOUGH = 0.5f

        /** 不能動的時候的代表姿勢，見 [MowanTimeline.stillClock] */
        fun stillClock(weather: Mowan.Weather) = MowanTimeline.stillClock(weather)
    }
}
