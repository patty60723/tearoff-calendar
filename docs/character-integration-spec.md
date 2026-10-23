# 墨丸 V3 — Android App 整合規格（Phase 2B）

> 狀態：**規劃文件，尚未開始實作**。本文件只描述現況與建議，不代表任何功能已完成。
> 相關文件：[實作計畫](character-implementation-plan.md)・[未定案事項](character-open-decisions.md)

## 標記說明

| 標記 | 意思 |
|---|---|
| **Existing** | 正式 App 程式碼裡已經有（附檔案位置） |
| **Missing** | 正式 App 裡沒有，尚未實作 |
| **Confirmed** | 使用者已確認的規格（角色聖經 / Phase 2B 需求） |
| **Proposed** | 本文件提出的建議，尚未批准 |
| **TBD** | 未定案，實作時必須做成可設定，不能寫死 |

程式碼路徑一律以 `app/src/main/java/com/patty60723/tearoff/` 為根，以下簡寫為 `…/`。
稽核基準：`main` @ `251204d`。

---

## 1. Repository Audit

### 1.1 技術棧（Existing）

| 項目 | 現況 |
|---|---|
| 語言 / 建置 | Kotlin 2.1.21、AGP 8.10.1、Gradle 8.14.3、Java 17 |
| SDK | compileSdk / targetSdk 35、minSdk 26 |
| 相依套件 | 只有 `cn.6tail:lunar:1.7.7`；測試用 junit 4.13.2、org.json、Robolectric 4.14.1 |
| UI | **沒有 Jetpack Compose，也沒有 AndroidX UI**。傳統 `Activity` + View + `android.graphics.Canvas` |
| 動畫 | `ValueAnimator`（撕頁）、`postInvalidateOnAnimation()`（小人每幀重畫） |
| CI | `.github/workflows/build.yml`：`testDebugUnitTest assembleDebug`，截圖推到 `screenshots/<branch>`，main 發 latest 預覽版 APK |

**結論**：整合必須以 Canvas + 自訂 View 為主。不需要、也不建議為了角色系統導入 Compose（見 open-decisions D-13）。

### 1.2 日曆主畫面、撕頁、日期狀態（Existing）

- `…/MainActivity.kt`：主畫面。日期來源是 `AppClock`（可在 debug 選單假造今天）。
- `…/TearPageView.kt`：撕頁效果。`ValueAnimator` 驅動捲頁，用反射矩陣畫背面，多邊形裁切紙張。
- `…/PageLayout.kt`、`…/Palette.kt`（12 個月份配色）、`…/Almanac.kt`（農民曆、宜忌）。
- 撕頁後呼叫 `StoryBook` 推進故事頁。
- 午夜更新：`…/MidnightAlarm.kt`（`setAndAllowWhileIdle`，RTC）。

### 1.3 14 頁故事與解鎖（Existing，內容不得改寫）

- 資料：`app/src/main/assets/story/chapter1.json`，共 14 個 beat。
- 每頁的 `unlock` 欄位（現況）：

| 頁 | unlock（程式碼） | Confirmed 角色規格 | 差異 |
|---|---|---|---|
| 1 | `line` | 身體線 | 無 |
| 2 | `head` | 頭 + 一筆 | 現有程式**沒有「一筆」造型**（Missing） |
| 3 | `eyes` | 眼睛 + 腮紅 | 現有程式**沒有腮紅**（Missing） |
| 4 | `armR` | 右手 | 無 |
| 5 | `armL` | 左手 | 無 |
| 6 | `legL` | 左腳 | 無 |
| 7 | `legR` | 右腳 | 無 |
| 8 | `voice` | 嘴巴 | 名稱不同，語意相同 |
| 9 | `redToes` | 紅墨腳尖 | 現有畫法是圓點，不是 V3 筆觸 |
| 10 | `scarf` | 圍巾 | 無 |
| 11 | `paperHat` | 斜戴紙帽 | 現有畫法是三角形，沒有斜戴 |
| 12 | 無（`ask:name`） | 不變 | 無 |
| 13 | 無 | 不變 | 無 |
| 14 | `bundle` | 包袱 | 無 |

