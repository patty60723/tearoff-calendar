#!/usr/bin/env bash
# tools/emulator/recover.sh 的測試：用假的 adb 注入「沒有回應」對話框、焦點跑掉、入口位置改變等情況，不需要模擬器。
# 驗證的是腳本的處理流程；真的系統對話框長怎樣、按鈕在哪裡，只有模擬器上真的出現時才看得到（Not tested）。
# 用法：bash tools/emulator/test-recover.sh（CI 的 Build APK 與 Emulator acceptance 都會跑）
set -u
cd "$(dirname "$0")"

failed=0
pass() { echo "ok    $1"; }
fail() { echo "FAIL  $1"; failed=1; }

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin"
# 假的 adb：狀態放在 $FAKE 的檔案裡
#   anr        還會出現幾次對話框（0＝沒有；按「關閉」或返回一次少一次；sticky 時關不掉）
#   focus      ours｜launcher（對話框關掉後焦點在哪）
#   after      對話框關掉後焦點變成什麼（ours｜launcher）
#   ui         ok｜fail（uiautomator 讀不讀得到對話框）
#   entry      入口的範圍 "x1 y1 x2 y2"
#   state      true｜false（點入口會切換）
#   dead       yes 時點入口沒反應（App 的問題，不是遮擋）
#   taps       點過的位置
cat >"$T/bin/adb" <<'EOF'
#!/usr/bin/env bash
F=$FAKE
get() { cat "$F/$1" 2>/dev/null; }
anr=$(get anr); anr=${anr:-0}
case "$*" in
  "shell dumpsys window")
    if [ "$anr" != 0 ]; then echo "  mCurrentFocus=Window{1 u0 Application Not Responding: com.google.android.apps.nexuslauncher}"
    elif [ "$(get focus)" = ours ]; then echo "  mCurrentFocus=Window{2 u0 com.patty60723.tearoff/com.patty60723.tearoff.MainActivity}"
    else echo "  mCurrentFocus=Window{3 u0 com.google.android.apps.nexuslauncher/.NexusLauncherActivity}"; fi
    echo "  mFocusedApp=ActivityRecord{x}" ;;
  "exec-out screencap -p") echo "PNG" ;;
  "logcat -d -b events") echo "10-30 am_anr: [0,123,com.google.android.apps.nexuslauncher,0,Input dispatching timed out]" ;;
  shell\ uiautomator*)
    [ "$(get ui)" = fail ] && exit 1
    echo '<node text="Close app" resource-id="android:id/aerr_close" class="android.widget.Button" bounds="[70,1180][1010,1306]" />' ;;
  "shell input keyevent KEYCODE_BACK")
    echo "back" >>"$F/taps"
    [ "$anr" != 0 ] && [ "$anr" != sticky ] && { echo $((anr - 1)) >"$F/anr"; [ $((anr - 1)) = 0 ] && cp "$F/after" "$F/focus"; } ;;
  shell\ input\ tap\ *)
    set -- $*; x=$4; y=$5
    echo "$x $y" >>"$F/taps"
    if [ "$anr" != 0 ]; then
      # 對話框擋著：只有「關閉應用程式」有用，其他點擊被對話框吃掉
      if [ "$x $y" = "540 1243" ] && [ "$anr" != sticky ]; then echo $((anr - 1)) >"$F/anr"; [ $((anr - 1)) = 0 ] && cp "$F/after" "$F/focus"; fi
      exit 0
    fi
    [ "$(get focus)" = ours ] || exit 0
    [ "$(get dead)" = yes ] && exit 0
    read -r x1 y1 x2 y2 <"$F/entry"
    if [ "$x" -ge "$x1" ] && [ "$x" -le "$x2" ] && [ "$y" -ge "$y1" ] && [ "$y" -le "$y2" ]; then
      [ "$(get state)" = true ] && echo false >"$F/state" || echo true >"$F/state"
    fi ;;
  shell\ am\ start*) echo ours >"$F/focus" ;;
  "shell dumpsys input") echo "  FocusedWindow: name='Window{2 u0 com.patty60723.tearoff/com.patty60723.tearoff.MainActivity}'" ;;
  *) echo "假的 adb 不認得：$*" >&2; exit 1 ;;
esac
EOF
chmod +x "$T/bin/adb"
export PATH="$T/bin:$PATH"

# 呼叫端要提供的東西
OUT="$T/out"; PKG=com.patty60723.tearoff
mkdir -p "$OUT/shots" "$OUT/ui"
LOGS=""; WARNS=0; SHOTS=0; SCROLLS=0
log() { LOGS="$LOGS$*"$'\n'; }
warn() { WARNS=$((WARNS + 1)); LOGS="${LOGS}WARN $*"$'\n'; }
shot() { SHOTS=$((SHOTS + 1)); }
dump_ui() { return 0; }
bounds_of() { [ "$(cat "$FAKE/visible" 2>/dev/null)" = no ] && return 0; cat "$FAKE/entry"; }
scroll_down() { SCROLLS=$((SCROLLS + 1)); echo yes >"$FAKE/visible"; }
RECOVER_WAIT=0 FOREGROUND_WAIT=0
. ./recover.sh
state() { cat "$FAKE/state"; }

