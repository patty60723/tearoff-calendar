# 墨丸 V3 — P7-3：狀態一致性（故事卡、App 日曆頁、桌面小工具）

> 日期：2026-11-03　分支：`feature/p7-3-consistency`（基準：`main` `f48443c` ＋ 合併 P7-4a `b332d95`）
> 狀態：**2026-11-03 驗收通過**（340 個測試、54 張 golden 一致、模擬器 56／56）。統一一致性矩陣全部通過；找到並修正 1 個不一致（換城市時故事卡的天氣）。不開 PR、不合併 main；沒有更新 golden 基準；沒有開始 P7-4b。
> 相關：[P7 計畫](character-p7-plan.md) §P7-3・[P7-4a 診斷](character-p7-4a-diagnosis.md)

證據分類：**CI（Robolectric，實際的 App 與小工具畫圖流程）**、**模擬器**、**實機（Not tested）**。

## 1. 怎麼取得「狀態摘要」

不拿設定值自己組預期的圖來比，而是從實際畫出來的東西讀：

| 顯示位置 | 怎麼讀 |
|---|---|
| App 首頁故事卡 | 把 `MowanView` 畫一次，`onFrame` 回報 `onDraw` 這一格實際用的部位、紙帽、包袱、天氣、節日、節日頭飾、是否靜態代表姿勢；頁數讀標題「第 N 頁」 |
| App 日曆頁 | 畫面上那張 `mowan_still` 的圖，對到 `MowanBitmap.onRender` 回報的參數，**再用同樣的參數重畫一次、逐像素相同**才算數；日期讀版面上的日與英文月份＋年，天氣讀日出日落那格的第一行 |
| 桌面小工具（COMPACT、MEDIUM、TALL、WIDE） | `CalendarWidget.onUpdated` 拿到**實際送給系統的** RemoteViews，取出四種版面大小各自那一份套用；其餘同日曆頁 |

這三個回報點是測試用的掛勾，正式執行時都是 null（不記錄、不留圖、不影響畫出來的東西）。底層狀態（`StoryBook`、`MowanSettings`、`WeatherService`、`AppClock`）只用來判斷規則。

## 2. 刻意不同的規則（不算不一致）

| # | 規則 | 依據 |
|---|---|---|
| 1 | 日曆頁顯示「最上面那張」，今天還沒撕時是**昨天**；故事卡與小工具一律是**今天** | 撕頁設計；故事卡不跟著翻頁（P7-2） |
| 2 | 日曆頁只有今天那頁畫節日外觀、天氣與天氣文字；不是今天那頁不畫（例如昨天那張不畫今天的天氣） | D-41 |
| 3 | 小工具一律畫今天那頁，不跟 App 的撕頁進度 | **D-55**（2026-11-03 確認） |
| 4 | App 停在前景跨午夜：故事卡與小工具換成新的一天；日曆頁暫時不動（保留畫的時候的節日與天氣），回到 App 才重算；背景抓到天氣時若已跨午夜或正在撕頁，也不重畫日曆頁 | D-44 |
| 5 | 故事卡會動（天氣日常、節日主演出／待機、點墨丸的反應）；日曆頁與小工具是靜態代表姿勢；Reduced Motion 時故事卡也是靜態代表姿勢。比較只看共同狀態，不比動畫的中間姿勢 | D-04、D-16、D-27、D-34 |
| 6 | 小工具角色小於 72dp 用簡化畫法；COMPACT 的節日只留頭飾與手上道具；日曆頁是完整版。狀態相同、畫法不同 | D-25、D-16、D-43 |
| 7 | 點故事卡的墨丸：標題與台詞換成天氣 6 秒（台詞不是狀態），不更新小工具 | D-48 |
| 8 | 換城市後，舊城市的天氣不再顯示：三處都是「沒有天氣」，直到新城市的天氣抓到 | 天氣資料綁城市 |
| 9 | 封面（第 0 頁）沒有日曆頁的墨丸 | 既有設計 |

應該完全相同的共同狀態：**部位（成長頁數）、紙帽、包袱、節日頭飾**（六個畫面一律相同）；**今天的節日與天氣**（故事卡、小工具，以及「今天那頁」的日曆頁）。

## 3. 統一一致性矩陣（`ConsistencyMatrixTest`）

