# 驗收證據歸檔試點：`acceptance/mowan-p5-2`

> 日期：2026-11-03　狀態：**Archive Verification Pending**（ZIP、manifest、SHA-256、檢查工具都準備好了；上傳到 GitHub Release 被這個執行環境的權限擋下，**沒有上傳、沒有歸檔完成**）。
> 原始分支 `acceptance/mowan-p5-2` **不刪**，等你審核流程。

## 1. 選這個分支的原因

| 條件 | `acceptance/mowan-p5-2` |
|---|---|
| 大小 | 6 個檔案、619,971 bytes（10 個 `acceptance/*` 裡最小） |
| 文件引用 | `docs/` 沒有任何連結指向它（只出現在 Branch Audit 的清單）→ 歸檔不會弄斷既有連結 |
| 歷史 | 1 個 commit、獨立歷史（跟 main 沒有共同祖先） |
| 風險 | 低：P5 早已結案，素材只用於當時的審核 |

## 2. 原始證據

- 分支：`acceptance/mowan-p5-2`
- commit：`c5797ed17fc3fc7fb79f0f268b600d2ab94793b2`（2026-10-26T04:16:45Z，「P5-2 小尺寸簡化對照圖（Build APK #59、87e62e0）」）
- 內容：P5-2 審核用的小尺寸簡化對照圖（48／72／96／120／160 px、每種天氣簡化前後、紙帽／圍巾／包袱有無、不同密度、實際像素）。由 CI（Build APK #59、commit `87e62e0`）用 Robolectric 畫的，不是實機截圖。

| 檔案 | bytes | SHA-256（前 16 碼） |
|---|---|---|
| `README.md` | 1,110 | `9705f518cf4768be…` |
| `actual-pixels.png` | 60,917 | `b3c327db36b1fae4…` |
| `densities.png` | 276,714 | `0e01df8721189a65…` |
| `equipment.png` | 25,420 | `ec35665960669175…` |
| `sizes.png` | 56,216 | `1837fd4fe19c1090…` |
| `weathers-before-after.png` | 199,594 | `8bc06fb853c47390…` |

**能不能重建**：理論上可以在 `87e62e0` 重跑 CI 再產生類似的圖，但 P5-2 的審核是看**這一版**的圖；重產的圖不保證逐像素相同（當時還沒有鎖定的 golden），所以把它當成**不可重建的人工審核證據**。

## 3. 歸檔檔案

| 檔案 | 位置 | 內容 |
|---|---|---|
| `acceptance-mowan-p5-2.zip` | 不放進 repo（試點時另外提供） | `git archive --format=zip --prefix=acceptance-mowan-p5-2/ c5797ed…`；605,119 bytes |
| SHA-256 | [`docs/evidence/SHA256SUMS`](evidence/SHA256SUMS) | `2487e36d338b7ceac58540fe607a77991376d587c11c8c92c4ae6d06b82b6747` |
| manifest | [`docs/evidence/acceptance-mowan-p5-2.manifest.json`](evidence/acceptance-mowan-p5-2.manifest.json) | 來源分支、commit、建立方式、每個檔案的路徑／大小／SHA-256／git blob |
| 檢查工具 | `tools/evidence/verify-archive.py`（測試：`tools/evidence/test-verify-archive.sh`，CI 會跑） | ZIP 的 SHA-256 與大小、檔案數（不多不少）、每個檔案的大小與 SHA-256 |

本機驗證（ZIP 剛建好時）：

| 檢查 | 結果 |
|---|---|
| 同一個 commit 建兩次 | 逐位元組相同（`git archive` 用 commit 時間當檔案時間，可重現；git 2.43.0） |
| ZIP 裡的檔案數 | 6＝分支上的檔案數 |
| 每個檔案的 SHA-256 | 跟分支上的 blob 內容一致 |
| `verify-archive.py` | `OK  acceptance-mowan-p5-2.zip：SHA-256 2487e36d…6747、6 個檔案、內容全部一致` |

## 4. GitHub Release Assets 的研究與實測

| 項目 | 結果 |
|---|---|
| 讀取 Releases（REST） | 可以。目前只有 1 個：tag `latest`、「最新版（第 183 版）」、prerelease、裡面是 `tearoff-calendar.apk`——**這是 App 的發布**，main 每次 CI 都會刪掉重建（`build.yml`） |
| 建立 Release（REST `POST /releases`） | **被擋**：HTTP 403「Creating, editing, or deleting releases is not permitted for this session type」。**沒有改用其他方式繞過** |
| 下載 Release asset（REST，`Accept: application/octet-stream`） | **可以**：拿既有的 APK 試，下載 1,265,456 bytes＝asset 的大小（試完就刪了）。所以你上傳之後，這個環境**可以**做下載驗證 |

適不適合（研究結論，尚未實測上傳）：

- 優點：跟 repo 綁在一起、有穩定網址（`https://github.com/patty60723/tearoff-calendar/releases/tag/<tag>`、`…/releases/download/<tag>/<檔名>`）、單檔上限 2 GiB、可以附說明。
- **風險：跟 App 的發布放在同一個 Releases 頁面**，手機使用者可能點錯。必須：tag 用不同的命名空間（`evidence/…`），標題標明「不是 App 發布」，勾 prerelease、**不設成 latest**，不放 APK。`build.yml` 只會刪 tag `latest`，不會碰 `evidence/…`。
- 風險：Release 可以被編輯、asset 可以被換掉 → 所以 SHA-256 與 manifest 要放在 repo（`docs/evidence/`），用 repo 裡的值驗證，不信任 Release 頁面上的說明。
- 替代方案（未測）：獨立的私人「證據 repo」的 Release；或維持現在的 `acceptance/*` 分支（最省事，但分支容易被誤刪、不好搜尋）。

