# 墨丸 V3 — P6 節日系統結案報告

> 日期：2026-10-29　分支：`feature/mowan-weather-poses`
> **狀態：P6 正式結案**（2026-10-29 最終審核通過）。不開 PR、不合併 main、V3 仍在除錯開關後面（改成唯一正式角色是 P7）。
> 相關文件：[P6 計畫](character-p6-plan.md)・[P6-3 驗收報告](character-p6-3-acceptance.md)・[P6-4c 驗收報告](character-p6-4c-acceptance.md)・[未定案事項](character-open-decisions.md)

## 結案依據

| 項目 | 結果 |
|---|---|
| CI Build APK #109（`43e3460`） | 313 個 JVM／Robolectric 測試與腳本自測（`test-quiet.sh`）通過 |
| Emulator acceptance #56（`43e3460`） | PASS 51、FAIL 0、WARN 0（Android 14、Pixel Launcher） |
| 審核 | P6-1～P6-3（2026-10-26）、P6-4a／P6-4b（2026-10-27）、P6-4c 圖片與整合（2026-10-28）、P6 整體（2026-10-29） |

節日美術、角色姿勢、成長降級、天氣組合、小工具版面都已審核；P6 不再修改已驗收的美術或節日規則。

## 各階段

| 階段 | 內容 | 結論 | 文件 |
|---|---|---|---|
| P6-1 | 節日日期、插槽與能力、一天一次的狀態機 | 完成 | [P6 計畫](character-p6-plan.md) |
| P6-2 | 節日服裝與道具的靜態繪製（D-01 C、D-02 B、D-03 1.0、D-29、D-30、D-38） | 完成 | 同上 |
| P6-3 | 主演出、待機、降級層級、Reduced Motion 代表姿勢（D-39 方案 A） | 結案 | [P6-3 驗收報告](character-p6-3-acceptance.md) |
| P6-4a | `MowanView` 接線、第一次可見觸發、一天一次持久化、節日當天不打雷、`resumed()` | 通過 | P6 計畫 §11.6、§11.7 |
| P6-4b | 故事卡、日曆頁、除錯選單、前景跨午夜（D-44） | 通過 | P6 計畫 §11.8 |
| P6-4c | 小工具節日外觀（D-16、D-43 Confirmed）、正式整合、靜止驗收補強 | 正式完成 | [P6-4c 驗收報告](character-p6-4c-acceptance.md) |

## P6-5（原計畫）逐項核對

原計畫的 P6-5 是「黃金數值、截圖矩陣、模擬器節日段落、驗收報告」。P6 的工作在 P6-3、P6-4 已經分段驗收，以下逐項對照現有證據；**沒有證據的不算通過**。

