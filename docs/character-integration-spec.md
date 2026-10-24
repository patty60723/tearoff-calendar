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
- 雷雨的打雷驚嚇（Proposed，方向已接受）：屬於「天氣日常」層，間隔隨機（建議 15–40 秒）。成長故事、節日主演出、節日 idle 播放時，輪到的那次打雷整個跳過（閃電也不出現），不排隊補播，也不打斷正在播的演出；雨與雷雨雲照常顯示。
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

---

## 5. 天氣與包袱定稿（Phase 2B 設計收斂）

參考程式：`docs/prototypes/src/weather.js`（搭配 `engine.js`、`core.js`）。審核用的截圖在 `docs/prototypes/audit/`。

### 5.1 驗收狀態

| 標記 | 意思 |
|---|---|
| **Design Approved** | 設計方向已由使用者確認 |
| **Code Reviewed** | 原型的邏輯已逐項檢查（手的佔用、成長降級、時間） |
| **Visually Verified** | 已實際渲染並看過畫面（註明範圍） |
| **Pending** | 還沒做 |

| 項目 | Design Approved | Code Reviewed | Visually Verified | Android |
|---|---|---|---|---|
| 晴：伸懶腰 5.5 s | ✓ | ✓ | ✓ 原型取樣格；App CI 截圖 | **已實作**（P2，黃金測試） |
| 雨：撐傘 4.2 s | ✓ | ✓ | ✓ 原型取樣格；App CI 截圖 | **已實作**（P2，黃金測試） |
| 熱：A 擦汗 | ✓ | ✓ | ✓ 原型取樣格（含 150 ms 逐格的抬手、放手） | **已實作**（W2 姿勢、W3 繪製，黃金數值逐筆比對）；故事卡未接（W6）；連續播放的視覺驗收 Pending |
| 陰：抬頭看雲 6 s | ✓ | ✓ | ✓ 原型取樣格 | **已實作**（W2 姿勢、W3 繪製，黃金數值逐筆比對）；故事卡未接（W6）；連續播放的視覺驗收 Pending |
| 霧：探頭、向前摸索 | ✓ | ✓ | ✓ 原型取樣格 | **已實作**（W2 姿勢、W3 繪製，黃金數值逐筆比對）；故事卡未接（W6）；連續播放的視覺驗收 Pending |
| 雷雨：撐傘為主、偶發驚嚇 | ✓ | ✓ | ✓ 原型取樣格（含驚嚇前後） | **已實作**（W2 姿勢、W3 繪製）；打雷排程 Pending（W5）；故事卡未接（W6） |
| 冷：輕抱、微抖 | ✓ | ✓ | ✓ 原型取樣格 | **已實作**（W2 姿勢、W3 繪製，黃金數值逐筆比對）；故事卡未接（W6）；連續播放的視覺驗收 Pending |
| 雪：B 抬頭看雪、身體微縮 | ✓ | ✓ | ✓ 原型取樣格 | **已實作**（W2 姿勢、W3 繪製，黃金數值逐筆比對）；故事卡未接（W6）；連續播放的視覺驗收 Pending |
| 包袱 B1 | ✓ | ✓ | ✓ 一般、伸懶腰、撐傘、雷雨、冷、霧、雪 B | **已實作**（W4，黃金數值逐筆比對；CI 截圖 `mowan-bundle.png`）；故事卡用 V3 時第 14 頁就會顯示；裝備切換只有除錯選單 |

「原型取樣格」是指每種天氣在第 14 頁取 10 格、第 1–10 頁取代表格，加上財神帽、聖誕帽、玉兔耳朵各配 6 種天氣（`audit/a-time.png`、`b-growth.png`、`c-hats.png`）。
以下都還**沒有**驗收（Pending）：