- 故事解鎖順序與 Confirmed 規格一致，**不需要改 JSON**。差異都在繪圖層。
- `…/story/StoryBook.kt`：SharedPreferences `"story"`，存 page、name、lastText、lastAction、lastProps、lastTornDate、recent。最多補 7 天沒撕的頁。debug 有 `jumpTo`、`reset`。
- `…/story/StoryEngine.kt`：順序 MAIN > FESTIVAL > SOLAR_TERM > DAILY。日常 beat 用日期當亂數種子挑選，跳過最近 30 個。
- `assets/story/daily.json`：32 個 beat（23 個日常、9 個天氣、**0 個節日**）。
- ⚠️ `StoryEngine.DEFAULT_NAME = "一筆"`。這跟 Confirmed Q6 衝突：正式名字是「墨丸」，「一筆」只是頭上那一筆。要不要改，見 open-decisions D-08。
- 配件狀態：`wearing = 已解鎖的 parts ∩ 配件清單`。**沒有「拿到但沒戴」的狀態**（Missing）。

### 1.4 角色繪製與動畫（Existing：舊版火柴人；V3：全部 Missing）

- `…/stickman/Pose.kt`：48×64 格、11 個關節，地面 y = 57。14 個 Action：idle、wiggle、look、wave、clap、hop、walk、bounce、cheer、sit、umbrella、wipe、shiver、gaze。每個有自己的週期（例如 idle 1600 ms、hop 600 ms）。
- `…/stickman/StickmanRenderer.kt`：用 `Paint.Style.STROKE` 畫等寬線條。顏色 INK `#2B2724`、RED `#C62F2F`，**不是 V3 色票**。
- `…/stickman/Parts.kt`：部位與配件清單。草帽會蓋掉紙帽。
- `…/stickman/StickmanView.kt`：顯示時**每個 vsync 都重畫**（`postInvalidateOnAnimation`），而且每幀 new 新的 `Path` / `RectF`。

V3 相關項目全部是 **Missing**：

- 毛筆多邊形筆觸（Catmull-Rom 中心線 + 左右偏移 + 圓頭）
- 頭部輪廓 `ring()`（72 點、2/3/5 次諧波）
- 一筆、腮紅、V3 五官
- Line boil（預先算好的 5 個變體、6 FPS）
- V3 色票
- 斜戴紙帽、頭飾貼合（`headPlace` / `conform`）
- 手持道具（`handCut`）、能力系統、場景解析、表演關鍵影格、降級規則

### 1.5 天氣（Existing）

- `…/weather/WeatherService.kt`：Open-Meteo，`HttpURLConnection`。SharedPreferences 快取，1 小時內算新鮮，6 小時內還能顯示。
- `…/weather/Weather.kt`：Sky 8 種（CLEAR、PARTLY、CLOUDY、FOG、RAIN、HEAVY_RAIN、THUNDER、SNOW）→ Mood 8 種 → 小人動作 + 道具：

| Mood | 現有動作 | V3 規格 |
|---|---|---|
| SUNNY | wave | **Confirmed**：伸懶腰，約 5.5 s |
| RAIN、THUNDER | umbrella | **Confirmed**：撐傘，約 4.2 s（雷雨畫法 TBD） |
| HOT | wipe | TBD |
| CLOUDY、FOG | gaze | TBD |
| COLD、SNOW | shiver | TBD |

- 更新時機：App resume、Widget 更新。點天氣列會讓故事卡播 6 秒天氣動作。debug 可假造 mood。

### 1.6 節日（Existing：日期判斷；角色主題：Missing）

- `…/TaiwanHolidays.kt`：
  - 春節：農曆 1/1–1/3（假日）
  - 中秋：農曆 8/15（假日）
  - 12/25：假日清單裡是「行憲紀念日」（2025 起），紀念日清單裡有「聖誕節」
- 畫面標籤：`DayPage.label = festivals.firstOrNull() ?: solarTerm`。
- ⚠️ 角色節日主題**不能從畫面標籤判斷**。例如 12/25 標籤可能是「行憲紀念日」而不是「聖誕節」。需要另外做主題判斷（Proposed，見 §3.4）。
- 節日服裝、道具、表演、節日故事 beat：全部 **Missing**。

