# 墨丸 V3 — 實作計畫（Phase 2B）

> 狀態：**Proposed，等待確認**。所有階段都還沒開始。
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

## P4 — 天氣對應 V3

- **目標**：把 8 種 Mood 對應到 V3 場景與天氣環境層。
- **檔案**：
  - `…/mowan/Scene.kt`
  - `…/weather/Weather.kt`：只加對應，不改抓資料的邏輯
- **技術**：
  - SUNNY → 伸懶腰；RAIN / THUNDER → 撐傘（右手）。
  - 天氣環境一直存在。
  - 沒有右手（第 4 頁前）→ 降級，不撐傘。
  - **其他 Mood（HOT、CLOUDY、FOG、COLD、SNOW）的 V3 樣子未定（D-10）** → 先用 normal 姿勢 + 環境層。
- **依賴**：P2、P3。
- **驗收**：
  - 8 種 Mood × 14 頁的對應表測試。
  - 天氣資料更新不會觸發新的演出。
- **風險**：天氣原型只有晴、雨兩種完整設計。
- **複雜度**：低–中。

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
