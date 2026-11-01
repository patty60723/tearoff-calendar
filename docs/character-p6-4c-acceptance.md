# 墨丸 V3 — P6-4c 小工具節日整合驗收報告

> 日期：2026-10-28　分支：`feature/mowan-weather-poses`
> **狀態：P6-4c 正式完成**（2026-10-28 審核通過；靜止驗收腳本補強後確認）。不開 PR、不合併 main、V3 仍在除錯開關後面
> 驗收素材：[`acceptance/mowan-p6-4c-widget`](https://github.com/patty60723/tearoff-calendar/tree/acceptance/mowan-p6-4c-widget)
> 相關文件：[P6 計畫 §11.9、§11.10](character-p6-plan.md)・[未定案事項 D-16、D-43](character-open-decisions.md)

## 結論

- 節日已接到正式小工具：節日當天，今天那頁的 V3 墨丸畫節日靜態外觀。COMPACT 用 C 極簡；WIDE、MEDIUM、TALL 用 B（節日背景＋最多一個不衝突的天氣線索）。仍是一張靜態圖，沒有輪播。
- V3 關閉時舊火柴人完全不變；既有 golden 不變。
- 在 Android 14 模擬器的 **Pixel Launcher**（`com.google.android.apps.nexuslauncher`）上實際放了小工具，用假日期換到春節、在節日當天改狀態、離開節日，全部有更新。

## 版本

| 項目 | 結果 |
|---|---|
| 程式 | `285a00d`（整合）；`32e84f9`、`f5564fd` 只改測試與截圖；`e57457f`、`c684fcd`、`43e3460` 只改模擬器腳本、腳本測試、CI 步驟與 debug 版的小工具宿主 |
| CI（Build APK #107，`c684fcd`） | 通過，313 個 JVM／Robolectric 測試（審核接受） |
| CI（Build APK #109，`43e3460`） | 通過：313 個測試＋腳本測試 `test-quiet.sh` |
| 模擬器（Emulator acceptance #55，`c684fcd`） | PASS 51、FAIL 0、WARN 0（審核接受） |
| 模擬器（Emulator acceptance #56，`43e3460`） | PASS 51、FAIL 0、WARN 0（補強後的靜止驗收） |

## 程式變更

| 檔案 | 內容 |
|---|---|
| `CalendarWidget.kt` | `build` 的 `festivalLook` 預設改成 true：小工具與 App 日曆頁都是今天那頁畫節日外觀（D-41）。背景與天氣的取捨照 `smallEnvFor(layout)`（P6-4c 已定案）。`false` 只給截圖的「沒有節日」對照 |
| `MainActivity.kt` | 日曆頁不用再特別傳 `festivalLook`（行為不變） |
| `WidgetHostActivity.kt`（只在 debug APK） | 每收到一次小工具更新記一筆 log，給模擬器的靜止檢查分辨「一次性的內容更新」與「持續重畫」 |
| `tools/emulator/w7-acceptance.sh` | 節日小工具的檢查（見下面） |

小工具的日期、農曆、宜忌、天氣文字、日出日落、四種版面規則、bitmap 張數都沒有改（測試見下面）。狀態全部來自 App 的同一個來源：`AppClock.today`（含除錯假日期）、`StoryBook`（成長頁數）、`MowanSettings`（V3 開關、紙帽、包袱、節日頭飾）、`WeatherService`。

## Pass（有證據）

### 自動測試（Robolectric，CI #107）

| # | 項目 | 證據 |
|---|---|---|
| 1 | 換到節日當天、節日中換一天、離開節日當天（除夕 → 初一 → 初二 → 初四） | `WidgetFestivalTest.enteringAndLeavingTheFestivalDay`（走 `AppWidgetManager` 與 `refreshAll`） |
| 2 | 四種版面照規則（COMPACT＝C、其他＝B），跟 App 狀態畫出來的一樣 | `everyLayoutFollowsTheRule` |
| 3 | 紙帽、節日頭飾、包袱、天氣、成長頁數、V3 開關切換後小工具跟著換 | `followsTheSharedState` |
| 4 | 節日當天也是靜態一張：ViewFlipper 隱藏、沒有圖、輪播間隔一天（三個節日 × 四種版面） | `staticOnFestivalDays` |
| 5 | V3 關閉：節日當天的舊火柴人 6 張輪播跟平常逐像素一樣（四種版面） | `oldStickmanIsTheSameOnFestivalDays`；既有 `WidgetV3Test.oldStickmanUnchangedWhenSwitchedOff` |
| 6 | bitmap 總量、一次更新畫幾張（4）、故事 JSON 解析次數（1）：節日當天跟平常一樣 | `budgetAndParsesDoNotGrow`；既有 `WidgetV3Test.bitmapBudget`、`StoryCacheTest` |
| 7 | 文字與版面不變（日期、宜忌、農曆、節日、天氣、日出日落；V3 開或關一樣，含春節） | 既有 `WidgetV3Test.textsAndLayoutUnchanged` |
| 8 | App 日曆頁 5 種螢幕（w320 mdpi／xhdpi、w360 xxhdpi、w411 xxhdpi、w600 xhdpi）：今天那頁畫節日外觀；角色小於 72dp（前三種）時跟小工具 TALL 一樣簡化 | `appPageOnDifferentScreens`；`robolectric/2-app-pages.png` |
| 9 | 春聯跟日期文字至少 6dp（MEDIUM 6.3dp、WIDE 17.3dp） | `WidgetFestivalFinalShotsTest.spacing` |
| 10 | 既有 golden、黃金數值不變 | 全部既有測試通過 |

既有 `WidgetV3Test` 改成固定一個不是節日的日期（不然 CI 剛好在 12/25 跑時會因為畫了節日而失敗）。

### 模擬器（Android 14，Pixel Launcher，#55）

| # | 項目 | 結果 |
|---|---|---|
| 11 | 小工具宿主（系統 AppWidgetHost）：春節當天 5 種大小都套用成功、角色在小工具裡、ViewFlipper 隱藏 | PASS（`widget-host-v3-spring.png`） |
| 12 | 春節小工具真的靜止 | PASS：沒有收到更新的 10 秒內畫了 0 張 |
| 13 | 桌面 Pixel Launcher：換到除夕（對照）、換到春節初一、節日當天卸下包袱、天氣換成雪、換成第 5 頁、關掉 V3（舊火柴人）、打開 V3、離開春節（初四） | 8 項 PASS（每一步跟上一張比，畫面有變；`launcher-sheet.png`） |
| 14 | 中秋、聖誕在桌面上的樣子 | 只截圖（`launcher-17-moon.png`、`launcher-17-xmas.png`） |
| 15 | 既有的 41 項回歸（故事卡、Reduced Motion、小工具、桌面更新、密度、字體） | 全部 PASS（關閉動畫時 V3 故事卡 10 秒內 0 張） |

注意：桌面的「畫面有變」是整個桌面截圖的像素差；換日那兩步本來就會因為日期文字改變而有差。節日外觀是否正確要看截圖（`launcher-10-spring.png` 是財神帽＋春聯＋雨傘；`launcher-16-after.png` 換回紙帽）。

## 小工具「真的靜止」的驗收方式（補強後）

### #40、#53 的失敗：原因 **Unknown**

- #40（2026-10-26）與 #53（2026-10-28）各一次：一般日期的 V3 小工具在第二段 10 秒畫了 9 張、6 張（門檻 ≤ 5），同一次的第一段 10 秒只有 1 張、0 張。是集中出現的一小段，不是持續重畫（會輪播的舊火柴人對照組 10 秒約 140–190 張）。
- 當時的腳本沒有記錄小工具的更新，所以**沒辦法知道那一段重畫的原因**。「綁定後背景抓天氣、抓到後重新套用」只是推測之一，**不是已確認的原因**。

### 判斷規則（`tools/emulator/quiet.sh`）

1. 先確認更新監測涵蓋受測小工具：從這次宿主的 log 取出綁定成功的小工具 id，每一個都要在宿主 log 裡看得到至少一次更新；少了任何一個就 FAIL。
2. 每次量 10 秒：宿主畫面重畫幾張、這段時間**受測小工具 id** 收到幾次內容更新（開始、結束標記之間；其他 id 不算）。
3. 收到更新的那一次不拿來判斷，記下來再量，最多 3 次；第一個「沒有收到更新」的完整 10 秒才判斷：≤ 5 張 PASS，超過 FAIL。
4. 3 次都收到更新（沒有穩定區間）：**FAIL**，不算 PASS 也不跳過。讀不到量測結果：FAIL。
5. 每一次的張數、更新次數、原因都寫進報告（`try=…` 一行一次，最後一行是判斷與理由）。

### 腳本測試（`tools/emulator/test-quiet.sh`，不需要模擬器）

CI Build APK（`Emulator script self-test` 步驟）與模擬器腳本開頭都會跑；失敗時模擬器驗收記 FAIL。

| 情境 | 每次的「張數,更新」 | 預期 | 結果 |
|---|---|---|---|
| 沒有更新、沒有重畫 | 0,0 | PASS（1 次） | ok |
| 沒有更新、少量重畫（門檻內） | 5,0 | PASS（1 次） | ok |
| 一次內容更新，之後穩定 | 6,5 → 0,0 | 重新量測，PASS（2 次） | ok |
| 兩次內容更新，第三次穩定 | 9,5 → 4,1 → 0,0 | PASS（3 次） | ok |
| 持續收到更新，三次都沒有穩定區間 | 6,5 → 3,2 → 5,1 | FAIL（3 次） | ok |
| 沒有更新卻持續重畫 | 60,0 | FAIL（1 次） | ok |
| 沒有更新、剛好超過門檻 | 6,0 | FAIL（1 次） | ok |
| 一次更新後，沒有更新卻持續重畫 | 6,5 → 60,0 | FAIL（2 次） | ok |
| 讀不到量測結果 | x | FAIL（1 次） | ok |
| 每次的張數、更新次數、原因都有記錄 | — | — | ok |
| 監測範圍：只拿綁定成功的 id、找出沒有更新的受測小工具、id 17 不會被當成 7 | — | — | ok |
| 監測範圍：只算開始／結束標記之間、受測 id 的更新 | — | — | ok |

另外確認測試抓得到錯：把規則故意改壞（3 次都有更新當成 PASS、不理會更新、不看張數、不過濾受測 id、收到更新就停不重量），5 種都會讓測試失敗。

### 模擬器結果（#56，`43e3460`）

```
腳本測試（test-quiet.sh）：全部通過
  try=1 frames=0 updates=0 note=沒有更新，靜止
  verdict=PASS tries=1 reason=第 1 次：沒有收到更新的完整 10 秒內畫了 0 張（≤ 5）
PASS  V3 小工具真的靜止（宿主畫面不重畫） — …（受測小工具 id：2 3 4 5 6）
  try=1 frames=11 updates=12 note=這 10 秒受測小工具收到 12 次內容更新，不拿來判斷，再量一次
  try=2 frames=0 updates=0 note=沒有更新，靜止
  verdict=PASS tries=2 reason=第 2 次：沒有收到更新的完整 10 秒內畫了 0 張（≤ 5）
PASS  節日小工具真的靜止（宿主畫面不重畫） — …（受測小工具 id：7 8 9 10 11）
```

節日小工具的第一段 10 秒收到 12 次更新、畫了 11 張，下一段沒有更新、0 張。這是這次實際觀察到的情況（更新從哪裡來沒有追，**不代表 #40、#53 的原因**）。

### 之前的處理紀錄

- `e57457f`：宿主記錄更新、量測收到更新就重量。`c684fcd`：修正我寫錯的解析（jank 欄位有好幾個字）。
- #54 的 5 項失敗：2 項是上面的解析錯誤（實際 0 張）；3 項包袱入口是模擬器上其他 App 一直跳出「沒有回應」對話框蓋住畫面（9 個 WARN 都是這個），#55、#56 照常通過。

## Not tested

| 項目 | 原因 |
|---|---|
| 實機 | 沒有實體手機 |
| Pixel Launcher 以外的 launcher（各廠牌、實際縮放） | 模擬器只有 Pixel Launcher |
| Android 13 以下 | 模擬器只有 Android 14 |
| 模擬器／Launcher 上的 WIDE 版面 | 小工具宿主 290×125dp 那格被系統套用成 COMPACT（一般日期也一樣，既有行為）；桌面上放的是 TALL。WIDE 只有 Robolectric 截圖 |
| 模擬器桌面上的中秋、聖誕更新流程 | 只截圖、沒有逐步檢查（春節有） |
| 真正跨過午夜換到節日 | 用午夜那次的廣播（`NEW_DAY`）＋假日期，不是真的等到午夜 |
| 48dp 在實機上的辨識度 | 只有 Robolectric 的 mdpi／xhdpi 實際像素與模擬器 xxhdpi |
| 耗電（長時間放著） | P7 |
| #40、#53 靜止檢查失敗的原因 | Unknown（見上面） |

## 素材

`acceptance/mowan-p6-4c-widget`：

- `robolectric/1-{SPRING,MOON,XMAS}-widgets.png`：3 節日 × 4 版面 × 9 天氣的整個小工具（最小尺寸，縮成 50%）
- `robolectric/1-*-weathers.png`：墨丸那張圖（xxhdpi 實際像素），第一欄是沒有節日的對照
- `robolectric/2-*-spacing.png`：整個小工具實際大小與春聯間距
- `robolectric/3-48dp-mdpi.png`、`3-48dp-xhdpi.png`：COMPACT 48dp 實際像素與放大
- `robolectric/2-app-pages.png`：App 日曆頁 5 種螢幕
- `emulator/launcher-*.png`、`launcher-sheet.png`：Pixel Launcher 上的節日小工具與每一步更新
- `emulator/widget-host-v3-spring.png`：小工具宿主（春節）；`widget-host-v3.png`：一般日期對照
- `emulator/report.txt`：模擬器報告全文
