# P7-4a 診斷：在關鍵時刻把「誰拿到焦點、觸控送到哪裡、App 跟 launcher 的程序狀態、ANR 事件、主執行緒」存下來。
# 用 source 載入；呼叫端要提供 OUT、PKG、log。只收集證據，不改任何驗收結果。
#
# 每一次快照存在 $OUT/diag/<tag>/（原始輸出），再用 diag_summary.py 整理成一行摘要，
# 印在 log（也就是 job log）裡：artifact 下載不了時，job log 和推到分支的結果仍然看得到。
#
# DIAG_ROOT=1 時（只有獨立的診斷 workflow）才會取主執行緒堆疊（kill -3 → /data/anr 的 traces，要 adb root）。
# 日常驗收不用 root：只靠焦點、輸入派送、程序狀態、事件記錄與重畫張數。

DIAG_ROOT=${DIAG_ROOT:-0}
LAUNCHER_PKG=${LAUNCHER_PKG:-com.google.android.apps.nexuslauncher}
DIAG_N=0
DIAG_PY="$(dirname "${BASH_SOURCE[0]}")/diag_summary.py"

# diag_snapshot <tag> [點擊 x y] [目標 x1 y1 x2 y2]：存一次快照，印出摘要
diag_snapshot() {
  local tag=$1 d
  DIAG_N=$((DIAG_N + 1))
  d="$OUT/diag/$(printf '%02d' $DIAG_N)-$tag"
  mkdir -p "$d"
  {
    echo "tag=$tag"
    echo "host_time=$(date -u +%FT%T.%3NZ)"
    echo "device_uptime=$(adb shell cat /proc/uptime 2>/dev/null | tr -d '\r' | awk '{print $1}')"
    echo "device_time=$(adb shell date +%m-%d\\ %H:%M:%S 2>/dev/null | tr -d '\r')"
    [ -n "${2:-}" ] && echo "tap=$2 $3"
    [ -n "${4:-}" ] && echo "target=$4 $5 $6 $7"
  } > "$d/meta.txt"
  adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -E "mCurrentFocus=|mFocusedApp=|mTopFocusedDisplayId" > "$d/window-focus.txt"
  adb shell dumpsys input 2>/dev/null | tr -d '\r' > "$d/input.txt"
  adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -E "topResumedActivity=|mResumedActivity|ResumedActivity:" > "$d/activities.txt"
  adb shell ps -A -o PID,PPID,STAT,WCHAN,NAME 2>/dev/null | tr -d '\r' | grep -E " ($PKG|$LAUNCHER_PKG|system_server)$" > "$d/ps.txt"
  adb logcat -d -b events -v threadtime 2>/dev/null | tr -d '\r' |
    grep -E " (am_anr|input_interaction|input_focus|am_kill|am_proc_died|am_proc_start|am_crash|wm_on_resume_called|wm_on_paused_called|am_focused_root_task)" |
    tail -60 > "$d/events.txt"
  # 輸入派送與 ANR 的系統訊息（日常驗收也有：視窗沒回應的警告、「ANR in …」的原因；
  # 診斷 workflow 另外打開 InputDispatcher 的派送記錄：每個事件送到哪個 channel、App 有沒有處理）
  adb logcat -d -v threadtime -s InputDispatcher:V ActivityManager:E 2>/dev/null | tr -d '\r' | grep -v "^-----" | tail -300 > "$d/dispatch.txt"
  # 主執行緒有沒有在跑：2 秒內 App 畫了幾張（畫面靜止時是 0，不代表卡住；只當旁證）
  adb shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
  sleep 2
  adb shell dumpsys gfxinfo $PKG 2>/dev/null | tr -d '\r' | grep -m1 "Total frames rendered" > "$d/gfx.txt"
  # 當下的狀態（例如包袱開關）：從 recover.sh 的 tap_toggle 呼叫時，讀狀態的指令是它的區域變數 get
  [ -n "${get:-}" ] && $get > "$d/state.txt" 2>/dev/null
  [ "$DIAG_ROOT" = 1 ] && diag_stacks "$d"
  # 診斷 workflow、點了沒反應時：用 uiautomator 讀元件「真正」在螢幕上的位置（含捲動）。
  # 墨丸一直在動時 uiautomator 讀不到，所以暫時關掉動畫、讀完再打開（只在診斷 workflow，日常驗收不做）
  if [ "$DIAG_ALWAYS" = 1 ] && [ -n "${DIAG_TARGET_ID:-}" ] && case "$tag" in *-miss) true ;; *) false ;; esac; then
    diag_real_bounds "$d" "$DIAG_TARGET_ID"
  fi
  local s
  s=$(python3 "$DIAG_PY" summary "$d" 2>&1)
  echo "$s" > "$d/summary.txt"
  log "  診斷[$tag] $s"
}