# setup <anr> <after> <ui> [entry] [visible]
setup() {
  export FAKE="$T/fake-$RANDOM$RANDOM"; mkdir -p "$FAKE"
  echo "$1" >"$FAKE/anr"; echo "$2" >"$FAKE/after"; echo "$3" >"$FAKE/ui"
  echo ours >"$FAKE/focus"; echo true >"$FAKE/state"
  echo "${4:-395 893 572 1019}" >"$FAKE/entry"; echo "${5:-yes}" >"$FAKE/visible"
  [ "$1" != 0 ] && echo launcher >"$FAKE/focus"
  rm -f "$OUT"/shots/* "$OUT"/ui/*
  LOGS=""; WARNS=0; SHOTS=0; SCROLLS=0; ANR_N=0
}
taps() { tr '\n' ',' <"$FAKE/taps" 2>/dev/null; }
evidence() { ls "$OUT/shots" | grep -c '^anr-'; }

echo "== 沒有遮擋"
setup 0 ours ok
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 0 && $(state) = false && $WARNS = 0 && $(taps) = "483 956," ]] \
  && pass "點一次就切換，沒有 WARN（點 $(taps)）" || fail "rc=$rc state=$(state) warns=$WARNS taps=$(taps)"

echo "== 注入「沒有回應」對話框"
setup 1 ours ok
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 0 && $(state) = false && $(taps) = "540 1243,483 956," && $(evidence) = 1 && -s "$OUT/ui/anr-1-t-1.txt" ]] \
  && pass "先留證據、按「關閉應用程式」、再點入口：切換成功（點 $(taps)；證據 $(evidence) 份）" || fail "rc=$rc state=$(state) taps=$(taps) evidence=$(evidence)"
grep -q "am_anr" "$OUT/ui/anr-1-t-1.txt" && grep -q "Application Not Responding" "$OUT/ui/anr-1-t-1.txt" \
  && pass "證據裡有焦點視窗與 am_anr 紀錄" || fail "證據不完整：$(cat "$OUT/ui/anr-1-t-1.txt")"
grep -q "WARN 系統跳出其他 App 的「沒有回應」對話框（com.google.android.apps.nexuslauncher）：按「關閉應用程式」（540 1243）" <<<"$LOGS" \
  && pass "記成 WARN（不影響檢查本身）" || fail "沒有記 WARN：$LOGS"

echo "== 對話框關掉後焦點跑到桌面"
setup 1 launcher ok
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 0 && $(state) = false ]] && grep -q "App 不在最前面，帶回前景" <<<"$LOGS" \
  && pass "帶回前景再點，切換成功" || fail "rc=$rc state=$(state) logs=$LOGS"

echo "== 讀不到對話框的按鈕"
setup 1 ours fail
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 0 && $(taps) = "back,483 956," ]] && grep -q "找不到按鈕，按返回" <<<"$LOGS" \
  && pass "改按返回，再點入口" || fail "rc=$rc taps=$(taps)"

echo "== 對話框連續出現兩次"
setup 2 ours ok
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 0 && $(state) = false && $(evidence) = 2 ]] \
  && pass "兩次都留證據、都關掉，切換成功（證據 $(evidence) 份）" || fail "rc=$rc state=$(state) evidence=$(evidence) taps=$(taps)"

echo "== 入口不在畫面上（捲動位置變了）"
setup 0 ours ok "400 1500 560 1620" no
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 0 && $SCROLLS = 1 && $(taps) = "480 1560," ]] \
  && pass "捲動後重新量位置再點（點 $(taps)）" || fail "rc=$rc scrolls=$SCROLLS taps=$(taps)"

echo "== 對話框關不掉：維持失敗"
setup sticky ours ok
tap_toggle t "入口" story_bundle state; rc=$?
[[ $rc = 1 && $(state) = true ]] && grep -q "3 次都沒有切換" <<<"$LOGS" \
  && pass "3 次都被擋住 → 回傳 1（呼叫端照樣記 FAIL，不會變成 WARN 或通過）；證據 $(evidence) 份" || fail "rc=$rc state=$(state)"

echo "== 點了沒反應（不是遮擋）：維持失敗"
setup 0 ours ok
echo yes >"$FAKE/dead"
tap_toggle t "入口" story_bundle state; rc=$?
n=$(grep -c . <<<"$(taps | tr ',' '\n')")
[[ $rc = 1 && $(state) = true && $n = 3 ]] \
  && pass "點 3 次都沒切換 → 回傳 1" || fail "rc=$rc state=$(state) taps=$(taps)"
grep -q "FocusedWindow" "$OUT/ui/t-miss-3.txt" && grep -q "mCurrentFocus" "$OUT/ui/t-miss-3.txt" \
  && pass "每次沒反應都留下焦點與觸控視窗的紀錄" || fail "沒反應的紀錄不完整：$(cat "$OUT/ui/t-miss-3.txt" 2>&1)"

echo
[ "$failed" = 0 ] && echo "全部通過" || echo "有測試失敗"
exit "$failed"
