# 墨丸 V3 — 實作計畫（Phase 2B）

> 狀態：階段順序已確認（2026-10-24）。P0–P3 已完成。P4：W1–W7 與 D-22 已完成（故事卡用 V3 時，8 種天氣與包袱都已接上；V3 改成預設在 P7）。P5（Widget V3）已結案（2026-10-26）。P6：P6-1～P6-3、P6-4a 完成，P6-4b 等待審核，見 [P6 計畫](character-p6-plan.md)。W7 驗收見 [W7 驗收報告](character-w7-acceptance.md)。
> 相關文件：[整合規格](character-integration-spec.md)・[未定案事項](character-open-decisions.md)

## 排序原則

原本的需求順序是「角色 → 動畫 → 天氣 → 節日 → Widget」。依照 repo 現況調整成下表，理由如下：

- 現有 App **完全沒有 V3 繪圖基礎**（筆觸、輪廓、boil 都是 Missing）。所以第一步一定是「畫得出靜態的 V3 墨丸」，而且要先用測試證明跟原型一致。
- 14 頁解鎖資料已經存在，只差繪圖。所以「成長外觀」可以接在 renderer 之後，風險低。
- Widget 跟 App 共用 renderer，但有 bitmap 大小限制。放在節日之前，可以先處理既有的技術債（24 張 bitmap、重複解析 JSON）。
- 節日最複雜（狀態機、能力、降級、持久化），放在基礎穩定之後。

| 階段 | 名稱 | 複雜度 | 依賴 |
|---|---|---|---|
| P0 | 原型原始碼入庫 + 黃金數值匯出 | 低 | 無 |
| P1 | V3 靜態 renderer | 中 | P0 |
| P2 | Line boil + V3 基本姿勢 + 生命週期 | 中 | P1 |
| P3 | 14 頁成長外觀 + 能力 + 紙帽裝備狀態 | 中 | P1 |
| P4 | 天氣對應 V3 | 低–中 | P2、P3 |
| P5 | Widget V3 | 中 | P1、P3 |
| P6 | 節日系統 | 高 | P2、P3、P4 |
| P7 | 整合、效能、QA | 中 | 全部 |

每個階段做完都要保持 App 可以正常使用。新 renderer 先放在 debug 開關後面，到 P7 才預設開啟。

---

## P0 — 原型原始碼入庫 + 黃金數值匯出

> **完成**：`docs/prototypes/`（6 個原型 HTML、`src/`、`tools/export-golden.js`），黃金數值在 `app/src/test/resources/mowan/golden.json`（P2 起共 61 張影格，含晴天、雨天）。

- **目標**：讓移植有可以核對的依據，不靠記憶或猜測。
- **檔案**：
  - `docs/prototypes/`（V3 line boil、festival v7、hat / umbrella 原型 HTML + README）
  - `app/src/test/resources/mowan/golden/*.json`
- **技術**：用 Node 或瀏覽器跑原型裡的幾何函式，匯出：
  - 各姿勢 × 5 個 boil 變體的多邊形點
  - `ring()` 輸出
  - mulberry32 序列
- **驗收**：黃金數值 JSON 可以重新產生，內容跟原型畫面一致。
- **風險**：原型檔案大（festival 約 126 KB）；要確認放進 repo 是否可接受（D-14）。
- **複雜度**：低。

## P1 — V3 靜態 renderer

> **完成（範圍：一般站姿）**：
> - `…/mowan/Rng.kt`、`PathData.kt`、`Geometry.kt`、`Mowan.kt`：純 Kotlin，輸出一張影格的繪圖清單。
> - `…/mowan/MowanPainter.kt`、`MowanView.kt`：Android 端，Path 在建構時就轉好，onDraw 不配置物件。
> - `MowanGoldenTest`：亂數、頭部輪廓、14 頁部位、19 張影格的每一條筆畫都跟原型一致（誤差 0.011 以內）。
> - 除錯選單「看墨丸 V3（靜態預覽）」：切頁數、筆觸影格、紙帽。故事卡和 Widget 仍然是舊火柴人。
> - `ScreenshotTest.mowanPages` 產生 `mowan-pages.png`（CI 截圖）。
> - 預設名字改成「墨丸」（D-08）。
> - 包袱（第 14 頁）V3 還沒設計（Q21），所以第 14 頁的外觀跟第 13 頁一樣，沒有自己畫。
> - 48px 簡化（P5）、節日（P6）還沒做。

- **目標**：在 App 裡畫出靜態的完整墨丸（第 14 頁外觀、normal 姿勢、boil 變體 0），外觀跟原型一致。
- **檔案（新）**：
  - `…/mowan/Rng.kt`：mulberry32、hash
  - `…/mowan/Brush.kt`：Catmull-Rom 中心線、左右偏移、圓頭
  - `…/mowan/Head.kt`：ring、一筆
  - `…/mowan/MowanGeometry.kt`：姿勢 → 多邊形，純 Kotlin
  - `…/mowan/MowanColors.kt`：V3 色票
  - `…/mowan/MowanRenderer.kt`：多邊形 → `Path` → `Canvas`
  - `…/mowan/MowanView.kt`：先只畫靜態
- **測試**：`app/src/test/.../mowan/GeometryGoldenTest.kt`
- **技術**：
  - 純 Kotlin，不用 Android API，才能在 JVM 測試。
  - 32-bit 整數運算要符合 JS 的 `Math.imul` / `>>> 0` 語意。
- **依賴**：P0。
- **驗收**：
  - 黃金數值測試全部通過（誤差 < 0.01）。
  - Robolectric 截圖跟原型並排，人工確認一致。
  - V3 比例、五官、輪廓、一筆都沒有改。