diag_real_bounds() { # diag_real_bounds <快照資料夾> <元件 id>：在 meta.txt 寫 real_target=x1 y1 x2 y2（不在畫面上寫 none）
  local d=$1 id=$2 xml r
  adb shell settings put global animator_duration_scale 0
  sleep 1
  xml=$(timeout 30 adb shell "uiautomator dump /sdcard/diag-ui.xml >/dev/null 2>&1 && cat /sdcard/diag-ui.xml" 2>/dev/null)
  adb shell settings put global animator_duration_scale 1
  echo "$xml" > "$d/uiautomator.xml"
  if [ -z "$xml" ]; then echo "real_target=unreadable" >> "$d/meta.txt"; return; fi
  r=$(echo "$xml" | python3 -c '
import re, sys
m = re.search(r"resource-id=\"[^\"]*:id/'"$id"'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", sys.stdin.read())
print(" ".join(m.groups()) if m else "none")')
  echo "real_target=$r" >> "$d/meta.txt"
}

# 主執行緒堆疊（只在 DIAG_ROOT=1）：送 SIGQUIT，從最新的 traces 取出 main 執行緒整段。
# 單一個取樣只是一個瞬間：我們的 App 取兩次（間隔 1 秒），兩次都停在同一個不是閒置的 Java 位置才算「卡住」的證據。
diag_stacks() {
  local d=$1 name n samples
  for name in $PKG $LAUNCHER_PKG; do
    [ "$name" = "$PKG" ] && samples=2 || samples=1
    : > "$d/stack-$name.txt"
    for n in $(seq 1 $samples); do
      echo "=== sample $n" >> "$d/stack-$name.txt"
      diag_one_stack "$name" >> "$d/stack-$name.txt"
      [ "$n" -lt "$samples" ] && sleep 1
    done
  done
}
diag_one_stack() { # diag_one_stack <套件>：印出 main 執行緒那一段（讀不到就印說明）
  local name=$1 p f before
  p=$(adb shell pidof $name 2>/dev/null | tr -d '\r' | awk '{print $1}')
  [ -n "$p" ] || { echo "（$name 沒有在執行）"; return; }
  before=$(adb shell "ls -t /data/anr/ 2>/dev/null | head -1" | tr -d '\r')
  adb shell "kill -3 $p" 2>/dev/null
  for _ in 1 2 3 4 5; do
    sleep 0.5
    f=$(adb shell "ls -t /data/anr/ 2>/dev/null | head -1" | tr -d '\r')
    [ -n "$f" ] && [ "$f" != "$before" ] && break
  done
  if [ -z "$f" ] || [ "$f" = "$before" ]; then echo "（讀不到 $name 的 traces）"; return; fi
  adb shell "cat /data/anr/$f" 2>/dev/null | tr -d '\r' |
    awk -v want="Cmd line: $name" '$0 ~ /^----- pid / {keep=0} $0==want {keep=1} keep' |
    awk '/^"main"/{m=1} m && /^$/ {exit} m'
}

# 給 recover.sh 呼叫的掛勾：只收集證據，不改流程、不改結果。
# 日常驗收（DIAG_ALWAYS=0）只在出事的路徑上收集（看到對話框、點了沒反應，之後的每次點擊前也收集），
# 不在每次正常點擊前多花時間，避免改變驗收的節奏；診斷 workflow（DIAG_ALWAYS=1）每次都收集。
DIAG_ALWAYS=${DIAG_ALWAYS:-0}
DIAG_ACTIVE=0
diag_hook() { # diag_hook <事件> <名稱>
  case "$1" in
    anr-seen) DIAG_ACTIVE=1; diag_snapshot "$2-anr-seen" ;;
    anr-closed) diag_snapshot "$2-anr-closed" ;;
    before-tap) [ "$DIAG_ALWAYS" = 1 ] || [ "$DIAG_ACTIVE" = 1 ] || return 0
      diag_snapshot "$2-before-tap" "$TAP_X" "$TAP_Y" ${TARGET_BOUNDS:-} ;;
    after-tap) [ "$DIAG_ALWAYS" = 1 ] || return 0
      diag_snapshot "$2-after-tap" "$TAP_X" "$TAP_Y" ${TARGET_BOUNDS:-} ;;
    miss) DIAG_ACTIVE=1; diag_snapshot "$2-miss" "$TAP_X" "$TAP_Y" ${TARGET_BOUNDS:-} ;;
  esac
}
