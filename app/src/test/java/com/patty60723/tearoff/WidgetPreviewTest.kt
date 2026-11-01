package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.patty60723.tearoff.mowan.ArtConfig
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.story.Parts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * P7-1：launcher 小工具清單的預覽（`calendar_widget_info.xml` 的 previewLayout）不能是空白。
 * 舊火柴人的 ViewFlipper 有預設圖；拿掉以後，`mowan_still` 的預設圖是 `drawable-nodpi/mowan_preview.png`：
 * 墨丸 V3 第 14 頁、戴紙帽、背包袱、沒有天氣、沒有節日，TALL 的大小、3x 密度（跟小工具同樣的 [MowanBitmap.still]）。
 * 新產生的圖存到 build/screenshots/p7-preview（要換預覽圖時從這裡拿；換了要再經美術審核，D-52）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WidgetPreviewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val committed = File("src/main/res/drawable-nodpi/mowan_preview.png")

    private fun render(): Bitmap {
        val dp = PageLayout.TALL.characterDp
        return MowanBitmap.still(
            Mowan.partsOf(Parts.everything), hatOn = true, bundleOn = true, weather = Mowan.Weather.NONE,
            widthPx = (dp * 3).toInt(), heightPx = (dp * 4 / 3 * 3).toInt(), density = 3f,
            festival = null, art = ArtConfig.DEFAULT.copy(smallEnv = CalendarWidget.smallEnvFor(PageLayout.TALL)),
        )
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    @Test
    fun previewIsTheV3Still() {
        val now = render()
        File("build/screenshots/p7-preview").apply { mkdirs() }.resolve("mowan_preview.png").outputStream().use { now.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val saved = BitmapFactory.decodeFile(committed.path, BitmapFactory.Options().apply { inScaled = false })
        assertEquals("預覽圖的大小（還是佔位圖的話，從 build/screenshots/p7-preview 拿新的）", "${now.width}x${now.height}", "${saved.width}x${saved.height}")
        assertTrue("預覽圖跟正式畫法一樣", pixels(now).contentEquals(pixels(saved)))
        assertTrue("預覽圖不是空白", pixels(saved).count { (it ushr 24) > 0 } > saved.width * saved.height / 20)
    }

    /** 四個版面直接 inflate（launcher 預覽就是這樣顯示）：墨丸的位置有圖、看得到 */
    @Test
    fun everyLayoutShowsTheCharacterWithoutTheApp() {
        for (layout in PageLayout.entries) {
            val v = LayoutInflater.from(context).inflate(layout.res, null)
            val still = v.findViewById<ImageView>(R.id.mowan_still)
            assertEquals(layout.name, View.VISIBLE, still.visibility)
            val image = (still.drawable as BitmapDrawable).bitmap
            assertTrue("${layout.name} 預覽有墨丸", pixels(image).count { (it ushr 24) > 0 } > image.width * image.height / 20)
        }
    }
}
