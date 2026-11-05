# 墨丸 V3 — P7-7 正式實作紀錄（已核准的四項）

> 分支：`feature/p7-7`（基準 `main` `6ae18f3`）。計畫：`feature/p7-7-prep` 的 `docs/character-p7-7-implementation-plan.md`。
> 狀態：**程式完成、等待審核**。**Locked Golden 沒有更新**（英文月份的 17 張等人工核准）。沒有合併 main，沒有開始 P7-4b。
> TALL 最小尺寸的大字體問題、WIDE 日期疊在山丘上的對比、春節墨丸的間距：**不在這個分支**，見準備文件 §10（獨立追蹤）。

## 1. Commit

| # | Commit | 內容 | golden |
|---|---|---|---|
| 1 | `8df69fc` | 測試工具 `GlyphInk`（字形實際有墨的像素）＋誤報／漏報回歸測試 `GlyphInkTest`；`WideLayoutTest`、`HomeShotsTest` 改用它 | 0 |
| 2 | `fc76e78` | 8 月 `deep` → `#84693D`；`PaletteContrastTest`（12 個月 deep 對紙色 ≥ 4.5） | 0 |
| 3 | `8709a07` | 導覽列候選 A：「回到最上面那頁」自己一列，最上面那頁時 GONE；`NavRowTest` | 0 |
| 4 | `37e94b4` | 日曆頁候選 C：`TearPageView.extraHeight`、`MainActivity.reserveForSun`、`widget_tall.xml` 底部保留加 id；`PageReserveTest` | 0 |
| 5 | `80b89f0` | 候選 C 測試的修正（那天沒有「忌」時，CI #198 的 `NoSuchElementException`） | 0 |
| 6 | `b53bf09` | 模擬器驗收第 8b 段（字體 2.0）；`view_bounds.py` 的 top 模式 | — |
| 7 | `6743b80` | golden 測試不一致時輸出「基準／這次／差異」一覽（`changed.png`）；不改基準與判定 | 0 |
| 8 | `a8494e3` | 英文月份 `#746D64`（`ink_month_en`）：WIDE、TALL（App 日曆頁也用 TALL 版面），COMPACT／MEDIUM 的隱藏欄位；`WideLayoutTest` 英文月份 4.5 門檻；`EnglishMonthContrastTest` | **17（預期，未核准）** |
| 9 | `84ed719` | 模擬器第 8b 段頁數讀取的修正（emulator #101 的「頁數 ? → ?」） | — |

## 2. CI（Robolectric）

| CI | head | 結果 | 說明 |
|---|---|---|---|
| #198 | `37e94b4` | **失敗** | `PageReserveTest.flipAndBackToTop`：測試自己的錯（那天沒有「忌」）。54 張 golden **全部一致** |
| #199 | `6743b80` | **成功** | 第 1～7 個 commit：全部測試通過，54 張 golden 全部一致 |
| #200 | `a8494e3` | **失敗（預期）** | 353 個測試，**只有** `V3PngGoldenTest` 失敗：剛好是預期的 17 張（下表）。其他 352 個通過 |
| #201 | `84ed719` | **失敗（預期）** | 同 #200：只有 `V3PngGoldenTest`（同樣 17 張）。只改了模擬器腳本 |

**#200 不是「測試全部通過」**：鎖定的 golden 有 17 張不同，因為英文月份的顏色改了、基準還沒核准。

### 17 張 golden 的差異（[`changed.png`](p7-7-impl/changed.png)、[差異圖](p7-7-impl/golden-diff/)、[報告](p7-7-impl/golden-report.md)）

| 版面 | 張數 | 不同的像素 |
|---|---|---|
| App 日曆頁 | 4（一般晴、一般雨、第 1 頁、卸下包袱） | 各 1113 |
| TALL | 4（同上） | 各 1113 |
| WIDE | 9（全部條件） | 1113～1357 |

- 不同的像素只在英文月份的字上（差異圖）。
- 這次畫出來的 17 張跟準備階段的候選（`P77GoldenCandidatesTest`，截圖分支 `screenshots/feature/p7-7-prep`）**逐像素相同**（17／17，每個通道容許 2）。
- 其他 37 張一致（故事卡、COMPACT、MEDIUM，以及有節日時 TALL／日曆頁隱藏英文月份的那幾張）。

## 3. 各項的自動測試結果

### 導覽列候選 A（[`nav.md`](p7-7-impl/nav.md)、[圖](p7-7-impl/nav.png)）

- 411dp／320dp × 100%／200%：
  - 看最上面那頁時 GONE，不佔高度（導覽列下緣＝城市入口上緣）。
  - 看昨天那頁時，三個按鈕都是一行、字形完整、高 ≥ 48dp（200% 時 59dp）。
  - 點了會回到最上面（21 日），按鈕再次 GONE。
- TalkBack 焦點順序（依版面）：前一天 → 後一天 → 回到最上面那頁。**實機朗讀 Not tested**。

