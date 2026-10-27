# 墨丸 V3 — P6-4c 小工具節日：選定方向的最終候選截圖（等待圖片驗收）

分支 `feature/mowan-weather-poses`，commit `d06c262`（CI Build APK #101，Robolectric 繪圖；不是模擬器或實機）。
用實際的小工具管線畫（`CalendarWidget.build` 不指定美術設定，照 `CalendarWidget.smallEnvFor` 的規則）。
**正式的小工具還沒接上節日**，圖片驗收通過後才接。

## 選定方向（2026-10-27）

- COMPACT（角色 48dp）：C 極簡，只有頭飾與手上道具（雨傘照畫）。
- WIDE（56dp）、MEDIUM（58dp）、TALL（64dp）：B 節日背景＋最多一個不衝突的天氣線索；不強制每種天氣都出現。
- 春聯跟日期保持明顯間距：只調整春聯，小尺寸時往角色移 14 單位；日期版面不動。
- 中秋遇到雨、雷雨、雪維持 D-37（沒有月亮），不額外補裝飾。

## 圖

| 檔案 | 內容 |
|---|---|
| `1-SPRING-weathers.png`、`1-MOON-weathers.png`、`1-XMAS-weathers.png` | 9 種天氣 × 4 種版面（墨丸那張圖，xxhdpi 實際像素）；第一欄是現在的小工具（沒有節日）對照 |
| `2-SPRING-spacing.png`、`2-MOON-spacing.png`、`2-XMAS-spacing.png` | 整個小工具（xxhdpi 實際大小、各版面的最小尺寸＝最擠），晴、雨；春節那張標了春聯跟文字的距離 |
| `3-48dp-mdpi.png`、`3-48dp-xhdpi.png` | 48dp（COMPACT）在 mdpi（48 px）、xhdpi（96 px）的實際像素與放大，左邊是現在的小工具對照 |

春聯左緣跟同一高度文字的距離（最小尺寸）：WIDE 17.3 dp、MEDIUM 6.3 dp（調整前約 1 dp）；COMPACT 沒有春聯；TALL 的春聯旁邊同一高度沒有文字。測試要求 ≥ 6 dp。