- 連續播放的動態節奏，例如抖動和驚嚇的感覺；目前只看了靜止的取樣格。
- 實機和多密度的畫面。
- 48px 的簡化版。
- 拿著節日道具（紅包、月餅）時的天氣動作：天氣草案沒有畫節日道具。
- 節日帽子的最終貼合方案：還沒批准，這裡只確認帽子會跟著頭，沒有錯位。

### 5.2 動作一致性審核

量測與看圖的結果，全部是第 14 頁、加上包袱的版本：

| 天氣 | 手臂長度（一般站姿 21.6） | 手尖離頭輪廓最近 | 手尖進到頭裡 |
|---|---|---|---|
| 晴（已定案） | 38.7–45.0 | 5.0 | 0 格 |
| 雨（已定案） | 16.4–21.2 | 16.6 | 0 格 |
| 熱 | 16.3–36.4 | 1.05（貼著臉頰外側） | 0 格 |
| 陰 | 19.4–21.6 | 26.8 | 0 格 |
| 霧 | 19.4–25.5 | 20.1 | 0 格 |
| 雷雨 | 19.6–23.4 | 16.2 | 0 格；左手末端正好在傘柄中心線上 |
| 冷 | 19.9–20.2 | 19.3 | 0 格 |
| 雪 B | 19.1–19.2 | 27.1 | 0 格 |

**Critical**：沒有。沒有穿模、斷肢，道具位置也沒有錯。

**Major**：發現兩項，都已在參考程式裡修正，並重新渲染確認。

1. **熱的抬手、放手**：原本直接從垂手內插到擦汗，手臂在過程中縮到 13.5（一般 21.6），看起來像斷掉一截。
   - 修正：加兩個中間姿勢 `R_WIPE_SIDE`、`R_WIPE_MID`。
   - 結果：最短 16.3，只出現約 70 ms（每 10 ms 取樣）。
2. **冷的抱身**：雙手在胸前交叉，右手尖還勾回來，96dp 時看起來像打結。
   - 修正：改成雙手從兩側繞到身體前、手尖碰在一起、不交叉。
   - 結果：手臂長 19.9–20.2，最接近一般站姿。

**Minor**（只記錄，不再修改）：

- 熱：手剛抬起的前 70 ms，手臂比垂手時（19.4）短約 16%。
- 冷：96dp 時抱身仍然只是一個小圈。這是尺寸的限制。
- 冷，第 4 頁（只有右手）：手繞到肚子前收起來，看起來稍微捲。
- 雷雨：兩個拳頭的圓圈中心相距 6.0，半徑加起來 6.2，有一點重疊，看起來是疊在一起握傘。
- 霧：往前伸的左手畫在包袱前面。從正面看合理，不會像在拿包袱。
- 晴：手臂最長 45，比一般長很多。這是已定案的伸懶腰，不在這次範圍。

**已確認沒有問題的檢查項目：**

- 圍巾一律在身體之後、頭之前，所以會落在頭和身體之間。
- 紙帽和節日帽在頭部座標裡，跟著頭轉，所有新動作都沒有錯位。
- 一筆最後才畫，不會被配件截斷。
- 雨傘只在有右手時出現；左手要已解鎖才會一起握傘柄。
- 包袱的布結、背帶都連在左肩後面。背帶末端藏在圍巾下面，不會穿過身體那條線。
- 情緒：
  - 驚嚇只有 0.4 秒，抖動 0.35。
  - 冷天抖動 0.45、雪 B 只有 0.15。
  - 沒有誇張的五官，也沒有大幅度的卡通動作。

**結論：沒有剩下的 Critical 或 Major，可以進入實作。**

**W2 之後補的連續性測試又發現一項 Major（D-22，已修正）**：熱天抬手、放手（0–500 ms、1700–2200 ms）太快，而且會頓。

