# 墨丸 V3 — P7-2：首頁正式實作（城市入口 A、故事卡墨丸天氣彩蛋）

> 日期：2026-11-01　分支：`feature/mowan-weather-poses`（App 程式最後一個 commit `de1b87e`；模擬器腳本 `794256f`）
> 狀態：**實作完成，等待審核**。不開 PR、不合併 main。PNG golden 54 張維持鎖定、全部一致，沒有更新任何基準。
> 相關：[P7-2 設計文件](character-p7-2-home-candidates.md)・[未定案事項](character-open-decisions.md) D-48、D-53、D-54・[P7 計畫](character-p7-plan.md)

證據分四種，分開寫：**CI（JVM 單元測試）**、**Robolectric（CI 上跑，App 的實際版面與繪圖，不是實機）**、**模擬器**、**實機**。沒做的寫 Not tested。

## 0. 這次依照的決定（2026-10-31 最終互動決策）

| # | 決定 | 實作 |
|---|---|---|
| 1 | D-48：中性姿勢過渡，不等週期走完 | 第 2 節 |
| 2 | 成長故事事件進行中點墨丸：完全不反應 | 不換台詞、不打斷；TalkBack 也不提供點擊動作 |
| 3 | 不震動 | `hapticFeedbackEnabled=false`，程式沒有呼叫震動 |
| 4 | 不加點擊漣漪 | 故事卡墨丸沒有背景（城市入口照設計保留系統淡漣漪） |
| 5 | 城市入口 A：位置、大小、方向核准；最終美術細節還要對照原圖 | 原圖保留在 [`p7-home/6-a-all.png`](p7-home/6-a-all.png)、[`p7-home/round1/`](p7-home/round1/)，沒有刪；正式版對照見第 1 節 |

## 1. 首頁（城市入口 A）

- 拿掉日曆下方的天氣列與城市列；天氣、溫度、日出日落照舊在日曆卡片裡。
- 導覽列下方、日曆上方靠右：「⌖ 臺北市 ⌄」（`@id/city`）。圖示是 `ic_city_pin`／`ic_city_chevron`（跟候選圖同樣的路徑、線寬 1.8、ink_soft），大小跟著字（字高 ×1.15），字體放大時一起放大。
- 不加卡片、實心背景或外框；按下時只有系統淡漣漪（`selectableItemBackground`）。點了沿用原本的城市選擇對話框。
- 日曆頁、TearPageView、墨丸、天氣、節日美術都沒改。

### 1.1 正式首頁截圖（Robolectric，`HomeShotsTest`）

| 圖 | 內容 |
|---|---|
| [`p7-home-final/1-home-final.png`](p7-home-final/1-home-final.png) | 正式首頁整頁：411dp／320dp × 字體 100%／200%（縮成同樣寬並排） |
| [`p7-home-final/2-home-touch.png`](p7-home-final/2-home-touch.png) | 同上，標出可點範圍：紅＝城市入口、藍＝故事卡墨丸、橘＝包袱入口 |
| [`p7-home-final/report.md`](p7-home-final/report.md) | 量測 |

| 設定 | 城市入口 | ≥ 48dp | 城市名 | 故事卡墨丸 | 包袱入口 | 墨丸與包袱重疊 | 整頁高 |
|---|---|---|---|---|---|---|---|
| 411dp 100% | 103×48dp | 是 | 完整 | 96×128dp | 66×48dp | 否 | 1271dp |
| 411dp 200% | 168×48dp | 是 | 完整 | 96×128dp | 127×48dp | 否 | 2063dp |
| 320dp 100% | 103×48dp | 是 | 完整 | 96×128dp | 67×48dp | 否 | 1318dp |
| 320dp 200% | 168×48dp | 是 | 完整 | 96×128dp | 128×48dp | 否 | 2732dp |

跟候選圖 A（`p7-home/report.md`）比：城市入口大小、整頁高四種設定都一樣。第一次實作時城市入口是 107dp 寬（右內距 12dp），改成 8dp 後跟候選圖一樣 103dp（CI #148 量到、`d4c354f` 修正）。

**最終美術細節（間距、圖示線寬、位置）還沒經過你對照原圖審核**，不標成 Pass。

### 1.2 城市入口 TalkBack（設定，不是實際朗讀）

- 內容描述：「地點：臺北市。天氣與日出日落依這個城市計算」；點擊動作的說明：「換城市」。
- 換完城市：`announceForAccessibility`「已換成○○，天氣與日出日落已更新」。
- 實際朗讀與焦點順序：**Not tested**（沒有在實機開 TalkBack）。

## 2. 故事卡墨丸的天氣彩蛋（D-48）

### 2.1 點擊的判斷（`MowanView.reactionNow()`）

