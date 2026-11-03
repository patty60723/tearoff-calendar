# Branch Audit（2026-11-03，P7-3 合併時）

> 基準：`main` `f48443c`（PR #3 合併前）。資料來源：`git ls-remote`、逐一 fetch 後用 `git merge-base --is-ancestor`、`git rev-list --count main..<branch>`、`git diff <branch> main`（偵測 squash 合併）、檔案數與大小、`docs/` 裡的引用次數。
> 本輪只刪除：`feature/p7-3-consistency`、`feature/p7-4a-anr-diagnosis`（PR #3 合併、main CI 與 golden 確認之後）。其他分支只列出建議，**不刪除、不合併**；驗收素材分支不併進 main。

## 1. 程式分支

| 分支 | 最新 SHA | 已包含於 main | main 沒有的 commit | 獨有驗收證據 | 建議 | 刪除風險／前置驗證 |
|---|---|---|---|---|---|---|
| `main` | `f48443c` | — | — | — | 保留 | — |
| `feature/p7-3-consistency` | `69a68e9` | PR #3 合併後是 | 15（含 P7-4a 的 7 個） | 無（證據在 `screenshots/feature/p7-3-consistency*`） | **本輪刪除**（PR #3 合併後） | 前置：merge commit 在 main、main CI 綠、golden 54 張一致、`git merge-base --is-ancestor` 確認 |
| `feature/p7-4a-anr-diagnosis` | `b332d95` | PR #3 合併後是（P7-3 以 `--no-ff` 合併了它） | 7 | 無（證據在 `screenshots/feature/p7-4a-anr-diagnosis*`） | **本輪刪除**（PR #3 合併後） | 同上 |
| `docs/p7-next-plan` | `23b8cf0` | PR #3 合併後是（P7-4a 從它分出） | 1 | 無 | 可刪除（下一輪） | 前置：確認 `23b8cf0` 是 main 的祖先 |
| `feature/mowan-weather-poses` | `56a6f3a` | **是**（PR #1） | 0 | 無（證據在 `screenshots/feature/mowan-weather-poses*`） | 可刪除（下一輪；PR #1 時你指示先保留） | 文件與 `emulator.yml` 的觸發條件提到這個名字（刪了不會壞，只是不再觸發） |
| `redesign` | `251204d` | **是** | 0 | 無 | 可刪除 | 低 |
| `claude/portfolio-readme` | `576d88a` | 內容是（PR #2 **squash** 合併：commit 不是 main 的祖先，但 `git diff` 與 main 完全相同） | 1（squash 前的原始 commit） | 無 | 可刪除 | 低；只有 `--is-ancestor` 會誤判成「沒合併」，要用 tree 比對 |

## 2. 驗收素材分支（`acceptance/*`，獨立歷史，與 main 沒有共同祖先）

| 分支 | 最新 SHA | 內容 | 大小 | `docs/` 引用 | 建議 |
|---|---|---|---|---|---|
| `acceptance/mowan-w7` | `914d40a` | W7 驗收素材（Robolectric #47、模擬器 #8） | 90 檔、16.0 MB | 3 | 待歸檔 |
| `acceptance/mowan-p5-2` | `c5797ed` | P5-2 小尺寸簡化對照圖 | 6 檔、0.6 MB | 0 | 待歸檔 |
| `acceptance/mowan-p5-3` | `bc91d42` | P5-3 小工具 V3 截圖 | 7 檔、1.1 MB | 0 | 待歸檔 |
| `acceptance/mowan-p5-4` | `2e14f81` | P5-4 驗收素材 | 34 檔、12.0 MB | 1 | 待歸檔 |
| `acceptance/mowan-p6-2` | `7c82f29` | P6-2 節日靜態美術對照圖 | 10 檔、1.2 MB | 1 | 待歸檔 |
| `acceptance/mowan-p6-3` | `f8278f2` | P6-3 修正前後對照圖（4 個 commit） | 35 檔、9.6 MB | 1 | 待歸檔 |
| `acceptance/mowan-p6-4b` | `8f19fca` | P6-4b 審核素材 | 72 檔、16.7 MB | 2 | 待歸檔 |
| `acceptance/mowan-p6-4c` | `819eeca` | P6-4c 小工具節日候選圖 | 9 檔、2.6 MB | 2 | 待歸檔 |
| `acceptance/mowan-p6-4c-final` | `aa07a17` | P6-4c 最終候選截圖 | 9 檔、2.7 MB | 1 | 待歸檔 |
| `acceptance/mowan-p6-4c-widget` | `9a8a59d` | P6-4c 靜止驗收補強（模擬器 #56） | 33 檔、17.4 MB | 1 | 待歸檔 |

這些是**唯一保存**的人工審核素材（核准時看的那一版圖），CI 不會重產。刪除前必須先歸檔（§4），並把 `docs/` 裡的連結改到歸檔位置。

## 3. CI 結果分支（`screenshots/*`，每次 CI 強制覆蓋，只留最新一次）

