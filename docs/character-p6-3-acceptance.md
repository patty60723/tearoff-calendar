# 墨丸 V3 — P6-3 節日演出驗收報告

> 日期：2026-10-26　分支：`feature/mowan-weather-poses`
> **狀態：P6-3 結案**（2026-10-26 最終視覺審核通過）
> 驗收素材：[`acceptance/mowan-p6-3`](https://github.com/patty60723/tearoff-calendar/tree/acceptance/mowan-p6-3)（GIF、關鍵影格、修正前後對照）
> 相關文件：[P6 計畫](character-p6-plan.md)・[未定案事項](character-open-decisions.md)

## 結論

- **沒有 Blocker。** 春節、聖誕、中秋的主演出、待機、成長階段降級、關閉動畫的代表姿勢都完成，並通過靜態與動態視覺審核。
- 節日演出**還沒有接到 App 畫面**（故事卡、日曆頁、小工具都還看不到），所以所有證據都來自 JVM 測試與 Robolectric 繪圖，不是實機、也不是模擬器上的實際畫面。接畫面是 P6-4。

## 最終版本

| 項目 | 結果 |
|---|---|
| 最終 commit | `87ab431`（P6-3 主體 `c9b0fcf`、`4a1aa20`；關鍵影格 `1431fb5`） |
| CI（Build APK #81） | 通過：https://github.com/patty60723/tearoff-calendar/actions/runs/40339812451 ，JVM／Robolectric 測試 259 個（含 4 個關鍵影格素材） |
| 模擬器回歸（Emulator acceptance #38） | PASS 41、FAIL 0、WARN 0：https://github.com/patty60723/tearoff-calendar/actions/runs/40339812400 （節日還沒接畫面，這是既有功能的回歸，不是節日驗收） |

## Pass（有證據）

| # | 項目 | 證據 |
|---|---|---|
| 1 | 演出跟原型一致 | `FestivalPerformanceGoldenTest`：368 個姿勢、12 張完整畫面逐一比對（D-39 刻意改動的兩項除外） |
| 2 | 撐傘拜年握傘點不動、撐傘手臂不變、傘角 −17°～−5° | `FestivalPerformanceTest.t15_*`、`umbrellaHandNeverMoves`；關鍵影格 `2-spring-bow-rain.png` |
| 3 | 紅包、月餅跟著實際拿著它的手 | `propsFollowTheHoldingHand` |
| 4 | 只動能力允許的部位 | `onlyAllowedPartsMove`、`FestivalActionsTest` |
| 5 | 開始與結束接回站姿、沒有瞬移、頭不突然轉 | `startsAndEndsAtTheStanceWithoutJumps`（每 33 ms 手 < 4、頭 < 2°；實測頭最多 0.64°） |
| 6 | 背包袱時道具不壓在包袱上（站姿、開始、結束、停留、代表姿勢） | `bundleAndHandProps`；`mooncake-before-after.png` |
| 7 | 頭飾與兔耳跟著頭轉、保持貼合 | `headwearStaysOnTheHead` |
| 8 | Reduced Motion 代表姿勢 | `reducedMotionHoldsTheKeyPose`；`p6/9-reduced-motion.png` |
| 9 | 中秋看不到月亮時看月餅（D-37） | `cakeLevelLooksDown`；`5a/5b-moon-cake-*.png` |
| 10 | 扶正聖誕襪（D-39 方案 A） | `sockTiltAndStraightenAngles`、`sockContactTiming`、`sockContactIsMidBody`、`sockStartsAndEndsAtTheStance`；`3-xmas-sock.png`、`3-xmas-sock-before-after.png` |
| 11 | 48 px 禮物的紅緞帶 | `gift-48px-before-after.png` |
| 12 | 既有天氣動畫與黃金數值不變 | 所有既有 golden 測試照樣通過 |

## Accepted Minor（審核接受，不修改）

| # | 項目 | 說明 |
|---|---|---|
| m1 | 聖誕襪歪著時腳尖靠近包袱布角 | 500–1500 ms 襪子歪 −22° 時，腳尖靠近包袱左布角；襪子在包袱後面的圖層，看起來是從包袱後面露出來（關鍵影格 `3-xmas-sock.png`） |
| m2 | 月餅舉起、放下時從包袱前面經過 | 約 1.2–2.1 s、3.5–4.1 s；手在身體前面、圖層正確（`6-moon-bundle-pass.png`） |
| m3 | 中秋看月餅的低頭幅度偏小 | 維持現狀（審核決定） |

## Not tested

| 項目 | 原因 |
|---|---|
| 實機上的動畫流暢度（30 FPS 姿勢、6 FPS 筆觸） | 沒有實體手機；而且節日演出還沒接到畫面 |
| 實機效能（每格重算的成本）與耗電（待機每 20–40 秒一次） | 同上 |
| Android 13 以下、各廠牌 | 同上 |
| 模擬器上的節日畫面 | 還沒接畫面，P6-4／P6-5 |
| 真實日期觸發（春節、聖誕、中秋當天） | P6-4 接畫面後用假日期在模擬器驗收 |
