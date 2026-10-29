# 墨丸 V3 — P7 計畫：正式化、首頁 UI/UX、相容性與 QA

> 日期：2026-10-29　分支：`feature/mowan-weather-poses`
> **狀態：計畫，等待審核。還沒開始寫程式**（不刪舊火柴人、不切換 V3 預設、不開 PR、不合併 main）。
> 相關文件：[實作計畫 P7](character-implementation-plan.md#p7--整合效能qa)・[P6 結案報告](character-p6-acceptance.md)・[未定案事項](character-open-decisions.md)

## 0. 前提（2026-10-29 確認）

- App 還沒有正式上線：**沒有既有使用者、沒有舊版本相容性負擔**。不規劃舊火柴人退場、使用者遷移或產品回退。
- 正式版：V3 墨丸是唯一正式角色；移除舊火柴人的繪製路徑與除錯切換開關；清理不再使用的 ViewFlipper 輪播；以 V3 正式 golden 當未來的回歸基準；保留有價值的歷史文件與驗收素材。
- 刪除舊程式前先確認共用元件的依賴（第 2 節），不破壞日曆、天氣、小工具、故事。

## 1. 分階段

每階段結束停下來審核。順序照風險：先修測試可靠性，再一次把舊程式拿掉（之後所有驗收都只看 V3），再做 UI，最後是相容性、實機與完整回歸。

| 階段 | 內容 | 主要檔案 | 停點 |
|---|---|---|---|
| **P7-0** | 測試可靠性：`count_updates()` 少了開始或結束標記時 FAIL（不能當成 0 次） | `tools/emulator/quiet.sh`、`test-quiet.sh` | 小，跟 P7-1 一起審 |
| **P7-1** | V3 正式化、舊火柴人與 ViewFlipper 清理、V3 正式 golden | 見第 2 節 | 清理範圍與新 golden 審核 |
| **P7-2** | 首頁天氣彩蛋與城市切換（第 3 節） | `activity_main.xml`、`MainActivity.kt`、strings、新的小圖示 | 視覺審核（截圖） |
| **P7-3** | 故事卡、日曆頁、小工具的狀態一致性 | 測試為主 | 一致性矩陣 |
| **P7-4** | 相容性：Android 8–14、不同 launcher、小工具尺寸調整與 WIDE | 模擬器 workflow、`WidgetHostActivity` | 模擬器矩陣 |
| **P7-5** | 真正跨午夜的日期與節日更新 | 模擬器腳本 | 模擬器結果 |
| **P7-6** | 效能：站姿重畫兩次、更新耗時、CPU、記憶體、耗電 | `MowanView.kt`、量測腳本 | 模擬器量測＋實機清單 |
| **P7-7** | 無障礙：Reduced Motion、TalkBack、觸控範圍、字體放大 | 版面、`contentDescription` | 檢查清單 |
| **P7-8** | 完整回歸：3 節日 × 8 天氣 × 14 頁 × 裝備 | 測試 | 回歸報告、P7 結案 |

### P7-0 測試可靠性

- `count_updates()` 找不到開始標記或結束標記時印出錯誤（不是 0），`measure_window` 傳回讀不到 → `quiet_judge` 判定 FAIL（「第 i 次量測讀不到張數或更新次數」）。
- `test-quiet.sh` 新增：少開始標記 → FAIL、少結束標記 → FAIL、兩個都有但中間沒有更新 → 0。
- **驗收**：腳本測試全部通過；故意拿掉標記的改壞版本會讓測試失敗。

### P7-1 V3 正式化與舊程式清理

- 移除：故事卡的 `StickmanView`、日曆頁與小工具的 ViewFlipper（6 張輪播）、舊火柴人的繪製（`StickmanRenderer`、`Pose`、`Action`／`Actions`）、除錯選單的「故事卡用墨丸 V3」與「看火柴人的動作…」、`MowanSettings.storyCard`、`updatePageMotion()`（日曆頁輪播的 Reduced Motion 處理）。
- 小工具版面：拿掉 `ViewFlipper` 與 `frame0`～`frame5`，`mowan_still` 是唯一的角色；`STILL_FLIP_MS` 與「間隔拉到一天」的繞路一起拿掉（P5 的 m5 自然解決）。
- 保留並改名／搬家：見第 2 節的「保留」欄（故事的部位名稱、故事 JSON 的 `action` 欄位等）。
- **V3 正式 golden**：
  - 既有的幾何 golden（`golden*.json`）照舊。
  - 新增小工具與 App 畫面的像素基準：4 版面 × 代表天氣 × 節日／非節日的 `MowanBitmap.still` 結果存成 PNG（或雜湊），之後改壞了 CI 會紅。基準本身要先人工看過才收（D-51）。
  - 舊火柴人相關的 golden、baseline（`WidgetBitmapBaselineTest` 的舊量測）變成歷史文件，不再當回歸基準。
- 測試：刪掉只測舊火柴人的測試；「V3 開關關閉」的測試改成「V3 是唯一角色」；`StoryEngineTest` 不再拿舊動作清單驗證故事的 `action`（D-50）。
- 模擬器腳本：拿掉舊火柴人對照組（第 6 段 Reduced Motion 對照、第 9 段、第 10 段舊火柴人宿主、第 11 段 V3 開關切換）；改成 V3 自己的門檻（例如關閉動畫時 10 秒 ≤ 10 張，不再跟舊火柴人比）。
- **驗收**：
  - `grep` 不到 `stickman` 套件、`StickmanView`、`ViewFlipper`、`storyCard`（文件與歷史素材除外）。
  - 新裝 App（沒有任何 prefs）打開就是 V3：故事卡、日曆頁、小工具。
  - 日曆、天氣、撕頁、故事文字、城市、午夜換頁、小工具 4 種版面、bitmap 預算、故事 JSON 快取全部照舊（既有測試改寫後通過）。
  - 小工具一次更新的 bitmap 總量不增加（拿掉 6 張輪播後應該只少不多）。
  - 模擬器回歸全部 PASS（項目數會因為拿掉舊火柴人對照組而變少，報告要列出拿掉了哪些、為什麼）。

### P7-3 狀態一致性

同一個時間點，故事卡、App 日曆頁、桌面小工具看到的是同一個狀態：

| 狀態 | 來源 | 檢查 |
|---|---|---|
| 今天、節日 | `AppClock.today`（含除錯假日期） | 三處同一天；只有「今天那頁」畫節日（D-41） |
| 成長頁數 | `StoryBook` | 撕頁後三處同時更新 |
| 紙帽、包袱、節日頭飾 | `MowanSettings` | 切換後三處同時更新（小工具靠 `refreshAll`） |
| 天氣 | `WeatherService` | 今天那頁與故事卡、小工具一致；不是今天那頁不畫天氣 |

- 自動測試：每種狀態改變之後比對三處的「狀態摘要」（日期、節日、頁數、裝備、天氣），不比像素。
- 模擬器：撕頁、換裝備、換天氣、換日之後，桌面小工具截圖與 App 一致（已有一部分，P6-4c）。

### P7-4 相容性、小工具尺寸與 WIDE

- **Android 版本**：模擬器 workflow 改成矩陣：API 26（Android 8.0）、28、30、33、34。Android 8–11 走「只送一個版面」（`PageLayout.fitting`），Android 12 以上走尺寸對應的 RemoteViews。
- **Launcher**：API 34 的 Pixel Launcher（已有）；舊版本模擬器的 Launcher3／Quickstep。各廠牌 launcher 只能實機（Not tested 清單）。
- **WIDE**：P6-4c 的小工具宿主 290×125dp 被系統套用成 COMPACT。先查原因（宿主給的尺寸、`OPTION_APPWIDGET_SIZES`、dp 換算）再修宿主，讓 WIDE 真的被選到；桌面上用 `appwidget` 指令或拖拉調整尺寸，四種版面都截圖。
- **尺寸調整**：小工具從 COMPACT 拉大到 TALL、再縮回，每一步版面正確、角色在小工具裡、沒有截斷（宿主已有 `characterInside`、`clipped` 檢查）。

### P7-5 真正跨午夜

- 模擬器（google_apis 映像可以 `adb root`）：關掉自動時間，把時間設成 23:59:30，等過午夜。
  - App 在前景：故事卡換成新的一天與節日、日曆頁不動（D-44）、撕頁狀態不變；回到 App 才重算。
  - App 在背景：午夜鬧鐘（`MidnightAlarm`）讓小工具換頁；節日當天換上節日外觀、隔天換掉。
  - 除夕 → 春節初一、12/24 → 12/25、中秋前一天 → 中秋，三組。
- 如果映像不能改時間：列為 Not tested，不用假日期代替。

### P7-6 效能

- **站姿筆觸重畫兩次**（P6-4a 補查）：筆觸間隔改成無條件進位；驗證非節日／節日站姿、長時間的影格節奏（例如 10 分鐘內筆觸張數與重畫次數）、Reduced Motion。
- **模擬器量測**（只能當比較，不代表實機）：一般站姿、天氣動畫、節日演出、節日待機的 10 秒重畫次數；小工具一次更新的耗時（`onUpdate` 到 `updateAppWidget`）。
- **實機才能量**：frame time、CPU、記憶體、長時間放著的耗電、小工具更新耗時（第 5 節）。

### P7-7 無障礙

- Reduced Motion：故事卡、節日演出、天氣彩蛋都有靜態回饋（已有的代表姿勢）；整個 App 關閉動畫時幾乎不重畫（已有模擬器檢查）。
- TalkBack：天氣列、城市列、撕頁按鈕、包袱入口、日曆頁都有清楚的描述與動作名稱；故事卡的墨丸有描述（例如「墨丸撐著傘」）。
- 觸控範圍 ≥ 48dp：**現在的天氣列、城市列只有約 36dp**（文字 15sp／13sp 加上下各 8dp），P7-2 一起修。
- 字體放大到 200%、小螢幕 320dp：沒有截斷、沒有重疊（已有故事卡的檢查，擴大到首頁兩列與日曆頁）。

### P7-8 完整回歸

- 3 節日（＋沒有節日）× 8 天氣（＋沒有天氣）× 14 頁 × 裝備（紙帽、包袱、節日頭飾各兩種）：自動測試確認每一種都畫得出來、不當掉，而且符合規則（雨傘、插槽、降級層級、只有今天那頁畫節日）。
- 像素比對只用 P7-1 的正式 golden（代表組合）；完整乘積只比規則，不比像素（數量太多、人工看不完）。
- 報告：Pass／Minor／Blocker／Not tested，P7 結案。

## 2. 舊程式清理範圍與依賴分析

### 2.1 會刪掉的

| 東西 | 位置 | 誰在用 | 處理 |
|---|---|---|---|
| `StickmanView` | `stickman/StickmanView.kt`、`activity_main.xml` 的 `story_stickman` | `MainActivity`（故事卡、點天氣、除錯預覽） | 刪；故事卡只有 `MowanView` |
| `StickmanRenderer` | `stickman/StickmanRenderer.kt` | `CalendarWidget.setStickman`（小工具與日曆頁的 6 張輪播） | 刪；`setMowan` 是唯一路徑 |
| `Pose`、`Action`、`Actions` | `stickman/Pose.kt` | `MainActivity`、`CalendarWidget`、`DebugMenu`、`StoryEngineTest`、`PoseTest` | 刪（D-50：故事 JSON 的 `action` 欄位怎麼處理） |
| ViewFlipper＋`frame0`～`frame5` | 4 個小工具版面 | `CalendarWidget`、`MainActivity.updatePageMotion`、日曆頁尺寸設定 | 刪；只留 `mowan_still` |
| `STILL_FLIP_MS`、`setFlipInterval` 繞路 | `CalendarWidget` | V3 時隱藏 ViewFlipper | 刪 |
| `drawable/stickman.xml` | 版面的預設圖 | 小工具版面的 `ImageView` | 刪（`mowan_still` 沒有預設圖時要確認小工具預覽，見下） |
| `MowanSettings.storyCard`／`setStoryCard` | `MowanSettings` | `MainActivity`、`CalendarWidget`、`DebugMenu`、很多測試、模擬器腳本 | 刪；V3 永遠開 |
| 除錯選單：「故事卡用墨丸 V3」「看火柴人的動作…」 | `DebugMenu` | — | 刪；其他除錯項目保留（假日期、跳頁、假天氣、紙帽、包袱、節日重播、節日頭飾、看墨丸 V3） |
| `updatePageMotion()`、`scaleListener`（日曆頁輪播） | `MainActivity` | 日曆頁 ViewFlipper 的 Reduced Motion 處理 | 刪（V3 日曆頁是靜態圖） |
| `weather.Props`、`Mood.action`、`Mood.props` | `weather/Weather.kt` | 只有舊火柴人用（草帽、太陽、傘…） | 刪（`Mood.keyword`、`Mood.line` 保留） |
| `StoryBook.lastAction`、`lastProps`（讀） | `StoryBook` | 只有舊火柴人用 | 刪讀取；prefs 的值留著不讀也可以（沒有上線，不需要清） |

### 2.2 會保留（共用）

| 東西 | 原因 | 處理 |
|---|---|---|
| `stickman/Parts.kt`（部位名稱：`LINE`、`LEG_R`、`VOICE`、`PAPER_HAT`、`BUNDLE`…） | 故事解鎖的部位（`StoryEngine.unlocked`、`StoryBook.parts`），V3 用 `Mowan.partsOf` 換算 | 保留；搬到 `story` 套件並改名（例如 `story/Parts.kt`），不是舊火柴人專用 |
| 故事 JSON 的 `action` 欄位 | V3 還沒有故事動作（D-10）；之後設計成長故事動作時可能用到 | D-50：保留欄位但不驗證／或拿掉 |
| `PageLayout.stickmanDp` | 小工具上角色的寬（V3 也用） | 改名成 `characterDp`（只改名，數值不變） |
| `Mood.keyword`、`Mood.line` | 天氣文字、點天氣時故事卡的那句話 | 保留 |
| `WidgetHostActivity`（debug） | 模擬器驗收 | 保留，拿掉 ViewFlipper 相關的回報欄位 |
| 歷史文件、驗收素材（`acceptance/*` 分支、`docs/character-*`） | 審核紀錄 | 保留；文件裡提到舊火柴人的地方加註「P7 已移除」，不改寫歷史 |

### 2.3 要先確認的

- **小工具預覽**（`calendar_widget_info.xml` 的 `previewLayout` 是 `widget_tall`）：拿掉 ViewFlipper 與預設圖以後，launcher 小工具清單裡的預覽會不會變成沒有角色；可能要給 `mowan_still` 一張預設的 V3 靜態圖（vector 或 PNG）。
- **App 圖示**（`ic_launcher_foreground.xml`）：**是**「一頁日曆，上面站著火柴人」的向量圖。要不要換成墨丸是美術決定（D-52）；不換也不影響清理（它是獨立的向量圖，不依賴 `stickman` 套件）。
- **封面頁**（`page_cover.xml`）：沒有火柴人（只有紙堆、山丘、色帶、細線），不受影響。
- 所有測試、模擬器腳本裡的 `storyCard` 切換：先列清單，改寫後再刪（第 1 節 P7-1）。

## 3. 首頁 UI/UX：天氣彩蛋與城市切換

> 我沒有收到你提到的 App 截圖；下面的「現在」是用 Robolectric 畫的實際 `MainActivity`（App 的字型與顏色）。對照圖：`app/build/screenshots/p7-home/`（CI 截圖分支；方案的 View 只在測試裡組出來，正式程式沒改）。

### 3.1 現在

- 天氣列：「陰 26°（21–27°）　點我看看 ›」，15sp，一般文字連結的樣子；沒有天氣圖示。
- 城市列：「天氣和日出日落依臺北市　換城市 ›」，13sp。
- 兩列都只有約 36dp 高（< 48dp）；沒有 TalkBack 描述（只讀出文字）。
- **點天氣時 V3 的實際行為**：故事卡的標題換成天氣、文字換成「墨丸：下雨了，撐起小傘。」，6 秒後回到故事。**墨丸本身沒有新的動作**：V3 故事卡平常就在演今天的天氣（天氣日常），點下去天氣沒變，所以動作不會重來（`sameWeatherAgainDoesNotRestartTheMotion`）。舊火柴人才有「演一段」。**V3 的天氣彩蛋要演什麼，需要你決定（D-48）**。

### 3.2 方案（對照圖 `1-phone-411dp.png`、`2-small-320dp-font130.png`、`3-360dp-font200.png`）

| | 方案 1（建議） | 方案 2 |
|---|---|---|
| 天氣列 | 細線天氣圖示＋「陰 26°」＋淡色「21–27°」＋尾端極小、極淡的手繪星芒 ✦ | 星芒疊在圖示右上角（不佔寬度） |
| 城市列 | 左邊「⌖ 臺北市 ⌄」，右邊低對比小字「天氣・日出日落」 | 置中兩行：「⌖ 臺北市 ⌄」，下面小字 |
| 分隔 | 兩列之間一條極淡的短線（`paper_edge` 色） | 同左 |

共同規則：
- **天氣列整列可以點**（≥ 48dp 高），沒有「點我看看」、沒有箭頭、沒有按鈕外框；星芒約 7dp、透明度約 55%。
- 點下去：觸發墨丸的天氣反應（D-48），不開新頁面；按壓時只有很淡的漣漪（或不要漣漪，D-49）。
- TalkBack：天氣列「今天陰，26°，21–27°。點一下，墨丸會對天氣做出反應」；城市列「地點：臺北市。天氣與日出日落依這個城市。點兩下可以換城市」（`accessibilityDelegate` 給動作名稱）。
- 城市列點下去：沿用現在的城市選擇對話框。
- 不新增大型卡片、不用高彩度實心按鈕；顏色只用現有的 `ink`、`ink_soft`、`paper_edge`。
- 天氣圖示：8 種天氣各一個細線圖示，線寬跟日曆的細線一樣（D-49：要不要做、用哪一套）。
- 沒有天氣資料時（「天氣讀取中…」）：不顯示星芒、不能點，或點了只重新抓天氣（現在的行為）。
- 字體 200%、320dp：方案 1 的城市列兩邊可能擠在一起，需要時右邊小字換到下一行（對照圖會看到）。

### 3.3 彩蛋的動作規則（建議，等 D-48）

| 情況 | 點天氣 |
|---|---|
| 一般日子、有天氣 | 天氣反應從頭演一次（例如雨天重新撐傘、晴天重新伸懶腰），故事卡顯示那句話 6 秒 |
| 節日當天 | 不演天氣動作（D-31：節日當天不播天氣日常）；正在播主演出時不打斷；只換故事卡的那句話。或演節日待機（D-48 的選項） |
| Reduced Motion | 不動；故事卡換成那句話，墨丸停在代表姿勢（靜態回饋） |
| 成長故事事件進行中 | 不打斷（優先順序：故事事件 > 主演出 > 待機 > 天氣） |

## 4. 各階段驗收條件（摘要）

| 階段 | 自動（CI） | 模擬器 | 只能實機 |
|---|---|---|---|
| P7-0 | 腳本測試（含少標記 → FAIL）；改壞版本會失敗 | 第 10 段照常 | — |
| P7-1 | 沒有舊火柴人的程式與開關；新裝即 V3；既有功能測試改寫後通過；正式 golden | 回歸全部 PASS（拿掉的項目列出） | — |
| P7-2 | 兩列 ≥ 48dp、TalkBack 描述、點擊行為（三種情況）、字體 200%／320dp 不截斷 | 截圖、點天氣錄影、TalkBack 開啟時的焦點順序 | 實際手感、TalkBack 朗讀 |
| P7-3 | 三處狀態摘要一致 | 撕頁、換裝備、換天氣、換日後桌面小工具一致 | — |
| P7-4 | `PageLayout.fitting` 各尺寸 | API 26／28／30／33／34；WIDE 真的被選到；尺寸調整 | 各廠牌 launcher |
| P7-5 | — | 真的跨午夜（前景、背景、三個節日） | 實機跨午夜 |
| P7-6 | 筆觸每秒 6 張、重畫 ≤ 7 次 | 重畫次數、小工具更新耗時（比較用） | frame time、CPU、記憶體、耗電 |
| P7-7 | 描述、觸控範圍、字體放大 | Reduced Motion 不重畫（已有） | TalkBack 實際朗讀 |
| P7-8 | 完整乘積的規則檢查；代表組合的像素 golden | — | — |

## 5. 只能用實機驗證的項目

- 動畫流暢度與 frame time（30 FPS 姿勢、6 FPS 筆觸）。
- CPU 喚醒頻率、記憶體、長時間放著的耗電（故事卡停在前景、小工具放一天）。
- 小工具更新耗時（午夜換頁、天氣更新）。
- 各廠牌 launcher（Samsung One UI、MIUI 等）的小工具尺寸與縮放、WIDE 的實際選擇。
- Android 13 以下實機（模擬器能補一部分）。
- 真的跨午夜（實機不改時間）。
- TalkBack 實際朗讀與操作手感。
- 48dp 小工具在實機上的辨識度。

## 6. 需要你決定（P7 開始前）

| # | 問題 | 建議 |
|---|---|---|
| D-48 | V3 的天氣彩蛋演什麼（現在點天氣，V3 只換文字，墨丸沒有新動作） | 一般日子：天氣反應從頭演一次；節日當天：只換文字、不打斷演出；Reduced Motion：靜態回饋（3.3） |
| D-49 | 天氣圖示與按壓回饋 | 8 種細線圖示（同日曆線寬）；按壓只有很淡的漣漪 |
| D-50 | 故事 JSON 的 `action` 欄位（舊火柴人的動作名稱） | 保留欄位（之後成長故事動作可能用），但不再驗證必須是舊動作 |
| D-51 | V3 正式 golden 的形式 | 幾何 golden 照舊＋小工具與 App 代表組合的 PNG 基準（人工看過才收） |
| D-52 | App 圖示、小工具預覽圖如果還是舊火柴人 | 換成墨丸（美術另外審） |
| D-15 | 舊 stickman 程式碼 | **改成：P7-1 刪除**（App 未上線，不保留 fallback） |
| — | 首頁方案 1 或方案 2 | 方案 1 |
