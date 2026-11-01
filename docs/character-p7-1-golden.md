# 墨丸 V3 — P7-1 第一停點：正式 golden（等待審核）

> 日期：2026-10-29（2026-10-30 依審核補充見第 6 節；WIDE 正式修正見第 7 節）　分支：`feature/mowan-weather-poses`
> **狀態：P7-0 通過；角色與節日美術通過（2026-10-30）。WIDE 正式修正後的畫面等待確認；PNG 基準尚未鎖定（`V3PngGoldenTest.LOCKED = false`），不自行批准。**
> 還沒刪任何舊程式、V3 仍在除錯開關後面（第二停點才切換）。相關：[P7 計畫](character-p7-plan.md)・[未定案事項](character-open-decisions.md)

## 1. 內容

| 項目 | 檔案 | 狀態 |
|---|---|---|
| V3 正式幾何 golden | `app/src/test/resources/mowan/golden-v3.json`（`mowan/V3GoldenTest`） | **生效**：每次 CI 都比，不一致就失敗 |
| 代表組合 PNG golden | `app/src/test/resources/v3-png/*.png`（`V3PngGoldenTest`，54 張） | **候選**：不一致只寫報告，審核通過後改 `LOCKED = true` |
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

### 2026-10-30 補充的候選（未批准）

