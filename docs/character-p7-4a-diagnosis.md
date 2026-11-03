# 墨丸 V3 — P7-4a：ANR 與模擬器驗收可靠性診斷

> 日期：2026-11-01　分支：`feature/p7-4a-anr-diagnosis`（從 `docs/p7-next-plan` 分出，基準 `main` `1b3f0c2`）
> 狀態：**2026-11-03 驗收通過、結案（Blocker 0）**。人工注入的座標錯誤已證實並修正；**#68／#79 自然發生的根因仍是 Unknown**（推定：腳本座標問題，未證實），不標成已解決。已併入 P7-3 工作分支 `feature/p7-3-consistency`。**2026-11-03 補充**：模擬器 #100（P7-3 分支）自然發生同類的 launcher ANR（`Application does not have a focused window`），截圖顯示對話框開著時頁面沒捲到最底，修正後的流程 3／3 通過——記錄為「同類自然 ANR 已證實修正流程有效」；#68／#79 個別事件仍是 Unknown（見 [P7-3 報告](character-p7-3-consistency.md) §5.1）。沒有改 App 程式、美術、golden 或小工具版面；只改 `tools/emulator/`、新的診斷 workflow、`build.yml` 的腳本自我測試與文件。不開 PR、不合併 main。
> 相關：[P7 計畫](character-p7-plan.md) §P7-4a・[P7 後續規劃](character-p7-next-plan.md) §5（初步調查）

證據分類：**自然發生**（日常驗收 #29～#79 的紀錄）、**人工注入**（診斷 workflow 用 `adb root` 暫停 Pixel Launcher）、**對照**（同一個診斷 workflow、不注入）。人工注入只是「同一種外觀的故障」，不等於自然發生的原因已經證實。

## 1. 結論摘要

| 問題 | 答案 | 證據等級 |
|---|---|---|
| 1. launcher ANR 時，焦點與觸控實際送到哪 | 對話框開著時：觸控送到「Application Not Responding」對話框的視窗（App 收不到）。對話框關掉後：焦點、最上層的 Activity、點擊位置最上層的視窗都是我們的 App；InputDispatcher 把每一下 DOWN／UP 都送到 App 的視窗 | 人工注入：**直接證據**（派送記錄、`dumpsys input`）。自然發生：沒有這些紀錄 |
| 2. App 主執行緒是否正常處理事件 | **正常**：輸入連線 `responsive=true`、`WaitQueue` 0；App 回報每一下 `handled=true`；主執行緒兩次取樣都在 Looper（`nativePollOnce` 或畫圖）；2 秒畫 31～42 張 | 人工注入＋對照：**直接證據**。自然發生：當時 gfxinfo 約 200 張／10 秒、後面各段正常（旁證） |
| 3. 包袱沒切換的原因 | **座標問題（腳本）**：對話框開著時，`setup` 的捲動手勢送到對話框、頁面沒有捲到最底；量位置的 `view_bounds.py` 假設已經捲到最底，算出的 (483,1088) 其實是日曆頁。點擊送到 App、被日曆頁處理，包袱入口根本不在畫面上（uiautomator 讀不到它） | 人工注入：**直接證據**＋修正前後 A／B（舊流程 0／3，修正後 3／3）。自然發生：**記錄特徵完全相同，但沒有直接證據** |
| 4. 驗收能不能分辨 App 失敗與環境失效 | 診斷 workflow：**能**（9 種分類，例如 COORDINATE_MISS、OBSCURED、APP_NOT_PROCESSING、DELIVERED_NO_CHANGE）。日常驗收：**部分**（沒有 root，只有焦點、視窗、佇列、程序狀態、系統訊息；分不出「送到 App 但點錯元件」）——所以日常驗收照樣記 FAIL，只附上診斷說明 | 見 §5 |

**#68／#79 的原因：仍是 Unknown**，但現在有一個「推定原因」：測試腳本在對話框開著時捲動、之後用假設的捲動位置點擊。理由：人工注入在同樣的條件下 3／3 重現出跟 #68／#79 完全相同的記錄，修正後 3／3 通過；**但自然發生的那幾次沒有派送記錄、uiautomator 位置或截圖（artifact 在這個環境下載不了）可以直接確認**。沒有證據顯示是 App 的問題。

## 2. 診斷工具