| 情況 | 結果 |
|---|---|
| 成長故事事件進行中（`MowanDirector` 是 GROWTH_STORY） | **NONE**：不換台詞、動作不變；`performClick()` 回傳 false；TalkBack 不提供點擊動作，描述是「墨丸正在演故事」 |
| 節日當天 | LINE_ONLY：只換台詞，主演出／待機不中斷 |
| Reduced Motion | LINE_ONLY：只換台詞，墨丸保持靜態代表姿勢 |
| 沒有天氣資料 | 台詞「天氣讀取中…」並重新抓天氣，動作不變 |
| 過渡或重播的那一輪還沒播完 | LINE_ONLY：不重啟動作，只把「6 秒後回到故事」重新計時 |
| 其他（一般日子、有天氣） | REPLAY：台詞換成天氣台詞，動作經由中性姿勢重新開始這一輪 |

優先順序沿用 `MowanDirector`（成長故事 > 節日主演出 > 節日待機 > 天氣日常 > 待機），沒改。

### 2.2 動畫怎麼銜接（不新增美術）

- 新的 `PoseBlend`：在**既有的兩個姿勢**之間插值（目前的姿勢 → 這個天氣週期的起始姿勢）。點、角度、位移都是線性內插加 smoothstep 緩動；表情、汗滴、白氣這種分段的東西在過渡的一半切換。
- 過渡時間依兩個姿勢差多遠決定：`300 + 距離×8` ms，限制在 300–600ms（0.3–0.4 秒只當參考：離起點近時約 0.3 秒，手舉最高時較久，讓手臂、頭、身體不會瞬間彈回）。就在起點時不用過渡。
- 過渡中，姿勢的時鐘停在起點；過渡結束後從起點往下播，所以**不會跳回第一格**。
- 環境（雲、雨、霧）照原本的時鐘走，跟點擊無關。
- 雷雨：只重播撐傘的動作，**打雷照原本的隨機排程，點擊不會強制打雷**。
- 連續點：過渡加上那一輪播完以前都算「播放中」，再點只延長台詞，不重啟動作。

### 2.3 觸控

- 唯一的入口是故事卡上的 `MowanView`（96×128dp）。日曆頁的墨丸維持靜態，**TearPageView 沒有改**。
- 用一般的點擊：手指移動超過系統的觸控閾值就算捲動，不會觸發；點擊也不會捲動頁面。
- 「卸下包袱」入口是另一個 View，不重疊，點墨丸不會觸發它，點它也不會觸發墨丸。
- 不震動、不加漣漪。

### 2.4 TalkBack（設定，不是實際朗讀）

| 情況 | 內容描述 | 點擊動作說明 |
|---|---|---|
| 一般日子 | 墨丸，今天（天氣） | 讓墨丸對天氣做出反應 |
| 節日當天 | 墨丸，今天是春節 | 聽墨丸說一句話 |
| 沒有天氣資料 | 墨丸 | 重新讀取天氣 |
| 成長故事事件進行中 | 墨丸正在演故事 | **不提供**（沒有點擊動作） |

故事卡台詞設成 `accessibilityLiveRegion=polite`，換台詞時 TalkBack 會唸出新台詞。**實際朗讀 Not tested**。

## 3. 測試

### 3.1 JVM（`WeatherReactionTest`，只測姿勢與時間軸）

| 測試 | 內容 |
|---|---|
| `noJumpWhenTapped` | 7 種天氣 × 7 個點擊時刻（含動作最大的地方、播了好幾輪以後）：點下去那一刻角色的每一筆都不動（差 < 1e-6） |
| `smoothDuringTheBlendAndEndsAtTheCycleStart` | 過渡 300–600ms；過渡中每 16ms 一格最多移動 4 個單位（角色約 120 寬）；過渡結束剛好等於這一輪的起點 |
| `blendIsLongerWhenThePoseIsFartherFromTheStart` | 就在起點是 0；起點附近 300–360ms；伸最高時比較久 |
| `environmentClockAndThunderAreUnchanged` | 雷雨：點了以後環境時鐘、雨、打雷的時間與次數完全一樣 |
| `oneCycleCountsAsReplayingThenTapsRestartAgain` | 過渡＋一輪都算播放中；播完才能再重播；換天氣就清掉 |
| `reducedMotionStaysStill` | Reduced Motion 時不過渡、保持靜態代表姿勢 |

### 3.2 Robolectric（`HomeEggTest`，整個 App）

| 測試 | 內容 |
|---|---|
| `weatherRowAndCityRowAreGoneAndTheCityEntryIsAboveTheCalendar` | 天氣列、城市列不在了；城市入口在日曆上方、≥ 48dp |
| `tapOnANormalDayShowsTheLineAndReplaysFromTheNeutralPose` | 一般日子：換天氣台詞、開始過渡，6 秒後回到故事 |
| `tappingAgainOnlyExtendsTheLine` | 再點不重啟動作，台詞從第二次點重新計時 |
| `festivalDayOnlyChangesTheLine` | 春節主演出中點：只換台詞，主演出的模式與開始時間不變 |
| `reducedMotionOnlyChangesTheLine` | 只換台詞、靜態 |
| `growthStoryEventIgnoresTapsAndOffersNoClickToTalkBack` | 不換台詞、`performClick` 回傳 false、沒有點擊動作 |
| `noWeatherDataRefreshesAndSaysLoading` | 「天氣讀取中…」 |
| `scrollingIsNotATap`、`tapDoesNotScrollThePage` | 在墨丸上拖動是捲動、不觸發；點擊不捲動 |
| `bundleEntryAndTheMowanDoNotInterfere` | 墨丸與包袱入口互不觸發 |
| `talkBackDescriptionsAndActions` | 上表四種描述與動作 |
| `calendarPageMowanStaysStatic` | 日曆頁的墨丸沒有點擊、TearPageView 的觸控不變 |

