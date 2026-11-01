# 墨丸 V3 — P5 Widget V3 驗收報告

> 日期：2026-10-26　分支：`feature/mowan-weather-poses`
> **狀態：P5 結案**（2026-10-26 最終審核通過，Blocker 0）（V3 仍在除錯開關後面；改成預設是 P7）
> 驗收素材：[`acceptance/mowan-p5-4` 分支](https://github.com/patty60723/tearoff-calendar/tree/acceptance/mowan-p5-4)
> 相關文件：[實作計畫 P5](character-implementation-plan.md#p5--widget-v3)・[未定案事項](character-open-decisions.md)・[W7 驗收報告](character-w7-acceptance.md)

## 結論

- **沒有 Blocker。**
- V3 小工具在 4 種版面都只放 **1 張靜態圖**（新的 `mowan_still` ImageView）；ViewFlipper 在 V3 時隱藏、6 格清空、輪播間隔一天。舊火柴人的版面與輪播完全不變。
- 一次更新的 bitmap：xxhdpi **342 KB**（舊火柴人 2053 KB，少 83%），每次更新畫 4 張。
- **沒有用實體手機測試。** 證據來自 Robolectric 與 Android Emulator（API 34）。實機效能、耗電、舊 Android 版本、各家 launcher 都列 Not tested。

## 驗收環境與證據來源

| 來源 | 內容 | 能證明什麼 | 不能證明什麼 |
|---|---|---|---|
| **Robolectric（CI `Build APK`）** | JVM／Robolectric 測試（`WidgetV3Test`、`DetailTest`、`PageMotionTest`、`StoryCacheTest`、`WidgetBitmapBaselineTest`、`SimplifiedShotsTest` 等） | RemoteViews 內容、套用到版面的結果、像素比對、`estimateMemoryUsage`、重畫次數 | ViewFlipper 真的會不會輪播（Robolectric 的視窗被當成看不到，舊火柴人也不輪播）、launcher 行為、時間 |
| **Android Emulator（CI `Emulator acceptance`）** | Pixel 6、Android 14（API 34）、1080×2400、420 dpi、軟體繪圖。debug APK 的 `WidgetHostActivity` 用系統 `AppWidgetHost` 掛 5 個大小的小工具；Launcher3（Pixel launcher）的「加入主畫面」 | 系統真的套用 RemoteViews、版面大小、是否裁切、`gfxinfo` 重畫張數、桌面 launcher 的顯示與更新 | 實機 GPU 與耗電、其他 launcher、Android 13 以下 |
| **實體手機** | 無 | — | 全部列 Not tested |

## ViewFlipper：V3 真的靜止

**限制**（`javap` 查 android-all 14）：ViewFlipper 能從 RemoteViews 呼叫的只有 `setFlipInterval`，以及 ViewAnimator 的 `setDisplayedChild`／`showNext`／`showPrevious`；`setAutoStart`、`stopFlipping` 都不能遠端呼叫。版面檔寫了 `autoStart="true"`，所以 launcher 一定會開始輪播；而且 ViewFlipper 只在視窗看不到時才停，把它設成 GONE 並不會停。

**做法**（最小修改，不重排版面）：

1. 4 個版面檔在 ViewFlipper 後面加一個同大小、同位置、預設 GONE 的 `ImageView`（`mowan_still`）。
2. V3：ViewFlipper 設 GONE、6 格圖清空（`setImageViewResource(…, 0)`）、`setDisplayedChild(0)`、`setFlipInterval(1 天)`；`mowan_still` 顯示那張靜態圖。
3. 舊火柴人：反過來（ViewFlipper 顯示、`mowan_still` GONE 並清空），跟以前一樣輪播。

ViewFlipper 在 V3 時仍然「在跑」（launcher 回報 `flipping=true`），但它是 GONE、6 格沒有圖、一天才換一次，所以不會畫任何東西。

**證據**：

- Robolectric `WidgetV3Test.oneStillPictureInEveryLayout`：4 種版面 `mowan_still` 顯示、ViewFlipper GONE、間隔 = 1 天、6 格都沒有 drawable；`oldStickmanUnchangedWhenSwitchedOff`：關掉 V3 後跟舊的一樣（含可見度）；`switchingReappliesCleanly`：來回切換不留殘影。
- 模擬器（宿主）：5 種大小都 `flipper=GONE interval=86400000 still=VISIBLE stillImage=true`；穩定後 10 秒 `gfxinfo` **0 張**（舊火柴人同一個宿主 185 張）。

## Bitmap 與更新成本

Robolectric 量測，不是實機。`RemoteViews.estimateMemoryUsage()` 是系統檢查上限用的數字，算的是 RemoteViews 裡實際放的圖（同一個 Bitmap 只算一次）。測試會確認這個數字剛好等於 4 張 V3 圖的大小，沒有多放別的圖。

| 密度 | V3：4 版面合計 | 圖片張數 | 舊火柴人：4 版面 × 6 張 | Android 8–11 只送 TALL（V3） |
|---|---|---|---|---|
| mdpi | 37.9 KB | 4 | 227.4 KB | 12.0 KB |
| hdpi | 85.5 KB | 4 | 513.3 KB | 27.0 KB |
| xhdpi | 152.0 KB | 4 | 911.8 KB | 48.0 KB |
| xxhdpi | 342.2 KB | 4 | 2053.1 KB | 108.0 KB |
| xxxhdpi | 342.2 KB | 4 | 2053.1 KB | 108.0 KB |

- 系統上限（讀 AOSP 原始碼，沒有實測）：螢幕寬 × 高 × 6 bytes，1080×2400 約 15 MB。
- **一次更新**：畫 4 張靜態圖（每個版面 1 張，`MowanBitmap.renderCount` 測過）；故事 assets 整個 process 只解析一次（`StoryCacheTest`：第一次更新 1 次、之後 0 次）。
- **不必要的重畫**：只有日期換頁、天氣更新、尺寸變更、設定切換時才更新；V3 小工具本身不輪播。
- **Robolectric 與實機的差距**：Parcel 大小在實機上可能走共享記憶體；畫圖時間在 Robolectric 與模擬器（軟體繪圖）都不代表實機，所以**沒有**列更新時間。

## 模擬器結果

以 [Emulator acceptance #30](https://github.com/patty60723/tearoff-calendar/actions/runs/40284503759)（commit `f127bd5`）為準：**PASS 41、FAIL 0、WARN 0**。CI [Build APK #70](https://github.com/patty60723/tearoff-calendar/actions/runs/40284503714) 同一個 commit 通過（154 個 JVM／Robolectric 測試，0 個略過）。

### 10. 測試宿主（系統 `AppWidgetHost`，5 種大小）

| 項目 | 結果 |
|---|---|
| V3：COMPACT 115×115、WIDE 290×125、MEDIUM 150×195、TALL 175×290、BETWEEN 200×200 dp | 5 種都套用成功、ViewFlipper GONE、靜態圖顯示、角色整個在小工具裡、`clipped=0` |
| V3 靜止 | 綁定後第一段 10 秒畫了 11 張（還在收 `onUpdate`／尺寸變更；#29 同一段是 0 張，只記錄）；**穩定後 10 秒 0 張** |
| 舊火柴人（關掉 V3） | 5 種都是 ViewFlipper 顯示、V3 圖隱藏；10 秒畫了 185 張（輪播，跟以前一樣） |
| 密度 320、560（V3） | 5 種大小都套用成功、沒有裁切 |

截圖：`emulator/shots/widget-host-v3.png`、`widget-host-old.png`、`widget-host-v3-320.png`、`widget-host-v3-560.png`。

### 11. 真正的桌面 launcher（Pixel Launcher）

用 App 的「把小工具放到桌面」→ launcher 的「Add to home screen」對話框 → 按加入；`dumpsys appwidget` 確認是 launcher 掛的小工具。之後每一步改設定、送更新廣播、回到桌面截圖，跟上一張比：

| 步驟 | 結果（跟上一張差幾個像素） |
|---|---|
| 放上桌面（V3、第 14 頁、雨） | 截圖 `launcher-1-v3.png` |
| 卸下包袱 | PASS（303） |
| 脫下紙帽 | PASS（214） |
| 換成第 5 頁 | PASS（5730） |
| 天氣換成雪 | PASS（3578） |
| 關掉 V3 → 舊火柴人 | PASS（2427） |
| 打開 V3 → 墨丸 | PASS（5042） |
| 在 App 故事卡點「卸下包袱」（App 自己呼叫 `refreshAll`） | PASS（303） |

並排圖：`emulator/launcher-sequence.png`。

**腳本這輪的修正**（只改驗收腳本，App 程式碼沒變）：

- #27：「V3 靜止」量到 13 張，是綁定後還在收更新的那段；改成先量一段只記錄，再量穩定後的一段。
- #27、#28：uiautomator 讀不到 launcher 對話框的畫面結構 → 讀不到時按對話框右下角的加入位置，是否真的放上去由 `dumpsys appwidget` 判斷；小工具被放在第二頁，截圖前滑過去。
- #29：Pixel Launcher 在模擬器上 ANR，對話框蓋住 App，包袱點擊全部沒反應 → 關掉背景 ANR 顯示，點擊前如果還有對話框就關掉並記 WARN（#30 沒有出現）。

## 驗收結果

### Pass（有驗證證據）

| # | 項目 | 證據 |
|---|---|---|
| 1 | 4 種版面的顯示 | Robolectric `oneStillPictureInEveryLayout`、`textsAndLayoutUnchanged`；模擬器宿主 5 種大小（含 4 種版面之間的 200×200）；`robolectric/p5/widgets-v3.png` |
| 2 | 角色與文字不被裁切（尺寸、密度） | 模擬器宿主 `characterInside=true clipped=0`：420、320、560 dpi × 5 種大小；Robolectric `widgets-v3-densities.png` |
| 3 | V3 開關切換小工具的角色 | Robolectric `switchingReappliesCleanly`；**桌面 launcher** 關 V3 → 舊火柴人、開 V3 → 墨丸 |
| 4 | 紙帽、包袱、頁數、天氣更新後小工具跟著換 | Robolectric `followsTheSameStateAsTheApp`、`bundleToggleUpdatesTheWidget`；**桌面 launcher** 6 次更新都有變化 |
| 5 | App 日曆頁與小工具一致 | Robolectric `followsTheSameStateAsTheApp`（同樣狀態，日曆頁用完整版、小工具用簡化版）、`appPageScreenshot` |
| 6 | 舊火柴人路徑不受影響 | Robolectric `oldStickmanUnchangedWhenSwitchedOff`（6 張圖跟 `StickmanRenderer` 逐張一樣、可見度一樣）；模擬器宿主 185 張輪播；桌面 launcher 截圖 6 |
| 7 | ViewFlipper：V3 真的靜止 | 見上面「ViewFlipper」；模擬器穩定後 0 張 |
| 8 | Bitmap 預算反映實際放進 RemoteViews 的圖 | `WidgetV3Test.bitmapBudget`：`estimateMemoryUsage` = 4 張 V3 圖的大小；xxhdpi 342 KB |
| 9 | 沒有不必要的重畫 | 一次更新畫 4 張（`renderCount`）；故事只解析一次（`StoryCacheTest`）；V3 小工具不輪播 |
| 10 | Reduced Motion | 模擬器：關閉動畫時故事卡 0 張、整個 App 0 張；App 日曆頁 V3 不輪播（`PageMotionTest`）；小工具本來就是靜態代表姿勢 |
| 11 | 背景／前景、更新不卡住 | 模擬器：背景 45 秒回來不閃退、打開動畫後繼續動；launcher 連續 7 次更新都有反應；全程沒有閃退 |
| 12 | 既有 App 驗收（W7 項目） | 模擬器第 1–9 段全部 PASS（天氣、成長頁數、包袱、Reduced Motion、密度與字體） |

### Minor（不阻擋，已確認保留）

| # | 項目 | 說明 |
|---|---|---|
| m1 | V3 在小工具裡比舊火柴人細、淡 | 已確認可接受 |
| m2 | 48 px 紙帽不清楚、頭部輪廓偏重 | 已確認保留 Minor |
| m3 | 陰天、霧的環境辨識度弱 | 已確認保留 Minor |
| m4 | 日曆頁最上面是昨天那頁時不畫天氣，故事卡照樣演今天的天氣 | 已確認保留規則 |
| m5 | V3 時 ViewFlipper 仍然「在跑」（GONE、沒有圖、間隔一天） | RemoteViews 沒辦法停；不會重畫。要徹底拿掉得改版面結構（P7 再評估） |
| m6 | 綁定後幾秒內會收到多次更新（`onUpdate`、尺寸變更），每次重畫 4 張 | 系統行為；之後就靜止。實機成本沒量 |

### Blocker

無。

### Not tested

| 項目 | 原因 |
|---|---|
| 實機效能（一次更新的時間、記憶體） | 沒有實體手機；Robolectric 與模擬器（軟體繪圖）的時間不代表實機，所以沒有列時間 |
| 實機耗電 | 同上 |
| Android 8–13 | 模擬器只有 API 34；Android 8–11 只送一個版面（Robolectric 算過 bitmap，沒有實際跑） |
| 其他 launcher（三星 One UI、小米、Nova 等）的 ViewFlipper 與尺寸行為 | 只有 Pixel Launcher |
| 在 launcher 上拖拉改變小工具大小 | 自動化拖拉 resize 不穩定；改用測試宿主的 5 種大小與 `updateAppWidgetSize` 驗證尺寸（launcher 只測了預設 2×3） |
| 日期自動換頁（午夜廣播、鬧鐘） | 用手動送 `NEW_DAY` 廣播代替；系統排程沒有等到真的換日 |
| 實際天氣 API 更新後推到小工具 | 用假天氣（`fakeMood`）代替 |

## 留給 P7

1. 實機：一次更新的時間（目標 < 50 ms）、記憶體、耗電；低階機。
2. Android 8–13 與非 Pixel launcher 的相容性（特別是 ViewFlipper 的 autoStart、尺寸對應）。
3. 把 V3 改成預設後，是否拿掉舊火柴人與 ViewFlipper（拿掉的話可以一併移除 m5）。
4. 48 px 紙帽、頭部輪廓、陰天／霧辨識度（m2、m3）的美術審查。
5. launcher 上拖拉 resize 與真實換日的人工檢查。