| 情況 | 故事卡 | 日曆頁 | COMPACT | MEDIUM | WIDE | TALL |
|---|---|---|---|---|---|---|
| 春節・雨（2027-02-06） | [<img src="../app/src/test/resources/v3-png/story-spring-rainy.png" width="180">](../app/src/test/resources/v3-png/story-spring-rainy.png) | [<img src="../app/src/test/resources/v3-png/page-spring-rainy.png" width="110">](../app/src/test/resources/v3-png/page-spring-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-spring-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-compact-spring-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-spring-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-medium-spring-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-spring-rainy.png" width="180">](../app/src/test/resources/v3-png/widget-wide-spring-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-spring-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-tall-spring-rainy.png) |
| 中秋・雨（2027-09-15） | [<img src="../app/src/test/resources/v3-png/story-moon-rainy.png" width="180">](../app/src/test/resources/v3-png/story-moon-rainy.png) | [<img src="../app/src/test/resources/v3-png/page-moon-rainy.png" width="110">](../app/src/test/resources/v3-png/page-moon-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-moon-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-compact-moon-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-moon-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-medium-moon-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-moon-rainy.png" width="180">](../app/src/test/resources/v3-png/widget-wide-moon-rainy.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-moon-rainy.png" width="110">](../app/src/test/resources/v3-png/widget-tall-moon-rainy.png) |
| 第 1 頁・晴（2027-10-21） | [<img src="../app/src/test/resources/v3-png/story-page1.png" width="180">](../app/src/test/resources/v3-png/story-page1.png) | [<img src="../app/src/test/resources/v3-png/page-page1.png" width="110">](../app/src/test/resources/v3-png/page-page1.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-page1.png" width="110">](../app/src/test/resources/v3-png/widget-compact-page1.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-page1.png" width="110">](../app/src/test/resources/v3-png/widget-medium-page1.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-page1.png" width="180">](../app/src/test/resources/v3-png/widget-wide-page1.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-page1.png" width="110">](../app/src/test/resources/v3-png/widget-tall-page1.png) |
| 卸下包袱・晴（2027-10-21） | [<img src="../app/src/test/resources/v3-png/story-bundle-off.png" width="180">](../app/src/test/resources/v3-png/story-bundle-off.png) | [<img src="../app/src/test/resources/v3-png/page-bundle-off.png" width="110">](../app/src/test/resources/v3-png/page-bundle-off.png) | [<img src="../app/src/test/resources/v3-png/widget-compact-bundle-off.png" width="110">](../app/src/test/resources/v3-png/widget-compact-bundle-off.png) | [<img src="../app/src/test/resources/v3-png/widget-medium-bundle-off.png" width="110">](../app/src/test/resources/v3-png/widget-medium-bundle-off.png) | [<img src="../app/src/test/resources/v3-png/widget-wide-bundle-off.png" width="180">](../app/src/test/resources/v3-png/widget-wide-bundle-off.png) | [<img src="../app/src/test/resources/v3-png/widget-tall-bundle-off.png" width="110">](../app/src/test/resources/v3-png/widget-tall-bundle-off.png) |

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

## 6. 2026-10-30 審核補充

### 6.1 WIDE 可讀性（只調查與候選圖，正式版面沒改）

報告：[`docs/p7-wide/report.md`](p7-wide/report.md)・各尺寸：[`1-wide-sizes.png`](p7-wide/1-wide-sizes.png)・前後對照：[`2-wide-candidates.png`](p7-wide/2-wide-candidates.png)（[放大 2 倍](p7-wide/3-wide-candidates-x2.png)）

1. **只有 WIDE 會截斷**：COMPACT、MEDIUM、TALL 最小尺寸、5 種日子都沒有截斷。WIDE 在最小尺寸 250×110 時英文月份被切掉（例如需要 90dp、只有 52dp），宜、農曆、忌都出現「…」；300×130 時只剩長月份（FEBRUARY、SEPTEMBER、NOVEMBER、DECEMBER）被切；350×170 以上沒有截斷。
2. **既有問題**：WIDE 的文字部分從 `4f7f40e`（2026-10-22，V3 之前）就沒改過；V3 只加了 `mowan_still`。
3. **對比**（WCAG，一般文字建議 ≥ 4.5）：最小尺寸時，農曆、忌是 `ink_soft` 疊在山丘上，春節／聖誕「忌」平均 1.8（最差 1.1）、一般日子 2.7；星期（月份深色）3.5–4.7。
4. **候選**（不改日期資料；「行憲紀念日」照舊）：
   - **A（不改內容）**：英文月份移到中文月份下一行（字距 0.3 → 0.2）；農曆、忌改用 `ink`。結果：英文月份完整，農曆／忌對比 7.0–13.1；宜、農曆、忌的「…」還在。
   - **B（A＋WIDE 少放一點）**：宜 1 項、農曆不加干支年、忌 2 項。最小尺寸沒有任何截斷。代價：WIDE 少了干支年與部分宜忌項目（TALL、日曆頁照舊顯示）。
   - 兩個候選的副作用：英文月份換行後，日期數字略小（自動縮放）。星期的對比沒有改善（3.5–4.7），要不要改需另外決定。
   - 只改 `widget_wide.xml`（A）或再加 WIDE 專用的文字組法（B）；其他版面不受影響。**等你選定後才改正式版面**，改了以後 WIDE 的 10 張 PNG 候選要重產。

### 6.2 新增 golden 候選

春節雨、中秋雨、第 1 頁、卸下包袱 × 6 個畫面 = 24 張 PNG 候選（共 54 張，仍是候選、未鎖定）；幾何 golden 同樣加 24 組（54 組，原本 30 組逐字相同）。`V3GoldenTest` 另外檢查：雨天有傘、春節雨有紅包、中秋雨沒有月亮、COMPACT 極簡不畫月亮、卸下包袱沒有包袱、第 1 頁部位較少。

### 6.3 PNG 繪圖的確定性（修正）

- CI 比對時 `story-moon-rainy`（28 像素、最大通道差 7）、之後 `story-spring-rainy`（69 像素、最大通道差 23）跟基準不同，只有故事卡的雨滴邊緣。
- 原因：測試開 App 時 App 會在背景**真的下載天氣**，回來的時間每次不同。修正（只改測試）：開 App 前標成剛抓過天氣（跟 `FestivalAppTest` 同樣做法）。
- 修正後連續兩次 CI（`97704ce`、`16e5574`）54 張全部一致、最大通道差 0。`story-spring-rainy` 的候選換成不下載時畫的版本（只有雨滴位置不同）。

### 6.4 模擬器包袱入口

- 腳本修改（第 4 段）：每次點擊前偵測其他 App 的「沒有回應」對話框；有的話先存截圖（`shots/anr-*.png`）、焦點視窗與 `am_anr` 紀錄（`ui/anr-*.txt`），按對話框的「關閉應用程式」（按不到才按返回），確認 App 在最前面，重新量入口位置再點；一下最多點 3 次，3 次都沒切換照樣算 **FAIL**（沒有降成 WARN 或跳過）。
- 結果：見第 6.5 節。**這幾次都沒有出現 ANR 對話框**，所以新的處理流程實際上沒有被執行到；#58～#61 的失敗原因仍是 **Unknown**（「被 ANR 對話框擋住」的推測與證據一致，但沒有被重現或確認）。

### 6.5 證據

| 類型 | 結果 |
|---|---|
| 本機 JVM | 185 個測試全部通過（含 `V3GoldenTest` 54 組） |
| CI Build APK（`296ac18`、`ef7d613`、`97704ce`、`16e5574`） | 全部通過；最後一次 PNG 54 張全部一致 |
| 模擬器（`296ac18`） | PASS 51、FAIL 0、WARN 0；包袱 3 下都在第 1 次點擊就切換；沒有 ANR 對話框 |
| 模擬器（`ef7d613`） | PASS 51、FAIL 0、WARN 0；沒有 ANR 對話框 |
| 模擬器（`97704ce`） | PASS 51、FAIL 0、WARN 0；沒有 ANR 對話框；V3 小工具靜止檢查第 1 次收到 12 次更新、第 2 次靜止 → PASS（照規則重量） |
| 模擬器（`16e5574`） | PASS 51、FAIL 0、WARN 0；沒有 ANR 對話框 |
| 實機 | Not tested |

### 6.6 尚未解決

| # | 項目 | 狀態 |
|---|---|---|
| 1 | WIDE 可讀性：選 A、B 或其他 | 等你決定；正式版面沒改 |
| 2 | WIDE 星期的對比 3.5–4.7（低於 4.5 的情況） | 候選沒處理，要不要改請決定 |
| 3 | PNG golden 54 張 | 候選，`LOCKED = false`，等審核 |
| 4 | #58～#61 包袱 FAIL 的原因 | **Unknown**；之後 4 次模擬器都沒有出現 ANR 對話框、包袱全部通過，新的 ANR 處理流程還沒有實際被執行過 |
| 5 | 實機 | Not tested |

## 7. WIDE 正式修正（2026-10-30 收尾）

決策：WIDE 用候選 B，但**依實際空間決定**是否精簡；星期、農曆、宜忌的對比一併改善（目標：最不利背景 ≥ 4.5:1）。只改 WIDE，日期資料來源不變，「行憲紀念日」照舊。

### 7.1 改了什麼

| 檔案 | 內容 |
|---|---|
| `WideText.kt`（新） | 右欄文字依小工具實際寬度「放得下多少放多少」：放不下的項目整項拿掉，不出現「…」；空間夠時恢復完整（宜 2 項、農曆加干支年、忌 3 項）。**連續判斷、沒有單一門檻**，變寬時項目只會增加。連 1 項宜都放不下時（四字項目，只在最小尺寸），只把宜那一行縮小（最小 12sp） |
| `CalendarWidget.kt` | 小工具實際寬度：Android 12 以上取系統回報的大小裡會用 WIDE 的最窄寬度，以下用最小寬度；WIDE 的星期改用加深的月份色（月份色混 40% ink），其他版面不變 |
| `widget_wide.xml` | 英文月份換到中文月份下一行（字距 0.3 → 0.2）；星期往上 5dp、右欄底部 4dp，避開山丘最深那層；分隔線上下 6 → 3dp、忌上方 2 → 1dp（有節日名稱時最小尺寸也放得下）；農曆、忌改用新的 `ink_mid`（#4A433D，比 ink 淡、比 ink_soft 深，保留層級） |

為什麼不只換顏色：春節紅、冬青紅月份的山丘最深那層上，連最深的 ink 也只有 4.2–4.3:1（計算值），所以先讓文字離開最深那層，再把顏色加深到剛好夠。

### 7.2 前後對照

- [`docs/p7-wide/4-before-after.png`](p7-wide/4-before-after.png)：9 個 golden 組合，左邊修正前、右邊修正後（250×110）
- [`docs/p7-wide/1-wide-sizes.png`](p7-wide/1-wide-sizes.png)：修正後，最難放的 8 種日子 × 四種大小
- [`docs/p7-wide/0-before-sizes.png`](p7-wide/0-before-sizes.png)、[`0-before-report.md`](p7-wide/0-before-report.md)：修正前的調查（保留）
- 像素差異圖：[`docs/p7-golden/wide-diff/`](p7-golden/wide-diff/)（紅色是不同的像素）

### 7.3 截斷測試（`WideLayoutTest`，Robolectric）

- **2027 年每一天 × 250×110、300×130、350×170、400×200 = 1460 次，全部沒有截斷。** 檢查中文月份、英文月份、日期、星期、節日、宜、農曆、忌：左右（「…」或被切掉）與上下（字實際畫出來的範圍超出 TextView 或外框）。
- 最難放的日子另外出圖：長英文月份（SEPTEMBER、DECEMBER）、最長節日名稱（和平紀念日）、最長宜／忌／農曆。
- 精簡天數（共 365 天）：

| 寬度 | 精簡的天數 | 宜縮小字的天數 |
|---|---|---|
| 250 | 365 | 12 |
| 300 | 363 | 0 |
| 350 | 12 | 0 |
| 400 | 2 | 0 |

  400 寬時沒有完整顯示的 2 天（2027-02-07、08-12）：宜的兩項都是四個字（修飾垣牆・平治道塗），400dp 放不下兩項，所以只放一項；農曆與忌是完整的。
- 變寬不減少：每一天 250～460dp 逐 1dp 檢查，項目數與宜的字級都不會變少（沒有門檻跳動）。
- **測試本身的驗證**：先在舊版面上推測試（`693e7e3`、`7db1cbc`、`8c75a34`）：第一版只看外框，**沒抓到**「忌」被壓扁；第二版抓到但把日期數字也誤判；第三版改看字實際畫出來的範圍，準確抓到 39 天的「忌」被壓扁（CI 失敗）。之後的版面修正（`fed7a4f` 還差 1.3dp 失敗、`b05413f` 通過）。

### 7.4 對比（`WideLayoutTest`）

12 個月份色（每月 10 日）＋春節、中秋、聖誕 × 四種大小，WCAG 對比，「最差」是文字範圍（行高 × 字寬）裡最不利的背景像素：

- **星期、宜、農曆、忌全部 ≥ 4.5（最差 4.9）。** 報告：[`docs/p7-wide/2-contrast.md`](p7-wide/2-contrast.md)
- 修正前（同樣量法）：農曆最差 1.9、忌最差 1.1、星期最差 2.7。
- 只量了這四種文字；節日名稱（紅字）、中文月份、英文月份、日期數字沒有列入 4.5 的要求（英文月份 8sp 的淡色字沒有量，**未驗證**）。
- 是 Robolectric 的繪圖；實機（螢幕、字型、深色模式桌布）**Not tested**。

### 7.5 Golden

- 54 個組合不變；**只有 WIDE 的 9 張改變**（9 個情況 × WIDE），故事卡、日曆頁、COMPACT、MEDIUM、TALL 共 45 張逐像素相同（CI #128）。幾何 golden 54 組不變（WIDE 只改文字版面，角色圖沒變）。
- WIDE 9 張候選已換成修正後的版本；仍是候選，`LOCKED = false`。
- 確定性：
  - #129 一致，但 #130 的 `story-moon-rainy` 又有 28 個像素不同（只有雨滴）。昨天說「原因是背景下載天氣」**不完整**：關掉下載後機率變低，但還會發生。
  - 記錄後找到原因：App 開好時 Robolectric 的時鐘已經走了幾 ms，而且偶爾差 1ms（記錄：82 或 83、3 或 4）；雨滴位置跟著毫秒連續變化，差 1ms 就是那 28 個像素。
  - 修正（只改測試）：故事卡截圖前，天氣日常走過的時間取整到 10ms。代價：6 張故事卡（一般晴、一般雨、春節雨、中秋雨、第 1 頁、卸下包袱）的動畫時間點平移 2–4ms，只有次像素差異，候選已換掉；差異圖在 [`docs/p7-golden/story-diff/`](p7-golden/story-diff/)。另一個試過的做法（把起點歸零，`ed0d4f4`）會讓 7 張故事卡的姿勢改變，已撤回。
  - 修正後：#134（`ee48a61`）與同一個 commit 重跑一次，兩次都是 54 張全部一致、最大通道差 0；兩次記錄到的原始時間仍然有 82／83 的抖動，取整後不影響畫面。

### 7.6 模擬器

- `recover.sh`：把點擊前的遮擋處理抽出來（偵測「沒有回應」對話框 → 存證據 → 按「關閉應用程式」或返回 → 帶回前景 → 重新量位置 → 點；最多 3 次，仍失敗照樣 FAIL）。
- `test-recover.sh`：用假的 adb **注入**對話框、焦點跑掉、讀不到按鈕、連續兩次、入口不在畫面上、對話框關不掉、點了沒反應，共 8 種情況全部通過；故意改壞 `recover.sh` 的 6 種版本都會讓測試失敗。CI（Build APK 的 Emulator script self-test）與模擬器腳本開頭都會跑。
- **限制**：注入只驗證腳本流程；真的系統對話框（按鈕位置、`uiautomator` 讀不讀得到、關閉後系統的反應）**Not tested**。
- **真的 ANR 出現過一次**（模擬器 #68，`7db1cbc`，App 程式跟 `4075296` 相同）：Pixel Launcher 沒有回應 → 腳本留下證據、找到並按了「關閉應用程式」（540 1233），對話框關掉了；**之後 3 下 × 3 次、共 9 次點擊（位置正確、App 在最前面）都沒有切換**，照規則記 FAIL 3（包袱切換、狀態保存、重開後卸下）。關掉對話框之後為什麼點不到，**原因 Unknown**。
  - 證據在該次的 artifact：https://github.com/patty60723/tearoff-calendar/actions/runs/41015471300/artifacts/12960018643（`shots/anr-1-bundle-1-1.png`、`shots/bundle-miss-*.png`、`ui/anr-1-bundle-1-1.txt`；這個環境下載不了，截圖分支已被後來的執行覆蓋，我沒有看到這些圖）
  - 補強：之後每次「點了沒反應」也會存焦點視窗與觸控送到哪個視窗（`dumpsys window`、`dumpsys input`），下次可以判斷點擊被誰拿走。
- 完整回歸：見 7.7。

### 7.7 證據

| 類型 | 結果 |
|---|---|
| CI Build APK #123（`4075296`） | 通過；但圖上看得到 250×110 有節日名稱時「忌」被切（當時的測試沒抓到） |
| CI #124～#127 | 測試驗證：#124 沒抓到、#125／#126 抓到（失敗）、#127 版面還差 1.3dp（失敗） |
| CI #128（`b05413f`） | 通過：1460 次沒有截斷、對比最差 4.9、非 WIDE 45 張 PNG 不變 |
| CI #129（`03be4c5`） | 通過：PNG 54 張全部一致（確定性第 1 次） |
| CI #130（`03e589c`） | 通過，但 PNG 有 1 張不一致（`story-moon-rainy`，Robolectric 時鐘抖動） |
| CI #131～#133 | 找原因：#131 起點歸零（7 張改變，撤回）、#132 記錄時間、#133 取整（6 張故事卡平移 2–4ms） |
| CI #134（`ee48a61`）＋重跑 | 兩次 PNG 54 張全部一致、最大通道差 0 |
| 模擬器 #66（`4075296`）、#67、#69～#76 | 全部 PASS 51、FAIL 0 |
| 模擬器 #77（`4568934`，最後一版） | **PASS 51、FAIL 0、WARN 0**；模擬器上的 `test-quiet.sh`、`test-recover.sh` 全部通過；包袱 3 下都在第 1 次點擊切換；沒有出現 ANR 對話框 |
| CI #135（`4568934`） | 通過；PNG 54 張全部一致（取整後第 3 次） |
| 模擬器 #68（`7db1cbc`） | PASS 48、FAIL 3、WARN 10：真的 ANR，處理流程執行了，之後點擊仍沒反應（Unknown） |
| 實機 | Not tested |

### 7.8 尚未解決

| # | 項目 | 狀態 |
|---|---|---|
| 1 | WIDE 最終畫面 | 等你確認；PNG 仍是候選，`LOCKED = false` |
| 2 | 250×110 時日期數字比原本小（英文月份換行佔掉高度） | 已知取捨，請一併確認 |
| 3 | WIDE 的英文月份、節日名稱、中文月份、日期數字的對比 | 沒有列入 4.5 的要求，未驗證 |
| 4 | 真的 ANR 對話框關掉之後點擊沒反應（#68） | **Unknown**；處理流程有執行，功能檢查照樣 FAIL；已加強證據 |
| 5 | 真的系統對話框各種情況（找不到按鈕、關不掉、焦點跑掉） | 只有注入測試，**Not tested** |
| 6 | 實機（WIDE 字型、對比、各 launcher 回報的大小） | Not tested |
