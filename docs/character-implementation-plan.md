# 墨丸 V3 — 實作計畫（Phase 2B）

> 狀態：階段順序已確認（2026-10-24）。P0–P3 已完成。P4：W1–W5 與 D-22 已完成（新天氣只在除錯選單看得到；包袱在故事卡用 V3 時第 14 頁會出現）；W6 之後還沒開始。
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
| **W6 接上故事卡** | `weatherOf` 對應 8 種 Mood；故事卡平常就顯示當天的天氣環境；天氣更新不重啟同一種天氣的姿勢 | W3、W5 | Robolectric：天氣更新時姿勢時鐘不重來；CI 截圖：8 種天氣 × 第 1、4、5、14 頁 |
| **W7 視覺驗收** | 在 CI 截圖和實機上，依整合規格 §5.1 的 Pending 項目逐一看過 | W6 | 每項標成 Visually Verified，或列出新的問題 |

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

不在 P4 做：

- 節日道具和天氣動作的組合（P6）。
- 48px（P5）。
- 包袱能不能卸下、最終布色（待決策）。

## P5 — Widget V3

- **目標**：Widget 改用 V3 renderer，並處理既有的 bitmap 與 JSON 技術債。
- **檔案**：`CalendarWidget.kt`、`res/layout/widget_*.xml`
- **技術**：
  - 故事資料解析一次就快取。
  - 依 D-04 決定送靜態圖或 5 張 boil 影格。
  - 計算 bitmap 總位元組，加上限保護。
  - 48px 規則做成設定（D-07）。
- **依賴**：P1、P3。
- **驗收**：
  - 4 種版面截圖。
  - bitmap 總大小測試低於設定的預算。
  - 低階機實測更新不失敗。
- **風險**：
  - `ViewFlipper` 在不同 launcher 的行為不一致。
  - 48px 下 V3 筆觸可能太細 → 依 D-07。
- **複雜度**：中。

## P6 — 節日系統

- **目標**：春節、聖誕、中秋的服裝、道具、主演出、idle，配合天氣與成長階段。
- **檔案（新）**：
  - `…/mowan/FestivalTheme.kt`：判斷主題，不依賴畫面標籤
  - `…/mowan/Props.kt`：財神帽、斗方春聯、紅包、聖誕帽、襪、禮物、玉兔耳朵、月亮、月餅
  - `…/mowan/Performances.kt`：作揖、遞紅包、掛襪、舉月餅望月，含降級等級
  - `…/mowan/Controller.kt`：狀態機
  - `…/mowan/ArtConfig.kt`：帽子 A/B/C、耳朵、雨傘尺寸
- **技術**：
  - 照原型移植 resolve / slots / ACTIONS。
  - 主演出一天一次，記在 `lastMainDate`。
  - 節日文案層不動，跟演出層分開（Confirmed）。
- **依賴**：P2、P3、P4。
- **驗收**：
  - 移植原型 19 個回歸測試到 JVM，全部通過。
  - 春雨「雨傘 + 紅包」可以正確顯示。
  - 演完後，天氣更新、重開 App 都不重播。
  - 人工美術檢查另外列。
- **風險**：
  - 未定案美術很多（D-01、D-02、D-03）→ 全部經由 `ArtConfig`，不寫死。
  - 節日故事 beat 不存在 → 本階段只做角色演出，不新增故事文字（D-11）。
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
- **驗收**：
  - idle 時 CPU 喚醒頻率約 6 次 / 秒，沒有每幀配置新物件。
  - 所有自動測試通過。
  - 人工檢查清單完成。
- **複雜度**：中。

---

## 建議的第一步

**確認後先做 P0 + P1**：把原型原始碼放進 repo、匯出黃金數值，然後用純 Kotlin 移植 V3 幾何（筆觸、頭部輪廓、一筆、亂數），以 JVM 測試證明數值一致。這一步完全不動既有畫面，風險最低，也是後面所有階段的基礎。
