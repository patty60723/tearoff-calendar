#!/usr/bin/env bash
# P7-4a：診斷工具（diag_summary.py、recover.sh 的掛勾）的測試，不需要模擬器。
# 涵蓋：正常的每一種分類、缺資料、讀不懂的輸入、錯誤的參數，以及「掛勾不會改變點擊流程的結果」。
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/diag_summary.py"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
FAILS=0
ok() { echo "ok    $1"; }
bad() { echo "FAIL  $1"; FAILS=$((FAILS + 1)); }
expect() { # expect <名稱> <要包含的字> <實際輸出>
  case "$3" in *"$2"*) ok "$1" ;; *) bad "$1：要有「$2」，實際「$3」" ;; esac
}

APP_WIN="a1 com.patty60723.tearoff/com.patty60723.tearoff.MainActivity"
LAUNCHER_WIN="b2 com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity"
ANR_WIN="c3 Application Not Responding: com.google.android.apps.nexuslauncher"

# snap <資料夾> <時間> <焦點視窗> <最上層視窗（觸控）> <我們的 WaitQueue> <responsive> <互動對象> [state] [tap] [target]
snap() {
  local d="$T/$1"; mkdir -p "$d"
  printf 'tag=%s\ndevice_time=11-01 10:00:%s\n' "$1" "$2" > "$d/meta.txt"
  [ -n "${9:-}" ] && echo "tap=$9" >> "$d/meta.txt"
  [ -n "${10:-}" ] && echo "target=${10}" >> "$d/meta.txt"
  echo "  mCurrentFocus=Window{f00 u0 $3}" > "$d/window-focus.txt"
  case "$3" in *tearoff*) echo "  topResumedActivity=ActivityRecord{e1 u0 com.patty60723.tearoff/.MainActivity t5}" ;;
    *) echo "  topResumedActivity=ActivityRecord{e2 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity t1}" ;; esac > "$d/activities.txt"
  cat > "$d/input.txt" <<EOF
Input Manager State:
Input Dispatcher State:
  DispatchEnabled: true
  FocusedDisplayId: 0
  FocusedWindows:
    displayId=0, name='$3'
  TouchStatesByDisplay:
    <no displays touched>
  Display: 0
    Windows:
      0: name='$4', id=10, displayId=0, inputConfig=0x0, alpha=1.00, frame=[0,0][1080,2400], globalScale=1.000000
      1: name='$APP_WIN', id=11, displayId=0, inputConfig=0x0, alpha=1.00, frame=[0,0][1080,2400], globalScale=1.000000
  Connections:
    0: channelName='$APP_WIN', windowName='$APP_WIN', status=NORMAL, monitor=false, responsive=$6
      OutboundQueue: <empty>
      WaitQueue: length=$5
    1: channelName='$LAUNCHER_WIN', windowName='$LAUNCHER_WIN', status=NORMAL, monitor=false, responsive=true
      OutboundQueue: <empty>
      WaitQueue: <empty>
EOF
  printf '%s\n' "  PID  PPID STAT WCHAN NAME" " 100 1 S do_epoll_wait com.patty60723.tearoff" " 200 1 S do_epoll_wait com.google.android.apps.nexuslauncher" > "$d/ps.txt"
  : > "$d/events.txt"
  [ -n "$7" ] && echo "11-01 10:00:$2.500  1000  1100 I input_interaction: Interaction with: $7" > "$d/events.txt"
  echo "Total frames rendered: 12" > "$d/gfx.txt"
  [ -n "${8:-}" ] && echo "$8" > "$d/state.txt"
  return 0
}
cls() { python3 "$PY" classify "$T/$1" "$T/$2" "$3" "$4"; }

echo "== 分類：正常的輸入"
snap ok-b 01 "$APP_WIN" "$APP_WIN" 0 true "" true "540 900" "400 850 700 950"
snap ok-a 03 "$APP_WIN" "$APP_WIN" 0 true "$APP_WIN" false
expect "狀態有變 → HANDLED" "verdict=HANDLED" "$(cls ok-b ok-a true false)"

snap obs-b 01 "$ANR_WIN" "$ANR_WIN" 0 true "" true "540 900" "400 850 700 950"
snap obs-a 03 "$ANR_WIN" "$ANR_WIN" 0 true "$ANR_WIN" true
expect "對話框蓋住點擊位置 → OBSCURED" "verdict=OBSCURED" "$(cls obs-b obs-a true true)"