### 1.7 Widget（Existing）

- `…/CalendarWidget.kt`：`AppWidgetProvider` + `RemoteViews`。
- 四種版面，小人尺寸：

| 版面 | 小人尺寸 |
|---|---|
| COMPACT | 36 dp |
| MEDIUM | 42 dp |
| WIDE | 44 dp |
| TALL | 48 dp |

- API 31+ 傳入 4 種版面的 SizeF map，系統自己挑。
- 動畫做法：`ViewFlipper autoStart` 輪播 6 張 bitmap，間隔 = 動作週期 / 6。密度最多算到 3x。
- 更新時機：`updatePeriodMillis` 3 小時、午夜鬧鐘、時間 / 開機 / 更新 App 廣播。天氣用 `goAsync` + thread 更新。

**限制與技術債**：

- 一次更新送 4 版面 × 6 張 = **24 張 bitmap**，有超過 Binder 交易上限（約 1 MB）的風險。
- 每次 `build()` 都 new 一個 `StoryBook`，等於每個版面都重新解析一次故事 JSON。
- RemoteViews 不能跑自訂 View，也不能用 Canvas 動畫。只能用預先畫好的 bitmap + `ViewFlipper`。

### 1.8 測試、效能、技術債

- 單元測試：`AlmanacTest`、`SunTimesTest`、`StoryEngineTest`、`PoseTest`、`WeatherTest`。
- Robolectric：`MainActivityTest`、`ScreenshotTest`（CI 會把截圖推到 `screenshots/*`）。
- 角色相關測試**沒有**：繪圖正確性、14 頁外觀、節日 × 天氣衝突、表演不重播。

技術債：

1. `StickmanView` 一直以 vsync 重畫，就算沒有動作也一樣。每幀配置新物件，有 GC 壓力。
2. Widget 重複解析 JSON；24 張 bitmap 的大小風險。
3. `DEFAULT_NAME = "一筆"`（見 D-08）。
4. 左右是以「螢幕的左右」命名，不是角色自己的左右（見 D-12）。

### 1.9 原型檔案（Missing in repo）

Repository 裡**沒有** HTML 原型。原型只存在於已發布的 Artifact：

| 原型 | 用途 |
|---|---|
| V3 Line Boil | 角色本體、筆觸、6 FPS 變體 |
| Growth（成長草稿） | 14 頁成長焦點 |
| Festival v7 | 三個節日、狀態機、能力系統、場景解析、19 個回歸測試 |
| Art Review | 演出關鍵姿勢、道具精修 |
| Hat / Umbrella | 頭飾 A/B/C、雨傘原尺寸 vs −5% |

這些原型**只是參考**（Confirmed），不能用 HTML / JS / WebView 當正式角色系統（Confirmed）。要從原型取幾何數字或測試基準值，得先把原型原始碼放進 repo（Proposed：`docs/prototypes/`，見 D-14）。

---

## 2. 原型 vs Android 差距分析

| 功能 | 原型 | Android | 移植方式（Proposed） |
|---|---|---|---|
| 毛筆筆觸 | 有（SVG path） | Missing | Kotlin 算多邊形 → `android.graphics.Path`，`Paint.Style.FILL` |
| 頭部輪廓 ring | 有 | Missing | 同上，純數學，可 JVM 測試 |
| Line boil | 5 變體，順序 [0,3,1,4,2]，166.67 ms/格；雨 8 FPS | Missing | 變體幾何預先算好、快取 Path；只換索引 |
| mulberry32 / hash 相位 | 有 | Missing | 照抄演算法（32-bit 整數運算要用 `Int` 溢位語意） |
| 姿勢 pose() | V3 關節座標 | 舊 48×64 格 | 新的 V3 座標系（viewBox 0 −20 120 160），舊的不改 |
| 天氣姿勢 | 晴天 5.5 s、雨天 4.2 s | 有舊動作 | 新增 V3 版 |
| 14 頁外觀 | 有 | 只有舊版 | 由 `StoryBook.page` 推導能力 |
| 能力系統 | `capabilities` | Missing | Kotlin data class，純函式 |
| 場景解析 / slot | `resolve`、slots | Missing | 純函式，可 JVM 測試 |
| 節日道具與服裝 | 財神帽、斗方、紅包、聖誕帽、襪、禮物、玉兔耳朵、月亮、月餅 | Missing | Path 繪製，做成可設定變體 |
| 表演關鍵影格 | `track()`、降級等級 | Missing | Kotlin 關鍵影格插值 |
| 狀態機 | Controller + Session | Missing | 小型 Kotlin 類別 + prefs 持久化 |
| 頭飾貼合 | `headPlace` / `conform` | Missing | 照抄 |
| 天氣切換轉場 | 有 | — | **不移植**（Confirmed：不需要） |
| Pool renderer | DOM 重複使用 | — | 不需要，Canvas 直接畫 |