- 手尖一格（33 ms）最多跳 6.35 單位，其他動作最多 0.41。
- 每一段關鍵格各自 ease in/out，所以手在中間姿勢 `R_WIPE_SIDE`、`R_WIPE_MID` 幾乎停住（速度從 200 掉到 15、6 單位／秒），看起來分三段一頓一頓的。
- 這是上面修「手臂縮短」時加中間姿勢的副作用。手臂長度沒問題，問題在時間與內插方式。
- 照指示先回報、沒有改美術數值。定案（A＋B）後已修正：抬手、放手各 650 ms，整段一次 ease、中間姿勢依手尖距離分配時間。手尖一格最多 2.98（抬）、3.64（放），中段最慢的一格超過最快的一半；手臂最短 16.27。

### 5.3 每種動作需要的部位與降級

部位名稱跟 `Mowan.partsOf` 一樣。沒有對應部位時，那一部分就不畫或不做，不另外畫一套動畫。

| 天氣 | 完整動作需要 | 降級 |
|---|---|---|
| 晴 | 頭、眼睛、雙手 | 已實作（照原型） |
| 雨 | 右手（撐傘） | 沒有右手：不撐傘，皺眉、一筆下垂 |
| 熱 | 右手（擦汗）、眼睛（瞇眼、汗滴）、嘴巴（平嘴） | 沒有右手：不擦；沒有眼睛：沒有汗滴、沒有表情；第 1 頁只有大太陽和熱氣 |
| 陰 | 頭（轉頭）、眼睛（看雲） | 沒有眼睛：只轉頭；第 1 頁只有雲 |
| 霧 | 左手（摸索）、眼睛（瞇眼）、頭（探頭） | 沒有左手：只探頭、瞇眼 |
| 雷雨 | 右手（撐傘）、左手（一起握傘柄）、眼睛和嘴巴（驚嚇） | 只有右手：單手撐傘；沒有右手：不撐傘，有頭以後才有驚嚇；驚嚇的「o」嘴要第 8 頁 |
| 冷 | 雙手（輕抱）、嘴巴（白氣）、圍巾（拉高） | 只有右手：右手單手收在前面；沒有手：只縮脖子、微抖；第 8 頁前沒有白氣 |
| 雪 B | 頭與眼睛（抬頭看雪）、雙手（收在身體兩側） | 沒有眼睛：只抬頭；沒有手：只微縮 |
| 包袱 B1 | 第 14 頁（`bundle`） | 第 14 頁前不畫 |

沒有腳時（第 1–5 頁），整個角色落到地面（`GROUND_DY`），雨傘跟著手一起落下。

### 5.4 時間、優先順序與 Reduced Motion

| 天氣 | 週期 | 動作時間 | 關閉動畫時的代表格 |
|---|---|---|---|
| 晴 | 5.5 s | — | 2000 ms |
| 雨 | 4.2 s | — | 1050 ms |
| 熱 | 3.2 s | 抬手 0–0.65 s、擦三下 0.65–1.85 s（每下 0.4 s）、放手 1.85–2.5 s、休息 0.7 s（D-22） | 650 ms（手剛碰到臉頰） |
| 陰 | 6 s | 整段慢慢左右看 | 1500 ms（視線追著雲往右上） |
| 霧 | 5 s | 手慢慢伸出、收回 | 1250 ms（伸到一半） |
| 雷雨 | 撐傘晃 4.2 s | 驚嚇是事件，見下面 | 沒有閃電、沒有驚嚇的一格 |
| 冷 | 2.8 s | 0–0.7 s、1.4–2.1 s 微抖；0.8–1.7 s 白氣 | 2300 ms（兩陣發抖之間） |
| 雪 B | 4 s | 3.0–3.6 s 輕輕縮一下 | 2000 ms（正抬頭看雪），雪花不放在頭和臉的範圍 |