- **風險**：
  - 浮點差異（JS double vs Kotlin `Float`）→ 幾何計算用 `Double`，只在最後轉 `Float`。
  - 填色多邊形的自我交叉 → 用 `Path.FillType` 跟原型 SVG 的 `fill-rule` 一致。
- **複雜度**：中。

## P2 — Line boil + V3 基本姿勢 + 生命週期

> **完成**：
> - `…/mowan/Poses.kt`：一般、晴天伸懶腰（5.5 秒循環）、雨天撐傘（4.2 秒循環），三種表情與一筆的三種樣子。
> - `Mowan.kt`：晴天的太陽、雨絲（8 FPS）、雨天的水灘、雨傘（傘柄、傘面、傘骨、拳頭）。沒有右手時不撐傘，但雨和皺眉照樣有；成長焦點期（還沒有手）天空降到 55%。傘的大小是參數（D-03 未定，預設原尺寸）。
> - `MowanView`：筆觸用時間算（6 FPS，順序 0,3,1,4,2）。一般站姿 5 張影格算一次就存起來，每 1/6 秒才重畫；天氣姿勢每 33 毫秒重算（約 30 FPS，跟原型一樣，比原本計畫的 vsync 省電）。系統關閉動畫時停在代表姿勢、不排重畫；看不到或離開畫面時不排重畫。
> - 故事卡：除錯選單「故事卡用墨丸 V3」打開後才換成 V3（預設還是舊火柴人，P7 才改預設）。故事動作（跳、揮手…）V3 還沒設計，先用一般站姿；點天氣時晴 → 伸懶腰、雨／雷雨 → 撐傘，其他天氣先用一般站姿（D-10）。
> - 測試：`MowanGoldenTest`（61 張影格逐筆比對原型）、`MowanSceneTest`（6 FPS 順序、8 FPS 雨、要有右手才撐傘、天空淡化、天氣對應）、`MowanViewTest`（顯示時排重畫、隱藏／離開畫面／關閉動畫時不排）。
> - Widget 還是舊火柴人（P5）。

- **目標**：
  - 6 FPS line boil。
  - 三個 Confirmed 姿勢：normal、晴天伸懶腰（約 5.5 s）、雨天撐傘（約 4.2 s）。
  - 正確停止重畫。
- **檔案**：
  - `…/mowan/Boil.kt`：變體順序 [0,3,1,4,2]
  - `…/mowan/Poses.kt`、`…/mowan/Track.kt`：關鍵影格插值
  - `MowanView.kt`
- **技術**：
  - idle 時用 `postInvalidateDelayed(166)` + 快取 Path。
  - 姿勢動畫期間才用 vsync。
  - boil 索引用時間計算。
  - `ValueAnimator.areAnimatorsEnabled()` 判斷 Reduced Motion。
  - `onVisibilityAggregated` / detach 時停止。
- **依賴**：P1。
- **驗收**：
  - 單元測試：時間 → boil 索引。
  - Reduced Motion 時 boil 固定。
  - 背景時沒有排程中的重畫。
  - 人工確認姿勢節奏跟原型一致。
- **風險**：
  - 舊 `StickmanView` 和故事卡、天氣點擊的 6 秒動作綁在一起 → 用介面包起來，舊新可切換。
- **複雜度**：中。

## P3 — 14 頁成長外觀 + 能力 + 紙帽裝備狀態

> **完成**：
> - `…/mowan/Capabilities.kt`：照原型 getCapabilities() 移植，只從已解鎖的部位推導（不另存頁碼表）。成長階段：第 1 頁只有線、第 2–3 頁有頭、第 4 頁一隻手、第 5–6 頁雙手、第 7 頁起完整。繪圖裡的「要有右手才撐傘」「沒有腳落在地上」「成長焦點期天空變淡」「閉眼笑要有眼睛」都改用這裡判斷。
> - 紙帽：「拿到」由故事決定（第 11 頁），「戴著」存在 prefs `mowan` 的 `paperHatOn`，預設戴著。怎麼脫下、節日時要不要自動收起還沒定（D-05），所以沒有給使用者的介面，只有除錯選單能切換；故事從頭開始時回到預設。
> - 故事挑選用的 `wearing` 條件還是看「拿到」，不看「戴著」；目前沒有任何故事片段用到這個條件，等 D-05 定案再決定。
> - `chapter1.json` 沒有修改。
> - 測試：`CapabilitiesTest`（14 頁每一項能力都跟原型一樣、第 12–13 頁跟第 11 頁完全相同、第 14 頁跟第 13 頁相同、紙帽要拿到而且戴著才畫）、`MowanSettingsTest`。
> - CI 截圖 `story-card-pages.png`：故事卡用 V3 時第 1–14 頁各一張。

- **目標**：
  - 第 1–14 頁每頁顯示正確部位（第 2 頁有一筆，第 3 頁有腮紅）。
  - 能力系統。
  - 紙帽「拿到 / 戴著」分開存。
- **檔案**：
  - `…/mowan/Capabilities.kt`
  - `…/mowan/MowanState.kt`：prefs `"mowan"`
  - `StoryBook.kt`：只加讀取介面，不改 JSON、不改順序
  - `DebugMenu.kt`：跳頁預覽
- **依賴**：P1。
- **驗收**：
  - 14 頁逐頁測試能力。
  - 每頁截圖。
  - 第 12、13 頁外觀不變。
  - `chapter1.json` 沒有任何修改。
- **風險**：
  - 紙帽裝備的互動方式未定（D-05）→ 先只存狀態、預設戴著，不做 UI。
- **複雜度**：中。

## P4 — 天氣與包袱（定稿後）

設計已在 Phase 2B 收斂。規格見[整合規格 §5](character-integration-spec.md)，參考程式是 `docs/prototypes/src/weather.js`。
每一項都在除錯開關（故事卡用 V3）後面完成，不影響正式使用者看到的畫面。