| 分支 | 最新 | 內容 | 大小 | 建議 |
|---|---|---|---|---|
| `screenshots/main` | `f48443c` 那次 CI | **只有 1 個檔案**：main CI #172 因 Maven Central 429 失敗，報告不完整（見 §5） | 0.0 MB | 保留（main CI 的輸出；下一次 main CI 會覆蓋） |
| `screenshots/feature/p7-3-consistency` | `f53e459` | 完整 CI 截圖、golden 報告、P7-3 狀態摘要 | 42.2 MB | 待歸檔（重要摘要已複製到 `docs/p7-3/`） |
| `screenshots/feature/p7-3-consistency-emulator` | `0cbd47f`（模擬器 #100） | **#100 自然發生 launcher ANR 的截圖與診斷快照** | 46.3 MB | **保留／歸檔**（P7-3 報告 §5.1 的證據；代表截圖已在 `docs/p7-3/natural-anr-100.png`） |
| `screenshots/feature/p7-3-consistency-diag` | `a8888b3`（診斷 #6） | 診斷原始資料 | 15.1 MB | 待歸檔 |
| `screenshots/feature/p7-4a-anr-diagnosis` | `b332d95` | 完整 CI 截圖 | 42.1 MB | 可刪除（跟 P7-3 那份重複；golden 結果相同） |
| `screenshots/feature/p7-4a-anr-diagnosis-diag` | `a326a05`（診斷 #4） | **P7-4a 報告引用的診斷原始資料** | 14.9 MB | **保留／歸檔** |
| `screenshots/feature/mowan-weather-poses` | `56a6f3a` | P7-2 最後的 CI 截圖 | 42.1 MB | 待歸檔（P7-2 報告的代表圖已在 `docs/p7-home-final/`） |
| `screenshots/feature/mowan-weather-poses-emulator` | `5aedbd7`（模擬器 #93） | P7-2 最後的模擬器結果 | 51.2 MB | 待歸檔 |
| `screenshots/docs/p7-next-plan` | `23b8cf0` | 文件分支觸發的一般 CI 截圖 | 42.1 MB | 可刪除（沒有獨有內容） |
| `screenshots/claude/portfolio-readme` | `576d88a` | README 分支觸發的一般 CI 截圖 | 42.1 MB | 可刪除 |
| `screenshots/redesign` | `251204d` | 早期 redesign 截圖 | 1.2 MB | 待歸檔（很小，也可以直接保留） |

注意：刪掉功能分支**不會**刪掉對應的 `screenshots/*` 分支（名字不同），本輪也不刪它們。

## 4. 驗收證據保留策略（建議）

| 類型 | 放哪裡 | 期限 |
|---|---|---|
| 正式 golden（54 張 PNG、幾何 JSON）與必要測試程式 | `main`（`app/src/test/resources/`、測試） | 永久 |
| 精簡驗收報告、少量代表截圖（每階段幾張） | `main` 的 `docs/`（例如 `docs/p7-3/`、`docs/p7-4a/`、`docs/p7-home-final/`） | 永久 |
| 完整 CI 截圖、log、影片、診斷原始資料 | `screenshots/*` 分支（只留最新）＋ Actions artifact | artifact 90 天；`screenshots/*` 在對應功能結案後 90 天內歸檔或刪除 |
| 里程碑的人工審核素材（`acceptance/*`、結案時的 `screenshots/*`） | GitHub Release 的 assets（例如 tag `evidence/p6`、`evidence/p7-3`，每個 zip 一個） | 永久；歸檔後再刪分支、改文件連結 |

風險：

- **人工審核素材只有一份**（`acceptance/*`）：先歸檔、驗證 zip 能下載且內容完整，再刪分支。
- **`screenshots/*` 會被下一次 CI 覆蓋**：結案時就該把要留的東西複製到 `docs/` 或 Release；P7-3 已複製摘要與 #100 截圖。
- **Actions artifact 90 天後過期**（#68／#78／#79 的 artifact 在 2027-01-28 過期；這個環境下載不了）：要留的話請在期限前手動下載歸檔。
- **文件連結**：`docs/` 有 9 個檔案、32 處引用 `acceptance/*`；歸檔後要一起改。
- 分支名稱也出現在 workflow 的觸發條件（`emulator.yml`、`emulator-diagnose.yml`）：刪分支不會讓 workflow 壞，只是不再自動觸發；下一個階段開新分支時要更新觸發條件（診斷 workflow 合併到 main 後也可以手動觸發）。

## 5. 合併前發現：main CI #172 是紅的（基礎設施）

`f48443c`（PR #2，只改 README）的 main CI #172：330 個測試裡 141 個失敗，全部是下載 Robolectric 的 `android-all-instrumented` 時 Maven Central 回 **HTTP 429**（流量限制），測試本身沒有跑。P7-3 分支（包含同一個 commit）的 CI 都通過。處理：重跑 #172 一次確認（結果見 P7-3 合併紀錄）。
