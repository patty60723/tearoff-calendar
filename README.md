# 墨丸 V3 — P6-4b 審核素材（節日接進 App）

分支 `feature/mowan-weather-poses`。程式 `fb79fa1`（之後的 commit 只改測試與截圖）。

## robolectric/（CI Build APK #93，Robolectric 繪圖，不是模擬器或實機）

App 實際的 MainActivity，w411dp xxhdpi。

| 檔案 | 內容 |
|---|---|
| `spring-sheet.png`、`spring-rain-sheet.png`、`xmas-sheet.png`、`xmas-snow-sheet.png`、`moon-sheet.png`、`moon-rain-sheet.png` | 故事卡：主演出代表時間、演完；日曆頁（今天那頁） |
| `spring-screen-top.png`、`spring-screen-card.png` | 整個畫面（最上面、捲到故事卡） |
| `rule-1-page-only-today.png` | D-41：最上面還是昨天那頁時不畫節日；往後翻到今天那頁才畫 |
| `rule-2-headwear-off.png` | 除錯選單關掉節日頭飾：照紙帽的裝備狀態 |
| `rule-3-v3-off.png` | V3 關閉：舊火柴人，沒有任何節日東西 |

## emulator/（CI Emulator acceptance #44，Android 14 模擬器，`a703928`）

用除錯的假日期打開 App。`*-page.png` 是剛打開（日曆頁），`*-card-main.png` 是捲到故事卡時，`*-card-after.png` 是約 8 秒後；`*.gif` 是捲動與主演出的錄影。

| 名稱 | 假日期、天氣 | 看什麼 |
|---|---|---|
| spring | 2027-02-06 春節初一、晴，今天那頁已撕 | 第一次看到故事卡：演主演出（`strip-spring.png` 有閉眼作揖的影格） |
| spring-again | 同一天重開 App | 不重播（`strip-spring-again.png` 全程站姿） |
| spring-rain | 初二、雨 | 初二自己演一次（撐傘版） |
| spring-yesterday-top | 初三、今天那頁沒撕 | 日曆頁最上面是初二那頁：不畫節日（D-41）；故事卡照樣是節日 |
| xmas | 2026-12-25、雪 | 聖誕 |
| moon、moon-rain | 2027-09-15 中秋、晴／雨 | 中秋；同一天換天氣重開不重播 |

`report.txt` 第 12 段有每次打開後「演過了」的紀錄。