| 項目 | 內容 | 依賴 | 驗收 |
|---|---|---|---|
| **W1 參考與黃金數值** ✅ | `export-golden.js` 改成也能從 `weather.js` 的 `drawFrame` 匯出。先確認 `drawFrame` 和原本的 `Renderer` 在晴、雨、一般站姿畫出一樣的筆畫，再匯出 6 種新天氣 × 第 1、3、4、5、8、14 頁 × 數個時間點，加上包袱 | 無 | 一致性檢查通過；`golden.json` 可以重新產生 |
| **W2 姿勢與表情** ✅ | `Poses.kt` 加 6 種天氣姿勢、`jitter`、`track` / `env`；`Face` 加 4 種 | W1 | 黃金數值比對通過；JVM 測試：手臂長度在 §5.2 的範圍內、手尖不進頭裡、雷雨左手在傘柄上 |
| **W3 天氣環境** ✅ | `Mowan.frame` 畫 §5.6 的環境；`Weather` 加 6 種；`Mowan.render` 正確處理 W2 新增的姿勢欄位（見下面的 W3 檢查清單） | W1、W2 | 黃金數值比對通過；第 1 頁只有環境、降級規則照 §5.3 |
| **W4 包袱 B1** ✅ | `BUNDLE` 圖層、跟隨規則（§5.5）；第 14 頁才畫 | W1 | 黃金數值比對通過；更新 `CapabilitiesTest`；CI 截圖：一般、伸懶腰、撐傘、冷 |
| **W5 時間與事件** ✅ | `MowanView`：各天氣的代表格、打雷事件（隨機間隔、跳過不補播）、靜止畫面雪花不擋臉 | W2、W3 | JVM 測試：給定時間 → 代表格；打雷遇到更高優先時不觸發也不補；Reduced Motion 時沒有閃電、不排重畫 |
| **W6 接上故事卡** ✅ | `weatherOf` 對應 8 種 Mood；故事卡平常就顯示當天的天氣環境；天氣更新不重啟同一種天氣的姿勢 | W3、W5 | Robolectric：天氣更新時姿勢時鐘不重來；CI 截圖：8 種天氣 × 第 1、4、5、14 頁 |
| **W7 視覺驗收** ✅ | 在 CI 截圖、Android Emulator 和實機上，依整合規格 §5.1 的 Pending 項目逐一看過 | W6 | 每項標成 Pass／Minor／Blocker／Not tested（實機沒有測，見報告） |

W1、W2 的實際結果：

- **W1**：黃金數值改成只匯出「姿勢」（`export-golden-weather.js` → `golden-weather.json`，432 組新天氣＋14 組晴雨）。原本計畫的「從 `drawFrame` 匯出整張畫面」移到 W3，跟環境一起做。比對結果見整合規格 §5.7。
- **W2**：
  - `Poses.kt`：新增 6 種天氣、`Pose.jittered`、`Effects`。
  - `Keyframes.kt`：關鍵影格內插。
  - `Face`：新增 4 種表情。
  - 雷雨的驚嚇改成傳入「打雷後經過的時間」，什麼時候打雷留給 W5。
  - `Mowan.frame` 還不會畫新天氣（W3），正式畫面沒有變化。

W2 之後補的測試：

- `GrowthRenderTest`：第 1–14 頁 × 每種天氣（雷雨含驚嚇）× 6 個時間點 × 紙帽戴／脫，經過 `Mowan.render` 後，畫出的部位剛好等於已解鎖、看得到的部位。雨傘只在姿勢有握點而且有右手時出現。
  - 為了這個測試，`Mowan.frame` 拆成「選姿勢」和 `render(pose)`。每一筆加上 `part` 標記它屬於哪個部位。
  - 黃金數值比對證明畫面完全沒有變。
- `WeatherPosesTest` 的連續性檢查：相鄰兩格（33 ms）手尖最多 1.5、頭最多轉 1°、頭最多晃 1。
  - 陰、霧、冷、雪、雷雨（含驚嚇前後）、熱的擦汗段、已上線的晴雨都通過。
  - 熱天抬手、放手原本不通過（D-22）；定案後已修正，`@Ignore` 已拿掉。

W3 檢查清單（W2 新增、目前 Renderer 還沒處理的欄位）：

- [x] `Face.HOT`、`FOG`、`COLD`、`STARTLE` 照 `weather.js` 的 `drawFace` 畫：眼、眉、嘴、腮紅不透明度。`when` 已列出全部表情，沒有 `else` 落回一般表情。
- [x] `headDx`：頭部變換加上水平位移；圍巾帶子跟著移動。
- [x] `gaze`：眼睛 ×1，閉眼線、眉毛、嘴巴 ×0.5。
- [x] `effects`：
  - [x] 汗滴（要有眼睛）
  - [x] 圍巾拉高（`scarfUp`：上移 1.0、加寬 1.12）
  - [x] 左手握傘柄的第二個拳頭（要有雙手）
  - [x] 白氣（要有嘴巴）
  - [x] 閃電
- [x] 黃金數值從 `weather.js` 的 `drawFrame` 匯出整張畫面（`golden-weather-render.json`，96 張），逐筆比對新天氣在第 1、3、4、5、8、14 頁的畫面。
- [x] 環境：大太陽、熱氣、雲（陰天會飄）、雷雨雲、閃電、密雨、霧帶前後兩層、雪花、積雪、冷天的地面。
- [x] Reduced Motion：`MowanView.stillClock` 補上 6 種天氣的代表格；靜止畫面的雪花不放在頭和臉上；雷雨不傳打雷時間，所以沒有閃電。

W4 的實際結果：