expect "座標不在元件裡 → COORDINATE_MISS" "verdict=COORDINATE_MISS" "$(snap cm-b 01 "$APP_WIN" "$APP_WIN" 0 true "" true "10 10" "400 850 700 950"; cls cm-b ok-a true true)"

snap nf-b 01 "$LAUNCHER_WIN" "$APP_WIN" 0 true "" true "540 900" "400 850 700 950"
snap nf-a 03 "$LAUNCHER_WIN" "$APP_WIN" 0 true "" true
expect "觸控視窗是 App、焦點是 launcher → NOT_FOREGROUND" "verdict=NOT_FOREGROUND" "$(cls nf-b nf-a true true)"

snap np-a 03 "$APP_WIN" "$APP_WIN" 4 false "$APP_WIN" true
expect "佇列卡住＋系統判定沒回應 → APP_NOT_PROCESSING" "verdict=APP_NOT_PROCESSING" "$(cls ok-b np-a true true)"

snap wq-a 03 "$APP_WIN" "$APP_WIN" 4 true "$APP_WIN" true
out=$(cls ok-b wq-a true true)
expect "只有 WaitQueue 不是 0 → 不判成 APP_NOT_PROCESSING" "verdict=UNKNOWN" "$out"
expect "只有 WaitQueue 不是 0 → 理由寫出來" "no second evidence" "$out"

snap stk-a 03 "$APP_WIN" "$APP_WIN" 4 true "$APP_WIN" true
printf '%s\n' '=== sample 1' '"main" prio=5 tid=1 Runnable' '  at com.patty60723.tearoff.Foo.busy(Foo.kt:1)' \
  '=== sample 2' '"main" prio=5 tid=1 Runnable' '  at com.patty60723.tearoff.Foo.busy(Foo.kt:1)' > "$T/stk-a/stack-com.patty60723.tearoff.txt"
expect "佇列卡住＋兩次取樣都停在同一個位置 → APP_NOT_PROCESSING" "verdict=APP_NOT_PROCESSING" "$(cls ok-b stk-a true true)"

snap mv-a 03 "$APP_WIN" "$APP_WIN" 4 true "$APP_WIN" true
printf '%s\n' '=== sample 1' '"main" prio=5 tid=1 Runnable' '  at com.patty60723.tearoff.Foo.a(Foo.kt:1)' \
  '=== sample 2' '"main" prio=5 tid=1 Native' '  at android.os.MessageQueue.nativePollOnce(Native method)' > "$T/mv-a/stack-com.patty60723.tearoff.txt"
expect "佇列不是 0 但主執行緒有在動（兩次取樣不同）→ 不判成卡住" "verdict=UNKNOWN" "$(cls ok-b mv-a true true)"

snap one-a 03 "$APP_WIN" "$APP_WIN" 4 true "$APP_WIN" true
printf '%s\n' '=== sample 1' '"main" prio=5 tid=1 Runnable' '  at com.patty60723.tearoff.Foo.busy(Foo.kt:1)' > "$T/one-a/stack-com.patty60723.tearoff.txt"
expect "只有一次取樣 → 不當成卡住的證據" "verdict=UNKNOWN" "$(cls ok-b one-a true true)"

snap bad-a 03 "$APP_WIN" "$APP_WIN" 4 true "$APP_WIN" true
printf '%s\n' '=== sample 1' 'garbage' '=== sample 2' '（讀不到 com.patty60723.tearoff 的 traces）' > "$T/bad-a/stack-com.patty60723.tearoff.txt"
expect "堆疊讀不懂 → 不當成卡住的證據" "verdict=UNKNOWN" "$(cls ok-b bad-a true true)"

snap idle-a 03 "$APP_WIN" "$APP_WIN" 0 true "$APP_WIN" true
printf '%s\n' '=== sample 1' '"main" prio=5 tid=1 Native' '  at android.os.MessageQueue.nativePollOnce(Native method)' > "$T/idle-a/stack-com.patty60723.tearoff.txt"
expect "摘要寫出取樣：狀態、位置、idle" "app_main=Native:android.os.MessageQueue.nativePollOnce:idle" "$(python3 "$PY" summary "$T/idle-a")"
expect "送到 App、主執行緒閒置、狀態沒變 → DELIVERED_NO_CHANGE" "verdict=DELIVERED_NO_CHANGE" "$(cls ok-b idle-a true true)"