- 筆觸 6 FPS、雨 8 FPS；有姿勢動畫的天氣每 33 ms 重算一次（跟 P2 一樣）。
- 時間軸（W5 已實作，`MowanTimeline`）：
  - 一種天氣一條時鐘。姿勢、筆觸、雨雪、雲、包袱、圍巾、表情都從同一個時間取樣，不會不同步。
  - 換天氣時，時鐘從 0 開始、打雷排程重來。
  - 同一種天氣再設一次（天氣資料更新），或換頁、換紙帽、換包袱，時鐘都不動。
  - 各天氣的姿勢本身就是循環的，時鐘一直往前走也不會跳回起點。
  - 環境裡會移動的東西（雲、雨、雪）都在畫面外繞回去；熱氣在淡出時才換位置。
- 雷雨的驚嚇（W5 已實作）：
  - 間隔是隨機 15–40 秒，均勻分布。這是 D-19 的建議值，**還沒批准**；改 `MowanTimeline.THUNDER_MIN_MS`、`THUNDER_MAX_MS` 就好。
  - 換成雷雨後，第一次打雷也要等一個間隔，不會一開始就打。
  - 閃電出現約 0.3 秒，驚嚇表情約 0.4 秒，整個事件 0.8 秒。
  - 這是獨立事件，跟撐傘晃動的 4.2 秒週期無關。
  - 錯過的打雷一律跳過、不補播，包括：正在播更高優先的演出、關閉動畫、App 在背景。回來時最多只會看到「剛好還在 0.8 秒內」的那一次。
  - 閃電只是一個圖形，畫面不會閃。
- 優先順序（Confirmed）：成長故事 > 節日主要演出 > 節日待機 > 天氣日常動作 > 一般待機。
  - 天氣資料更新時，天氣**環境**（雨、雲、雪、太陽）立刻換。天氣**動作**只有在目前播的是「天氣日常」或「一般待機」時才換；更高優先的演出播完以後才輪到天氣動作。
  - 天氣沒有變（只是重新抓到同樣的天氣）時，姿勢時鐘不重來。
  - 打雷驚嚇只在「天氣日常」層觸發。剛好輪到時如果有更高優先的演出，這次整個跳過，閃電也不出現，不排隊補播。
  - 天氣環境可以跟節日演出同時存在（Confirmed）。
  - 手：
    - 雨傘只用右手。
    - 節日道具不能跟雨傘用同一隻手。
    - 左手拿著節日道具時，雷雨改回單手撐傘，冷天只用右手抱。
- Reduced Motion（W5 已實作，讀系統的「移除動畫」設定 `ValueAnimator.areAnimatorsEnabled()`）：
  - 只畫上表的代表格，筆觸固定第 1 張，不排任何重畫。
  - 雨、雪、雲停住。
  - 不打雷、不出現閃電。這段時間輪到的打雷直接跳過，打開動畫後也不補。
  - 雪花停在半空，但不放在頭和臉的範圍裡。
  - 天氣辨識需要的造型和道具都保留：雨傘、雷雨雲、雨絲、雪、霧、太陽、包袱、紙帽。測試確認靜止畫面畫出的部位跟會動時一樣。
  - 動畫重新打開時，時鐘照常繼續，不會卡在代表格。

### 5.5 包袱 B1：圖層、錨點、跟隨

- 圖層：
  - 在地面之後、傘柄之前，所以會在身體、手臂、圍巾、頭之後。
  - Android 的 `Mowan.Layer` 在 `GROUND` 和 `SHAFT` 之間加了 `BUNDLE`（W4 已實作）。
  - 背帶也畫在這一層，末端由之後畫的圍巾蓋住。
- 形狀：
  - 收口的布袋、兩道淡皺褶、兩個布角、一個結、三顆半透明 Ivory 碎花點，外加一條背帶。
  - 布用 Sage `#91A49B`（D-20 Confirmed）。
- 錨點與跟隨：
  - 以布結 (48, 73.6) 為支點。
  - 水平方向跟著身體：位移 = (`body[1].x` − 61.4) × 0.8 ＋ 頭部抖動 × 0.6。
  - 沒有腳時跟著整個角色落地。
  - 左手舉高：`raise` = clamp((96 − 左手末端 y) / 50)。
  - 往上提 1.6 × `raise`，以布結為中心轉 −6° × `raise`。
  - 沒有物理模擬。
