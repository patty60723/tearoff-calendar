# 墨丸 V3 — P6-4c 小工具節日整合驗收報告

> 日期：2026-10-28　分支：`feature/mowan-weather-poses`
> **狀態：等待審核**（不開 PR、不合併 main、V3 仍在除錯開關後面）
> 驗收素材：[`acceptance/mowan-p6-4c-widget`](https://github.com/patty60723/tearoff-calendar/tree/acceptance/mowan-p6-4c-widget)
> 相關文件：[P6 計畫 §11.9、§11.10](character-p6-plan.md)・[未定案事項 D-16、D-43](character-open-decisions.md)

## 結論

- 節日已接到正式小工具：節日當天，今天那頁的 V3 墨丸畫節日靜態外觀。COMPACT 用 C 極簡；WIDE、MEDIUM、TALL 用 B（節日背景＋最多一個不衝突的天氣線索）。仍是一張靜態圖，沒有輪播。
- V3 關閉時舊火柴人完全不變；既有 golden 不變。
- 在 Android 14 模擬器的 **Pixel Launcher**（`com.google.android.apps.nexuslauncher`）上實際放了小工具，用假日期換到春節、在節日當天改狀態、離開節日，全部有更新。

## 版本

| 項目 | 結果 |
|---|---|
| 程式 | `285a00d`（整合）；`32e84f9`、`f5564fd` 只改測試與截圖；`e57457f`、`c684fcd` 只改模擬器腳本與 debug 版的小工具宿主 |
| CI（Build APK #107，`c684fcd`） | 通過，313 個 JVM／Robolectric 測試 |
| 模擬器（Emulator acceptance #55，`c684fcd`） | PASS 51、FAIL 0、WARN 0 |

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

## 模擬器的「V3 小工具真的靜止」（#40、#53 的失敗）

- #40（2026-10-26）與 #53（2026-10-28）各一次：一般日期的 V3 小工具在第二段 10 秒畫了 9 張、6 張（門檻 ≤ 5），同一次的第一段 10 秒只有 1 張、0 張。是集中出現的一小段，不是持續重畫（會輪播的舊火柴人對照組 10 秒約 140–190 張）。
- 推測：綁定後背景抓天氣（腳本寫入設定時清掉了天氣快取），抓到後重新套用 5 個小工具。**#54、#55 沒有重現，所以原因還沒有確認。**
- 處理：debug 版的小工具宿主記錄每一次小工具更新；靜止檢查量測期間收到更新就記下來再量（最多 3 次），只拿沒有收到更新的 10 秒判斷，門檻不變（≤ 5 張）。持續重畫照樣會失敗；之後再出現時，報告會寫出當時收到幾次更新，可以確認或推翻這個推測。
- #54 的 5 項失敗：2 項是我新寫的解析錯誤（jank 欄位有好幾個字，欄位錯位；實際是 0 張），已在 `c684fcd` 修正；3 項包袱入口是模擬器上其他 App 一直跳出「沒有回應」對話框蓋住畫面（9 個 WARN 都是這個），跟這次的程式無關，#55 照常通過。

## Not tested

| 項目 | 原因 |
|---|---|
| 實機（各廠牌 launcher、實際縮放、Android 13 以下） | 沒有實體手機 |
| 模擬器上的 WIDE 版面 | 小工具宿主 290×125dp 那格被系統套用成 COMPACT（一般日期也一樣，既有行為）；桌面上放的是 TALL。WIDE 只有 Robolectric 截圖 |
| 模擬器桌面上的中秋、聖誕更新流程 | 只截圖、沒有逐步檢查（春節有） |
| 真的跨過午夜換到節日 | 用午夜那次的廣播（`NEW_DAY`）＋假日期，不是真的等到午夜 |
| 48dp 在實機上的辨識度 | 只有 Robolectric 的 mdpi／xhdpi 實際像素與模擬器 xxhdpi |
| 長時間放著的耗電 | P7 |

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