---

## 3. 架構（Proposed）

原則：沿用現有 View + Canvas + SharedPreferences，不加新框架。新程式碼放在新 package `…/mowan/`，舊的 `…/stickman/` 在切換完成前保留，用 debug 開關切換。

### 3.1 Character Model

```kotlin
data class Capabilities(       // 由故事頁推導，純函式
    val line: Boolean, val head: Boolean, val face: Boolean,   // p1, p2(含一筆), p3(含腮紅)
    val armR: Boolean, val armL: Boolean, val legL: Boolean, val legR: Boolean,
    val mouth: Boolean, val redToes: Boolean, val scarf: Boolean,
    val paperHatGranted: Boolean, val bundle: Boolean,
)
fun capabilitiesFor(page: Int): Capabilities
```

- 來源：`StoryBook.page` + `chapter1.json` 的 unlock。不另外複製一份頁碼表，避免兩邊不一致。
- 紙帽：「拿到」（granted，第 11 頁）和「戴著」（equipped）分開存。裝備細節 TBD（D-05）。

### 3.2 Renderer

- `MowanGeometry`：純 Kotlin，不依賴 Android。輸入姿勢 + boil 變體，輸出多邊形點陣列（`FloatArray`）。**可以在 JVM 單元測試裡對原型的黃金數值比對**。
- `MowanRenderer`：把點陣列轉成 `Path`，用 `Paint.Style.FILL` 畫到 `Canvas`。App 和 Widget 共用。
- 快取：靜態姿勢 × 5 個 boil 變體的 `Path` 預先算好。只有姿勢動畫進行中才每幀重算。
- 座標系：V3 viewBox `0 −20 120 160`，頭中心 (60, 47.5)、rx 22.5、ry 20（Confirmed 數值，照聖經 §2）。外面用 `Canvas.scale` 對應到 View 大小。

### 3.3 Animation System（Confirmed：各層頻率分開）

| 層 | 頻率 | 驅動方式（Proposed） |
|---|---|---|
| Line boil | 6 FPS（雨 8 FPS） | `postInvalidateDelayed(166)`，只換變體索引 |
| 姿勢動畫（天氣、表演、成長） | 60 FPS（vsync） | 只有播放期間用 `postInvalidateOnAnimation` |
| 天氣環境（雨絲等） | 依原型 | 跟 boil 同一個節拍，或獨立 |
| 節日主演出 | 一天一次 | 狀態機觸發 |
| 節日 idle | 低頻循環 | 狀態機 |
| 成長動畫 | 撕頁後 | 狀態機 |

- 姿勢的 60 FPS 與 boil 的 6 FPS 共存：boil 索引 = `floor(時間 / 166.67) % 5` 依時間計算，**不是**每畫一幀加 1。所以畫面刷新率再高，boil 還是 6 FPS。
- Reduced Motion：`ValueAnimator.areAnimatorsEnabled() == false` 時，停掉 boil（固定變體 0），表演只顯示停留姿勢。
- 生命週期：View `onDetachedFromWindow` / `onVisibilityAggregated(false)` / Activity `onPause` 時停止所有重畫排程。

### 3.4 Scene Resolver（Confirmed 優先順序）

```
成長故事 > 節日主演出 > 節日 idle > 天氣日常 > idle
```