snap el-a 03 "$APP_WIN" "$APP_WIN" 0 true "$LAUNCHER_WIN" true
expect "互動記錄是 launcher → DELIVERED_ELSEWHERE" "verdict=DELIVERED_ELSEWHERE" "$(cls ok-b el-a true true)"

snap old-a 03 "$APP_WIN" "$APP_WIN" 0 true "" true
echo "11-01 09:59:00.000  1000  1100 I input_interaction: Interaction with: $APP_WIN" > "$T/old-a/events.txt"
expect "點擊之前的互動記錄不算 → UNKNOWN" "verdict=UNKNOWN" "$(cls ok-b old-a true true)"

echo "== 元件真正的位置（uiautomator，診斷 workflow 點了沒反應時）"
snap rt-a 03 "$APP_WIN" "$APP_WIN" 0 true "$APP_WIN" true
echo "real_target=395 1700 572 1826" >> "$T/rt-a/meta.txt"
expect "元件其實在別的地方 → COORDINATE_MISS" "verdict=COORDINATE_MISS" "$(cls ok-b rt-a true true)"
snap rn-a 03 "$APP_WIN" "$APP_WIN" 0 true "$APP_WIN" true
echo "real_target=none" >> "$T/rn-a/meta.txt"
expect "元件不在畫面上 → COORDINATE_MISS" "target is not on screen" "$(cls ok-b rn-a true true)"
snap ri-a 03 "$APP_WIN" "$APP_WIN" 0 true "$APP_WIN" true
echo "real_target=400 850 700 950" >> "$T/ri-a/meta.txt"
expect "元件真的在點擊位置 → 不判成 COORDINATE_MISS（照樣看派送／互動記錄）" "verdict=DELIVERED_NO_CHANGE" "$(cls ok-b ri-a true true)"
snap ru-a 03 "$APP_WIN" "$APP_WIN" 0 true "$APP_WIN" true
echo "real_target=unreadable" >> "$T/ru-a/meta.txt"
expect "uiautomator 讀不到 → 不判成 COORDINATE_MISS（照樣看派送／互動記錄）" "verdict=DELIVERED_NO_CHANGE" "$(cls ok-b ru-a true true)"

echo "== 派送記錄（診斷 workflow 打開 InputDispatcher 的記錄時）"
snap dsp-a 03 "$APP_WIN" "$APP_WIN" 0 true "" true
printf '%s\n' "11-01 10:00:02.100   500   600 D InputDispatcher: channel '$APP_WIN (server)' ~ startDispatchCycle" \
  "11-01 10:00:02.180   500   600 D InputDispatcher: channel '$APP_WIN (server)' ~ finishDispatchCycle - seq=12, handled=false" > "$T/dsp-a/dispatch.txt"
out=$(cls ok-b dsp-a true true)
expect "派送記錄送到 App、狀態沒變 → DELIVERED_NO_CHANGE" "verdict=DELIVERED_NO_CHANGE" "$out"
expect "派送記錄寫出 handled" "handled=['false']" "$out"
snap dse-a 03 "$APP_WIN" "$APP_WIN" 0 true "" true
printf '%s\n' "11-01 10:00:02.100   500   600 D InputDispatcher: channel '[Gesture Monitor] swipe-up (server)' ~ startDispatchCycle" \
  "11-01 10:00:02.120   500   600 D InputDispatcher: channel '$LAUNCHER_WIN (server)' ~ startDispatchCycle" > "$T/dse-a/dispatch.txt"
expect "派送記錄只有 launcher（手勢監聽不算）→ DELIVERED_ELSEWHERE" "verdict=DELIVERED_ELSEWHERE" "$(cls ok-b dse-a true true)"
snap dso-a 03 "$APP_WIN" "$APP_WIN" 0 true "" true
echo "11-01 09:59:59.000   500   600 D InputDispatcher: channel '$APP_WIN (server)' ~ startDispatchCycle" > "$T/dso-a/dispatch.txt"
expect "點擊之前的派送記錄不算 → UNKNOWN" "verdict=UNKNOWN" "$(cls ok-b dso-a true true)"

