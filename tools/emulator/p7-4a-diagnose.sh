#!/usr/bin/env bash
# P7-4a 診斷（只在獨立的診斷 workflow 跑，不是驗收）：
#   對照組：一般情況點包袱入口（跟日常驗收第 4 段同樣的 tap_toggle 流程）。
#   人工注入·前景：回到桌面、暫停 Pixel Launcher 再點它 → 前景 ANR、顯示「沒有回應」對話框（#68／#79 的外觀），
#             再打開我們的 App、用同樣的流程（tap_toggle：關對話框 → 帶回前景 → 點）點包袱入口。
#   人工注入·背景：App 在前面時暫停 launcher → 手勢監聽收不到觸控而 ANR（不顯示對話框）。
# 兩組交錯進行；每次點擊前後都用 diag.sh 存快照（焦點、輸入派送、程序狀態、事件、主執行緒堆疊），
# 最後用 diag_summary.py 逐次分類。
#
# 標示：報告裡【對照】【人工注入】【自然發生】分開寫。人工注入產生的 launcher ANR 只是「同一種外觀的故障」，
# 不代表 #68／#79 的原因已經證實。
#
# 結束碼：診斷工具本身失敗（自我測試失敗、快照缺資料而判成 INSUFFICIENT、對照組沒通過）→ 1；
# 人工注入那組點不到是要觀察的結果，不算工具失敗。
# 用法：tools/emulator/p7-4a-diagnose.sh <APK> <輸出資料夾> [每組次數，預設 3]
set -u
APK=$1
OUT=$2
TRIALS=${3:-3}
PKG=com.patty60723.tearoff
LAUNCHER_PKG=com.google.android.apps.nexuslauncher
HERE=$(dirname "$0")
mkdir -p "$OUT/shots" "$OUT/ui" "$OUT/diag"
LOG="$OUT/report.txt"
: > "$LOG"
log() { echo "$*" | tee -a "$LOG"; }
warn() { log "WARN  $*"; }
TOOL_FAIL=0
tool_fail() { TOOL_FAIL=1; log "TOOL-FAIL  $*"; }

for t in test-quiet.sh test-recover.sh test-diag.sh; do
  if bash "$HERE/$t" > "$OUT/$t.txt" 2>&1; then log "腳本測試（$t）：全部通過"; else tool_fail "腳本測試（$t）失敗，見 $t.txt"; fi
done

# 只有診斷 workflow 用 root（取主執行緒堆疊、暫停 launcher）
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
[ "$(adb shell id -u | tr -d '\r')" = 0 ] && log "adb root：是" || tool_fail "adb root 失敗（取不到堆疊、無法注入）"

# InputDispatcher 的派送記錄（每個事件送到哪個 channel、有沒有處理）：系統服務啟動時才讀這個設定，
# 所以設好之後重新啟動 framework 一次（只在診斷 workflow）
adb shell setprop log.tag.InputDispatcherDispatchCycle DEBUG
adb shell setprop log.tag.InputDispatcherOutboundEvent DEBUG
adb shell stop; sleep 2; adb shell start
for _ in $(seq 1 90); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
for _ in $(seq 1 60); do [ -n "$(adb shell pidof com.google.android.apps.nexuslauncher | tr -d '\r')" ] && break; sleep 2; done
sleep 15
log "framework 重新啟動：$(adb shell getprop sys.boot_completed | tr -d '\r')（派送記錄設定 $(adb shell getprop log.tag.InputDispatcherDispatchCycle | tr -d '\r')）"