- `Mowan.Layer` 加 `BUNDLE`（地面之後、傘柄之前）。`render` 照 `weather.js` 畫：背帶、布包、兩道皺褶、兩個布角、結、三顆碎花，共 14 筆。
- 跟隨規則：跟著身體上段與頭的晃動；左手舉高時以布結為中心提起、擺動。
- `frame`、`render` 加 `bundleOn`。`MowanSettings.bundleOn` 存裝備狀態，預設背著；除錯選單可以切換；故事卡和預覽都照這個狀態畫。
- 黃金數值：`golden-bundle.json` 只含包袱自己的筆畫，第 14 頁 × 9 種姿勢共 19 張。
  - `golden.json`、`golden-weather-render.json` 匯出時沒有包袱，所以這兩個測試改成比對「卸下包袱」的畫面。
- 測試：
  - `BundleTest`：黃金數值、第 1–13 頁不出現、第 14 頁照裝備狀態、圖層、手舉高時提起。
  - `MowanSettingsTest`：保存與重設。
  - 「第 14 頁跟第 13 頁一樣」改成「第 14 頁只多了包袱，卸下時跟第 13 頁一樣」。
  - `GrowthRenderTest` 把包袱和紙帽的戴、脫組合都跑一遍。

W5 的實際結果：

- 新增 `MowanTimeline`（純 Kotlin，時間由呼叫的人傳入、亂數可換）：
  - 一種天氣一條時鐘。
  - 打雷排程：隨機間隔、錯過不補、遇到更高優先的演出就跳過。
  - Reduced Motion 時回傳代表格。
- `MowanView` 改成從時間軸取樣。修掉了 W2 以來的一個問題：換頁、換紙帽或包袱會讓動作重來，現在只有換天氣才會。
- 更高優先的演出用 `higherPriorityPlaying` 傳入。P6 之前沒有這種演出，固定是 false。
- 測試：
  - `MowanTimelineTest` 14 個：打雷間隔、觸發、結束、跳過、背景回來、換天氣重設、Reduced Motion、週期接縫、筆觸節奏、同一條時間軸。
  - `MowanViewTest` 加 2 個：換頁／紙帽／包袱不重來；Reduced Motion 開關不卡住。

W6 的實際結果：

- 故事卡平常演今天的天氣。`MowanSettings.weatherOf` 對應 8 種天氣；天氣資料更新時 `showWeather` 會同步給墨丸。
- 新增 `MowanDirector`（動畫優先順序的介面）。`MowanView` 的時間軸用它決定要不要跳過打雷。
- 包袱的切換入口：故事卡文字下方的「卸下包袱 ›／背上包袱 ›」。
- D-17 晴天文案改成「太陽暖暖的，伸伸懶腰再出門。」（舊火柴人的草帽道具不動）。
- 測試：
  - `StoryCardTest`（Robolectric）5 個：8 種天氣、成長頁數、同天氣不重來、包袱入口、舊火柴人沒有入口。
  - `MowanDirectorTest` 3 個。
  - `MowanSceneTest` 的天氣對應改成 8 種。
- CI 截圖 `story-card-weathers.png`。

W7 的實際結果（[驗收報告](character-w7-acceptance.md)）：

- 沒有 Blocker。修正 1 個功能問題：App 停在前景時重新打開系統動畫，墨丸會停住（Android 13 以上改成收到設定通知就重畫）。
- 新增 Android Emulator 驗收（`.github/workflows/emulator.yml`、`tools/emulator/w7-acceptance.sh`）：8 種天氣、錄影、成長頁數、包袱入口點擊與保存、Reduced Motion、背景／前景、密度與字體大小、重畫次數。
- 新增回歸測試：背景時錯過的雷不補播、故事卡上的 Reduced Motion、前景重新打開動畫、7 種密度與字體組合下包袱入口不截斷。
- 驗收素材在 `acceptance/mowan-w7` 分支。實體手機沒有測（Not tested）。
- 留給 P5：48px 簡化版（表情在 48px 看不出來）。留給 P7：實機動態與效能、Reduced Motion 時 App 其他部分的少量重畫。

不在 P4 做：

- 節日道具和天氣動作的組合（P6）。
- 48px（P5）。
- 包袱能不能卸下、最終布色（待決策）。

## P5 — Widget V3

> 狀態：決策已確認（2026-10-26：D-04 靜態、D-07 簡化的完整角色、D-23 共用開關、D-24 日曆頁一起換、D-25 暫定 120 px、D-26 一個天氣元素、D-27 不播故事動作）。P5-1、P5-2（D-25 = 72 dp）、P5-3、P5-4 完成。**P5 結案**（2026-10-26 最終審核通過，Blocker 0；[P5 驗收報告](character-p5-acceptance.md)）。Minor 與 Not tested 保留；ViewFlipper 的 m5 留到 P7 決定舊火柴人去留時處理。V3 仍在除錯開關後面（P7 才改預設）。

### 現況（讀過程式碼）

- `CalendarWidget.build()` 同時被兩個地方用：桌面小工具（4 種版面）和 **App 主畫面的日曆頁**（`MainActivity` 傳更大的 `stickmanDp`）。改 Widget 的角色，日曆頁右下角的小人也會一起變。
- 角色區是 `ViewFlipper` + 6 張 bitmap（`StickmanRenderer.frames`），`autoStart` 輪播。尺寸：COMPACT 36dp、WIDE 42dp、MEDIUM 44dp、TALL 48dp（寬；高 = 寬 × 4/3），密度最多算到 3x。
- Android 12 以上一次送 4 種版面（SizeF map），所以一次更新 = 4 × 6 = 24 張 bitmap，3x 時約 2 MB。
- `setStickman` 每個版面都 new 一次 `StoryBook`（重新解析故事 JSON 4 次）。
- `ViewFlipper` 不理會系統的「移除動畫」；W7 的 Minor M7（關閉動畫時 App 仍有少量重畫）很可能就是日曆頁上這個 `ViewFlipper`。

