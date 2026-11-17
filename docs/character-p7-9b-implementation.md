# P7-9b 天氣接力彩蛋：正式 App 整合與模擬器驗收

> 狀態：**實作完成，等待驗收**（看過模擬器錄影後再決定是否正式通過）。不合併 main、不開 PR、不擴充另外五種天氣。
> 分支：`feature/p7-9b`（從 main `390e2d3` 建立，併入 `feature/p7-9a`）。
> 決策：[D-56](character-open-decisions.md)（D-48 的補充，D-48 原文不改）。分鏡：[P7-9a](character-p7-9a-weather-egg.md)（驗收通過，本階段不改）。

## 1. 證據類型

| 項目 | 證據 | 狀態 |
|---|---|---|
| 單元與 Robolectric 測試（含 54 張鎖定 golden） | CI「Build APK」 | 見 §6 |
| 實際 App 操作錄影、位置量測 | CI「Emulator P7-9b weather egg」（Android 14 模擬器、Pixel 6 profile、軟體繪圖） | 見 §6 |
| 分鏡沒有改 | P7-9a 的自動檢查（跳格、手臂長度）在 P7-9b 分支上的數字跟驗收時一模一樣 | 有 |
| 實機、實機效能、TalkBack 實際朗讀 | — | **Not tested** |
| 人工審核模擬器錄影 | — | 等你審 |

## 2. 從 P7-9a 重用的東西

| P7-9a | P7-9b |
|---|---|
| `test/.../mowan/WeatherEgg.kt`（三組分鏡） | 移到 `main/.../mowan/WeatherEgg.kt`。時間、姿勢、素材都不變；只改兩點：① 大墨丸的「日常」由呼叫的人傳進來（App 裡是時間軸當下的姿勢，預覽裡是 P7-9a 的固定起點），② 雪花跟著大墨丸右手的那段從預覽測試搬進 `motes` |
| 預覽測試裡畫素材的程式 | `main/.../mowan/WeatherEggPaint.kt`（畫法不變，預覽與 App 共用） |
| `WeatherEggPreviewTest` | 保留，改用正式程式；輸出的自動檢查數字跟 P7-9a 驗收時一樣 |

## 3. 架構

```
點天氣資訊 ──► TearPageView（手勢判定）──► MainActivity.tapWeather()
                                              │ WeatherEggSession.decide()：PLAY / TEXT_ONLY / IGNORED
                                              ▼
                                    WeatherEggController（每 33ms 一格）
                       ┌──────────────────────┼───────────────────────┐
                       ▼                      ▼                       ▼
         日曆頁 mowan_still            WeatherEggOverlay           MowanView（故事卡）
         播放時 INVISIBLE、            不吃觸控的疊層：點擊回饋、    egg 掛勾：時間軸的日常姿勢
         換回時 VISIBLE               小墨丸動畫、接力素材          → 分鏡的姿勢；環境、筆觸、時鐘照常
```

| 檔案 | 做什麼 |
|---|---|
| `mowan/WeatherEggSession.kt` | 狀態（IDLE／PLAYING／COOLDOWN）與規則，不依賴 Android |
| `WeatherEggController.kt` | 開始、每一格（中斷檢查、切換小墨丸）、結束與清理 |
| `WeatherEggOverlay.kt` | 疊層：位置每一格從實際元件算（跟著捲動走），只畫在捲動區域裡 |
| `TearPageView.kt` | `tapTarget`／`onTap`：點擊與撕頁的判定；`onPeelStart`：開始掀紙時通知 |
| `mowan/MowanView.kt` | `egg` 掛勾；彩蛋中點故事卡墨丸只換台詞 |
| `mowan/MowanPainter.kt` | `rainAlpha`、`drawRain`：雨雪那一層可以淡入淡出（預設 1，其他畫面不變） |
| `mowan/Mowan.kt` | `dailyPose`：把原本 `frame` 裡算日常姿勢的兩行抽出來（行為不變） |
| `MainActivity.kt` | 接線；天氣資訊的 TalkBack 點擊；除錯版的 logcat 紀錄 |

### 3.1 手勢（天氣資訊在左下撕頁區裡）

撕頁區（左下、右下 35%×35%）**沒有縮小**。只有「按下的點在天氣資訊上（四周多 8dp）」時改成先等：

| 手指 | 結果 |
|---|---|
| 在天氣資訊上按下，移動 ≤ touchSlop 就放開 | 點擊：播彩蛋，紙完全不動 |
| 在天氣資訊上按下，移動超過 touchSlop | 從左下角撕頁（跟以前一樣），不算點擊 |
| 在紙角其他地方按下（包括天氣資訊外面的左下角、右下角） | 立刻開始撕，跟以前完全一樣 |
| 手勢被系統取消 | 都不算 |
| 撕頁中（手指拉著或動畫中）點天氣 | 不啟動 |
| 播放中開始掀紙（手指或「撕下這一頁」按鈕） | 先取消彩蛋、靜態墨丸回來，**再**畫要撕的那張快照；大墨丸用 0.3 秒從當下姿勢接回日常 |

