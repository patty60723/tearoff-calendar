# 墨丸 V3 — P7 計畫：正式化、首頁 UI/UX、相容性與 QA

> 日期：2026-10-29　分支：`feature/mowan-weather-poses`
> **狀態：計畫已審核（2026-10-29，D-15、D-48～D-52 定案）。目前執行 P7-0 與 P7-1 第一停點，完成後停下等待審核**。不開 PR、不合併 main；V3 在 P7-1 第二停點（第一停點核准後）才成為預設。
> 相關文件：[實作計畫 P7](character-implementation-plan.md#p7--整合效能qa)・[P6 結案報告](character-p6-acceptance.md)・[未定案事項](character-open-decisions.md)

## 0. 前提（2026-10-29 確認）

- App 還沒有正式上線：**沒有既有使用者、沒有舊版本相容性負擔**。不規劃舊火柴人退場、使用者遷移或產品回退。
- 正式版：V3 墨丸是唯一正式角色；移除舊火柴人的繪製路徑與除錯切換開關；清理不再使用的 ViewFlipper 輪播；以 V3 正式 golden 當未來的回歸基準；保留有價值的歷史文件與驗收素材。
- 刪除舊程式前先確認共用元件的依賴（第 2 節），不破壞日曆、天氣、小工具、故事。

## 1. 分階段

每階段結束停下來審核。順序照風險：先修測試可靠性，再一次把舊程式拿掉（之後所有驗收都只看 V3），再做 UI，最後是相容性、實機與完整回歸。

| 階段 | 內容 | 主要檔案 | 停點 |
|---|---|---|---|
| **P7-0** | 測試可靠性：`count_updates()` 少了開始或結束標記、或順序錯誤時 FAIL（不能當成 0 次） | `tools/emulator/quiet.sh`、`test-quiet.sh` | 小，跟 P7-1 第一停點一起審 |
| **P7-1 第一停點** | V3 正式幾何 golden＋代表組合 PNG golden（刪舊程式之前） | 測試、`app/src/test/resources/mowan/` | **golden 審核（不自行批准）** |
| **P7-1 第二停點** | 刪舊火柴人、ViewFlipper、6 張輪播、V3 除錯開關；新裝預設 V3；golden 前後一致 | 見第 2 節 | 清理審核 |
| **P7-2** | 首頁：城市入口精簡、拿掉日曆下方的獨立天氣列、彩蛋入口提案（第 3 節）。**先出候選圖、審核後才實作** | `activity_main.xml`、`MainActivity.kt`、strings | 候選圖審核 → 實作 → 視覺審核 |
| **P7-3** | 故事卡、日曆頁、小工具的狀態一致性 | 測試為主 | 一致性矩陣 |
| **P7-4** | 相容性：Android 8–14、不同 launcher、小工具尺寸調整與 WIDE | 模擬器 workflow、`WidgetHostActivity` | 模擬器矩陣 |
| **P7-5** | 真正跨午夜的日期與節日更新 | 模擬器腳本 | 模擬器結果 |
| **P7-6** | 效能：站姿重畫兩次、更新耗時、CPU、記憶體、耗電 | `MowanView.kt`、量測腳本 | 模擬器量測＋實機清單 |
| **P7-7** | 無障礙：Reduced Motion、TalkBack、觸控範圍、字體放大 | 版面、`contentDescription` | 檢查清單 |
| **P7-8** | 完整回歸：3 節日 × 8 天氣 × 14 頁 × 裝備 | 測試 | 回歸報告、P7 結案 |

### P7-0 測試可靠性

- `count_updates()` 找不到開始標記、找不到結束標記、或結束標記在開始標記之前時，印出 `error:…`（不是 0）並傳回非 0；`measure_window` 因此給出非數字 → `quiet_judge` 判定 FAIL（「第 i 次量測讀不到張數或更新次數」）。
- `test-quiet.sh` 補齊：正常（只算期間內、受測小工具）、兩個標記都有但中間沒有更新 → 0、少開始標記 → FAIL、少結束標記 → FAIL、順序錯誤 → FAIL、整條鏈（`quiet_judge` 收到這個結果 → FAIL）。
- **驗收**：腳本測試全部通過；故意改回舊行為（缺標記當 0）的版本會讓測試失敗；測試失敗時 CI 的「Emulator script self-test」步驟會紅（`exit 1`），模擬器腳本記成 `check FAIL`。

### P7-1 V3 正式化與舊程式清理

分兩個停點，**第一停點核准後才刪程式**。

**第一停點：V3 正式 golden（不刪任何東西）**

1. V3 正式幾何 golden：故事卡、日曆頁、小工具 4 種版面在一般天氣與 3 個節日的 `MowanBitmap.still`／`Mowan.frame()` 輸出的繪製指令幾何（JSON，跟既有 `golden*.json` 同一種比法）。
2. 代表組合 PNG golden：故事卡、日曆頁、小工具 4 種版面 × 一般天氣（晴、雨）與 3 個節日（春節、中秋、聖誕）。CI（Robolectric NATIVE）產生 PNG，跟存在 repo 的基準逐像素比。
3. 可以直接在 GitHub 看的原始對照圖（基準 PNG 本身就在 repo；另外產一張總覽圖）與差異報告。
4. **基準 PNG 標成「候選」，人工審核前不鎖定**：在審核前，PNG 比對只輸出報告、不讓 CI 紅；審核通過後才改成不一致就失敗（D-51）。

**第二停點：清理（第一停點核准後）**

- 刪除舊火柴人路徑、ViewFlipper、6 張輪播、V3 除錯開關；新安裝直接是 V3。
- 保留：故事部位、天氣台詞、故事 JSON 的 `action` 欄位（D-50）；`MowanView` 的 Reduced Motion、節日靜態姿勢、動畫倍率監聽不能刪。
- 小工具預覽不能變空白。
- V3 golden（幾何與 PNG）清理前後必須完全相同。
- 重跑 CI 與模擬器。

以下是第二停點的細項：

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

## 3. 首頁 UI/UX：城市入口與天氣彩蛋（2026-10-29 修訂）

> 依你提供的實機截圖修訂。原本「獨立天氣列＋細線圖示＋星芒」的方案**取消**（D-49）。之前測試裡組出的對照圖（`HomeRowsMockShotsTest`、`p7-home/`）屬於取消的方案，P7-2 會換成新的候選圖。

### 3.1 方向

- **刪除日曆下方獨立的天氣列**：日曆本身已經有天氣、溫度、日出日落。
- 不新增圖示列、閃光、額外卡片。
- 城市說明句「天氣和日出日落依臺北市　換城市 ›」改成精簡的「⌖ 臺北市 ⌄」（定位圖示＋城市名＋下拉符號）。
- 位置比較兩個：**A** 日曆上方右側；**B** 跟「前一天／後一天」導覽整合。
- 沿用現有的城市選擇對話框；觸控範圍 ≥ 48dp；TalkBack 有完整描述；320dp 與字體 200% 都要能用，不靠縮小字體擠成一行（放不下就換行）。
- 日曆之後直接接故事卡。
- 不改日曆本體、角色、日期、天氣資訊、故事卡的美術。

### 3.2 天氣彩蛋入口（只提案，不實作）

天氣列拿掉後，入口候選是**點日曆內的墨丸**。P7-2 要評估並提出：
- 跟撕頁手勢的衝突（點與拖的判斷、撕到一半）。
- 跟日曆頁既有的點擊事件（目前日曆頁的點擊行為）是否衝突。
- 無障礙入口（TalkBack 的自訂動作，不能只靠點小小的角色）。
- Reduced Motion 時的靜態回饋。

### 3.3 彩蛋的動作規則（D-48 Confirmed）

| 情況 | 觸發 |
|---|---|
| 一般日子、有天氣 | 重新播放既有的天氣反應；**等目前動作告一段落，或經由中性姿勢自然銜接，不得突然跳回第一格**（2026-10-30 正式確認） |
| 節日當天 | 只更新文字，不中斷節日演出 |
| Reduced Motion | 靜態回饋（文字＋代表姿勢） |
| 成長故事事件進行中 | 不打斷（優先順序：故事事件 > 主演出 > 待機 > 天氣） |

### 3.4 P7-2 的產出（先圖後做）

A／B 完整首頁候選圖、320dp、字體 200%、日曆接故事卡的流程、彩蛋入口建議。審核設計後才實作。

## 4. 各階段驗收條件（摘要）

| 階段 | 自動（CI） | 模擬器 | 只能實機 |
|---|---|---|---|
| P7-0 | 腳本測試（含少標記 → FAIL）；改壞版本會失敗 | 第 10 段照常 | — |
| P7-1 第一停點 | V3 幾何 golden；代表組合 PNG（候選，人工審核前不鎖定） | — | — |
| P7-1 第二停點 | 沒有舊火柴人的程式與開關；新裝即 V3；既有功能測試改寫後通過；golden 清理前後相同 | 回歸全部 PASS（拿掉的項目列出） | — |
| P7-2 | 城市入口 ≥ 48dp、TalkBack 描述、字體 200%／320dp 不截斷；彩蛋規則（三種情況） | 截圖、彩蛋錄影、TalkBack 焦點順序 | 實際手感、TalkBack 朗讀 |
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

## 6. 決策（2026-10-29 定案）

| # | 結論 |
|---|---|
| D-15 | V3 是唯一正式角色；P7-1 第二停點刪除舊火柴人，不保留 fallback |
| D-48 | 一般日子重新播放既有天氣反應，等目前動作告一段落或經由中性姿勢銜接、不得突然跳回第一格（2026-10-30 正式確認）；節日只換文字、不打斷；Reduced Motion 靜態回饋；入口依新首頁重新評估（3.2） |
| D-49 | **取消**獨立天氣列＋圖示＋閃光；日後天氣圖示另案設計 |
| D-50 | 保留 JSON `action` 欄位，不再依賴舊火柴人的動作清單 |
| D-51 | 幾何 golden＋代表組合 PNG；PNG 人工審核後才鎖定 |
| D-52 | App 圖示、小工具預覽換成墨丸；先出候選圖等美術審核 |