adb install -r "$APK" >/dev/null || { tool_fail "安裝 APK 失敗"; exit 1; }
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put secure anr_show_background 0
SIZE=$(adb shell wm size | tr -d '\r' | awk '{print $NF}')
W=${SIZE%x*}
H=${SIZE#*x}
TODAY=$(adb shell date +%F | tr -d '\r')
YESTERDAY=$(date -d "$TODAY -1 day" +%F)
log "裝置：$(adb shell getprop ro.product.model | tr -d '\r')，API $(adb shell getprop ro.build.version.sdk | tr -d '\r')，螢幕 $SIZE；每組 $TRIALS 次"

# ---- 跟 w7-acceptance.sh 相同的準備動作（複製，不改原本的腳本）----
story_text() {
  python3 - "$1" <<'EOF'
import json, sys, html
p = int(sys.argv[1])
beats = json.load(open("app/src/main/assets/story/chapter1.json"))["beats"]
b = next(b for b in beats if b.get("when", {}).get("page") == p)
print(html.escape(b["text"].replace("{name}", "墨丸")))
EOF
}
write_prefs() {
  printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n%s\n</map>\n" "$2" |
    adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/$1.xml'"
}
write_state() {
  write_prefs story "  <int name=\"page\" value=\"14\" />
  <string name=\"lastTornDate\">$YESTERDAY</string>
  <string name=\"lastText\">$(story_text 14)</string>"
  write_prefs mowan "  <boolean name=\"paperHatOn\" value=\"true\" />
  <boolean name=\"bundleOn\" value=\"true\" />"
  write_prefs weather "  <string name=\"fakeMood\">SUNNY</string>"
}
scroll_down() {
  for _ in 1 2 3 4; do
    adb shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 20 / 100)) 250
    sleep 0.4
  done
  sleep 1
}
dump_ui() {
  adb shell dumpsys activity top > "$OUT/ui/$1.txt" 2>/dev/null
  grep -q "story_mowan" "$OUT/ui/$1.txt" || { log "  （$1：dumpsys 找不到故事卡）"; return 1; }
}
bounds_of() {
  local r
  r=$(python3 "$HERE/view_bounds.py" "$OUT/ui/$1.txt" "$2" 2>/dev/null)
  [ "${r##* }" = V ] && echo "${r% *}"
}
shot() { adb exec-out screencap -p > "$OUT/shots/$1-full.png"; }
bundle_on() { adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | grep -o 'name="bundleOn" value="[a-z]*"' | grep -o 'true\|false'; }

. "$HERE/recover.sh"
DIAG_ROOT=1
DIAG_ALWAYS=1
DIAG_TARGET_ID=story_bundle
. "$HERE/diag.sh"

launcher_pid() { adb shell pidof $LAUNCHER_PKG 2>/dev/null | tr -d '\r' | awk '{print $1}'; }
wait_launcher() { # launcher 有在執行而且沒有被暫停（最多 60 秒）
  local p st
  for _ in $(seq 1 30); do
    p=$(launcher_pid)
    if [ -n "$p" ]; then
      st=$(adb shell ps -o STAT -p "$p" 2>/dev/null | tr -d '\r' | tail -1)
      case "$st" in T*) ;; *) return 0 ;; esac
    fi
    sleep 2
  done
  return 1
}