只有今天那頁、有天氣資料時天氣資訊才是點擊目標；別天那頁在那裡按下就是撕頁。

### 3.2 日曆小墨丸

- 不改 RemoteViews 的版面：疊層照 `mowan_still` 的位置、大小、`imageMatrix` 畫，用跟靜態圖一樣的簡化畫法（`Detail.of`，密度上限 3）。
- 不會兩隻：播放時靜態圖設成 INVISIBLE（還佔位置，版面不動），換回時設回 VISIBLE；兩件事在同一格做。
- 第一格與最後一格：開始、結束各 0.3 秒，角色就是靜態圖那一格（同一張筆觸、同一個姿勢），只有雨雪從靜態的位置淡到會動的位置（結束反過來）。測試確認第 0 格跟靜態圖**每個像素都一樣**，換回前最後一格看不出差別。
- 播完：疊層清空、暫存的圖回收、靜態圖顯示。桌面小工具完全沒有碰（彩蛋不重畫任何靜態圖，測試確認 `MowanBitmap.renderCount` 不變）。

### 3.3 故事卡大墨丸

- 重用 `MowanView` 與 renderer：每一格先算時間軸此刻的日常姿勢，交給分鏡決定要畫的姿勢；環境（雨、雪、太陽）、筆觸（6 FPS）、外觀、大小都跟平常一樣。
- 接手、接回都從「此刻的日常姿勢」過渡，所以不管日常走到哪裡點，都接得上（本機檢查：四個不同起點，每 33ms 最大移動 2.9 單位）。
- 原本的點擊（P7-2）保留；彩蛋播放中點它只換台詞、不重播、不打斷彩蛋。
- 故事卡高度、文字區域不變：彩蛋不換台詞、不改任何 LayoutParams。

### 3.4 接力素材

光點、雨滴、雪花畫在疊層上，可以跨過日曆與故事卡；一格最多 6 個基本圖形（晴 6、雨 5、雪 3），沒有粒子系統。疊層 `isClickable=false`、TalkBack 看不到，不參與版面量測。

## 4. 播放狀態與中斷

| 情況 | 結果 | 測試 |
|---|---|---|
| 播放中連點 | 忽略（不重來、不疊加） | `tappingAgainWhilePlayingIsIgnored`、模擬器 |
| 播完（或取消）1 秒內 | 不重播 | `allThreeWeathers…`、模擬器 |
| 離開 App、鎖定畫面（onPause） | 取消，大墨丸直接回日常；回來不續播 | `leavingTheAppCancels`、模擬器 |
| 換頁（前一天、後一天、回到最上面那頁） | 取消 | `switchingPagesCancels` |
| 開始撕頁 | 取消，大墨丸 0.3 秒接回日常 | `startingToTearCancels…`、`theTearButtonAlsoCancels…`、模擬器 |
| 天氣更新 | 取消，換成新天氣的日常 | `aWeatherUpdateCancels…` |
| 成長故事 | 優先：不反應；播放中開始就取消 | `growthStoryHasPriority` |
| 節日 | 不播接力，只換一句話 | `festivalDayOnlyShowsTheLine` |
| Reduced Motion | 不播動作，故事卡用固定高度換一句話；播放中打開就取消 | `reducedMotion…`、模擬器 |
| 另外五種天氣、墨丸還沒長齊 | 只換一句話 | `theOtherFiveWeathers…`、`beforeMowanIsFullyGrown…` |
| 沒有天氣資料 | 不觸發（那裡按下就是撕頁） | `withoutWeatherDataNothingHappens` |
| Widget | 不播 | `theWidgetAndTheStaticImageAreNotRedrawnByTheEgg` |

每次結束（播完或任何取消）都做同一套清理：停止排程、疊層清空並回收暫存圖、靜態墨丸 VISIBLE、大墨丸交還時間軸。測試 `assertClean` 每個情況都檢查這四件事。

## 5. 不改的東西

- P7-9a 的三組分鏡（時間、姿勢、素材）。
- 定稿的墨丸美術與既有天氣動畫（`Poses`、`Mowan.render` 的畫法）。
- P7-7 的 A1＋V2b 版面（日曆頁、操作區、故事卡位置）。
- 54 張鎖定 golden（沒有更新任何一張）。

## 6. 測試結果

見回報；CI 的 run 編號與模擬器結果在完成後補上。

## 7. 已知 Minor、Not tested、效能風險

見回報。
