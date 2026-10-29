# 墨丸 V3 — P7-1 第一停點：正式 golden（等待審核）

> 日期：2026-10-29　分支：`feature/mowan-weather-poses`
> **狀態：候選，等待人工審核。PNG 基準尚未鎖定（`V3PngGoldenTest.LOCKED = false`），不自行批准。**
> 還沒刪任何舊程式、V3 仍在除錯開關後面（第二停點才切換）。相關：[P7 計畫](character-p7-plan.md)・[未定案事項](character-open-decisions.md)

## 1. 內容

| 項目 | 檔案 | 狀態 |
|---|---|---|
| V3 正式幾何 golden | `app/src/test/resources/mowan/golden-v3.json`（`mowan/V3GoldenTest`） | **生效**：每次 CI 都比，不一致就失敗 |
| 代表組合 PNG golden | `app/src/test/resources/v3-png/*.png`（`V3PngGoldenTest`） | **候選**：不一致只寫報告，審核通過後改 `LOCKED = true` |
| 總覽圖 | [`docs/p7-golden/overview.png`](p7-golden/overview.png) | 只給人看，不拿來比 |

組合（兩種 golden 相同）：故事卡、App 日曆頁、小工具 COMPACT／MEDIUM／WIDE／TALL × 一般晴、一般雨、春節晴、中秋晴、聖誕晴 = **30 組**。共同條件：第 14 頁（全部部位）、紙帽戴著、包袱背著、節日頭飾開、美術設定為已確認的預設。

- **幾何 golden**：每一筆的部位、圖層、填色、描邊、線寬、透明度、漸層、路徑（小數 5 位，容許 1e-4），以及頭與傘的變換。小工具與日曆頁用跟 `MowanBitmap.still` 一樣的參數（密度 3、`Detail`、靜止時鐘、版面的小尺寸取捨：COMPACT 極簡、其他 CUE）；故事卡是完整畫法、時間 0、沒有演出（節日是站姿）。日曆頁的角色寬度用 411dp 手機的算法（60.33dp）。
- **PNG golden**：Robolectric NATIVE 繪圖、411dp xxhdpi。小工具是整個小工具（版面最小尺寸，含文字）；日曆頁、故事卡是 `MainActivity` 實際的 View（今天撕完、第 14 頁；節日故事卡是主演出結束後 100ms 的站姿）。每個通道容許差距 2，超過就算不同；輸出 `actual/`、`diff/`（不同的像素標紅）、`report.md` 到截圖分支的 `p7-golden/`。

## 2. 對照圖（基準本身，可在 GitHub 直接看；點圖看原尺寸）