| # | 原定項目 | 證據 | 結論 |
|---|---|---|---|
| 1 | 黃金數值總整理 | `golden-festival.json`、`golden-festival-acts.json`（`FestivalPerformanceGoldenTest`：368 個姿勢、12 張完整畫面）；既有 `golden*.json` 全部不變（每次 CI 都比） | Pass |
| 2 | 截圖矩陣：3 節日 × 8 天氣 | `FestivalShotsTest.weathers`（第 14 頁）；小工具 `p6-4c-widget/1-*-widgets.png`（4 版面 × 9 天氣） | Pass |
| 3 | 截圖矩陣：成長階段 | `FestivalShotsTest.growth`（第 1、2、3、4、5、6、8、11、14 頁 × 晴、雨）、`FestivalAnimShotsTest.growth` | Pass |
| 4 | 截圖矩陣：裝備狀態 | 包袱背著／卸下（`FestivalShotsTest.overlap`）、節日頭飾開關（`FestivalAppShotsTest.rulesInTheApp`）、紙帽讓位（`FestivalSceneTest`）。**3 節日 × 8 天氣 × 14 頁 × 裝備的完整乘積沒有做**，只有主要組合 | 部分（主要組合 Pass；完整乘積列入 P7 回歸） |
| 5 | 模擬器：假日期進入春節、聖誕、中秋 | 模擬器第 12 段截圖（P6-4b 起每次都有）；P6-4c 桌面小工具 | Pass（截圖；春節在桌面有逐步檢查） |
| 6 | 模擬器：主演出播一次 | 第 12 段春節錄影有作揖影格（`acceptance/mowan-p6-4b` 的 `strip-spring.png`） | Pass（錄影的人工判讀，不是自動檢查） |
| 7 | 模擬器：重開不播 | 同一天重開 App 全程站姿（`strip-spring-again.png`）；`festivalMainPlayed` 紀錄只有一筆 | Pass（同上） |
| 8 | 模擬器：換天氣不播 | 中秋同一天換成雨天重開（`fest-moon-rain`）；紀錄沒有新增 | Pass（截圖與紀錄；換天氣後的畫面沒有逐格檢查） |
| 9 | 模擬器：Reduced Motion 的節日畫面 | 只有 Robolectric（`reducedMotionHoldsTheKeyPoseThenReturns`、`p6/9-reduced-motion.png`） | **Not tested（模擬器）** |
| 10 | 模擬器：小工具外觀 | P6-4c：小工具宿主 5 種大小、靜止、Pixel Launcher 換到春節與狀態更新 | Pass |
| 11 | 模擬器：跨午夜 | 只有 Robolectric 的系統廣播與假日期；桌面小工具用 `NEW_DAY` 廣播 | **Not tested（真的跨過午夜）** |
| 12 | 驗收報告（Pass／Minor／Blocker／Not tested） | 本文件＋P6-3、P6-4c 報告 | Pass |

## Minor（保留，審核接受）

| # | 項目 | 出處 |
|---|---|---|
| m1 | 聖誕襪歪著時腳尖靠近包袱布角 | P6-3 |
| m2 | 月餅舉起、放下時從包袱前面經過 | P6-3 |
| m3 | 中秋看月餅的低頭幅度偏小 | P6-3 |
| m4 | 48dp 的紅包、月餅細節小；COMPACT 聖誕主要靠帽子辨識 | P6-4c（接受現狀） |

Blocker：0。

## Not tested（保留）

| 項目 | 原因 |
|---|---|
| 實機（動畫流暢度、Reduced Motion 切換、效能、耗電） | 沒有實體手機 |
| Pixel Launcher 以外的 launcher、Android 13 以下 | 模擬器只有 Android 14、Pixel Launcher |
| 模擬器／Launcher 上的 WIDE 版面 | 宿主 290×125dp 被系統套用成 COMPACT（既有行為）；WIDE 只有 Robolectric |
| 真正跨過午夜（App 前景、小工具） | 只用假日期與廣播 |
| 模擬器上 Reduced Motion 的節日畫面 | 只有 Robolectric |
| 模擬器上的前景跨午夜、除錯選單實際點擊 | 只有 Robolectric |
| 模擬器桌面上中秋、聖誕的逐步更新 | 只截圖 |
| 撕頁中跳過天氣更新 | 已接受的取捨（等撕完或回到 App 才換上新天氣） |
| 3 節日 × 8 天氣 × 14 頁 × 裝備的完整乘積 | 只有主要組合（P7 回歸） |
| **#40、#53 小工具靜止檢查失敗的原因** | **Unknown**（當時沒有記錄小工具更新；背景抓天氣只是推測之一，不是已確認的原因） |

## 留給 P7

- V3 改成唯一正式角色、移除舊火柴人與除錯切換、清理 ViewFlipper（見 [P7 計畫](character-p7-plan.md)）。
- 站姿筆觸重畫兩次（P6-4a 補查，記為 P7 效能待辦）。
- `tools/emulator/quiet.sh` 的 `count_updates()`：少了開始或結束標記時應判定 FAIL。
- 上面所有 Not tested 中可以在 P7 驗的項目（實機、跨午夜、WIDE、其他 launcher、Android 8–13）。
