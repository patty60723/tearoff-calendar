# Branch Audit — 合併後的目前狀態（2026-11-03）

> 這份是 **PR #3 合併後** 的狀態；合併前的快照保留在 [branch-audit-2026-11-03.md](branch-audit-2026-11-03.md)（不改）。

## 1. 合併後的實際 SHA 與驗證

| 項目 | SHA | 驗證 |
|---|---|---|
| `main`（PR #3 merge commit） | `6ae18f3` | 兩個 parent：`f48443c`（合併前的 main）、`f75502c`（P7-3） |
| `feature/p7-3-consistency` | `f75502c` | PR #3 合併時的 head＝`f75502c`（GitHub PR 紀錄）；`git merge-base --is-ancestor f75502c main` 成立；與 main 內容無差異 |
| `feature/p7-4a-anr-diagnosis` | `b332d95` | 7 個 commit 都是 main 的祖先（P7-3 以 `--no-ff` 合併後，再由 PR #3 進 main） |
| `docs/p7-next-plan` | `23b8cf0` | 是 main 的祖先（P7-4a 從它分出） |
| main CI | #183（`6ae18f3`） | 通過 |
| PNG golden | 54 張 | 全部一致、最大通道差 0，仍是鎖定狀態（`screenshots/main` 的 `p7-golden/report.md`） |

## 2. 無法刪除的遠端分支（權限限制，需要人工處理）

這個執行環境沒有刪除遠端分支的權限：`git push --delete` 被 git proxy 拒絕（HTTP 403），GitHub REST API 的刪除 ref 也回「Write access to this GitHub API path is not permitted through this proxy」。**不再嘗試其他方式繞過。**

| 分支 | SHA | 前置驗證 | 狀態 |
|---|---|---|---|
| `feature/p7-3-consistency` | `f75502c` | 已完成（§1） | **待人工刪除** |
| `feature/p7-4a-anr-diagnosis` | `b332d95` | 已完成（§1） | **待人工刪除** |

人工操作步驟（GitHub 網頁）：

1. 開 <https://github.com/patty60723/tearoff-calendar/branches>。
2. 找到上面兩個分支，確認最新 commit 的 SHA 跟表格一致（不一致就停下來，表示有人又推了東西）。
3. 按垃圾桶圖示刪除。GitHub 保留復原按鈕一段時間，刪錯可以按「Restore」。
4. 也可以在 PR #3 頁面下方按「Delete branch」（只會刪 `feature/p7-3-consistency`）。

刪除後的影響：`emulator.yml`、`emulator-diagnose.yml` 的觸發條件裡還有這兩個分支名，不會壞，只是不會再觸發；下一個階段的分支要另外加進觸發條件（診斷 workflow 在 main 上可以手動觸發）。

其他遠端分支**本輪都不刪**（清單與建議見合併前快照 §1～§3，內容沒有變；只有上面兩個分支與 `docs/p7-next-plan` 從「待合併」變成「已包含於 main」）。

## 3. 修訂：squash 合併的安全判斷

合併前快照把 `claude/portfolio-readme` 判成「可刪除」，依據是「`git diff <branch> main` 是空的」。**這個判斷不夠**：

- tree diff 只比「現在」的兩個快照。如果 main 之後剛好有別的 commit 把同樣的檔案改成同樣的內容，或者分支在 squash 之後又加了 commit、而 main 也碰巧做了等價的修改，diff 一樣會是空的，但分支上的 commit 並沒有被那個 PR 整合。
- 反過來，main 之後改過那些檔案時，diff 不是空的，但分支內容其實早已整合。
- 分支上可能有 tree 以外的東西（CI 驗收結果、`screenshots/<分支>` 的證據），diff 看不到。

修訂後的判斷要同時成立：

| 檢查 | `claude/portfolio-readme` 的結果 |
|---|---|
| 1. PR 紀錄：分支對應的 PR 已合併、合併方式（squash／merge） | PR #2，merged，squash，merge commit `f48443c` |
| 2. 合併當時的 head：分支現在的 SHA＝PR 合併時的 head SHA（之後沒有再推 commit） | 分支 `576d88a`＝PR #2 的 head `576d88a`；PR 只有 1 個 commit |
| 3. 合併時的基準：PR 的 base 與 squash commit 的 parent 相同，且「base → 分支 head」的變更＝「base → squash commit」的變更 | base `1b3f0c2`＝`f48443c` 的 parent；兩段變更都是同一個檔案、同樣 9 行（`README.md`） |
| 4. 獨有證據：分支或 `screenshots/<分支>` 有沒有別處沒有的驗收證據 | `screenshots/claude/portfolio-readme` 是一般 CI 截圖，跟其他分支的重複，沒有獨有證據 |
| 5. 目前 tree diff（輔助） | `git diff 576d88a main -- README.md` 沒有差異（僅輔助，不單獨作為依據） |

結論：`claude/portfolio-readme` 仍可刪除，但依據改成上面 1～4；本輪不刪。同樣的方式也適用以後的 squash 合併。

## 4. 目前需要人工處理的事

| 事項 | 狀態 |
|---|---|
| 刪除 `feature/p7-3-consistency`、`feature/p7-4a-anr-diagnosis` | 待人工（§2） |
| 其他分支（`feature/mowan-weather-poses`、`redesign`、`docs/p7-next-plan`、`claude/portfolio-readme`、`screenshots/*` 的重複項） | 只提供建議，等你決定 |
| `acceptance/*` 歸檔 | 試點見 [evidence-archive-pilot.md](evidence-archive-pilot.md)；原始分支不刪 |