| 情況 | 故事卡 | 日曆頁 | COMPACT | MEDIUM | WIDE | TALL |
|---|---|---|---|---|---|---|
| 一般・晴（2027-10-21） | [<img src="../app/src/test/resources/v3-png/story-normal-sunny.png" width="180">](../app/src/test/resources/v3-png/story-normal-sunny.png) | [<img src="../app/src/test/resources/v3-png/page-normal-sunny.png" width="110">](../app/src/test/resources/v3-png/page-normal-sunny.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-normal-sunny.png" width="110">](../app/src/test/resources/v3-png/widget-compact-normal-sunny.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-normal-sunny.png" width="110">](../app/src/test/resources/v3-png/widget-medium-normal-sunny.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-normal-sunny.png" width="180">](../app/src/test/resources/v3-png/widget-wide-normal-sunny.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-normal-sunny.png" width="110">](../app/src/test/resources/v3-png/widget-tall-normal-sunny.png) |
| 一般・雨（2027-10-21） | [<img src="../app/src/test/resources/v3-png/story-normal-rainy.png" width="180">](../app/src/test/resources/v3-png/story-normal-rainy.png) | [<img src="../app/src/test/resources/v3-png/page-normal-rainy.png" width="110">](../app/src/test/resources/v3-png/page-normal-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-normal-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-compact-normal-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-normal-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-medium-normal-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-normal-rainy.png" width="180">](../app/src/test/resources/v3-png/widget-wide-normal-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-normal-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-tall-normal-rainy.png) |
| 春節・晴（2027-02-06） | [<img src="../app/src/test/resources/v3-png/story-spring.png" width="180">](../app/src/test/resources/v3-png/story-spring.png) | [<img src="../app/src/test/resources/v3-png/page-spring.png" width="110">](../app/src/test/resources/v3-png/page-spring.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-spring.png" width="110">](../app/src/test/resources/v3-png/widget-compact-spring.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-spring.png" width="110">](../app/src/test/resources/v3-png/widget-medium-spring.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-spring.png" width="180">](../app/src/test/resources/v3-png/widget-wide-spring.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-spring.png" width="110">](../app/src/test/resources/v3-png/widget-tall-spring.png) |
| 中秋・晴（2027-09-15） | [<img src="../app/src/test/resources/v3-png/story-moon.png" width="180">](../app/src/test/resources/v3-png/story-moon.png) | [<img src="../app/src/test/resources/v3-png/page-moon.png" width="110">](../app/src/test/resources/v3-png/page-moon.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-moon.png" width="110">](../app/src/test/resources/v3-png/widget-compact-moon.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-moon.png" width="110">](../app/src/test/resources/v3-png/widget-medium-moon.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-moon.png" width="180">](../app/src/test/resources/v3-png/widget-wide-moon.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-moon.png" width="110">](../app/src/test/resources/v3-png/widget-tall-moon.png) |
| 聖誕・晴（2027-12-25） | [<img src="../app/src/test/resources/v3-png/story-xmas.png" width="180">](../app/src/test/resources/v3-png/story-xmas.png) | [<img src="../app/src/test/resources/v3-png/page-xmas.png" width="110">](../app/src/test/resources/v3-png/page-xmas.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-xmas.png" width="110">](../app/src/test/resources/v3-png/widget-compact-xmas.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-xmas.png" width="110">](../app/src/test/resources/v3-png/widget-medium-xmas.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-xmas.png" width="180">](../app/src/test/resources/v3-png/widget-wide-xmas.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-xmas.png" width="110">](../app/src/test/resources/v3-png/widget-tall-xmas.png) |

## 3. 證據

| 類型 | 結果 |
|---|---|
| 本機 JVM | `V3GoldenTest` 通過；改動 golden 裡一個座標 0.001 → 失敗（指出 `widget-compact/spring 第 5 筆（bundle）`） |
| CI Build APK #115（`aa54fbe`） | 通過；`V3PngGoldenTest` 產生 30 張候選（報告：30 張「沒有基準」，因為還沒有基準） |
| CI Build APK #116（`f7bf5de`，加入基準後） | 通過；30 張**全部一致，最大通道差 0**（同一份程式重畫逐像素相同，繪圖是確定的） |
| 模擬器 #59（`8c9d3be`）、#60（`aa54fbe`） | PASS 48、FAIL 3、WARN 9。兩個小工具靜止檢查用新的 `count_updates` 都是 PASS（0 張、0 次更新）；腳本自測通過。**FAIL 3 都是第 4 段包袱入口**（3 下都沒切換、狀態沒存、重開還是背著） |
| 模擬器 #58（`fbd73de`，P7 開始前） | **同樣的 3 個 FAIL**；它的 App 程式跟通過的 #57（`576b19f`）完全相同（中間只改文件與測試）。P7-0／P7-1 沒有改 App 程式（`git diff 576b19f HEAD` 在 docs、test 以外只有 `tools/emulator/quiet.sh`、`test-quiet.sh`） |
| 模擬器 #61（`f7bf5de`） | 執行中，結果未納入 |
| 實機 | Not tested |

### 模擬器包袱 FAIL（P7 開始前就有，原因 Unknown）

- 觀察：#60 的 `bundle-2-off-full.png` 拍到「Pixel Launcher isn't responding」對話框蓋在畫面中間（約 y 1020–1440 px），包袱入口在 y 960–1086 px，點擊位置落在對話框範圍內；腳本每次都記到「系統跳出其他 App 的『沒有回應』對話框，已關掉」。
- 推測（**未確認**）：點擊被 launcher 的 ANR 對話框吃掉。Robolectric 的包袱測試照常通過，App 程式與通過的 #57 相同。
- 這次沒有處理（不在 P7-0／P7-1 第一停點範圍）。建議另外處理：腳本點擊前確認沒有 ANR 對話框、或先讓 launcher 穩定；要不要做請你決定。

## 4. 審核時請看

1. 30 張圖是不是你要的「正式長相」（已驗收的 P6 美術，沒有修改）。
2. 組合是否足夠；要不要加（例如 3 節日 × 雨、沒有天氣、包袱卸下、第 1 頁）。
3. 觀察到但**沒有處理**的既有現象（不是這次的改動）：WIDE 的英文月份被截斷（「SEPTE」「DECEM」）；聖誕當天 WIDE／TALL 的節日欄顯示「行憲紀念日」。
4. 審核通過後：`LOCKED = true`（PNG 不一致 CI 就紅），再進第二停點（刪除舊程式；golden 前後必須完全相同）。

## 5. 限制

- PNG 是 Robolectric 的繪圖（字型、抗鋸齒與實機不同），只當回歸基準，不代表實機長相。
- 繪圖的確定性只在 CI 的同一種機器上確認過（#115 產生、#116 比對）；換 Robolectric 或 JDK 版本可能要重新產生基準（仍須人工審核）。
