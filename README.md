# 墨丸 V3 — W7 驗收素材

驗收報告：[`docs/character-w7-acceptance.md`](https://github.com/patty60723/tearoff-calendar/blob/feature/mowan-weather-poses/docs/character-w7-acceptance.md)（`feature/mowan-weather-poses` 分支）

- `robolectric/`：CI 的 Robolectric 用 App 的繪圖程式畫出來的圖（Android 原生繪圖，[Build APK #47](https://github.com/patty60723/tearoff-calendar/actions/runs/40166228415)）。
- `emulator/`：Android Emulator（Pixel 6、Android 14、420 dpi、軟體繪圖）上實際開 App 的截圖與錄影（[Emulator acceptance #8](https://github.com/patty60723/tearoff-calendar/actions/runs/40165781624)），紀錄在 [`emulator/report.txt`](emulator/report.txt)。
- **沒有實體手機的素材。**

## 1. 八種天氣（模擬器，第 14 頁）

| 晴 | 熱 |
|---|---|
| ![](emulator/shots/weather-SUNNY.png) | ![](emulator/shots/weather-HOT.png) |
| **陰** | **霧** |
| ![](emulator/shots/weather-CLOUDY.png) | ![](emulator/shots/weather-FOG.png) |
| **雨** | **雷雨** |
| ![](emulator/shots/weather-RAIN.png) | ![](emulator/shots/weather-THUNDER.png) |
| **冷** | **雪** |
| ![](emulator/shots/weather-COLD.png) | ![](emulator/shots/weather-SNOW.png) |

## 2. 動畫

Robolectric 影格（15 FPS，跟 App 同一條時間軸取樣，每種天氣兩個循環；雷雨示範每 2 秒打一次雷）：

| 晴 | 雨 | 熱 | 陰 | 霧 |
|---|---|---|---|---|
| ![](robolectric/w7/sunny.gif) | ![](robolectric/w7/rainy.gif) | ![](robolectric/w7/hot.gif) | ![](robolectric/w7/cloudy.gif) | ![](robolectric/w7/fog.gif) |
| **雷雨** | **冷** | **雪** | **包袱（2 秒卸下、4 秒背上）** | |
| ![](robolectric/w7/thunder.gif) | ![](robolectric/w7/cold.gif) | ![](robolectric/w7/snow.gif) | ![](robolectric/w7/bundle.gif) | |

模擬器錄影（GIF 12 FPS；原始影片是同名 `.mp4`）：

| 晴 | 冷 | 包袱入口點三下 |
|---|---|---|
| ![](emulator/video/sunny.gif) | ![](emulator/video/cold.gif) | ![](emulator/video/bundle-toggle.gif) |

- 雷雨 45 秒（第 28 秒左右打雷）：[`thunder.gif`](emulator/video/thunder.gif)・[`thunder.mp4`](emulator/video/thunder.mp4)
- 其他天氣：[`rain`](emulator/video/rain.gif)・[`hot`](emulator/video/hot.gif)・[`cloudy`](emulator/video/cloudy.gif)・[`fog`](emulator/video/fog.gif)・[`snow`](emulator/video/snow.gif)
- 背景 45 秒後回到雷雨（不補播）：[`thunder-back.gif`](emulator/video/thunder-back.gif)
- Reduced Motion 從背景回來打開動畫：[`reduced-back.gif`](emulator/video/reduced-back.gif)；留在前景關掉又打開（W7 修正後）：[`reduced-live.gif`](emulator/video/reduced-live.gif)

## 3. 小尺寸：160／96／48 px

真實像素、沒有縮放；48 px 另外最近鄰放大 4 倍；最後兩欄是 96 px 的靜止畫面與卸下包袱。

![](robolectric/w7/sizes.png)

## 4. 成長頁數 × 天氣（第 1、3、4、5、8、13、14 頁）

![](robolectric/w7/growth.png)

模擬器（晴天）：[第 1 頁](emulator/shots/page-1.png)・[第 3 頁](emulator/shots/page-3.png)・[第 4 頁](emulator/shots/page-4.png)・[第 5 頁](emulator/shots/page-5.png)・[第 8 頁](emulator/shots/page-8.png)・[第 13 頁](emulator/shots/page-13.png)・[第 14 頁](emulator/shots/page-14.png)

## 5. 包袱、圍巾、紙帽、雨傘的重疊（4 倍放大）

![](robolectric/w7/layers-zoom.png)

## 6. 第 13、14 頁與包袱入口

模擬器：背上 → 點一下卸下 → 重開 App 仍是卸下

| 背上 | 卸下 | 重開 App |
|---|---|---|
| ![](emulator/shots/bundle-1-on.png) | ![](emulator/shots/bundle-2-off.png) | ![](emulator/shots/bundle-3-restart.png) |

Robolectric：第 13 頁（沒有入口）、第 14 頁背上、點一下卸下、重開 App

![](robolectric/w7/story-card-bundle.png)

## 7. Reduced Motion（系統「移除動畫」）的代表姿勢

| 晴 | 熱 | 陰 | 霧 |
|---|---|---|---|
| ![](emulator/shots/reduced-1.png) | ![](emulator/shots/reduced-HOT.png) | ![](emulator/shots/reduced-CLOUDY.png) | ![](emulator/shots/reduced-FOG.png) |
| **雨** | **雷雨** | **冷** | **雪** |
| ![](emulator/shots/reduced-RAIN.png) | ![](emulator/shots/reduced-THUNDER.png) | ![](emulator/shots/reduced-COLD.png) | ![](emulator/shots/reduced-SNOW.png) |

## 8. 不同密度與字體大小

Robolectric（完整卡片；每一張都檢查了包袱入口不截斷、不重疊、至少 48dp 高）：

| mdpi | hdpi | xxhdpi | xxxhdpi |
|---|---|---|---|
| ![](robolectric/w7/story-card-w411dp-mdpi-font100.png) | ![](robolectric/w7/story-card-w411dp-hdpi-font100.png) | ![](robolectric/w7/story-card-w411dp-xxhdpi-font100.png) | ![](robolectric/w7/story-card-w411dp-xxxhdpi-font100.png) |
| **字體 130%** | **字體 200%** | **320dp、字體 130%** | |
| ![](robolectric/w7/story-card-w411dp-xxhdpi-font130.png) | ![](robolectric/w7/story-card-w411dp-xxhdpi-font200.png) | ![](robolectric/w7/story-card-w320dp-xhdpi-font130.png) | |

模擬器（整個螢幕；大密度、大字體時卡片上半部捲出螢幕外）：[320 dpi](emulator/shots/density-320-font-1.0.png)・[420 dpi](emulator/shots/density-420-font-1.0.png)・[字體 1.3](emulator/shots/density-420-font-1.3.png)・[字體 2.0](emulator/shots/density-420-font-2.0.png)・[560 dpi](emulator/shots/density-560-font-1.0.png)

## 9. 沒有受影響的部分

舊火柴人故事卡（模擬器）：[`stickman.png`](emulator/shots/stickman.png)・Widget：[tall](robolectric/widget-tall.png)・[wide](robolectric/widget-wide.png)・[medium](robolectric/widget-medium.png)・[compact](robolectric/widget-compact.png)・App 主畫面：[舊火柴人](robolectric/app.png)・[V3](robolectric/app-mowan.png)

W6 之前的素材：[`story-card-weathers.png`](robolectric/story-card-weathers.png)・[`story-card-pages.png`](robolectric/story-card-pages.png)・[`mowan-bundle.png`](robolectric/mowan-bundle.png)・[`mowan-weather.png`](robolectric/mowan-weather.png)・[`mowan-pages.png`](robolectric/mowan-pages.png)