echo "== SPY 視窗（launcher 的手勢監聽）不算擋住"
snap spy-b 01 "$APP_WIN" "$APP_WIN" 0 true "" true "540 900" "400 850 700 950"
sed -i "s|      0: name='$APP_WIN', id=10|      0: name='[Gesture Monitor] swipe-up', id=9, displayId=0, inputConfig=NOT_FOCUSABLE \| TRUSTED_OVERLAY \| SPY, alpha=1.00, frame=[0,0][1080,2400], globalScale=1.000000\n      5: name='$APP_WIN', id=10|" "$T/spy-b/input.txt"
expect "最上層是 SPY 視窗 → 不判成 OBSCURED" "top_window_at_tap=APP" "$(python3 "$PY" summary "$T/spy-b")"

echo "== 真的 Android 14 輸出（P7-4a 診斷 #1 的對照組，截掉不需要的部分）"
R34="$HERE/fixtures/diag-api34"
s34=$(python3 "$PY" summary "$R34/before")
expect "焦點是 App" "focus=APP" "$s34"
expect "輸入焦點是 App" "input_focus=APP" "$s34"
expect "App 的輸入連線正常、佇列是空的" "app_conn=responsive=True,wait=0,out=0" "$s34"
expect "launcher 的連線也找得到" "launcher_conn=responsive=True" "$s34"
expect "點擊位置最上層是 App（手勢監聽的 SPY 不算）" "top_window_at_tap=APP" "$s34"
expect "launcher 程序狀態讀得到" "launcher_proc=S" "$s34"
expect "真的輸出：狀態有變 → HANDLED" "verdict=HANDLED" "$(python3 "$PY" classify "$R34/before" "$R34/after" true false)"
expect "真的輸出：狀態沒變、沒有派送記錄 → 不亂判（UNKNOWN）" "verdict=UNKNOWN" "$(python3 "$PY" classify "$R34/before" "$R34/after" true true)"

echo "== 缺資料"
mkdir -p "$T/noinput-a"; cp "$T/ok-a/"* "$T/noinput-a/"; rm "$T/noinput-a/input.txt"
expect "點之後沒有 dumpsys input → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls ok-b noinput-a true true)"
expect "缺的東西寫出來" "input(after)" "$(cls ok-b noinput-a true true)"
mkdir -p "$T/notap-b"; cp "$T/ok-b/"* "$T/notap-b/"; grep -v '^tap=' "$T/ok-b/meta.txt" > "$T/notap-b/meta.txt"
expect "沒有點擊座標 → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls notap-b ok-a true true)"
expect "沒有狀態 → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls ok-b ok-a '' '')"
expect "快照資料夾不存在 → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls nothing-b nothing-a true true)"
mkdir -p "$T/empty"
s=$(python3 "$PY" summary "$T/empty")
expect "空的快照：摘要寫 missing、不猜" "input=missing" "$s"
expect "空的快照：程序狀態 missing" "procs=missing" "$s"

echo "== 讀不懂的輸入"
mkdir -p "$T/garbage-b" "$T/garbage-a"
for d in garbage-b garbage-a; do cp "$T/ok-b/meta.txt" "$T/$d/"; head -c 2000 /dev/urandom > "$T/$d/input.txt"; printf '\x00\xff' > "$T/$d/window-focus.txt"; done
expect "亂碼的 dumpsys → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls garbage-b garbage-a true true)"
mkdir -p "$T/nowin-b"; cp "$T/ok-b/"* "$T/nowin-b/"; sed -i '/frame=/d' "$T/nowin-b/input.txt"
expect "視窗清單是空的 → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls nowin-b ok-a true true)"
mkdir -p "$T/badmeta-b"; cp "$T/ok-b/"* "$T/badmeta-b/"; sed -i 's/^tap=.*/tap=abc def/' "$T/badmeta-b/meta.txt"
expect "座標不是數字 → INSUFFICIENT" "verdict=INSUFFICIENT" "$(cls badmeta-b ok-a true true)"

