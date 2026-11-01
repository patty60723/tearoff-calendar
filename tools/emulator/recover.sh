# 模擬器腳本：點擊前的遮擋處理（P7-1）。w7-acceptance.sh 用 source 載入；tools/emulator/test-recover.sh 用假的 adb 測。
# 呼叫端要提供 log、warn、shot、dump_ui、bounds_of、scroll_down，以及 OUT、PKG。
#
# - 系統的「沒有回應」對話框（其他 App 的 ANR，模擬器上常是 Pixel Launcher）：先留證據（截圖、焦點視窗、am_anr 事件），
#   再按對話框的「關閉應用程式」；按不到才按返回（＝等待，可能再跳出來）。
# - 我們的 App 不在最前面就帶回來（不重開，狀態不變）。
# - 每次點之前重新量元件位置（捲動位置可能變了）。
# - 最多點 3 次；3 次都沒切換就回傳 1，由呼叫端照樣記 FAIL。這些處理只排除遮擋，不會讓任何檢查變成通過。

RECOVER_WAIT=${RECOVER_WAIT:-1.5}
FOREGROUND_WAIT=${FOREGROUND_WAIT:-2}
ANR_N=0

anr_focus() { adb shell dumpsys window | tr -d '\r' | grep -o "mCurrentFocus=.*Application Not Responding[^}]*"; }

handle_anr() { # handle_anr <情境>：有對話框而且處理了回傳 0
  local focus tag xy how
  focus=$(anr_focus) || return 1
  ANR_N=$((ANR_N + 1))
  tag="anr-$ANR_N-$1"
  adb exec-out screencap -p > "$OUT/shots/$tag.png"
  { echo "$focus"; adb shell dumpsys window | tr -d '\r' | grep -E "mFocusedApp"; adb logcat -d -b events | grep am_anr | tail -3; } > "$OUT/ui/$tag.txt" 2>&1
  xy=$(timeout 20 adb shell "uiautomator dump /sdcard/anr.xml >/dev/null 2>&1 && cat /sdcard/anr.xml" 2>/dev/null | python3 -c '
import re, sys
m = re.search(r"resource-id=\"android:id/aerr_close\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", sys.stdin.read())
m and print((int(m[1]) + int(m[3])) // 2, (int(m[2]) + int(m[4])) // 2)')
  if [ -n "$xy" ]; then adb shell input tap $xy; how="按「關閉應用程式」（$xy）"; else adb shell input keyevent KEYCODE_BACK; how="找不到按鈕，按返回"; fi
  sleep "$RECOVER_WAIT"
  anr_focus >/dev/null && how="$how，還在"
  warn "系統跳出其他 App 的「沒有回應」對話框（${focus##*: }）：$how；證據 shots/$tag.png、ui/$tag.txt"
}

ensure_foreground() {
  adb shell dumpsys window | tr -d '\r' | grep "mCurrentFocus=" | grep -q "$PKG" && return 0
  log "  App 不在最前面，帶回前景"
  adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep "$FOREGROUND_WAIT"
}

locate() { # locate <dump 名稱> <元件 id>：量看得到的元件中心，設定 TAP_X TAP_Y；看不到時捲動再量一次
  local r
  dump_ui "$1" || return 1
  r=$(bounds_of "$1" "$2")
  if [ -z "$r" ]; then scroll_down; dump_ui "$1" || return 1; r=$(bounds_of "$1" "$2"); fi
  [ -n "$r" ] || return 1
  set -- $r
  TAP_X=$(( ($1 + $3) / 2 )); TAP_Y=$(( ($2 + $4) / 2 ))
}

tap_toggle() { # tap_toggle <檔名用的名稱> <記錄用的名稱> <元件 id> <讀狀態的指令>：狀態有變回傳 0；3 次都沒變回傳 1
  local name=$1 label=$2 id=$3 get=$4 before after try
  before=$($get)
  after=$before
  for try in 1 2 3; do
    handle_anr "$name-$try"
    ensure_foreground
    if ! locate "$name-tap-$try" "$id"; then
      warn "$label第 $try 次：找不到看得到的元件"; shot "$name-miss-$try"; continue
    fi
    handle_anr "$name-$try-before-tap"
    adb shell input tap $TAP_X $TAP_Y; sleep "$RECOVER_WAIT"
    after=$($get)
    if [ "$before" != "$after" ]; then log "  $label：$before → $after（第 $try 次點擊，位置 $TAP_X $TAP_Y）"; return 0; fi
    warn "$label第 $try 次沒有反應（$before，位置 $TAP_X $TAP_Y），重新確認畫面再點；證據 shots/$name-miss-$try、ui/$name-miss-$try.txt"
    shot "$name-miss-$try"
    # 點了沒反應時，誰拿到焦點、觸控送到哪個視窗（#68：關掉 launcher 的對話框後 9 下都沒反應，原因不明）
    { adb shell dumpsys window | tr -d '\r' | grep -E "mCurrentFocus|mFocusedApp"; adb shell dumpsys input | tr -d '\r' | grep -iE "FocusedWindow|TouchedWindow|touchedWindows|name='" | head -40; } > "$OUT/ui/$name-miss-$try.txt" 2>&1
  done
  log "  $label：$before → $after（3 次都沒有切換）"
  return 1
}