| 檔案 | 內容 |
|---|---|
| `tools/emulator/diag.sh` | `diag_snapshot`：存一次快照——焦點（`dumpsys window`）、輸入派送（`dumpsys input` 全部：焦點視窗、視窗清單與範圍、各連線的 `responsive`／`OutboundQueue`／`WaitQueue`、上一次 ANR）、最上層 Activity、App 與 launcher 的程序狀態（`ps` 的 STAT）、事件記錄（`am_anr`、`input_focus`、`input_interaction`、程序啟動／結束）、InputDispatcher 與 ActivityManager 的系統訊息、2 秒內畫了幾張、點擊座標與目標範圍、當下的包袱狀態、裝置時間。**診斷 workflow** 另外：主執行緒堆疊（`kill -3`，App 取兩次樣本）、點了沒反應時用 uiautomator 讀元件真正的位置 |
| `tools/emulator/diag_summary.py` | 摘要（一行，缺的寫 missing、不猜）與分類（見下表）；`classify-all` 把每次點擊的前後快照配對 |
| `tools/emulator/recover.sh` | 加掛勾：看到對話框、關掉對話框、點之前、點之後、點了沒反應時呼叫診斷（沒載入診斷時什麼都不做；掛勾失敗也不影響結果）。**修正**：關掉對話框後重新捲到最底再量位置（§4） |
| `tools/emulator/p7-4a-diagnose.sh`＋`.github/workflows/emulator-diagnose.yml` | 獨立的診斷 workflow：對照、人工注入·前景（舊流程）、人工注入·前景（修正後）、人工注入·背景交錯進行；原始資料推到 `screenshots/feature/p7-4a-anr-diagnosis-diag` 分支（artifact 下載不了也能用 git 拿） |
| `tools/emulator/w7-acceptance.sh` | 日常驗收：開頭多跑 `test-diag.sh`；載入診斷（不用 root、正常點擊前不多花時間，只在出事的路徑收集）；第 4 段包袱沒有全部切換時印出分類（不影響 FAIL） |

分類（依序判斷）：

| 分類 | 條件 |
|---|---|
| HANDLED | 狀態有變 |
| INSUFFICIENT | 必要資料缺少或讀不懂（列出缺什麼），不做推論 |
| COORDINATE_MISS | 座標不在算出的範圍裡；或 uiautomator 讀到元件真正的位置不在點擊處／不在畫面上 |
| OBSCURED | 點擊位置最上層、能接收觸控的視窗不是 App（launcher 的手勢監聽是 SPY 視窗，只收副本，不算） |
| NOT_FOREGROUND | 輸入焦點或最上層 Activity 不是 App |
| APP_NOT_PROCESSING | App 的佇列卡住，**而且**有第二個證據：系統判定沒回應，或主執行緒兩次取樣都停在同一個不是閒置的位置。**只有 WaitQueue 不是 0 不會判成這個** |
| DELIVERED_ELSEWHERE | 派送／互動記錄顯示觸控交給別的視窗 |
| DELIVERED_NO_CHANGE | 派送記錄顯示觸控交給了 App、App 沒有卡住，但狀態沒變 |
| UNKNOWN | 以上都不成立（列出已知證據） |

`input_interaction` 事件只在「接收的視窗組合改變」時才記錄，不是每一下都有，所以不能單獨當成送達的證據；診斷 workflow 用 InputDispatcher 的派送記錄（`log.tag.InputDispatcherDispatchCycle` 設成 DEBUG 後重新啟動 framework）。

### 2.1 工具的測試

| 測試 | 結果 |
|---|---|
| `test-diag.sh`（新增，53 項） | 每一種分類的正常輸入；缺資料（沒有 input、沒有座標、沒有狀態、沒有快照、空的快照）；讀不懂的輸入（亂碼、空的視窗清單、座標不是數字、讀不懂的堆疊）；錯誤參數（結束碼 2）；只有 WaitQueue／只有一次取樣／兩次取樣不同都**不**判成 APP_NOT_PROCESSING；SPY 視窗不算遮擋；**真的 Android 14 輸出**（診斷 #1 的對照組，`fixtures/diag-api34/`）；掛勾不改變點擊流程的結果 |
| `test-recover.sh`（加 4 項） | 沒有對話框時不多捲動；對話框開著時捲動沒生效的情況：修正後通過、**舊流程照樣失敗（測試分得出兩者）**；App 的點擊壞掉時，重新捲動後照樣 3 次都沒切換 → 回傳 1 |
| `test-quiet.sh` | 照舊全部通過 |
| 故意改壞 | 把 APP_NOT_PROCESSING 改成只看 WaitQueue → `test-diag.sh` 2 項失敗；把重新捲動的預設改回舊流程 → `test-recover.sh` 失敗 |

## 3. 診斷結果

### 3.1 執行紀錄