另外 `StoryCardTest`、`ScreenshotTest` 改成點故事卡的墨丸（原本點天氣列）。

### 3.3 修測試時找到的問題

| CI | 失敗 | 原因與處理 |
|---|---|---|
| #148 | `WidgetV3Test.bitmapBudget` 數到 7 張（應為 4） | 前一個測試開 App 時在背景真的下載天氣，抓到後重畫，落在下一個測試裡。`WidgetV3Test` 開始時標成剛抓過天氣（只改測試） |
| #148／#149 | `festivalDayOnlyChangesTheLine` 停在 BASE | Robolectric 裡 View 不算「在畫面上」，`checkVisible` 不會把今天交給狀態機；測試直接交給它再重播主演出（只改測試） |
| #149／#150 | `bundleToggleUpdatesTheWidget` 開 App 是閃退畫面 | **App 程式的問題**：小工具 `onUpdate` 抓天氣時 `goAsync()` 在不是從廣播呼叫的情況（測試直接建立小工具）是 null，背景執行緒結束時 NPE、寫下閃退記錄，下次開 App 就顯示閃退畫面。改成 `pending?.finish()`（`de1b87e`）。系統送來的廣播一定有 PendingResult，實機上預期不會碰到；這個推論**沒有實機驗證** |

## 4. PNG golden（54 張鎖定）

CI #151（`de1b87e`）與 #153（`794256f`）：**54 張全部一致（最大通道差 0）**，沒有預期變更、沒有更新任何基準。幾何 golden（`V3GoldenTest`）也通過。報告：截圖分支 `p7-golden/report.md`。

## 5. 模擬器

Emulator acceptance #90（`794256f`）：**PASS 51、FAIL 0、WARN 0**，全程沒有閃退。裝置：sdk_gphone64_x86_64、Android 14（API 34）、1080×2400、密度 420。結果在 `screenshots/feature/mowan-weather-poses-emulator` 分支（`report.txt`、`shots/egg-*.png`）。

新增的第 13 節（關閉動畫，只看台詞造成的像素變化，不判斷動畫本身）：

| 檢查 | 結果 |
|---|---|
| 城市入口 ≥ 48dp | PASS：105×48dp（模擬器像素換算 dp 的取整，Robolectric 是 103×48） |
| 城市入口在日曆上方 | PASS |
| 從墨丸身上滑動是捲動、不觸發彩蛋 | PASS：捲回原位後故事卡差 0 像素 |
| 點故事卡上的墨丸：台詞換成天氣 | PASS |
| 6 秒後回到故事 | PASS：跟點之前差 0 像素 |

第 1–12 節（天氣、撕頁、包袱、小工具、節日等既有檢查）全部 PASS。

- 前一次 #89（`de1b87e`）第 13 節「6 秒後回到故事」FAIL：截圖看得到 App 已經回到故事，失敗是**腳本**的問題——台詞長短不同，故事卡高度跟著變，頁面捲在最底時 ScrollView 會位移，腳本卻用點擊前量的位置裁切。`794256f` 改成每次截圖前捲回最底、重新量位置。App 沒有改。
- **觀察（請你判斷）**：頁面捲到最底時點墨丸，台詞變短使故事卡變矮，畫面內容會往下位移一段；回到故事時再變回來。這不是 App 主動捲動，是版面高度改變；實機上看起來是否明顯，**Not tested**。
- 模擬器沒有驗證：過渡動畫的觀感（動畫關閉）、TalkBack 朗讀、實機觸控手感。

## 6. 保留事項（不得標成 Pass）

| 項目 | 狀態 |
|---|---|
| 實機手感（點擊、捲動區分、過渡是否自然） | **Not tested** |
| 頁面在最底時點墨丸，故事卡高度改變造成的畫面位移 | 模擬器觀察到，是否要處理請你判斷；實機 Not tested |
| TalkBack 實際朗讀、焦點順序 | **Not tested** |
| 城市入口 A 最終美術細節（對照原圖） | 等你審核 |
| 200% 字體：「回到最上面那頁」直排；日曆內「晴 25°」與「忌」重疊（截圖 320dp／411dp 200% 看得到） | P7-7（D-54），不是 Pass |
| WIDE 英文月份 3.52:1、8 月中文月份 4.36:1 | P7-7 |
| #68／#79 ANR | P7-4，原因 Unknown；這次沒有宣稱修復 |
| App 圖示 | 尚未更新（D-52），沒有套用小工具預覽圖 |