### 角色在 Widget 裡怎麼畫

1. **同一套 V3 renderer**：`Mowan.frame(parts, variant, hatOn, weather, stillClock(weather), …, still = true, bundleOn)`。部位、紙帽、包袱、天氣都從 App 同一個來源讀（`StoryBook.parts`、`MowanSettings`、`WeatherService.current` + `weatherOf`），所以跟 App 一致。
2. **姿勢一律用靜止代表姿勢**（`MowanTimeline.stillClock`）：跟 App 的 Reduced Motion 畫面是同一張，W7 已經驗收過；Widget 不播天氣動作、不打雷、不演節日主演出（D-16）。
3. **不動**（D-04 已確認）：永遠一張靜態圖，不做筆觸輪播。
4. **小尺寸用簡化版，不直接縮小**（D-07）。

### 小尺寸簡化策略（D-07 的具體做法）

依「角色實際畫出來的高度（px）」分兩級，不看 dp，因為同一個 36dp 在 mdpi 只有 48px、在 xxxhdpi 有 192px：

| 等級 | 角色高度 | 畫法 |
|---|---|---|
| **完整** | ≥ 120 px | 跟 App 一樣（現有黃金數值不變） |
| **簡化** | < 120 px（48px 是主要目標） | 見下方 |

簡化版在 `Mowan.render` 加一個 `detail` 參數，用每一筆既有的 `part` 標籤過濾與加粗，**不改任何座標**：

- **保留**：頭的輪廓、一筆、眼睛、身體與四肢的姿勢、紙帽、圍巾、包袱、雨傘、當天天氣的一個主要元素（太陽、雲、雨絲、雪花）。
- **加粗**：所有筆畫畫出來至少 1.5 px（依縮放比例換算 strokeWidth 的下限），避免細線消失；圍巾、包袱改成實心色塊，不畫碎花、皺褶。
- **拿掉**：腮紅、嘴（< 72 px 時）、汗滴、白氣、霧的第二條帶子、地面陰影、多餘的雨絲與雪花（只留 3–4 個、稍微放大）。
- **表情**：只留眼睛。48px 時表情本來就看不出來（W7 M4），不勉強保留。
- 冷天抱身（W7 M1）在簡化版靠加粗的手臂圈來辨識，不另外改姿勢。

D-07 已確認用簡化的完整角色（不只畫頭）。D-26：只保留一個主要天氣環境元素，雨傘等角色道具不算在內。

### 更新與效能

- **更新時機**：維持現有（午夜、撕頁、天氣抓到新資料、時間／開機／App 更新廣播、`updatePeriodMillis` 3 小時）。另外補上**紙帽與包袱切換**時呼叫 `refreshAll`（目前故事卡的「卸下包袱 ›」不會更新 Widget）。
- **Bitmap 預算**：系統對一次 Widget 更新的 bitmap 記憶體上限約是螢幕 1.5 倍大小（1080×2400 約 15 MB）。V3 靜態 1 張 × 4 版面，3x 時約 0.34 MB；5 張 boil 約 1.7 MB。加一個測試算總位元組，超過設定的預算（建議 1 MB）就失敗。
- **快取**：故事解析一次（`StoryBook.beats` 改成 process 內共用）；同一次更新裡 4 個版面共用同一份 parts、天氣。
- **Reduced Motion**：建 RemoteViews 時讀 `ValueAnimator.areAnimatorsEnabled()`；關閉時只送 1 張、不設 flip。日曆頁上的 `ViewFlipper` 也一樣（順便處理 W7 M7）。
- **開關**：Widget 和日曆頁的 V3 跟故事卡用同一個除錯開關（「故事卡用墨丸 V3」，D-23），P7 才改成預設。關閉時完全走舊火柴人，現有截圖不變。

### 不能破壞的東西

- 日期、星期、月份、宜忌、忌、農曆、節日、日出日落、天氣文字、版面切換規則（`PageLayout`）全部不動；角色區的大小與位置不動。
- 舊火柴人路徑保留（D-15）。

### 測試

| 類型 | 內容 |
|---|---|
| JVM | 完整等級的 Widget 影格跟 App 靜止畫面逐筆一樣（同一個 `Mowan.frame`）；簡化等級：保留／拿掉的 part 清單、最細筆畫 ≥ 1.5 px、部位與天氣元素的有無照規則；14 頁 × 8 天氣 × 紙帽／包袱 組合都畫得出來 |
| 現有 Golden | `MowanGoldenTest`、`WeatherRenderGoldenTest`、`BundleTest` 不能變（完整等級不改座標） |
| Robolectric | 4 種版面 × 第 1、4、11、14 頁 × 8 種天氣建得出來；文字欄位跟改之前一樣（逐欄比對）；bitmap 總位元組在預算內；關閉動畫時只送 1 張；開關關閉時跟舊版完全一樣；包袱、紙帽切換後 Widget 有更新 |
| 「細線不消失」 | Robolectric 在 48／72／96 px 畫簡化版，數頭部、身體範圍的墨色像素，低於門檻就失敗（防止之後改壞） |
| CI 截圖 | `widget-*.png` 4 種版面 × V3；小尺寸對照（mdpi／xhdpi／xxxhdpi × 4 版面 × 8 天氣）；完整 vs 簡化並排 |
| 模擬器 | 現有 `w7-acceptance.sh` 加一段：在主畫面放 Widget（`appwidget grantbind` + 測試用 host Activity）截圖；不行的話列 Not tested |
| 人工 | 48px 簡化版的美術（認不認得出是墨丸）仍需人看，跟自動測試分開列 |

### 建議的步驟