- 48px 不畫。
- 第 14 頁取得後永久擁有，可以裝備、卸下（D-05 Confirmed）：跟紙帽一樣，「取得」看故事解鎖，「裝備」另外存在 prefs `mowan` 的 `bundleOn`（預設背著；故事從頭開始時回到預設）。目前切換入口只有除錯選單「墨丸的包袱」，給使用者的入口還沒設計。
- 布色 Sage `#91A49B`（D-20 Confirmed）。

### 5.6 需要修改的 Kotlin

- `mowan/Poses.kt`：
  - 加熱、陰、霧、雷雨、冷、雪 B 的姿勢，以及上半身抖動 `jitter`。
  - 加關鍵影格內插 `track` / `env`，照原型 `core.js`。
- `mowan/Mowan.kt`：
  - `Weather` 加 `HOT`、`CLOUDY`、`FOG`、`THUNDER`、`COLD`、`SNOW`。
  - `Face` 加 `HOT`、`FOG`、`COLD`、`STARTLE`。
  - 環境：大太陽、熱氣、雲（會飄）、雷雨雲、閃電、密雨、霧帶（前、後兩層）、雪花、積雪、白氣、汗滴。
  - 加 `BUNDLE` 圖層。
  - 霧帶前層和白氣畫在 `FRONT` 層。
- `mowan/MowanView.kt`：
  - 各天氣的代表格。
  - 打雷事件排程：隨機間隔；`isHigherPriorityPlaying()` 在 P6 之前固定回傳 false。
  - 靜止畫面不放擋臉的雪花。
- `mowan/MowanSettings.kt`：`weatherOf` 改成 8 種 Mood 都有對應，`THUNDER` 跟 `RAIN` 分開。
- 測試：
  - 黃金數值改從 `weather.js` 匯出。
  - `CapabilitiesTest` 的「第 14 頁跟第 13 頁一樣」要改，因為包袱開始畫了。

### 5.7 W1 比對結果（定稿參考 vs 已上線的晴、雨）

工具：`docs/prototypes/tools/check-weather-consistency.js`。

- **方法**：用 `weather.js` 的 `drawFrame` 和 App 已用黃金數值比對過的 `core.js` `Renderer`，在第 1–14 頁的一般、晴（4 個時間點）、雨（4 個時間點）各畫一次，只比角色本身的每一條筆畫，容差 0.011。
- **結果**：126 組裡有 103 組完全一致。
  - 晴天：14 頁全部一致。
  - 雨天：第 4–14 頁全部一致，包含雨傘握點 (82, 78) 與傘的角度。
- **不一致的兩處都不影響移植**，所以繼續 W2：
  1. **一般站姿的手臂**（第 4 頁起）：`weather.js` 為了關鍵影格內插，把放下的手改成 5 個控制點，App 的一般站姿是 4 個。兩者長度相同（左 21.67 vs 21.68、右 19.50 vs 19.53）、手尖位置相同，中心線最多差 0.36 單位（故事卡 128dp 高時約 0.3dp）。新天氣照參考用 5 點，一般站姿維持 App 現狀。
  2. **雨天沒有右手時（第 1–3 頁）**：`weather.js` 的 `PLAIN.rainy` 是預覽用，直接套雨天骨架；App 照已確認的規則，用一般站姿加皺眉、一筆下垂。新的雷雨在沒有右手時也用一般站姿，跟 App 的規則一致。
- 晴、雨的 Android 姿勢另外在 `WeatherPosesTest.shippedSunnyAndRainyMatchReference` 跟原型 `engine.js` 的 `pose()` 直接比對：控制點、頭的角度、表情、一筆、握點、傘的角度、5.5 s 與 4.2 s 週期都一致。

晴、雨的造型沒有修改。