echo "== 錯誤的參數"
python3 "$PY" >/dev/null 2>&1; [ $? = 2 ] && ok "沒有參數 → 結束碼 2" || bad "沒有參數應該結束碼 2"
python3 "$PY" classify a b >/dev/null 2>&1; [ $? = 2 ] && ok "參數不夠 → 結束碼 2" || bad "參數不夠應該結束碼 2"
python3 "$PY" explode x >/dev/null 2>&1; [ $? = 2 ] && ok "不認得的指令 → 結束碼 2" || bad "不認得的指令應該結束碼 2"

echo "== classify-all：把每次點擊前後的快照配對"
R="$T/root"; mkdir -p "$R"
cp -r "$T/ok-b" "$R/01-control-1-1-before-tap"; cp -r "$T/ok-a" "$R/02-control-1-1-after-tap"
cp -r "$T/obs-b" "$R/03-inject-1-1-before-tap"; cp -r "$T/obs-a" "$R/04-inject-1-1-after-tap"; cp -r "$T/obs-a" "$R/05-inject-1-1-miss"
all=$(python3 "$PY" classify-all "$R")
expect "對照那一次 HANDLED" "control-1-1: verdict=HANDLED" "$all"
expect "注入那一次 OBSCURED（用 after-tap 配對）" "inject-1-1: verdict=OBSCURED" "$all"
[ "$(echo "$all" | wc -l)" = 2 ] && ok "兩次點擊、兩行結果" || bad "應該兩行：$all"
R2="$T/root2"; mkdir -p "$R2"
cp -r "$T/ok-b" "$R2/01-inject-fg-old-1-1-before-tap"; cp -r "$T/el-a" "$R2/02-inject-fg-old-1-1-after-tap"; cp -r "$T/el-a" "$R2/03-inject-fg-old-1-1-miss"
echo "real_target=none" >> "$R2/03-inject-fg-old-1-1-miss/meta.txt"
expect "after-tap 配對時，用同一次點擊的 miss 快照裡的真正位置" "inject-fg-old-1-1: verdict=COORDINATE_MISS" "$(python3 "$PY" classify-all "$R2")"
expect "沒有診斷資料夾 → INSUFFICIENT" "verdict=INSUFFICIENT" "$(python3 "$PY" classify-all "$T/none")"

echo "== 掛勾不改變點擊流程的結果"
(
  OUT="$T/hookout"; mkdir -p "$OUT/shots" "$OUT/ui"; PKG=com.patty60723.tearoff
  log() { :; }; warn() { :; }; shot() { :; }; dump_ui() { return 0; }; scroll_down() { :; }
  bounds_of() { echo "400 850 700 950"; }
  adb() { case "$*" in *"dumpsys window"*) echo "mCurrentFocus=Window{1 u0 com.patty60723.tearoff/.MainActivity}" ;; esac; return 0; }
  RECOVER_WAIT=0; FOREGROUND_WAIT=0
  . "$HERE/recover.sh"
  CALLS=""
  diag_hook() { CALLS="$CALLS $1"; return 1; }   # 掛勾失敗也不能影響結果
  STATE=true
  get_state() { echo "$STATE"; }
  # 點了會切換
  adb() { case "$*" in *"dumpsys window"*) echo "mCurrentFocus=Window{1 u0 com.patty60723.tearoff/.MainActivity}" ;; *"input tap"*) STATE=false ;; esac; return 0; }
  tap_toggle t1 "測試" story_bundle get_state; r1=$?
  # 點了不會切換
  adb() { case "$*" in *"dumpsys window"*) echo "mCurrentFocus=Window{1 u0 com.patty60723.tearoff/.MainActivity}" ;; esac; return 0; }
  STATE=true
  tap_toggle t2 "測試" story_bundle get_state; r2=$?
  echo "$r1 $r2 |$CALLS"
) > "$T/hook.txt" 2>&1
h=$(cat "$T/hook.txt")
expect "會切換時照樣回傳 0、不會切換照樣回傳 1（掛勾失敗也一樣）" "0 1 |" "$h"
expect "掛勾在點之前、點之後被呼叫" "before-tap after-tap" "$h"
expect "沒反應時呼叫 miss 掛勾" "miss" "$h"

echo
[ "$FAILS" = 0 ] && { echo "全部通過"; exit 0; } || { echo "失敗 $FAILS 項"; exit 1; }