| 測試 | 範圍 |
|---|---|
| `matrix` | 4 種日子（一般 2026-10-21、春節、中秋、聖誕）× 9 種天氣（8 種＋沒有天氣資料）＝ **36 種**；每一列輪換成長頁數（1、5、8、13、14）、紙帽、包袱、節日頭飾、今天撕了沒（沒撕時日曆頁停在昨天）、Reduced Motion。每種都實際開 App，讀故事卡、日曆頁、四種小工具共 **6 個畫面** |（**代表性組合**：每個維度的每個值都至少出現一次，並輪換搭配；**不是**完整排列組合，完整乘積留給 P7-8 只比規則）
| `tappingTheMowanChangesNoState` | 一般日子、中秋：點故事卡墨丸前後，存起來的狀態（prefs）完全相同、六個畫面的共同狀態相同、小工具沒有被更新 |
| `bundleToggleSyncsEverywhere` | 故事卡點「卸下包袱」→ 日曆頁、四種小工具同步；再背回去 |
| `hatAndHeadwearSyncEverywhere` | 春節：紙帽拿掉、節日頭飾不戴、紙帽戴回去（除錯選單，走跟正式同樣的「三處一起更新」） |
| `weatherUpdateSyncsEverywhere` | 真的天氣資料（Open-Meteo 格式）雨 → 背景抓到雪（`onWeatherRefreshed`）→ 三處一起換 |
| `cityChangeSyncsEverywhere` | 換城市（城市選擇對話框）→ 三處都沒有天氣 → 新城市的天氣抓到 → 三處一起換成陰。不連網 |
| `tearingSyncsGrowth` | 第 13 頁、日曆頁停在昨天 → 撕頁 → 第 14 頁（拿到包袱）三處一起換 |
| `festivalDayAcrossReopenAndMidnight` | 中秋當天 → 重開 App → 跨午夜（系統的日期廣播＋App 在前景：日曆頁暫時不動）→ 回到 App |
| `reducedMotionKeepsTheSameState` | 一般日子、聖誕：動畫開著與 Reduced Motion，六個畫面的共同狀態完全相同，只有故事卡的「靜態」不同 |
| `widgetStillIsTheStoryCardsRepresentativePose` | 9 種天氣（紙帽、包袱輪換）：四種小工具的靜態圖與故事卡 Reduced Motion 的代表姿勢是同一組狀態 |

每個情境的六個畫面摘要寫在 CI 的截圖分支 `screenshots/feature/p7-3-consistency` 的 `p7-3/*.md`。

## 4. 發現並修正的不一致

| # | 不一致 | 原因 | 修正 | 證據 |
|---|---|---|---|---|
| 1 | **換城市後，故事卡的墨丸還在演舊城市的天氣**；日曆頁與小工具已經沒有天氣（舊城市的資料不再顯示）。新城市的天氣要等下一次回到 App 才會抓 | `pickCity` 只重畫日曆頁與小工具，沒有更新故事卡、也沒有抓新天氣 | `pickCity` 改呼叫既有的 `refreshWeather()`（更新故事卡＋背景抓新城市的天氣，抓到後三處一起更新） | CI #174（修正前）：`cityChangeSyncsEverywhere` 失敗「故事卡 expected NONE but was RAINY」；修正後見 §5 |

另外 CI #174 有一個**測試本身**的錯誤（不是 App）：點墨丸後標題換成天氣「雨 22°（…）」，測試把 22 當成頁數。改成只讀「第 N 頁」。

## 5. 結果