1. **P5-1**：快取故事解析、Widget 更新的 bitmap 預算測試（先量現況）。
2. **P5-2**：`detail` 參數與簡化規則（純 Kotlin + JVM 測試），完整等級的 Golden 不變。
3. **P5-3**：`CalendarWidget` 接 V3（開關後面）、Reduced Motion、包袱／紙帽切換時更新。
4. **P5-4**：截圖、模擬器、驗收報告。

### P5-1 的實際結果（2026-10-26）

**故事資料快取**

- 呼叫鏈：`CalendarWidget.render` → 每種版面 `build` → `setStickman` → `StoryBook(context)` → `beats`（每個實例各自 lazy 解析）。Android 12 以上一次更新 4 種版面 = 解析 4 次（每個小工具）；App 日曆頁每次換頁、下一頁的快照也各自解析。
- 改成 `StoryBook.Assets`：故事 assets（APK 內附，執行期間不變）整個 process 解析一次、共用。頁數、名字、最近片段等狀態**沒有**快取，每次照樣從 prefs 讀。`StoryBook` 的 API 不變。
- `StoryCacheTest`：小工具 4 種版面 → 解析 1 次、第二次更新 0 次；開 App（含故事卡、日曆頁、下一頁快照）→ 1 次；跳頁後 `parts`／`page` 立刻是新的。

**Bitmap 基準（舊火柴人，Robolectric 量測，不是實機）**：`WidgetBitmapBaselineTest`，完整表格在 CI 截圖分支的 `p5/bitmap-baseline.md`。

| 密度 | Android 12+ 一次更新（4 版面 × 6 張） | 只送 1 張時 | Android 8–11（只送 TALL，6 張） |
|---|---|---|---|
| mdpi | 227 KB | 38 KB | 72 KB |
| xhdpi | 912 KB | 152 KB | 288 KB |
| xxhdpi／xxxhdpi（密度上限 3x） | **2053 KB** | 342 KB | 648 KB |

- 數字是 `RemoteViews.estimateMemoryUsage()`（系統檢查上限時用的），跟像素估算（寬 × 高 × 4 × 張數）完全一樣。
- 6 張輪播佔了 5/6；換成 D-04 的靜態一張，xxhdpi 從 2053 KB 降到約 342 KB（V3 尺寸一樣時）。
- xxhdpi 以上目前就超過 1 MB 的初始預算，但還遠低於系統上限（AOSP 規則：螢幕寬 × 高 × 6 bytes，1080×2400 約 15 MB；讀原始碼得到，沒在實機量）。
- Parcel 大小在 Robolectric 裡比估算多約 13 KB；實機上大 bitmap 可能走共享記憶體，這一欄不能當實際傳輸量。
- App 日曆頁的小人（xxhdpi、411dp）每張 181×241 px × 6 張 ≈ 1 MB，只在 App 裡，不經過小工具傳送。

**Reduced Motion／ViewFlipper（W7 M7）**

- 來源確認：App 日曆頁是 `CalendarWidget.build(TALL)` 用 `RemoteViews.apply` 套出來的，裡面的 `ViewFlipper`（`autoStart`）用 `postDelayed` 換圖，不看系統動畫設定。小工具和日曆頁共用同一個 `setStickman`（6 張圖 + `setFlipInterval`）。
- 修正（只動 App 日曆頁，不動小工具）：`MainActivity.updatePageMotion()` 關閉動畫時 `stopFlipping` 並停在第一張；打開時才輪播。Android 13 以上收到設定通知就切換；更舊的版本回到 App 時 `onResume` 會重建這一頁。舊火柴人和 V3 兩種模式的日曆頁都適用。
- 證據：`PageMotionTest`（兩種模式、關閉／打開、切換不卡住）；模擬器關閉動畫後整個 App 10 秒內重畫 **0 張**（修正前 38 張），新增為必要檢查。
- 還沒處理：故事卡的**舊火柴人**（`StickmanView`，V3 開關關閉時才看得到）不理會 Reduced Motion，關閉動畫時 10 秒約 290 張。這是舊模式的既有行為，P7 決定舊火柴人去留（D-15）時一起處理。桌面小工具的 `ViewFlipper` 在 P5-3 改成靜態一張（D-04）後自然解決。

**P5-2 開始前要注意**

- D-25 的 120 px 是「實際繪製高度」：同一個版面在 mdpi 是 48–64 px（簡化），在 xxhdpi 是 144–192 px（完整）；日曆頁約 241 px（完整）。截圖要涵蓋不同密度才看得出切換點。
- 小工具的 `ViewFlipper` 版面保留 6 個 ImageView；靜態時只設第一張、其他不設 bitmap，並停止輪播，不改版面檔。
- 簡化版的「至少 1.5 px」要用實際縮放比例換算（mdpi 的 48 px 角色 viewBox 縮放約 0.3），規則放在 renderer，不放在小工具。
- Android 12 以上 4 個版面共用同一份 bitmap 快取，量預算時要量組合後的 RemoteViews。

### P5-2 的實際結果（2026-10-26，完成）

**位置**：`mowan/Detail.kt`（畫法與門檻）、`Mowan.frame/render` 多一個 `detail` 參數（預設 `Detail.FULL`，跟原本逐筆一樣）。小工具還沒接（P5-3）。

**簡化規則**（只拿掉與加粗，不改座標、比例、姿勢）：

