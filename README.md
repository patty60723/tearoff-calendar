# 墨丸 V3 — P6-3 節日演出素材

- 程式 commit：`4a1aa20`（P6-3 主要在 `c9b0fcf`）
- Robolectric 繪圖（Android 原生繪圖），不是實機錄影；GIF 15 FPS，從演出前 0.4 秒到演出後 0.4 秒

## GIF（`gif/`）
| 檔案 | 內容 |
|---|---|
| `p6-spring-main.gif` | 作揖遞紅包（晴）、撐傘拜年（雨、雷雨） |
| `p6-spring-idle.gif` | 看看紅包（晴、雨天左手） |
| `p6-xmas-main.gif` | 掛襪子、看禮物（晴、雪、雨天左手） |
| `p6-xmas-idle.gif` | 偷看禮物 |
| `p6-moon-main.gif` | 舉月餅望月（晴）、看月餅（雨、雪，D-37） |
| `p6-moon-idle.gif` | 抬頭望月（晴）、看月餅（雷雨） |
| `p6-{spring,xmas,moon}-growth.gif` | 成長階段降級：第 1 頁線條、2 頭部、4 單手、5 雙手沒腳、14 完整 |
| `p6-overlap.gif` | 撐傘與包袱的動態對照：春雨、中秋、聖誕 × 背包袱／卸下 |

## 圖片
- `p6/9-reduced-motion.png`：關閉動畫的代表姿勢（第 2、4、5、14 頁，最右是沒有演出的站姿）
- `mooncake-before-after.png`：背包袱時的月餅，左邊修正前（P6-2）、右邊修正後
- `gift-48px-before-after.png`：聖誕禮物 48／72／96 px，上排修正前（P6-2）、下排修正後（紅緞帶與小結）
- `p6/1-…8-*.png`：P6-2 對照圖重新產生（預設改成確認的頭飾 C、兔耳 B）
- `p6/10-mooncake-fix.png`：背包袱的月餅、紅包（第 14 頁）

## 動態審核補件：關鍵影格（`keyframes/`，commit `1431fb5`，不改動作）
每格上排全身、下排上半身放大，左上角是時間（ms）；筆觸固定第一張，免得筆觸抖動被看成動作。
| 檔案 | 內容 |
|---|---|
| `1-spring-bow.png` | 春節作揖與遞紅包：開始、抬手看紅包、雙手到胸前、低頭最低、停留、遞出、遞出停住、放回 |
| `2-spring-bow-rain.png` | 雨天撐傘拜年：同樣時間點 |
| `3-xmas-sock.png` | 聖誕掛襪子：開始（襪子歪）、抬頭、手抬起、碰到襪子、扶正、確認、手放下、看見禮物、結束 |
| `4-moon-toast.png` | 中秋晴天：開始、捧到腹前、捧著、舉起中、舉高、停留望月、停留結束、放下中、放回、結束 |
| `5a-moon-cake-rain.png`、`5b-moon-cake-snow.png` | 雨、雪看月餅：開始、低頭、捧起、看著、微笑、放回、抬頭、結束 |
| `6-moon-bundle-pass.png` | 背包袱時月餅舉起（1000–2300 ms）與放下（3300–4400 ms），每 100 ms 一格 |
| `7a-idle-spring.png`、`7b-idle-xmas.png`、`7c-idle-moon.png` | 三種節日待機 |
