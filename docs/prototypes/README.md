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
| `src/engine.js`、`src/core.js` | 從 `mowan-hat-umbrella.html` 拆出來的原型程式，給匯出工具用 |
| `tools/export-golden.js` | 匯出黃金數值，給 Android 的 JVM 測試比對 |

## 重新產生黃金數值

```sh
node docs/prototypes/tools/export-golden.js > app/src/test/resources/mowan/golden.json
```

`MowanGoldenTest` 會拿 Kotlin 算出來的每一條筆畫跟這份檔案比對（誤差 0.011 以內）。
只有原型程式改了才需要重新產生；Kotlin 這邊不能為了讓測試通過去改黃金數值。