| | 保留 | 拿掉 |
|---|---|---|
| 角色 | 頭、一筆、眼睛（加粗到 1.5 px）、身體、四肢、腳尖紅墨、圍巾帶子與尾巴 | 腮紅、眉毛、汗滴、白氣、圍巾分岔；嘴巴在角色 < 96 px 時不畫 |
| 配件 | 紙帽帽身、包袱（布包、兩個布角、結）、雨傘（傘柄、傘面、拳頭）、雷雨左手握傘 | 帽子摺線、包袱背帶、皺褶、碎花、傘骨 |
| 環境（一個） | 晴、熱：太陽；陰：最大那朵雲；雷雨：左邊那朵烏雲；雨：4 條雨絲；雪：4 片雪；霧：腳邊那條霧帶；冷、一般：沒有 | 其他雲、雨、雪、霧帶，熱氣、閃電、地面影子、水窪、積雪 |

**筆畫下限**：在產生筆畫幾何的地方（`brush`／`ring` 的寬度函式、`limb`、眼睛半徑、Paint 描邊寬）取 `max(原本寬度, 下限)`，中心線不動。下限用角色實際像素高度換算成 viewBox 單位：主要墨線（頭、一筆、眼睛、身體、四肢、傘柄、圍巾）1.5 px；配件輪廓與環境（紙帽、包袱、傘面、拳頭、雲、雨、雪、太陽圈）1 px，不加粗到主要墨線。完整版的下限是 0，寬度不變（`fullDetailIsUnchanged`、所有 Golden）。

**D-25 門檻比較**（`DetailTest.pxRuleVersusDpRule`、`p5/densities.png`）：

| 角色視覺高度 | mdpi | hdpi | xhdpi | xxhdpi | 照 120 px | 照 72 dp |
|---|---|---|---|---|---|---|
| 48 dp（小工具 COMPACT） | 48 px | 72 px | 96 px | 144 px | 簡化／簡化／簡化／**完整** | 全部簡化 |
| 64 dp（小工具 TALL） | 64 px | 96 px | 128 px | 192 px | 簡化／簡化／**完整**／**完整** | 全部簡化 |
| 80 dp（App 日曆頁） | 80 px | 120 px | 160 px | 240 px | 簡化／完整／完整／完整 | 全部完整 |
| 128 dp（故事卡） | 128 px | 192 px | 256 px | 384 px | 全部完整 | 全部完整 |

- 照 px：同一個小工具在高密度手機上變回完整版（有腮紅、傘骨、很多雨絲），實際大小一樣，細節卻不同；App 日曆頁在 mdpi 會被簡化。
- **D-25 已確認改成照 dp：視覺高度 < 72 dp 用簡化版**（小工具全部簡化、日曆頁與故事卡全部完整，每種密度一樣）。筆畫下限、嘴巴是否畫照樣用 px。`DetailTest.defaultRuleIsSeventyTwoDp`（71.9／72 dp 邊界 × 7 種密度）、`inkFloorAndMouthStillUsePixels`。

**測試**：`DetailTest`（純 Kotlin 13 項）——門檻、完整版不變、部位保留／拿掉、一個環境元素、靜止姿勢與 Reduced Motion 相同、14 頁、紙帽包袱裝備狀態、主要墨線 ≥ 1.5 px（從實際的筆畫幾何量左右兩側距離）、配件輪廓 1–1.5 px、48 px 時兩腳尖相隔 ≥ 1 px。故意把下限改成 1.2 px，`mainLinesAreAtLeastOneAndAHalfPixels` 會失敗（確認測試有效）。

**對照圖**（`SimplifiedShotsTest`，CI 截圖分支 `p5/`）：`sizes.png`、`weathers-before-after.png`、`equipment.png`、`densities.png`、`actual-pixels.png`。

**保留待正式小工具截圖後再看**（2026-10-26 決定先不調整）：48 px 的雨天，紙帽在傘下只剩白色三角加 1 px 輪廓；48 px 的頭輪廓相對比較粗。

### P5-3 的實際結果（2026-10-26，完成）

**最小修改清單**（實際改的檔案）：

| 項目 | 做法 | 位置 |
|---|---|---|
| 共用 V3 開關（D-23、D-24） | `MowanSettings.storyCard` 打開時，小工具 4 種版面與 App 日曆頁都走 V3；關閉時完全走原本的 `setStickman` | `CalendarWidget.build` |
| 一張靜態代表姿勢（D-04、D-27） | `MowanBitmap.still`：`Mowan.frame(…, stillClock(天氣), still = true)`，筆觸固定第一張，不播撕頁動作 | `mowan/MowanBitmap.kt` |
| ViewFlipper | 版面檔不改；6 格放同一張圖（RemoteViews 只存一份）、`setDisplayedChild(0)`、輪播間隔一天。App 日曆頁的 V3 直接停止輪播 | `CalendarWidget.setMowan`、`MainActivity.updatePageMotion` |
| 72 dp 簡化（D-25） | `Detail.of(實際繪製高度 px, 密度)`；小工具 48–64 dp 高 → 簡化，日曆頁約 80 dp → 完整 | `MowanBitmap.still` |
| 狀態來源 | 部位：`StoryBook.parts`；紙帽、包袱：`MowanSettings`；天氣：`WeatherService.current` + `weatherOf`（只畫今天那頁，跟天氣文字一樣） | `CalendarWidget.setMowan` |
| 切換時更新 | 故事卡「卸下／背上包袱」後重建日曆頁並 `CalendarWidget.refreshAll`；除錯選單的紙帽、包袱、V3 開關原本就會更新 | `MainActivity.showBundleToggle` |
| 文字與版面 | 不動任何版面檔與文字欄位 | — |

**驗證**（`WidgetV3Test` 9 項、`PageMotionTest` +1）：