| 類型 | 執行 | 結果 |
|---|---|---|
| CI Build APK（修正前） | [#174](https://github.com/patty60723/tearoff-calendar/actions/runs/41601930237)（`6019f01`） | 340 個測試、2 個失敗：`cityChangeSyncsEverywhere`（真的不一致，§4）、`tappingTheMowanChangesNoState`（測試本身讀錯頁數） |
| CI Build APK（修正後） | [#176](https://github.com/patty60723/tearoff-calendar/actions/runs/41602554329)（`0cbd47f`）、[#177](https://github.com/patty60723/tearoff-calendar/actions/runs/41602709559)（`4203877`，只改文件） | **全部通過**（340 個測試，其中 P7-3 新增 10 個；矩陣 36 種 × 6 個畫面）；腳本自我測試 `test-quiet.sh`、`test-recover.sh`、`test-diag.sh` 通過 |
| PNG golden（54 張鎖定） | #176 | **全部一致，最大通道差 0**；沒有更新基準 |
| 模擬器驗收（基準：main＋P7-4a） | [#97](https://github.com/patty60723/tearoff-calendar/actions/runs/41600995879)（`ccf948c`） | PASS 53、FAIL 0、WARN 0 |
| 模擬器驗收（P7-3 最後的 App 程式） | [#100](https://github.com/patty60723/tearoff-calendar/actions/runs/41602554256)（`0cbd47f`） | **PASS 56、FAIL 0**、WARN 1（自然發生的 launcher ANR，見下）。新增 3 項：App 卸下包袱後的桌面小工具＝同樣狀態直接更新的小工具（差 0 像素）；點故事卡墨丸後桌面小工具不變（差 0）；App 背回包袱後回到原樣（差 0） |

每個情境六個畫面的摘要：[`p7-3/matrix.md`](p7-3/matrix.md) 等（`bundle`、`hat`、`weather`、`city`、`tear`、`festival`、`reduced`、`tap`）。

### 5.1 模擬器 #100：自然發生的 launcher ANR（有證據）

第 4 段第一次點包袱之前，Pixel Launcher 跳出「沒有回應」（跟 #68／#79 同一個地方）。這次留下了證據（[截圖](p7-3/natural-anr-100.png)）：

| 證據 | 內容 |
|---|---|
| ANR 原因 | `am_anr`：`com.google.android.apps.nexuslauncher`，**Input dispatching timed out (Application does not have a focused window)** |
| 對話框開著時 | 截圖：頁面**停在最上面**（日曆頁佔上半，故事卡在下面），`setup` 的捲動沒有生效——跟人工注入看到的一樣 |
| 焦點 | 對話框開著：輸入焦點是對話框；關掉後：輸入焦點是 App、點擊位置最上層是 App；App 的連線 responsive、佇列 0 |
| 之後 | 修正後的流程：關掉對話框 → 重新捲到最底 → 3 下包袱**都在第 1 次點擊就切換**；後面各段照常 |

這是第一次在**自然發生**時拿到證據，而且跟人工注入的鏈條一致（對話框開著時頁面沒捲到最底）。**2026-11-03 審核：#100 記錄為「同類自然 ANR 已證實修正流程有效」**；**#68／#79 個別事件的根因仍是 Unknown**（那兩次的截圖與紀錄看不到），#100 只是間接佐證。

## 6. P7-4a 診斷工具在這個分支

| 執行 | commit | 結果 |
|---|---|---|
| [診斷 #5](https://github.com/patty60723/tearoff-calendar/actions/runs/41600995919) | `ccf948c`（main＋P7-4a） | 正常：對照 3／3、人工注入·前景（修正後）3／3、背景 3／3 都切換；舊流程 0／3、9 下都是 COORDINATE_MISS；腳本自我測試通過 |
| [診斷 #6](https://github.com/patty60723/tearoff-calendar/actions/runs/41602062118) | `a8888b3`（加上 P7-3 的模擬器檢查） | 同上 |
| 日常驗收 #97、#100 | — | 診斷收集只在出事時啟動：#97 沒有對話框、沒有快照；#100 自然發生時留下 6 份快照與分類資料（§5.1） |

結論：P7-4a 的診斷腳本、重新捲動修正、日常驗收的證據收集在 P7-3 分支都正常。

## 7. 審核決定（2026-11-03）

| # | 問題 | 決定 |
|---|---|---|
| 1 | 小工具一律畫今天那頁、不跟 App 的撕頁進度 | 列為正式決策 **D-55** |
| 2 | #100 自然發生的 launcher ANR | 記錄為「同類自然 ANR 已證實修正流程有效」；#68／#79 個別事件維持 Unknown，註明 #100 的間接佐證 |
| 3 | 36 種矩陣 | 明確寫成代表性組合覆蓋，不宣稱完整排列組合 |

## 8. 尚未驗證

| 項目 | 狀態 |
|---|---|
| 實機（各 launcher 的小工具更新時機、Samsung／MIUI 等） | Not tested |
| 真正跨午夜（不改時間） | Not tested（P7-5）；這次用假日期＋系統的日期廣播 |
| Android 8–13 的小工具（只送一個版面的路徑） | Not tested（P7-4b） |
| 模擬器的 Pixel Launcher 以外的 launcher | Not tested（P7-4b） |
| TalkBack | Not tested |
