# 墨丸 V3 — P7-1 第二停點：移除舊火柴人與輪播（等待審核）

> 日期：2026-10-30　分支：`feature/mowan-weather-poses`
> 狀態：**完成，等待審核**（之後是 P7-2）。不開 PR、不合併 main。相關：[P7 計畫](character-p7-plan.md)・[P7-1 golden](character-p7-1-golden.md)

## 1. 前置：golden 鎖定（第一停點收尾）

| 項目 | 結果 |
|---|---|
| WIDE 補測對比（英文月份、中文月份、日期數字、節日名稱） | 見 [`p7-wide/2-contrast.md`](p7-wide/2-contrast.md)「其他文字」。日期數字（大字，目標 3:1）全部達標，最差 3.9；節日名稱（4.5:1）全部達標；**中文月份 8 月 4.36 未達 4.5**（沙金的深色）；**英文月份全部 3.52 未達 4.5**（ink_soft 疊在紙色上）。兩者都是既有配色（TALL、MEDIUM 同色），你已核准「目前文字配色全部保留」，所以沒有改，列為未達標 |
| PNG golden 鎖定 | `V3PngGoldenTest.LOCKED = true`（`b6ab0b8`）；CI #138：54/54 一致、最大通道差 0 |
| 幾何 golden | `V3GoldenTest` 54 組通過（每次 CI 都跑） |

## 2. 刪除與搬家的檔案

| 動作 | 檔案 |
|---|---|
| 刪除 | `app/src/main/java/com/patty60723/tearoff/stickman/StickmanView.kt` |
| 刪除 | `app/src/main/java/com/patty60723/tearoff/stickman/StickmanRenderer.kt` |
| 刪除 | `app/src/main/java/com/patty60723/tearoff/stickman/Pose.kt`（`Pose`、`Action`、`Actions`） |
| 刪除 | `app/src/main/res/drawable/stickman.xml` |
| 刪除（測試） | `PageMotionTest.kt`（日曆頁輪播的 Reduced Motion）、`WidgetBitmapBaselineTest.kt`（舊火柴人 6 張輪播的 bitmap 量測）、`story/PoseTest.kt` |
| 搬家 | `stickman/Parts.kt` → `story/Parts.kt`（故事解鎖的部位名稱；內容不變，只改套件） |
| 新增 | `res/drawable-nodpi/mowan_preview.png`（小工具預覽的預設圖）、`WidgetPreviewTest.kt` |

合計 39 個檔案，+226／−1489 行（`b6ab0b8..7e49755`）。

## 3. 程式裡拿掉的東西

- 4 個小工具版面：`ViewFlipper` 與 `frame0`～`frame5`；`mowan_still` 是唯一的角色，預設圖是 `mowan_preview`。
- 故事卡：`story_stickman`；`MowanView` 一律顯示、一律有「今天」（節日判斷不再看開關）。
- `MowanSettings.storyCard`／`setStoryCard`（新安裝就是 V3）。
- 除錯選單：「看火柴人的動作…」「故事卡用墨丸 V3」（其他項目順序往前移）。
- `MainActivity`：`updatePageMotion()`、日曆頁的動畫倍率監聽、`stickmanFlipper()`。
- `CalendarWidget`：`setStickman`、`FRAME_IDS`、`STILL_FLIP_MS`。
- `weather.Props`、`Mood.action`、`Mood.props`；`StoryBook.lastAction`、`lastProps`（`lastProps` 不再寫入）。
- 改名：`PageLayout.stickmanDp` → `characterDp`（數值不變）。

**保留**（沒有動）：`MowanView` 的 Reduced Motion、節日靜態姿勢、`MowanView` 自己的動畫倍率監聽；故事部位、天氣台詞（`Mood.keyword`、`Mood.line`）、日期資料；故事 JSON 的 `action` 欄位（D-50，故事檔沒有改；`StoryBook` 照樣存最近一次的 action，只是沒有人讀）；墨丸的美術、天氣、節日規則。

## 4. 小工具預覽

拿掉 ViewFlipper 以後，launcher 小工具清單的預覽（`previewLayout = widget_tall`）會沒有角色，所以 `mowan_still` 加了預設圖：
墨丸 V3 第 14 頁、紙帽、包袱、沒有天氣、沒有節日，TALL 大小 3x（跟小工具同一個 `MowanBitmap.still`，是已核准的畫法，不是新的美術）。