- 天氣**環境**（雨、晴天光線）一直存在，不被節日覆蓋（Confirmed）。
- 純函式：`resolveScene(caps, festivalTheme?, weatherMood?, session) → Scene`（姿勢、各手道具、服裝、環境層）。
- 節日主題判斷（Proposed）：獨立的 `FestivalTheme.of(date)`，用農曆 / 國曆判斷 SPRING / XMAS / MOON，不依賴 `DayPage.label`。
- 手部規則（Confirmed）：
  - 雨傘是功能道具，拿在右手。
  - 另一隻已解鎖的手才能拿節日道具。同一隻手不能拿兩樣。
  - 不能用還沒解鎖的部位做動作。不夠時降級。
  - 春雨必須支援「雨傘 + 紅包」。

### 3.5 Capability / 降級

- 每個表演定義需要的能力（例如作揖需要雙手），依目前能力選最高可用的降級等級（照原型 ACTIONS 的 level）。
- 能力不足、也沒有降級版本時 → 只顯示服裝 / 道具，不演出。

### 3.6 Geometry 與可設定美術（TBD 項目不寫死）

- `ArtConfig`：集中放未定案的變體選擇。例如：
  - `hat`：A / B / C（目前候選 C，**未批准**）
  - `ears`：A / B
  - `umbrellaScale`：1.0 / 0.95（傾向原尺寸，**未批准**）
  - `widget48Rule`：完整 / 單一元素
- 預設值先跟原型的「目前建議」一致，但文件和程式碼註明是 Proposed。
- V3 本體幾何（頭身比例、五官、輪廓、一筆、boil 強度）是 Confirmed，**不放進設定**，避免被誤改。

### 3.7 Persistence（Proposed）

新的 SharedPreferences `"mowan"`：

| key | 用途 |
|---|---|
| `paperHatEquipped` | 紙帽是否戴著（細節 TBD） |
| `lastMainDate` / `lastMainTheme` | 今天是否已演過節日主演出。**重開 App、天氣更新都不會重播**（Confirmed） |
| `lastGrowthPage` | 成長動畫是否已播過 |

既有 `"story"` prefs 不改格式。

### 3.8 Widget 策略（Proposed）

- 沿用 `ViewFlipper` + bitmap，但改成 V3 渲染。
- 選項（D-04 / Q4 未定）：
  - (a) 一張靜態圖
  - (b) 5 張 boil 影格
- Bitmap 預算：只算目前版面，或降低張數，確保一次更新遠低於 Binder 上限。
- 故事 JSON 解析結果快取一次，不要每個版面重新 new `StoryBook`。
- Widget **不放**節日主演出（太長、幀數太多）。只放服裝 / 道具 + boil（Proposed）。
- 48px 規則做成設定（D-07）。

---

## 4. 測試與效能

| 項目 | 方法（Proposed） |
|---|---|
| 幾何正確性 | JVM 測試：把原型輸出的多邊形點當黃金數值，誤差 < 0.01 |
| 14 頁正確性 | 對 1–14 頁逐頁檢查能力與繪出的部位；Robolectric 截圖 |
| 節日 × 天氣衝突 | 移植原型 19 個回歸測試到 JVM（3 節日 × 天氣 × 5 能力階段） |
| 主演出不重播 | 測試：演完 → 模擬天氣更新 → 模擬重開 App（重建 prefs 讀取）→ 不再觸發 |
| Canvas Path 成本 | 快取 Path；idle 時每 166 ms 只畫一次；用 `Choreographer` / systrace 量測 |
| 6 FPS 與 60 FPS 共存 | boil 索引用時間計算；單元測試給定時間 → 索引 |
| 背景停止 | Robolectric：pause 後沒有待執行的 invalidate |
| Reduced Motion | 測試 `areAnimatorsEnabled=false` 時 boil 固定、不演出 |
| 多密度 | 截圖測試 mdpi / xhdpi / xxxhdpi |
| 48px Widget | 截圖 + 人工檢查（依 D-07 規則） |

**自動測試只能證明邏輯正確；美術品質（姿勢好不好看、道具認不認得出來）仍需人工檢查。** 兩者在驗收時分開列。