declare -a RESULTS
trial() { # trial <control|inject-bg|inject-fg> <i>
  local kind=$1 i=$2 name="$1-$2" label lp ok anr_seen=0
  case "$kind" in
    control) label="【對照】" ;;
    inject-bg) label="【人工注入·背景】" ;;
    inject-fg) label="【人工注入·前景】" ;;
    inject-fg-old) label="【人工注入·前景·舊流程】" ;;
  esac
  log ""
  log "== $label $name =="
  wait_launcher || warn "$name：launcher 沒有在正常執行"
  adb logcat -b events -c; adb logcat -c
  adb shell am force-stop $PKG
  write_state
  case "$kind" in
    inject-bg)
      # launcher 在後面時暫停它：它的手勢監聽收不到觸控而 ANR（系統當成背景 ANR，不顯示對話框）
      adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2
      lp=$(launcher_pid)
      log "  注入（背景）：App 在前面時暫停 launcher（pid $lp）→ 關掉 App → 對 launcher 送按鍵"
      adb shell "kill -STOP $lp"
      adb shell am force-stop $PKG
      sleep 1
      timeout 15 adb shell input keyevent KEYCODE_SPACE >/dev/null 2>&1 &
      ;;
    inject-fg|inject-fg-old)
      # launcher 在前面時暫停它，再點它：前景 ANR，系統顯示「沒有回應」對話框（#68／#79 看到的外觀）
      lp=$(launcher_pid)
      adb shell input keyevent KEYCODE_HOME; sleep 3
      log "  注入（前景）：回到桌面、暫停 launcher（pid $lp）→ 點桌面"
      adb shell "kill -STOP $lp"
      timeout 15 adb shell input tap $((W / 2)) $((H / 2)) >/dev/null 2>&1 &
      ;;
  esac
  if [ "$kind" != control ]; then
    for _ in $(seq 1 20); do anr_focus >/dev/null && { anr_seen=1; break; }; sleep 2; done
    [ "$anr_seen" = 1 ] && log "  注入：出現「沒有回應」對話框（$(anr_focus | sed 's/.*: //')）" || log "  注入：40 秒內沒有出現「沒有回應」對話框"
    diag_snapshot "$name-injected"
  fi
  # 打開 App（自然發生時，對話框在第 4 段開頭已經在，App 照樣打開；對話框可能還蓋在上面）
  adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2
  scroll_down
  diag_snapshot "$name-start"
  # 舊流程（RECOVER_RESCROLL=0：關掉對話框後不重新捲動）只在對照的「舊流程」組用，證明修正是不是原因
  local rescroll=1
  [ "$kind" = inject-fg-old ] && rescroll=0
  if RECOVER_RESCROLL=$rescroll tap_toggle "$name" "$label$name 包袱入口" story_bundle bundle_on; then ok=1; else ok=0; fi
  # 對照組也可能自然遇到 launcher 的對話框：tap_toggle 裡處理過就記下來
  if [ "$kind" = control ] && ls "$OUT/diag" | grep -q -- "-$name-.*anr-seen"; then
    log "  【自然發生】對照組 $name 遇到「沒有回應」對話框"
  fi
  RESULTS+=("$kind $i toggled=$ok dialog=$anr_seen")
  log "  結果：$label$name 包袱切換 $([ $ok = 1 ] && echo 成功 || echo 失敗)"
  # 收尾：launcher 還被暫停就恢復；對話框還在就按返回；等 launcher 重新可以用
  lp=$(launcher_pid)
  [ -n "$lp" ] && adb shell "kill -CONT $lp" 2>/dev/null
  anr_focus >/dev/null && { adb shell input keyevent KEYCODE_BACK; sleep 2; }
  wait_launcher || warn "$name 之後 launcher 沒有恢復"
}

for i in $(seq 1 "$TRIALS"); do
  trial control "$i"
  trial inject-fg-old "$i"
  trial inject-fg "$i"
  trial inject-bg "$i"
done
n=$(grep -l "startDispatchCycle" "$OUT"/diag/*/dispatch.txt 2>/dev/null | wc -l)
log ""
log "派送記錄：$n 個快照裡有 InputDispatcher 的派送記錄（0 表示這個系統映像不支援用設定打開，分類只能靠焦點、視窗與佇列）"

log ""
log "== 每次點擊的分類（diag_summary.py classify-all）=="
python3 "$HERE/diag_summary.py" classify-all "$OUT/diag" | tee "$OUT/diag/classify.txt" | sed 's/^/  /' | tee -a "$LOG"
log ""
log "== 結果 =="
for r in "${RESULTS[@]}"; do log "  $r"; done
grep -q "verdict=INSUFFICIENT" "$OUT/diag/classify.txt" && tool_fail "有點擊的證據不足（INSUFFICIENT）：診斷工具沒收到需要的資料"
[ -s "$OUT/diag/classify.txt" ] || tool_fail "沒有任何點擊被分類"
for r in "${RESULTS[@]}"; do
  case "$r" in "control "*"toggled=0"*) tool_fail "對照組沒有通過：$r（一般情況就點不到，先查這個）" ;; esac
done
log "診斷工具：$([ $TOOL_FAIL = 0 ] && echo 正常 || echo 有失敗)"
exit $TOOL_FAIL