## 4.1 三種保存方式的比較（2026-11-04 補充；都還沒有實測上傳）

> 期限、大小、計費的數字是依 GitHub 公開文件的一般規則整理，**沒有在這個環境驗證**；實際採用前要再對照 repo 的方案設定。

| 項目 | GitHub Release Assets（本 repo） | 專用 evidence repository | Actions Artifacts |
|---|---|---|---|
| 保存期限 | 不會過期（除非手動刪除 Release／asset） | 不會過期 | **預設 90 天**（可設 1～400 天，有方案上限）；過期自動刪除 |
| 費用 | 公開 repo 不計費；私人 repo 的 Release asset 也不算 Actions 儲存空間 | 同一般 repo；用 Release asset 或 Git LFS（LFS 有額度與費用） | 計入 Actions 的 storage 額度（私人 repo 有上限，超過要付費） |
| 單檔／總量 | 單檔 < 2 GiB；每個 Release 的 asset 數量有上限 | 同 Release；放 git 本身時大檔不適合（repo 變大、clone 變慢） | 單個 artifact 可以很大，但會占額度 |
| 穩定連結 | `…/releases/download/<tag>/<檔名>`（tag 不改就穩定） | 同上（在另一個 repo） | **不穩定**：artifact id 每次不同，過期就失效 |
| 跟 App 發布的混淆 | **高**：跟「最新版」APK 在同一個 Releases 頁面，要靠命名、prerelease、不設 latest 區隔 | **低**：完全分開 | 低（不在 Releases 頁面） |
| 權限與誤刪 | 有 repo 寫入權的人都能刪；CI 的 `gh release delete latest` 只刪 `latest`，但規則寫錯就可能誤刪 | 可以單獨設權限（例如只有擁有者能寫） | 有寫入權的人都能刪；過期自動刪 |
| 這個執行環境 | 建立被擋（403）；**下載可以** | 未測（需要建立新 repo，這個環境大概也不允許） | 下載被擋（blob 主機） |
| 適合放 | 里程碑的審核素材（小量、要長期保存） | 大量或敏感的證據、需要不同權限時 | 暫時的 CI 輸出（截圖、log、影片） |

建議（待你決定）：

- **短期**：維持現狀（`acceptance/*` 分支）＋這次的 manifest 與 SHA-256 放在 repo；分支不刪。
- **里程碑歸檔**：Release Assets（`evidence/…` tag、prerelease、不設 latest、不放 APK）。最省事，但要接受跟 App 發布放在同一頁的風險，並在 `build.yml` 確認不會刪到 `evidence/…`。
- 證據量變大、或要分開權限時，再改成專用 evidence repository。
- **Actions Artifacts 只當暫存**，不當長期證據（期限、連結都不穩定）。

## 5. 請你手動上傳（建議的方式）

ZIP 在這次的回覆裡另外提供；也可以自己用 `git archive` 重建（git 版本不同時 ZIP 的位元組可能不同，那時改用 manifest 逐檔比對）。

```sh
# 1. 建立 prerelease（不是 latest），tag 指到證據分支的 commit
gh release create "evidence/acceptance-mowan-p5-2" acceptance-mowan-p5-2.zip \
  --repo patty60723/tearoff-calendar \
  --target c5797ed17fc3fc7fb79f0f268b600d2ab94793b2 \
  --prerelease --latest=false \
  --title "【驗收證據歸檔，不是 App 發布】P5-2 小尺寸簡化對照圖" \
  --notes "驗收證據歸檔（試點）。沒有 APK，不要安裝。來源：acceptance/mowan-p5-2 c5797ed。SHA-256：2487e36d338b7ceac58540fe607a77991376d587c11c8c92c4ae6d06b82b6747（以 repo 的 docs/evidence/SHA256SUMS 為準）。"
```

或在網頁：Releases → Draft a new release → tag 填 `evidence/acceptance-mowan-p5-2`、Target 選 `acceptance/mowan-p5-2` → 上傳 ZIP → 勾「Set as a pre-release」、**取消**「Set as the latest release」→ Publish。

上傳後我會做（下一輪）：

1. 用 REST 把 asset 下載回來（不經過網頁）。
2. `python3 -I tools/evidence/verify-archive.py docs/evidence/acceptance-mowan-p5-2.manifest.json <下載的檔案>`：SHA-256、檔案數、每個檔案的內容都要一致。
3. 確認 `…/releases/download/evidence/acceptance-mowan-p5-2/acceptance-mowan-p5-2.zip` 這個穩定連結可以用，再把它寫進 Branch Audit 與這份文件。
4. 全部通過才標成「歸檔完成」；原始分支仍等你決定才刪。

## 6. 狀態

| 項目 | 狀態 |
|---|---|
| ZIP、manifest、SHA-256 | 完成 |
| 檢查工具與測試 | 完成（9 項測試；測試抓到一個工具本身的錯：壞掉的 ZIP 會讓檢查當掉，已修正） |
| 上傳到 Release | **未完成**：權限限制 |
| 下載驗證、穩定連結 | **Archive Verification Pending** |
| 刪除原始分支 | 不做（等你審核） |
