# 墨丸 V3 — P5 Widget V3 驗收報告

> 日期：2026-10-26　分支：`feature/mowan-weather-poses`
> **狀態：草稿（等模擬器結果）**（V3 仍在除錯開關後面；改成預設是 P7）
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
- 模擬器（宿主）：5 種大小都 `flipper=GONE interval=86400000 still=VISIBLE stillImage=true`；`gfxinfo`：見下面「模擬器結果」。

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

見下方「結果」與 [模擬器 run](#結果)（填入最終 run）。

## 驗收結果

（填入）