- 4 種版面都是 6 格同一張圖，而且跟用 App 狀態直接畫出來的一模一樣（像素比對）。
- 小工具用簡化版、不是完整版；App 日曆頁（60 dp 寬）用完整版。
- 紙帽、包袱、天氣、頁數改了，圖跟著改；不是今天那頁不畫天氣。
- 關閉 V3：6 張圖跟舊的 `StickmanRenderer` 逐張一樣。
- 所有 TextView 的文字與可見度，V3 開或關都一樣（今天、明天、2027-02-06 × 4 種版面）。
- Bitmap 預算：4 種版面合計 xxhdpi **342 KB**（舊火柴人 2053 KB），mdpi 38 KB；都在 1 MB 以內。
- 故事卡點「卸下包袱」後，桌面小工具（`ShadowAppWidgetManager`）與 App 日曆頁的圖都換了。
- V3 的日曆頁就算動畫打開也不輪播。

**截圖**：CI 截圖分支 `p5/widgets-v3.png`（舊火柴人／V3 並排）、`widgets-v3-weathers.png`、`widgets-v3-densities.png`、`app-page-v3.png`、`bitmap-v3.md`。

**注意**：App 日曆頁最上面那張如果是昨天那頁（今天還沒撕），日曆頁上的墨丸不畫天氣，故事卡則照樣演今天的天氣（故事卡規則不變）。

### P5-4 的實際結果（2026-10-26，完成）

- **ViewFlipper 真的靜止**：RemoteViews 不能呼叫 `stopFlipping`／`setAutoStart`，而且 GONE 不會讓 ViewFlipper 停。改成 4 個版面檔各加一個同位置、預設 GONE 的 `mowan_still` ImageView；V3 時它顯示靜態圖，ViewFlipper 隱藏、6 格清空、間隔一天。舊火柴人反過來，行為不變。上一版「6 格同一張圖」的做法拿掉。
- **Bitmap**：RemoteViews 裡只有 4 張 V3 圖，xxhdpi 342 KB（舊火柴人 2053 KB）；`bitmapBudget` 確認估算剛好等於這 4 張。
- **測試宿主**：debug APK 的 `WidgetHostActivity`（只在 debug）用系統 `AppWidgetHost` 掛 5 種大小，回報可見度、輪播狀態、裁切。
- **模擬器**（#30 全部通過）：宿主 5 種大小 × 3 種密度、穩定後 0 張重畫（舊火柴人 185 張）；**真正的 Pixel Launcher** 放上小工具後，包袱、紙帽、頁數、天氣、V3 開關、App 裡的包袱切換都反映到桌面。
- 詳細結果、Minor 與 Not tested 見 [P5 驗收報告](character-p5-acceptance.md)。

- **風險**：`ViewFlipper` 在不同 launcher 行為不一；低階機一次建 4 個版面的時間（目標 < 50 ms，用測試量）；簡化版的美術需要另外審。
- **複雜度**：中。

## P6 — 節日系統

> 狀態：P6-1、P6-2、P6-3 完成（P6-3 於 2026-10-26 結案，見 [P6-3 驗收報告](character-p6-3-acceptance.md)）；P6-4 整合計畫等待審核。詳細見 [P6 計畫](character-p6-plan.md)。

- **目標**：春節、聖誕、中秋的服裝、道具、主演出、待機，配合 8 種天氣、成長階段與裝備狀態。
- **階段**（每階段結束停下來審核）：
  - P6-1：節日日期判定、插槽與能力規則、一天一次的狀態機（純 Kotlin）。
  - P6-2：節日服裝與道具的靜態繪製（頭飾 A／B／C、耳朵 A／B 可切換）。
  - P6-3：主演出、待機、降級層級、Reduced Motion 代表姿勢。
  - P6-4：保存、天氣組合、App 故事卡與日曆頁、小工具靜態外觀、除錯選單。
  - P6-5：黃金數值、截圖、模擬器驗收、結案。
- **依賴**：P2、P3、P4、P5。
- **驗收**（總）：
  - 原型 19 個回歸測試移植到 JVM 並通過（T01–T14、T16、T18 在 P6-1；T15、T17、T19 在 P6-2／P6-3）。
  - 春雨「雨傘 + 紅包」正確。
  - 演完後，天氣更新、重開 App 都不重播。
  - 人工美術檢查另外列。
- **風險**：
  - 未定案美術多（D-01、D-02、D-03、D-29、D-30）→ 經由 `ArtConfig`，不寫死。
  - 原型只定了晴、雨、陰跟節日的搭配；其餘 5 種天氣要新定規則（D-31、D-32）。
  - 節日故事 beat 不存在 → 只做角色演出，不新增故事文字（D-11）。
- **複雜度**：高。

## P7 — 整合、效能、QA

- **目標**：
  - 預設改用 V3。
  - 移除舊 stickman（或保留為 fallback，待決定）。
  - 效能與相容性驗收。
- **工作**：
  - 多密度截圖。
  - 低階機量測 frame time。
  - 長時間放著的耗電檢查。
  - Reduced Motion。
  - 撕頁連續操作。
  - 7 天補頁情境。
  - **站姿筆觸重畫兩次**（P6-4a 補查發現，2026-10-27 記錄為 P7 待辦）：`MowanView` 的筆觸間隔捨去成整數毫秒，落在影格邊界前一點點，接著補一次 1 ms 重畫；有效筆觸每秒約 6 張，但重畫約 12 次。初步方向：間隔改成無條件進位。實作時要驗證非節日與節日站姿、長時間的影格節奏、Reduced Motion、實機效能。P6 不改這個排程。
- **驗收**：
  - idle 時 CPU 喚醒頻率約 6 次 / 秒，沒有每幀配置新物件。
  - 所有自動測試通過。
  - 人工檢查清單完成。
- **複雜度**：中。

---

## 建議的第一步

**確認後先做 P0 + P1**：把原型原始碼放進 repo、匯出黃金數值，然後用純 Kotlin 移植 V3 幾何（筆觸、頭部輪廓、一筆、亂數），以 JVM 測試證明數值一致。這一步完全不動既有畫面，風險最低，也是後面所有階段的基礎。
