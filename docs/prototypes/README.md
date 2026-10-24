# 墨丸 V3 原型（參考用）

這些是設計階段做的 HTML 原型，**只當參考**。正式 App 的角色系統是 Kotlin + Canvas（`app/src/main/java/com/patty60723/tearoff/mowan/`），不會用 HTML、JS 或 WebView。

| 檔案 | 內容 |
|---|---|
| `mowan-lineboil.html` | V3 角色本體、毛筆筆觸、6 FPS line boil（一般、晴天、雨天） |
| `mowan-growth.html` | 14 頁成長的外觀草稿 |
| `mowan-decision.html` | Phase 1.5 設計決策預覽（紙帽、紅墨腳尖方案） |
| `mowan-festival.html` | Phase 2A.1 節日 × 天氣 × 成長階段原型（狀態機、能力系統、回歸測試） |
| `mowan-art-review.html` | Phase 2A.2 演出關鍵姿勢、道具精修 |
| `mowan-hat-umbrella.html` | Phase 2A.2 頭飾 A/B/C、雨傘原尺寸 vs −5%（最新的原型程式） |
| `mowan-weather-draft.html` | 給設計師的草案：熱、陰、霧、雷雨、冷、雪與第 14 頁包袱（全部未批准） |
| `mowan-weather-revision.html` | 草案的小幅美術修正：雷雨偶發驚嚇、包袱 B1 重畫、關閉動畫時的代表姿勢（全部未批准） |
| `src/engine.js`、`src/core.js` | 從 `mowan-hat-umbrella.html` 拆出來的原型程式，給匯出工具用 |
| `src/weather.js` | 天氣與包袱的定稿參考程式（第二輪審核版，加上動作一致性審核的兩個修正） |
| `audit/` | Phase 2B 動作一致性審核的渲染截圖 |
| `tools/load-weather.js` | 匯出、檢查工具共用：載入 engine.js + core.js + weather.js |
| `tools/export-golden.js` | 匯出黃金數值，給 Android 的 JVM 測試比對 |

## 黃金數值

| 檔案 | 產生方式 | 內容 | 容差 |
|---|---|---|---|
| `app/src/test/resources/mowan/golden.json` | `node docs/prototypes/tools/export-golden.js` | 一般、晴、雨在第 1–14 頁的整張畫面（每一條筆畫），來源 `core.js` 的 `Renderer` | 0.011（原型輸出小數兩位） |
| `app/src/test/resources/mowan/golden-weather.json` | `node docs/prototypes/tools/export-golden-weather.js` | 熱、陰、霧、雷雨、冷、雪 B 的姿勢參數，來源 `weather.js` | 1e-6 |
| `app/src/test/resources/mowan/golden-weather-render.json` | `node docs/prototypes/tools/export-golden-weather-render.js` | 上面 6 種天氣的整張畫面（每一條筆畫），第 1、3、4、5、8、14 頁 × 2–3 個時間點，雪另有靜止畫面，共 96 張；來源 `weather.js` 的 `drawFrame` | 座標 0.011、不透明度 0.0006 |

`golden-weather.json` 的內容：

- 場景與成長頁：6 種天氣 × 第 1、2、3、4、5、6、8、10、14 頁。
- 時間點：
  - 熱：0–3700 ms 共 13 個，含抬手、放手的中間格。
  - 陰：0–7000 ms 共 5 個。
  - 霧：0–5600 ms 共 5 個。
  - 雷雨：0、2100、15000 ms，以及打雷後 0–800 ms。
  - 冷：0–3100 ms 共 9 個。
  - 雪：0–4500 ms 共 6 個。
- 每一組的姿勢參數：
  - 骨架控制點：身體、雙手、雙腳、圍巾尾巴。
  - 頭：角度、位移（headDx、headDy）、視線。
  - 表情、一筆的形狀。
  - 雨傘：握點與角度。
  - 動作量：汗滴、圍巾拉高、左手握傘柄、白氣、閃電。
- 已上線的晴、雨各 7 個時間點，來源 `engine.js` 的 `pose()`。
- 座標：viewBox `0 −20 120 160`，y 朝下。頭先以 (60.5, 66) 為中心轉 headAngle 度，再位移 (headDx, headDy)。姿勢還沒套用「沒有腳時落到地面」。
- 雷雨的 `sinceStrike` 是打雷後經過的毫秒數。原型的雷打在 9000 ms（90 的倍數），所以原型的抖動相位跟「打雷後的時間」完全相同。

`check-weather-consistency.js`：確認 `weather.js` 跟已上線的晴、雨畫出一樣的角色（結果見整合規格 §5.7）。

## 重新產生黃金數值

```sh
node docs/prototypes/tools/export-golden.js > app/src/test/resources/mowan/golden.json
```

`MowanGoldenTest` 會拿 Kotlin 算出來的每一條筆畫跟這份檔案比對（誤差 0.011 以內）。
只有原型程式改了才需要重新產生；Kotlin 這邊不能為了讓測試通過去改黃金數值。
