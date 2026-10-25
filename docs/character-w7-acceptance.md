# 墨丸 V3 — W7 整合驗收報告

> 日期：2026-10-25　分支：`feature/mowan-weather-poses`
> **狀態：W7 結案**（獨立審查通過；驗收腳本的退出碼已修正並重跑，見文末「結案」）
> 驗收素材（截圖、GIF、影片、模擬器紀錄）：[`acceptance/mowan-w7` 分支](https://github.com/patty60723/tearoff-calendar/tree/acceptance/mowan-w7)
> 相關文件：[整合規格](character-integration-spec.md)・[實作計畫](character-implementation-plan.md)・[未定案事項](character-open-decisions.md)

## 結論

- **沒有 Blocker。** W7 找到 1 個功能問題（App 停在前景時重新打開系統動畫，墨丸會停住），已修正並在模擬器上確認。
- 先前保留的 3 項 Minor 維持 Minor，沒有改美術。另外記下 4 項新的 Minor。
- **沒有用實體手機測試。** 標成 Pass 的動態項目，證據都來自 Android Emulator（API 34）或 Robolectric 繪圖，不是實機。實機項目都列在「Not tested」。
- 建議進入下一階段（P5 Widget V3），48px 簡化版在 P5 一起處理；把 V3 改成預設（P7）之前，先用實機看一次動態與效能。

## 驗收環境與證據來源

| 來源 | 內容 | 能證明什麼 | 不能證明什麼 |
|---|---|---|---|
| **Robolectric（CI `Build APK`）** | 116 個 JVM／Robolectric 測試；W7 新增 `AcceptanceShotsTest`（素材）、`StoryCardTest` +2、`MowanViewTest` +1 | 邏輯、時鐘、像素是否改變、版面量測（Android 原生繪圖） | 真實的視窗、系統設定通知、效能 |
| **Android Emulator（CI `Emulator acceptance`）** | Pixel 6 設定檔、Android 14（API 34）、1080×2400、420 dpi、軟體繪圖（swiftshader）。腳本 `tools/emulator/w7-acceptance.sh`：寫入狀態 → 開 App → 截圖、錄影、點擊、`dumpsys gfxinfo` | 真的 APK 在真的 Android 上跑：顯示、點擊、保存、生命週期、系統「移除動畫」設定、密度與字體大小 | 實機 GPU 的流暢度與耗電；Android 13 以下；廠商字體 |
| **實體手機** | 無 | — | 全部列在 Not tested |

模擬器驗收以 [run #8](https://github.com/patty60723/tearoff-calendar/actions/runs/40165781624)（commit `0e1fb35`）為準；之後只改了測試與文件，App 程式碼相同。

## 驗收結果

### Pass（有驗證證據）

| # | 項目 | 證據 |
|---|---|---|
| 1 | 八種天氣都顯示在故事卡上 | `StoryCardTest.everyWeatherShowsOnTheStoryCard`；模擬器逐一切換並重開 App 截圖 `emulator/shots/weather-*.png`，沒有閃退 |
| 2 | 八種天氣完整循環：節奏、停頓、循環接縫 | `MowanTimelineTest.loopsCrossPeriodBoundariesSmoothly`（循環邊界沒有跳格）；每種天氣兩個循環的 GIF `robolectric/w7/*.gif`（15 FPS，跟 App 同一條時間軸取樣）；模擬器錄影 `emulator/video/*.mp4`。看過晴、冷、雷雨的逐格：沒有跳動或卡住。**實機手感另列 Not tested** |
| 3 | 雷雨驚嚇：觸發、表情、閃電、恢復 | 模擬器 45 秒錄影：第一次打雷在第 28–30 秒（落在 15–40 秒內），閃電約 0.3 秒，驚嚇表情後回到抱傘；逐格見 `robolectric/w7/thunder.gif`（示範每 2 秒打一次）；`MowanTimelineTest` 的排程、包絡、跳過不補播 |
| 4 | 晴天伸懶腰時包袱跟著動 | `robolectric/w7/layers-zoom.png`（開始伸、最高、放下 4 倍放大）、`sunny.gif`、模擬器 `sunny.mp4`：包袱一直跟著上半身，不脫離肩膀（靠近腋下見 Minor M3） |
| 5 | 雨傘、圍巾、包袱、紙帽的圖層與重疊 | `layers-zoom.png`：手臂在包袱前、背帶藏在圍巾下、紙帽在傘下；包含雷雨嚇一跳那一格。沒有錯誤的遮擋 |
| 6 | 第 1、3、4、5、8、13、14 頁的成長限制 | `robolectric/w7/growth.png`（7 頁 × 8 種天氣）：第 1 頁只有線和環境、第 3 頁沒有手、第 4 頁單手（雨天單手撐傘）、第 5 頁雙手、第 8 頁才有嘴、第 13 頁沒有包袱、第 14 頁有包袱；`StoryCardTest.growthPartsFollowThePage`；模擬器 `emulator/shots/page-*.png`，第 1–13 頁沒有包袱入口、第 14 頁有 |
| 7 | 包袱入口：顯示、點擊、保存、畫面更新 | Robolectric `bundleToggleOnlyAfterPageFourteen`、`story-card-bundle.png`；模擬器：用 `input tap` 實際點三下不閃退，`bundleOn=false` 寫進設定，重開 App 仍是「背上包袱 ›」；`bundle-toggle.gif` 錄到包袱消失、出現，伸懶腰沒有重來 |
| 8 | Reduced Motion 代表姿勢 | 模擬器打開系統「移除動畫」：8 種天氣的靜止畫面 `emulator/shots/reduced-*.png`，跟 `MowanTimeline.stillClock` 的代表姿勢一致（雷雨沒有閃電、冷天沒有白氣） |
| 9 | Reduced Motion 切換前後 | 模擬器：關閉時相隔 3 秒的兩張截圖 **0 個像素不同**；從背景回來打開動畫後 5 秒畫了 110 張；**留在前景關掉又打開後 5 秒畫了 118 張（W7 修正後）**。Robolectric：`reducedMotionOnTheStoryCard`（像素不變、包袱切換後仍靜止、打開後又動）、`reenablingAnimationsInTheForegroundRedraws` |
| 10 | 天氣更新、換頁、紙帽、包袱切換不會重設時鐘 | `MowanViewTest.pageHatAndBundleDoNotRestartTheMotion`、`StoryCardTest.sameWeatherAgainDoesNotRestartTheMotion`、`bundleToggleOnlyAfterPageFourteen`（切換前後時鐘一樣）；模擬器包袱錄影 |
| 11 | 第 14 頁背上／卸下包袱不閃退 | 模擬器點三下、重開 App，logcat 沒有 `FATAL EXCEPTION`；Robolectric 實際點擊 |
| 12 | 背景／前景不補播錯過的雷 | `StoryCardTest.backgroundDoesNotReplayMissedThunder`：打雷間隔固定 20 秒，App 在背景 30 秒（錯過第 20 秒的雷），回來時沒有驚嚇、時鐘沒重來，第 40 秒照常打雷；模擬器：背景 45 秒後回來，錄影前 3 秒沒有閃電 |
| 13 | V3 在 Reduced Motion 時不持續重畫 | `MowanViewTest.reducedMotionDrawsStillFrame`（不排重畫）；模擬器畫面像素完全不變。整個 App 仍有少量重畫，見 Minor M7 |
| 14 | 160px、96px 顯示 | `robolectric/w7/sizes.png`（真實像素、沒有縮放）：臉、表情、四肢、紙帽、圍巾、包袱都看得清楚 |
| 15 | 不同密度與字體大小的故事卡 | Robolectric 7 種組合（mdpi–xxxhdpi、字體 100%／130%／200%、320dp 窄螢幕）`story-card-*.png`，**每一種都檢查**：入口一行、沒有省略號、不跟故事文字重疊、在卡片內、至少 48dp 高；模擬器 5 種組合（320／420／560 dpi、字體 1.0／1.3／2.0）實測入口高度（96／126／168 px = 48dp）、位置在故事文字下方 |
| 16 | 舊火柴人模式、Widget、原有故事卡 | 既有測試全過（`MainActivityTest`、Widget 截圖）；`StoryCardTest.oldStickmanCardHasNoBundleToggle`；模擬器關掉 V3 後故事卡是舊火柴人、沒有包袱入口 |
| 17 | W1–W6 全部測試與 Golden | CI `Build APK` 116 個測試全過（含 `MowanGoldenTest`、`WeatherRenderGoldenTest`、`BundleTest` 等逐筆比對） |

### Minor（可接受，留待後續）

| # | 項目 | 觀察 | 建議 |
|---|---|---|---|
| M1 | 冷天抱身辨識度（W7 保留項） | 160px、96px 看得出雙手抱身；48px 只剩胸前一個小圈 | 維持；48px 簡化時一起處理（P5） |
| M2 | 雷雨紙帽與雨傘間距（W7 保留項） | 帽尖跟傘緣很近，但取樣的每一格（含嚇一跳）都沒有碰到 | 維持 |
| M3 | 晴天伸懶腰時包袱靠近腋下（W7 保留項） | 伸到最高時，包袱有一部分疊在身體線和圍巾尾端上；圖層正確，不會誤讀 | 維持 |
| M4 | 48px 顯示 | 紙帽（白色三角）、圍巾（紅）、包袱（綠點）還認得出；**表情看不出來**、四肢只剩約 1px 淡線 | 目前 App 和 Widget 都沒有 48px 的 V3。P5 做 Widget 時需要簡化版（規格已列） |
| M5 | 冷天白氣疊在右臉頰（新） | 白氣出現時那一團雲疊在臉的輪廓裡，小尺寸看起來像臉上的污點。這是原型的設計，黃金數值一致 | 美術決定是否把白氣往外移；這輪不改 |
| M6 | 雷雨閃電疊在傘緣（新） | 閃電約 0.3 秒，畫在傘右緣前面 | 看起來像閃電在傘後方的天空，可接受 |
| M7 | Reduced Motion 時 App 仍有少量重畫（新） | 關閉動畫後 10 秒有 38 張重畫，但畫面像素完全不變。V3 本身不排重畫（測試證明）。很可能的來源：App 日曆頁右下角的小人用 Widget 的 `ViewFlipper` 輪播，它不理會 Reduced Motion（P5 會一起處理）。對照組：舊火柴人故事卡關閉動畫時 10 秒 390 張（**舊火柴人不理會 Reduced Motion**，是既有行為） | P5 處理日曆頁的 `ViewFlipper`；P7 再量一次 |
| M8 | 模擬器密度截圖有一部分被切掉（素材問題） | 捲到底時大密度、大字體的故事卡上半部在螢幕外 | 完整卡片看 Robolectric 的 `story-card-*.png`；入口檢查不受影響 |

### Blocker（正式整合前必須修正）

無。

本輪修掉的功能問題：**App 停在前景時重新打開系統動畫，墨丸停住**（模擬器錄影相鄰影格差 0 像素），要到背景再回來才會動。修正：Android 13 以上用 `ValueAnimator.registerDurationScaleChangeListener`，設定一改就重畫（`MowanView.kt`）。修正後模擬器 5 秒畫了 118 張。Android 12 以下沒有這個通知，回到這個 App（從背景回來）就會恢復，見 Not tested。

### Not tested（環境限制，不能標成 Pass）

| 項目 | 原因 | 目前有的證據 |
|---|---|---|
| 實體手機上的動畫手感（節奏、停頓、抖動的感覺） | 沒有實機 | 模擬器錄影（軟體繪圖約 23 FPS，App 目標 30 FPS） |
| 實機效能：FPS、jank、CPU、耗電、長時間記憶體 | 沒有實機；模擬器是軟體繪圖，jank 比例（約 75%）不能代表實機 | 模擬器 10 秒：晴天 235 張、雷雨 234 張、沒有天氣 97 張；CPU 快照 7–28% |
| Android 8–12（API 26–32） | 模擬器只跑 API 34 | Robolectric 用 API 35；前景重新打開動畫在 API 32 以下要回到 App 才恢復（設計如此） |
| 不同廠商字體、極窄螢幕（< 320dp） | 沒有對應裝置 | Robolectric 320dp + 字體 130% |
| 雷雨驚嚇遇到更高優先的演出（成長故事、節日） | 那些演出還沒做（P6） | `MowanDirectorTest`、`MowanTimelineTest` 用假的優先順序測試 |
| 拿著節日道具時的天氣動作 | 節日還沒做（P6） | — |
| TalkBack 讀包袱入口 | 不在這輪範圍 | 入口是有文字的 TextView，可以被選取 |

## 這輪的修改

- **修正**：`MowanView` 註冊系統動畫設定的通知（Android 13 以上），App 留在前景時重新打開動畫，墨丸馬上恢復動作。
- **新的回歸測試**：
  - `StoryCardTest.backgroundDoesNotReplayMissedThunder`：背景時錯過的雷不補播、時鐘不重來，下一次照常打。
  - `StoryCardTest.reducedMotionOnTheStoryCard`：故事卡上的靜止畫面、包袱切換後仍靜止、打開後又動。
  - `MowanViewTest.reenablingAnimationsInTheForegroundRedraws`：設定通知到了就重畫。
  - `AcceptanceShotsTest.storyCardDensities`：7 種密度與字體組合下，包袱入口不截斷、不重疊、按得到。
- **驗收素材**：`AcceptanceShotsTest`（Robolectric）產生尺寸對照、成長 × 天氣、圖層放大、各密度故事卡、包袱切換、9 段動畫影格（CI 轉成 GIF）。
- **模擬器驗收**：`.github/workflows/emulator.yml` + `tools/emulator/w7-acceptance.sh`（只在這個分支和手動觸發時跑，約 17 分鐘）。結果推到 `screenshots/feature/mowan-weather-poses-emulator`；這份報告引用的那一次，另外存在 `acceptance/mowan-w7`。
- 沒有改角色造型、成長順序、故事、天氣文案、W3 天氣與 W4 包袱的美術。

## 已確認決策（這輪照做）

- V3 維持除錯開關控制，P7 才改成預設。
- 包袱切換維持故事卡文字入口，不加點擊墨丸切換。
- 舊版 `playWeather` 和 V3 天氣動畫的整合留到 P6／P7。
- D-19 雷雨間隔維持 15–40 秒：模擬器兩次錄影分別在第 28、30 秒左右打雷，沒有發現太頻繁或太久的問題。

## 結案（2026-10-25）

### 驗收腳本的退出碼

審查指出 `tools/emulator/w7-acceptance.sh` 的 `check()` 只印 PASS／FAIL、最後固定 `exit 0`，驗收失敗時 CI 仍可能顯示成功（之前靠 workflow 另外 grep 報告補救）。已修正：

- `check` 累計 PASS／FAIL 數量；**任何一項 FAIL，或必要檢查少跑（應該 23 項）**，腳本最後 `exit 1`。
- 讀不到畫面結構時，該項算 FAIL，不再默默跳過。
- 截圖、錄影、GIF 這類素材問題改成 **WARN**，只記錄，不算功能失敗。
- workflow 拿掉 grep 報告的步驟，直接由腳本的退出碼決定成敗（`reactivecircus/android-emulator-runner` 遇到非零就讓這一步失敗）；結果同時寫進執行摘要頁。
- 手動觸發時可以勾 `force_fail`，故意加一項失敗，確認 CI 會變紅。

### 重跑結果

| 執行 | 內容 | 結果 |
|---|---|---|
| [Emulator acceptance #12](https://github.com/patty60723/tearoff-calendar/actions/runs/40218717255)（`f942dcd`） | 一般驗收 | **PASS 23、FAIL 0、WARN 0**，執行成功 |
| [Emulator acceptance #13](https://github.com/patty60723/tearoff-calendar/actions/runs/40218720420)（`f942dcd`，`force_fail`） | 故意加一項失敗 | 執行中 |

### W7 最終狀態

- **結案**。Blocker 0；Minor 8 項（M1–M8）不阻擋 W7，M4（48px）與 M7（日曆頁 `ViewFlipper`）交給 P5。
- **Not tested 維持不變**：實機動畫手感、實機效能與耗電、Android 8–12、廠商字體等，不因結案改成 Pass。
- V3 繼續在除錯開關後面，P7 才改成預設。已驗收的造型、關節、天氣動作與包袱不再調整。
- 下一步：P5 Widget V3，計畫與待決策事項見[實作計畫 P5](character-implementation-plan.md#p5--widget-v3)。