- `WidgetPreviewTest`：放進 repo 的預覽圖跟正式畫法逐像素相同、不是空白；4 個版面直接 inflate 時墨丸看得到。
- 實際程式畫上去之後一定會蓋掉預設圖；模擬器的宿主回報 `stillImage` 只算程式畫的圖（預設圖不算），所以預設圖不會讓檢查變成通過。
- D-52（App 圖示、小工具預覽換成墨丸，先出候選圖等美術審核）：這張預覽圖請一併審；**App 圖示還是舊的火柴人向量圖，沒有改**。

## 5. Golden 差異

| 時間點 | CI | 結果 |
|---|---|---|
| 清理前（鎖定後） | #138 `b6ab0b8` | 54/54 一致、最大通道差 0 |
| 清理後（預覽圖是佔位圖） | #139 `5f39d21` | 54/54 一致；310 個測試只有 `WidgetPreviewTest` 2 個失敗（預期，佔位圖） |
| 清理後（正式預覽圖） | #140 `7e49755` | **54/54 一致、最大通道差 0**；310 個測試全部通過；幾何 golden 通過 |

清理前後 54 張 PNG 完全相同。

## 6. 顯示 V3 的確認

| 地方 | 證據 |
|---|---|
| 新安裝（沒有任何設定）就是 V3 | `FestivalAppTest.v3IsTheOnlyCharacterFromAFreshInstall`（故事卡、日曆頁、節日第一次看到就演） |
| 故事卡 | `StoryCardTest`、`V3PngGoldenTest` 的 `story-*`；模擬器第 1、3、4、6 段 |
| App 日曆頁 | `V3PngGoldenTest` 的 `page-*`；`FestivalAppTest` |
| 小工具 4 種版面 | `WidgetV3Test.oneStillPictureInEveryLayout`（沒有 ViewFlipper、只有一個角色）、`V3PngGoldenTest` 的 `widget-*`；模擬器第 10 段（5 種大小）、第 11 段（Pixel Launcher 桌面） |
| 程式裡沒有舊的東西 | `stickman`、`ViewFlipper`、`storyCard` 在程式碼裡只剩註解與「不能有 ViewFlipper」的斷言 |

## 7. 模擬器回歸

| 執行 | 程式 | 結果 |
|---|---|---|
| #81 `7e49755`（最後一版） | 清理後 | **PASS 46、FAIL 0、WARN 0**；模擬器上的 `test-quiet.sh`、`test-recover.sh` 通過；沒有 ANR |
| #80 `5f39d21` | 清理後（預覽圖是佔位圖） | 通過 |
| #79 `b6ab0b8` | **清理前**（App 程式跟之前相同） | PASS 48、FAIL 3、WARN 10：又出現一次真的 ANR（Pixel Launcher），按「關閉應用程式」後對話框還在；帶回前景後 9 次點擊都沒反應，照規則記 FAIL。**原因 Unknown（#68 同樣的情況），沒有修好**。點了沒反應時的焦點與觸控紀錄在該次 artifact：https://github.com/patty60723/tearoff-calendar/actions/runs/41079232555/artifacts/12991425188（這個環境下載不了，我沒有看到內容） |

項目從 51 變 46，拿掉的 5 項（都是舊火柴人對照或 V3 開關）：

| 拿掉的項目 | 段 |
|---|---|
| 關閉動畫時 V3 不比舊火柴人多畫（改用 V3 自己的門檻：10 秒 ≤ 10 張，原本就有） | 第 6 段 |
| 舊火柴人沒有包袱入口 | 第 9 段（整段） |
| 小工具（舊火柴人）不受影響 | 第 10 段 |
| 桌面小工具：關掉 V3 後換回舊火柴人／打開 V3 後換回墨丸 → 改成「回到第 14 頁、雨天後更新」 | 第 11 段（2 項變 1 項） |
| 桌面小工具：節日當天關掉 V3／節日當天打開 V3 → 改成「節日當天回到第 14 頁、雨天」 | 第 11 段（2 項變 1 項） |

## 8. 尚未解決

| # | 項目 | 狀態 |
|---|---|---|
| 1 | 真的 ANR 關掉後點擊沒反應（#68、#79） | **Unknown**，沒有修好；功能檢查照樣 FAIL |
| 2 | WIDE 英文月份（3.52）、8 月中文月份（4.36）對比未達 4.5 | 既有配色，已核准保留；未改 |
| 3 | 小工具預覽圖、App 圖示（D-52） | 預覽圖請審；App 圖示沒改 |
| 4 | 實機 | Not tested |