### 日曆頁候選 C（[`1-page.md`](p7-7-impl/1-page.md)、[`2-tear.md`](p7-7-impl/2-tear.md)、[`3-flip.md`](p7-7-impl/3-flip.md)、[圖](p7-7-impl/1-page.png)、[撕頁](p7-7-impl/2-tear.png)）

- 4 種設定 × 一般日／春節：
  - 天氣三行不碰「忌」、農曆、宜、節日、墨丸（實際有墨的像素）。
  - 文字都完整。
  - 日期字形跟實作前一樣大。
  - 字體 100% 時頁面不加高、畫面逐像素相同。
- 200% 時：
  - 頁面加高：411dp 是 +47dp（515 → 561dp）；320dp 是 +42dp（383 → 424dp）。
  - 天氣與「忌」的間距：16dp（實作前 −30／−26dp，是重疊的）。
- 200% 撕頁：
  - 撕的時候，下一頁的截圖跟撕之前的頁面同大小。
  - 撕完頁數 +1，故事卡更新，頁面高度換成新那頁的。
  - 撕之前是前一天那頁（+10／+5dp），撕完是今天那頁（+47／+42dp）：**撕完那一刻頁面變高 36～37dp**（計畫 §2 的風險，請看圖判斷）。
- 翻頁：
  - 200% 時今天 561dp、前一天 525dp（411dp），**翻頁時頁面高度會變**。
  - 翻回來、按「回到最上面那頁」，高度都正確。
- 捲到最底點故事卡墨丸不位移：`HomeShotsTest` 照舊通過（4 種設定，頁面已經是加高後的）。

### 英文月份（[`month-en.md`](p7-7-impl/month-en.md)）

- WIDE：4 種大小 × 15 天，英文月份最差像素 ≥ 4.5（`WideLayoutTest`，現在是門檻）。
- TALL、App 日曆頁：12 個月都是 **4.82**（`EnglishMonthContrastTest`）。

### 8 月 `deep`

- 12 個月 deep 對紙色都 ≥ 4.5，8 月 4.87（`PaletteContrastTest`）。
- **WIDE 250×110 的 8 月日期疊在山丘上只有 4.33，沒有解決**。這一項獨立追蹤（準備文件 §10.5），這個分支不宣稱解決。

### 量法（`GlyphInkTest`，回歸）

- 重現了以下 6 種情況。每一項都同時證明舊量法真的會錯，新量法分得出來：
  - 字距誤報（`getTextBounds`）。
  - 日期字形被切卻判通過的漏報。
  - 外框切字。
  - 「…」。
  - 方框重疊但字形沒碰到。
  - 墨丸透明留白。

## 4. golden 以外的影響

- `widget_tall.xml` 的底部保留加了 `android:id`：小工具的畫面不變（54 張一致，#199）。
- `WideLayoutTest` 的對比改看字實際畫到的像素：
  - 以前看行範圍，會量到別行的字（8 月日期的「1.05」）。
  - 改了以後，原有的項目照樣全部 ≥ 4.5。
- WIDE 的日期數字在 `WideLayoutTest` 照原本的規則用大字的 3:1 只記錄（全部達標）。用 4.5 的掃描在準備文件 §10.5，結果不合格。

## 5. 模擬器（Android Emulator，API 34，pixel_6）

第 8b 段，字體 2.0，密度 420。

| 執行 | head | 結果 |
|---|---|---|
| emulator #101 | `a8494e3` | **失敗**：PASS 61、FAIL 1。FAIL 是「手指撕頁成功」：腳本讀頁數的錯（「? → ?」），頁面高 1371 → 1465px 看得出撕了。其他第 8b 段全部 PASS |
| emulator #102 | `84ed719` | **成功**：PASS 62、FAIL 0、WARN 0。第 8b 段 6 項全部 PASS（含「手指撕頁成功：頁數 14 → 15、頁面高 1371 → 1465px」） |

截圖與報告在 emulator #102 的 Actions artifact `emulator-acceptance`（手動觸發的執行不推截圖分支）。人工還沒看過這些截圖。

#101、#102 第 8b 段的 PASS（模擬器實測）：
- 最上面那頁不顯示「回到最上面那頁」。
- 看昨天時，「回到最上面那頁」自己一列、高 149px（≥ 48dp）。
- 翻到前一天再回來，頁面高度一樣（1465px）。
- 按「回到最上面那頁」回到今天，按鈕不再顯示，頁面高度一樣。
- 捲到最底點墨丸，墨丸與包袱入口不位移。

## 6. Blocker、Minor、Unknown、Not tested

| 類別 | 內容 |
|---|---|
| **Blocker** | 17 張 golden 等人工核准（核准前 CI 會失敗） |
| **Minor** | 大字體時翻頁、撕完的那一刻頁面高度會變 36～37dp（候選 C 的已知取捨） |
| **Unknown** | 實機上撕頁動畫中頁面變高的觀感 |
| **Not tested** | 實機、TalkBack 朗讀、人工看圖（含模擬器截圖）；其他 launcher；模擬器只測了字體 2.0、密度 420 |