| 執行 | commit | 內容 | 結果 |
|---|---|---|---|
| [診斷 #1](https://github.com/patty60723/tearoff-calendar/actions/runs/41327453165) | `603d8a0` | 對照 3、人工注入（背景）3 | 6／6 切換；背景注入**沒有出現對話框** |
| [診斷 #2](https://github.com/patty60723/tearoff-calendar/actions/runs/41328457805) | `939fd41` | 對照 3、前景 3、背景 3（加派送記錄、兩次取樣） | 前景 **0／3**（每次 3 下都沒切換）；對照、背景 6／6 |
| [診斷 #3](https://github.com/patty60723/tearoff-calendar/actions/runs/41330237520) | `df46975` | 對照 3、前景·舊流程 3、前景·修正後 3、背景 3 | 舊流程 **0／3**；修正後 **3／3**；對照、背景 6／6 |
| [診斷 #4](https://github.com/patty60723/tearoff-calendar/actions/runs/41332661898) | `a326a05` | 同 #3（分類用 uiautomator 位置） | 見 3.4 |

### 3.2 人工注入·背景（App 在前面時暫停 launcher）

- launcher 的手勢監聽（`[Gesture Monitor] swipe-up`）收不到觸控而 ANR（`am_anr`：nexuslauncher「Input dispatching timed out」），系統當成背景 ANR，**不顯示對話框**（日常驗收設定了 `anr_show_background 0`）。
- launcher 被暫停（STAT `T`）時，點包袱入口 **9／9 都正常切換**（#1～#3）。所以「launcher 沒回應本身會擋住我們的點擊」**在這個注入下沒有發生**。

### 3.3 人工注入·前景（回到桌面、暫停 launcher、點桌面 → 「沒有回應」對話框）

截圖：[`p7-4a/inject-fg-old.png`](p7-4a/inject-fg-old.png)（左：對話框；右：舊流程關掉對話框後，頁面停在最上面，點擊落在日曆頁）。

舊流程（#2、#3 共 6 次，每次 3 下）的每一下都是同一個樣子：

| 證據 | 內容 |
|---|---|
| 捲動 | `setup` 捲到最底的手勢（16:56:26）全部送到 `Application Not Responding: com.google.android.apps.nexuslauncher` 的視窗（派送記錄） |
| 對話框關掉後 | 焦點、最上層 Activity、點擊位置最上層視窗都是 App；App 的連線 `responsive=true`、`WaitQueue` 0 |
| 點擊 | 派送記錄：DOWN／UP 在 (483,1088)，送到 `com.patty60723.tearoff/…MainActivity`，**`handled=true`** |
| 主執行緒 | 兩次取樣都在 Looper（`nativePollOnce` 或 `HardwareRenderer.nSyncAndDrawFrame`），2 秒畫 31～42 張 |
| 真正的位置 | uiautomator：**畫面上沒有 `story_bundle`**（頁面在最上面，包袱入口在畫面下面）；截圖相同 |
| 分類 | COORDINATE_MISS（target is not on screen；dispatch to app=8 handled=true） |

修正後（#3 共 3 次）：關掉對話框 → 重新捲到最底 → 第 1 下就切換（3／3）。

### 3.4 診斷 #4（最後一版，`a326a05`）

| 組 | 次數 | 包袱切換 | 分類 |
|---|---|---|---|
| 【對照】 | 3 | 3／3 | HANDLED ×3 |
| 【人工注入·前景·舊流程】 | 3（每次 3 下） | **0／3** | **COORDINATE_MISS ×9**（target is not on screen；dispatch to app=8、handled=true） |
| 【人工注入·前景】（修正後） | 3 | **3／3** | HANDLED ×3（都是第 1 下） |
| 【人工注入·背景】 | 3 | 3／3 | HANDLED ×3 |
| 【自然發生】 | 0 | — | 對照組這 4 次診斷（共 12 次）都沒有自然遇到對話框 |

診斷工具本身：腳本自我測試全部通過、沒有任何點擊是 INSUFFICIENT、對照組全部通過 → workflow 綠燈。（工具失敗的情況會讓它紅：自我測試失敗、證據不足、對照組沒通過。）

## 4. 驗收腳本的修改與假陽性風險

| 修改 | 為什麼 | 會不會造成假陽性（把 App 的問題當成通過） |
|---|---|---|
| `recover.sh`：關掉「沒有回應」對話框後，重新捲到最底再量位置（`RECOVER_RESCROLL=1`） | 對話框開著時捲動沒有生效，量位置的方法假設已經在最底（§3.3） | **低**。點擊次數（最多 3 次）與 FAIL 條件不變，不是多一次重試；只在有對話框而且關掉之後才捲動（沒有對話框時捲動 0 次，有測試）；App 的點擊壞掉時照樣 3 次都失敗（有測試）。剩下的風險：如果 App 本身有「點了之後頁面亂跳」的問題，重新捲動可能讓那一次點到正確的位置——但這種版面問題由第 13 段與 Robolectric 的捲動測試另外檢查，不靠第 4 段 |
| 日常驗收載入診斷（只在出事時收集） | 自然發生時留下證據 | **沒有**：只收集，不改任何 PASS／FAIL；掛勾失敗也不影響結果（有測試）；必要檢查的數量不變（53） |
| 第 4 段失敗時印出分類 | 方便判斷 | **沒有**：印在 FAIL 之後，FAIL 照樣算 |
| 沒有做的 | 不把環境失效改成 WARN、不加重試、不降門檻 | — |

日常驗收（emulator acceptance）在這個分支的結果：

| 執行 | commit | 結果 |
|---|---|---|
| [#94](https://github.com/patty60723/tearoff-calendar/actions/runs/41327478277) | `603d8a0`（加診斷收集） | PASS 53、FAIL 0；沒有出現對話框 |
| [#95](https://github.com/patty60723/tearoff-calendar/actions/runs/41330252426) | `df46975`（加重新捲動） | PASS 53、FAIL 0；沒有出現對話框（修正的路徑沒有被走到） |
| [#96](https://github.com/patty60723/tearoff-calendar/actions/runs/41332675250) | `a326a05` | PASS 53、FAIL 0；沒有出現對話框 |

CI（Build APK，含 `test-quiet.sh`、`test-recover.sh`、`test-diag.sh` 與全部單元測試）：#164（`603d8a0`）～#168（`077e26e`）全部通過。App 程式沒有改，PNG golden 不受影響（沒有更新基準）。

## 5. 每個可能原因：證據、反證、Unknown

| 可能原因 | 支持 | 反證 | 狀態 |
|---|---|---|---|
| A. App 主執行緒卡住、沒處理輸入 | — | 人工注入：連線 responsive、佇列 0、`handled=true`、兩次取樣都在 Looper、持續畫圖；自然發生：之後 gfxinfo 正常、後面各段通過、沒有我們套件的 ANR | **沒有證據**（自然發生那幾次沒有堆疊，不能 100% 排除，但沒有任何支持） |
| B. 對話框關掉後，觸控仍送到別的視窗／被遮擋 | 對話框開著時觸控確實送到對話框 | 關掉之後：焦點、最上層視窗都是 App，派送記錄送到 App | 對話框開著的那段：成立；關掉之後：**人工注入不成立** |
| C. 座標錯誤（頁面沒捲到最底，量位置的假設不成立） | 人工注入：派送記錄、uiautomator、截圖三方一致；修正前後 A／B（0／3 → 3／3）；自然發生的記錄特徵相同（對話框在第 4 段第一次點之前就在、關掉後 9 下都沒切換、同一個座標） | 自然發生沒有直接證據 | 人工注入：**證實**。自然發生：**推定原因**，未證實 |
| D. 讀狀態的方法讀到舊值 | — | 對照組、修正後都讀到正確的切換 | 沒有證據 |
| E. launcher 為什麼會在第 4 段前 ANR | 模擬器軟體繪圖、錄影、反覆重開 App 的負載（推測） | — | **Unknown**（不影響我們 App 的判斷；不是我們能修的） |

## 6. P7-4a 能不能結案

- 四個問題在**人工注入**下都有直接證據的答案；工具有完整測試；日常驗收沒有變寬鬆。
- **自然發生的原因仍是 Unknown（推定：腳本座標問題）**。不需要無限增加執行次數：自然發生在 89 次裡出現 11 次、最近 10 次都沒有。下一次自然發生時，日常驗收會留下：對話框開著／關掉時的快照（焦點、視窗、佇列、系統訊息）、之後每次點擊前後的快照、點了沒反應的截圖（推到 `screenshots/<分支>-emulator`），並且走修正後的流程。如果修正後的流程在自然發生時通過，就是推定原因的佐證；如果還是失敗，診斷快照會指出是哪一種。
- 建議：**P7-4a 可以結案**，條件是你接受「#68／#79 = 推定原因、仍標 Unknown，等下一次自然發生確認」。沒有具體阻礙。

## 7. P7-3 可以安全開始的條件

1. 這個分支的修改（重新捲動、診斷收集）先進到 P7-3 的工作基準（合併方式由你決定；本次不開 PR）。
2. P7-3 的模擬器部分如果遇到 launcher 對話框：照樣記 FAIL，看診斷分類；分類是 COORDINATE_MISS／OBSCURED（環境或腳本）就另外記錄，不能當成 P7-3 的功能結果，也不能改成通過。
3. P7-3 的自動測試（Robolectric）不受影響，可以先開始。

## 8. 限制

- #68／#78／#79 的 artifact 仍然下載不了（GitHub 的 blob 儲存主機被這個環境的網路政策擋住），所以自然發生的截圖與 `ui/*.txt` 沒有看過。
- 人工注入用 `kill -STOP` 暫停 launcher，跟自然發生的 launcher ANR 原因不同（自然的原因 Unknown）；相同的是「對話框出現的時間點」與之後的流程。
- 診斷 workflow 只在 API 34 google_apis 模擬器上跑；其他版本是 P7-4b。
- 實機：Not tested。
