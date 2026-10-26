#!/usr/bin/env bash
# W7 驗收：在 Android Emulator 上實際打開 App，截圖、錄影、量重畫的影格數。
# 用法：tools/emulator/w7-acceptance.sh <APK> <輸出資料夾>
# 狀態（頁數、假天氣、故事卡用 V3、包袱）直接寫進 App 的 SharedPreferences（debug 版才能 run-as）。
set -u
APK=$1
OUT=$2
PKG=com.patty60723.tearoff
mkdir -p "$OUT/shots" "$OUT/video" "$OUT/ui"
LOG="$OUT/report.txt"
: > "$LOG"
log() { echo "$*" | tee -a "$LOG"; }

# 必要的驗收項目用 check：任何一項 FAIL，腳本最後就以非零結束（CI 會失敗）。
# 截圖、錄影、GIF 這類素材出問題只用 warn 記下來，不算功能失敗。
PASS_N=0
FAIL_N=0
WARN_N=0
# 必要檢查的總數；少跑了（例如某段直接跳過）也算失敗
EXPECTED_CHECKS=34
check() { # check <名稱> <條件結果 0/1> <說明>
  if [ "$2" = 0 ]; then PASS_N=$((PASS_N + 1)); log "PASS  $1 — $3"
  else FAIL_N=$((FAIL_N + 1)); log "FAIL  $1 — $3"; fi
}
warn() { WARN_N=$((WARN_N + 1)); log "WARN  $*"; }
finish() {
  log ""
  local ran=$((PASS_N + FAIL_N))
  [ "$ran" -ge "$EXPECTED_CHECKS" ] || { FAIL_N=$((FAIL_N + 1)); log "FAIL  必要檢查只跑了 $ran 項（應該 $EXPECTED_CHECKS 項）"; }
  log "結果：PASS $PASS_N、FAIL $FAIL_N、WARN $WARN_N（WARN 是素材問題，不影響結果）"
  [ "$FAIL_N" = 0 ] && exit 0 || exit 1
}

adb install -r "$APK" >/dev/null || { check "安裝 APK" 1 "$APK"; finish; }
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
# 模擬器剛開機時其他 App（例如 Pixel Launcher）常常 ANR，對話框會蓋住我們的 App、吃掉點擊；不顯示背景 ANR
adb shell settings put secure anr_show_background 0
adb logcat -c

SIZE=$(adb shell wm size | tr -d '\r' | awk '{print $NF}')
W=${SIZE%x*}
H=${SIZE#*x}
TODAY=$(adb shell date +%F | tr -d '\r')
YESTERDAY=$(date -d "$TODAY -1 day" +%F)
log "裝置：$(adb shell getprop ro.product.model | tr -d '\r')，Android $(adb shell getprop ro.build.version.release | tr -d '\r')（API $(adb shell getprop ro.build.version.sdk | tr -d '\r')），螢幕 $SIZE，密度 $(adb shell wm density | tr -d '\r' | awk '{print $NF}')"

story_text() { # 第 N 頁的故事（名字用預設的「墨丸」）
  python3 - "$1" <<'EOF'
import json, sys, html
p = int(sys.argv[1])
beats = json.load(open("app/src/main/assets/story/chapter1.json"))["beats"]
b = next(b for b in beats if b.get("when", {}).get("page") == p)
print(html.escape(b["text"].replace("{name}", "墨丸")))
EOF
}

write_prefs() { # write_prefs <檔名> <內容>
  printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n%s\n</map>\n" "$2" |
    adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/$1.xml'"
}

# setup <頁數> <天氣> [包袱 true/false]：關掉 App、寫入狀態、重新打開、捲到故事卡
setup() {
  adb shell am force-stop $PKG
  write_prefs story "  <int name=\"page\" value=\"$1\" />
  <string name=\"lastTornDate\">$YESTERDAY</string>
  <string name=\"lastText\">$(story_text "$1")</string>"
  write_prefs mowan "  <boolean name=\"storyCard\" value=\"true\" />
  <boolean name=\"paperHatOn\" value=\"true\" />
  <boolean name=\"bundleOn\" value=\"${3:-true}\" />"
  write_prefs weather "  <string name=\"fakeMood\">$2</string>"
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 2
  scroll_down
}

scroll_down() {
  for _ in 1 2 3 4; do
    adb shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 20 / 100)) 250
    sleep 0.4
  done
  sleep 1
}

# 畫面上元件的位置：用 dumpsys activity top 的 View 階層算（uiautomator 在畫面持續重畫時 dump 不出來）
dump_ui() { # dump_ui <名稱>
  adb shell dumpsys activity top > "$OUT/ui/$1.txt" 2>/dev/null
  grep -q "story_mowan" "$OUT/ui/$1.txt" || { log "  （$1：dumpsys 找不到故事卡）"; return 1; }
}
bounds_of() { # bounds_of <名稱> <id>：輸出 "x1 y1 x2 y2"（看得到的才輸出）
  local r
  r=$(python3 tools/emulator/view_bounds.py "$OUT/ui/$1.txt" "$2" 2>/dev/null)
  [ "${r##* }" = V ] && echo "${r% *}"
}

# 故事卡的範圍（墨丸上緣到包袱入口或故事文字下緣）
CARD=""
card_region() { # card_region <dump 的名稱>
  local m b t
  m=$(bounds_of "$1" story_mowan); t=$(bounds_of "$1" story_text); b=$(bounds_of "$1" story_bundle)
  [ -n "$m" ] || return 1
  set -- $m
  local top=$(( $2 - 60 )) bottom=$4
  for r in "$t" "$b"; do [ -n "$r" ] && set -- $r && [ "$4" -gt "$bottom" ] && bottom=$4; done
  bottom=$(( bottom + 40 )); [ "$top" -lt 0 ] && top=0; [ "$bottom" -gt "$H" ] && bottom=$H
  CARD="${W}x$(( bottom - top ))+0+$top"
}

shot() { # shot <名稱>：整個畫面，加上裁切後的故事卡
  adb exec-out screencap -p > "$OUT/shots/$1-full.png"
  [ -s "$OUT/shots/$1-full.png" ] || warn "截圖 $1 是空的"
  crop "$1"
}
crop() {
  [ -n "$CARD" ] || { warn "截圖 $1 沒有裁切（找不到故事卡位置）"; return 0; }
  convert "$OUT/shots/$1-full.png" -crop "$CARD" +repage "$OUT/shots/$1.png" || warn "截圖 $1 裁切失敗"
}

frames_in() { # 這段時間 App 畫了幾張（gfxinfo），印出 "總數 jank"
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  sleep "$1"
  adb shell dumpsys gfxinfo $PKG > "$OUT/ui/gfx-$2.txt"
  local total jank
  total=$(grep -m1 "Total frames rendered" "$OUT/ui/gfx-$2.txt" | awk '{print $NF}')
  jank=$(grep -m1 "Janky frames:" "$OUT/ui/gfx-$2.txt" | sed 's/.*Janky frames: //')
  echo "${total:-?} ${jank:-?}"
}

record() { # record <名稱> <秒數> [GIF 的 FPS] [GIF 寬度]：錄影，裁成故事卡，轉成 GIF
  adb shell screenrecord --time-limit "$2" /sdcard/rec.mp4
  adb pull /sdcard/rec.mp4 "$OUT/video/$1-full.mp4" >/dev/null 2>&1
  to_gif "$1" "${3:-12}" "${4:-480}"
}
to_gif() {
  [ -s "$OUT/video/$1-full.mp4" ] || { warn "錄影 $1 沒有錄到"; return 0; }
  [ -n "$CARD" ] || { warn "錄影 $1 沒有裁切成 GIF（找不到故事卡位置）"; return 0; }
  local w h x y fps=${2:-12} gw=${3:-480}
  w=${CARD%%x*}; h=${CARD#*x}; h=${h%%+*}; y=${CARD##*+}; x=0
  ffmpeg -loglevel error -y -i "$OUT/video/$1-full.mp4" -vf "crop=$w:$h:$x:$y,fps=$fps,scale=$gw:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=bayer" -loop 0 "$OUT/video/$1.gif"
  ffmpeg -loglevel error -y -i "$OUT/video/$1-full.mp4" -vf "crop=$w:$h:$x:$y" -c:v libx264 -pix_fmt yuv420p -an "$OUT/video/$1.mp4" &&
    rm -f "$OUT/video/$1-full.mp4" || warn "錄影 $1 轉檔失敗（保留原始影片）"
}

crashed() { adb logcat -d | grep -q "FATAL EXCEPTION" ; }
dismiss_anr() { # 其他 App 的「沒有回應」對話框還是跳出來的話按返回（等於「等待」），回傳 0 表示有關掉
  adb shell dumpsys window | grep -q "mCurrentFocus=.*Application Not Responding" || return 1
  adb shell input keyevent KEYCODE_BACK; sleep 1
  warn "系統跳出其他 App 的「沒有回應」對話框，已關掉（不是我們的 App）"
}

log ""
log "== 1. 8 種天氣（第 14 頁）=="
setup 14 SUNNY
log "  寫入的設定：$(adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | tr '\n' ' ')"
dump_ui layout-14 && card_region layout-14
[ -n "$CARD" ] && log "  故事卡範圍：$CARD" || warn "找不到故事卡位置，截圖和錄影不會裁切"
for m in SUNNY HOT CLOUDY FOG RAIN THUNDER COLD SNOW; do
  setup 14 $m
  shot "weather-$m"
done
check "8 種天氣都顯示" "$(crashed && echo 1 || echo 0)" "逐一切換、重開 App，沒有閃退"

log ""
log "== 2. 動畫錄影 =="
setup 14 SUNNY;   record sunny 12
setup 14 COLD;    record cold 9
setup 14 RAIN;    record rain 9
setup 14 HOT;     record hot 7
setup 14 CLOUDY;  record cloudy 7
setup 14 FOG;     record fog 7
setup 14 SNOW;    record snow 9
# 雷雨：第一次打雷在 15–40 秒之間，錄 45 秒一定錄得到
setup 14 THUNDER; record thunder 45 8 360

log ""
log "== 3. 成長頁數（晴天）=="
CARD14=$CARD
for p in 1 3 4 5 8 13 14; do
  setup $p SUNNY
  adb exec-out screencap -p > "$OUT/shots/page-$p-full.png"
  if dump_ui "page-$p"; then
    card_region "page-$p" || CARD=$CARD14
    crop "page-$p"
    b=$(bounds_of "page-$p" story_bundle)
    if [ "$p" = 14 ]; then check "第 $p 頁包袱入口" "$([ -n "$b" ] && echo 0 || echo 1)" "應該出現（$b）"
    else check "第 $p 頁包袱入口" "$([ -z "$b" ] && echo 0 || echo 1)" "不應該出現"; fi
  else
    check "第 $p 頁包袱入口" 1 "讀不到畫面結構，無法檢查"
  fi
done
CARD=$CARD14

log ""
log "== 4. 包袱入口：點擊、狀態保存、畫面更新 =="
setup 14 SUNNY true
# 位置要在點之前、同一次開 App 時量：重開 App 後捲動的位置可能不一樣（P5-1 遇過三下都沒點到）。
# 量兩次，捲動停下來了位置才會一樣。dump_ui 會暫時關動畫，量完打開後墨丸會自己恢復（W7 修正）。
dump_ui bundle-before; sleep 2; dump_ui bundle-before
b=$(bounds_of "bundle-before" story_bundle)
if [ -n "$b" ]; then
  set -- $b
  bx=$(( ($1 + $3) / 2 )); by=$(( ($2 + $4) / 2 ))
  log "  入口位置 $b（高 $(( $4 - $2 )) px = $(( ($4 - $2) * 160 / 420 )) dp）"
  sleep 1
  shot bundle-1-on
  # 每點一下就看設定有沒有切換。沒反應時記 WARN 再點一次（模擬器偶爾吃掉點擊：P5-2 有兩次三下後狀態沒變，
  # 同一份 App 其他次都通過）；再點還是沒切換才算失敗。
  bundle_on() { adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | grep -o 'name="bundleOn" value="[a-z]*"' | grep -o 'true\|false'; }
  tap_bundle() { # tap_bundle <第幾下>：切換成功回傳 0
    local before after
    before=$(bundle_on)
    dismiss_anr
    adb shell input tap $bx $by; sleep 1.5
    after=$(bundle_on)
    if [ "$before" = "$after" ]; then
      warn "包袱入口第 $1 下沒有反應（$before），再點一次"
      dismiss_anr
      adb shell input tap $bx $by; sleep 1.5
      after=$(bundle_on)
    fi
    log "  第 $1 下：$before → $after"
    [ "$before" != "$after" ]
  }
  # 錄影：卸下、背上、再卸下；伸懶腰不應該重來
  adb shell screenrecord --time-limit 16 /sdcard/rec.mp4 &
  REC=$!
  toggled=0
  for k in 1 2 3; do sleep 1.5; tap_bundle $k && toggled=$((toggled + 1)); done
  wait $REC
  check "包袱入口每一下都切換" "$([ "$toggled" = 3 ] && echo 0 || echo 1)" "3 下裡切換了 $toggled 下"
  adb pull /sdcard/rec.mp4 "$OUT/video/bundle-toggle-full.mp4" >/dev/null 2>&1
  to_gif bundle-toggle
  check "包袱點擊不閃退" "$(crashed && echo 1 || echo 0)" "點三下"
  shot bundle-2-off
  dump_ui bundle-after
  log "  點完的文字看截圖 bundle-2-off（dumpsys 沒有文字內容）"
  stored=$(adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | grep bundleOn)
  check "包袱狀態存起來" "$(echo "$stored" | grep -q 'value="false"' && echo 0 || echo 1)" "$stored"
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 2; scroll_down
  shot bundle-3-restart
  dump_ui bundle-restart
  stored=$(adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | grep bundleOn)
  check "重開 App 還是卸下" "$(echo "$stored" | grep -q 'value="false"' && echo 0 || echo 1)" "$stored；畫面看截圖 bundle-3-restart"
else
  check "找到包袱入口" 1 "dumpsys 沒找到看得到的 story_bundle"
fi

log ""
log "== 5. 重畫次數（gfxinfo，10 秒；模擬器是軟體繪圖，張數與 jank 只能比較，不代表實機）=="
setup 14 SUNNY;   log "  晴天動畫：$(frames_in 10 sunny)（總張數 jank）"
setup 14 THUNDER; log "  雷雨動畫：$(frames_in 10 thunder)"
# 沒有天氣資料：關掉網路、清掉存著的天氣
adb shell svc wifi disable; adb shell svc data disable
adb shell am force-stop $PKG
write_prefs weather ""
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2; scroll_down
log "  沒有天氣（一般站姿，只有筆觸 6 FPS）：$(frames_in 10 none)"
adb shell svc wifi enable; adb shell svc data enable
adb shell top -b -n 1 -o PID,%CPU,RES,NAME | grep -E "PID|$PKG" | tee -a "$LOG"

log ""
log "== 6. Reduced Motion（系統「移除動畫」）=="
# 實際使用時要到設定裡改，App 會先到背景
setup 14 SUNNY
adb shell input keyevent KEYCODE_HOME
adb shell settings put global animator_duration_scale 0
sleep 3
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 2
shot reduced-1
sleep 3
shot reduced-2
diff=$(compare -metric AE "$OUT/shots/reduced-1.png" "$OUT/shots/reduced-2.png" null: 2>&1 | awk '{print $1}')
check "關閉動畫時故事卡不動" "$([ "${diff%.*}" = 0 ] && echo 0 || echo 1)" "相隔 3 秒的兩張故事卡差 $diff 個像素"
f=$(frames_in 10 reduced)
# P5-1：日曆頁的 ViewFlipper 關閉動畫時也停住，整個 App 應該幾乎不重畫（W7 M7）
check "關閉動畫時整個 App 幾乎不重畫" "$([ "${f%% *}" -le 10 ] 2>/dev/null && echo 0 || echo 1)" "V3 故事卡：10 秒內畫了 $f 張"
for m in HOT CLOUDY FOG RAIN THUNDER COLD SNOW; do
  setup 14 $m
  sleep 1
  shot "reduced-$m"
done
# 對照組：舊火柴人故事卡也關閉動畫
adb shell am force-stop $PKG
write_prefs mowan "  <boolean name=\"storyCard\" value=\"false\" />"
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2; scroll_down
g=$(frames_in 10 reduced-stickman)
log "  INFO  關閉動畫、舊火柴人故事卡（對照組）：10 秒內畫了 $g 張"
check "關閉動畫時 V3 不比舊火柴人多畫" "$([ "${f%% *}" -le $(( ${g%% *} + 3 )) ] 2>/dev/null && echo 0 || echo 1)" "V3 ${f%% *} 張、舊火柴人 ${g%% *} 張"
# 從背景回來時打開動畫
setup 14 SUNNY
adb shell input keyevent KEYCODE_HOME
adb shell settings put global animator_duration_scale 1
sleep 3
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 1
f=$(frames_in 5 reduced-back)
check "打開動畫後繼續動（從背景回來）" "$([ "${f%% *}" -ge 60 ] 2>/dev/null && echo 0 || echo 1)" "5 秒內畫了 $f 張"
record reduced-back 4
# 留在前景直接改設定（例如分割畫面）
adb shell settings put global animator_duration_scale 0
sleep 3
adb shell settings put global animator_duration_scale 1
sleep 2
f=$(frames_in 5 reduced-live)
check "留在前景、關掉又打開動畫後繼續動" "$([ "${f%% *}" -ge 60 ] 2>/dev/null && echo 0 || echo 1)" "之後 5 秒畫了 $f 張（Android 13 以上）"
record reduced-live 4

log ""
log "== 7. 背景／前景（雷雨）=="
setup 14 THUNDER
adb shell input keyevent KEYCODE_HOME
sleep 45
adb shell am start -W -n $PKG/.MainActivity >/dev/null
record thunder-back 4
check "背景 45 秒後回來不閃退" "$(crashed && echo 1 || echo 0)" "錄了回來後的 4 秒（錯過的雷不補播，除非剛好輪到下一次）"

log ""
log "== 8. 螢幕密度與字體大小 =="
for d in 320 420 560; do
  for fs in 1.0 1.3 2.0; do
    [ "$d" != 420 ] && [ "$fs" != 1.0 ] && continue
    adb shell wm density $d
    adb shell settings put system font_scale $fs
    setup 14 SUNNY
    CARD_SAVE=$CARD
    adb exec-out screencap -p > "$OUT/shots/density-$d-font-$fs-full.png"
    if dump_ui "density-$d-font-$fs" && card_region "density-$d-font-$fs"; then
      crop "density-$d-font-$fs"
      b=$(bounds_of "density-$d-font-$fs" story_bundle)
      t=$(bounds_of "density-$d-font-$fs" story_text)
      [ -n "$b" ] && [ -n "$t" ] || { check "密度 $d、字體 $fs 的包袱入口" 1 "找不到入口或故事文字"; CARD=$CARD_SAVE; continue; }
      set -- $b; bh=$(( $4 - $2 )); set -- $t; tb=$4; set -- $b
      ok=$([ "$bh" -ge $(( 48 * d / 160 - 1 )) ] && [ "$2" -ge "$tb" ] && [ "$3" -le "$W" ] && echo 0 || echo 1)
      check "密度 $d、字體 $fs 的包袱入口" "$ok" "入口 $b（高 $bh px），故事文字下緣 $tb"
    else
      check "密度 $d、字體 $fs 的包袱入口" 1 "讀不到畫面結構，無法檢查"
    fi
    CARD=$CARD_SAVE
  done
done
adb shell wm density reset
adb shell settings put system font_scale 1.0

log ""
log "== 9. 舊火柴人故事卡 =="
adb shell am force-stop $PKG
write_prefs mowan "  <boolean name=\"storyCard\" value=\"false\" />"
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2; scroll_down
shot stickman
dump_ui stickman
check "舊火柴人沒有包袱入口" "$([ -f "$OUT/ui/stickman.txt" ] && [ -z "$(bounds_of stickman story_bundle)" ] && echo 0 || echo 1)" ""

log ""
log "== 10. 小工具（P5-4）=="
# 狀態：關掉 App、寫設定（關掉才不會被 App 記憶體裡的舊設定蓋掉）
state() { # state <頁數> <天氣> <V3 true/false> [包袱] [紙帽]
  adb shell am force-stop $PKG
  write_prefs story "  <int name=\"page\" value=\"$1\" />
  <string name=\"lastTornDate\">$YESTERDAY</string>
  <string name=\"lastText\">$(story_text "$1")</string>"
  write_prefs mowan "  <boolean name=\"storyCard\" value=\"$3\" />
  <boolean name=\"paperHatOn\" value=\"${5:-true}\" />
  <boolean name=\"bundleOn\" value=\"${4:-true}\" />"
  write_prefs weather "  <string name=\"fakeMood\">$2</string>"
}
# 測試用宿主（debug APK 的 WidgetHostActivity）：用系統的 AppWidgetHost 掛 5 個不同大小的小工具
host_run() { # host_run <名稱>：開宿主、截圖、把這次的報告存到 ui/widget-host-<名稱>.txt
  adb shell log -t W7HOST "mark $1"
  adb shell am start -W -n $PKG/.WidgetHostActivity >/dev/null
  sleep 7
  adb exec-out screencap -p > "$OUT/shots/widget-host-$1.png"
  adb logcat -d -s W7HOST:I | awk -v m="mark $1" 'index($0, m) { buf = ""; next } { buf = buf $0 "\n" } END { printf "%s", buf }' > "$OUT/ui/widget-host-$1.txt"
}
host_line() { grep "report layout=$2 " "$OUT/ui/widget-host-$1.txt" | tail -1; }

if adb shell appwidget grantbind --package $PKG --user 0 >/dev/null 2>&1; then
  # V3：5 種大小都套用成功、ViewFlipper 隱藏、靜態圖顯示、角色整個在小工具裡
  state 14 RAIN true
  host_run v3
  for L in COMPACT WIDE MEDIUM TALL BETWEEN; do
    r=$(host_line v3 $L)
    ok=$(echo "$r" | grep -q "ok=true" && echo "$r" | grep -q "flipper=GONE" && echo "$r" | grep -q "still=VISIBLE stillImage=true" && echo "$r" | grep -q "characterInside=true" && echo 0 || echo 1)
    check "小工具 $L（V3）" "$ok" "${r#*report }"
  done
  # 綁定後的幾秒內還會陸續收到 onUpdate／尺寸變更（每次都重新套用一次），模擬器的 GPU 也偶爾卡住後補畫；
  # 所以先量一段只記錄，再量第二段（綁定後約 17 秒起）當作穩定狀態檢查。會輪播的話兩段都會很多（舊火柴人約 170 張）。
  f0=$(frames_in 10 widget-v3-settle)
  log "  INFO  V3 小工具剛綁定後 10 秒：畫了 $f0 張（還在收更新，只記錄）"
  f=$(frames_in 10 widget-v3)
  check "V3 小工具真的靜止（宿主畫面不重畫）" "$([ "${f%% *}" -le 5 ] 2>/dev/null && echo 0 || echo 1)" "穩定後 10 秒內畫了 $f 張"
  # 舊火柴人：ViewFlipper 顯示、V3 的圖隱藏（對照組會一直重畫）
  state 14 RAIN false
  host_run old
  bad=0
  for L in COMPACT WIDE MEDIUM TALL BETWEEN; do
    r=$(host_line old $L)
    echo "$r" | grep -q "ok=true" && echo "$r" | grep -q "flipper=VISIBLE" && echo "$r" | grep -q "still=GONE" && echo "$r" | grep -q "characterInside=true" || bad=$((bad + 1))
  done
  g=$(frames_in 10 widget-old)
  check "小工具（舊火柴人）不受影響" "$([ $bad = 0 ] && echo 0 || echo 1)" "5 種大小裡 $bad 種不對；10 秒內畫了 $g 張（輪播）"
  # 密度
  for d in 320 560; do
    adb shell wm density $d
    state 14 RAIN true
    host_run v3-$d
    bad=0
    for L in COMPACT WIDE MEDIUM TALL BETWEEN; do
      r=$(host_line v3-$d $L)
      echo "$r" | grep -q "ok=true" && echo "$r" | grep -q "still=VISIBLE stillImage=true" && echo "$r" | grep -q "characterInside=true" || { bad=$((bad + 1)); log "  $d：$r"; }
    done
    check "小工具（V3）密度 $d" "$([ $bad = 0 ] && echo 0 || echo 1)" "5 種大小裡 $bad 種不對"
  done
  adb shell wm density reset
else
  for _ in 1 2 3 4 5 6 7 8 9; do check "小工具宿主" 1 "appwidget grantbind 失敗，沒辦法綁定小工具"; done
fi

# 真正的桌面 launcher：用 App 的「把小工具放到桌面」，在 launcher 的對話框按加入。
# 這一段放不上去時只記 WARN、列為沒測（launcher 的對話框不在我們控制之內）；放上去之後的檢查失敗照樣算失敗。
log ""
log "== 11. 桌面 launcher 上的小工具（P5-4）=="
screen_diff() { # screen_diff <a> <b>：兩張全螢幕截圖（扣掉狀態列和導覽列）差幾個像素
  local y0=$((H * 6 / 100)) h=$((H * 86 / 100))
  convert "$OUT/shots/$1.png" -crop "${W}x$h+0+$y0" +repage /tmp/a.png
  convert "$OUT/shots/$2.png" -crop "${W}x$h+0+$y0" +repage /tmp/b.png
  compare -metric AE -fuzz 3% /tmp/a.png /tmp/b.png null: 2>&1 | awk '{print $1}'
}
# Launcher3 把小工具放在第一頁右邊那一頁（第一頁有日期與 App），按 HOME 會回第一頁，所以再往左滑一頁
home_shot() {
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe $((W * 85 / 100)) $((H / 2)) $((W * 15 / 100)) $((H / 2)) 300; sleep 2
  adb exec-out screencap -p > "$OUT/shots/$1.png"
}
pinned() { adb shell dumpsys appwidget | python3 -c "
import sys, re
t = sys.stdin.read()
w = t.split('Widgets:', 1)[-1]
n = 0
for block in re.split(r'\n\s+\[\d+\]', w):
    if 'com.patty60723.tearoff/com.patty60723.tearoff.CalendarWidget' in block or 'com.patty60723.tearoff/.CalendarWidget' in block:
        m = re.search(r'pkg:([\w.]+)', block)
        if m and m.group(1) != 'com.patty60723.tearoff':
            n += 1
print(n)
"; }
state 14 RAIN true
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2; scroll_down
dump_ui pin
p=$(bounds_of pin pin)
placed=0
if [ -n "$p" ]; then
  set -- $p
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  sleep 3
  adb exec-out screencap -p > "$OUT/shots/launcher-pin-dialog.png"
  # uiautomator 在對話框還在動的時候常常讀不到，多試幾次
  for _ in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui/launcher-pin.xml" >/dev/null 2>&1 && break
    sleep 2
  done
  a=$(python3 - "$OUT/ui/launcher-pin.xml" <<'PYEOF'
import re, sys
import xml.etree.ElementTree as ET
try:
    nodes = list(ET.parse(sys.argv[1]).iter("node"))
except Exception:
    sys.exit()
for want in ["add automatically", "add to home screen", "add", "新增", "加入"]:
    for n in nodes:
        if (n.get("text") or "").strip().lower() == want:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit()
PYEOF
)
  if [ -z "$a" ]; then
    # 讀不到畫面結構時，按 Launcher3 對話框「加入」按鈕的位置（右下角）；有沒有真的放上去由下面的 dumpsys 判斷
    a="$((W * 77 / 100)) $((H * 94 / 100))"
    log "  讀不到 launcher 對話框的畫面結構，改按右下角的加入按鈕位置 $a"
  fi
  if [ -n "$a" ]; then
    adb shell input tap $a
    sleep 3
    [ "$(pinned)" -ge 1 ] 2>/dev/null && placed=1
  fi
fi
if [ $placed = 1 ]; then
  log "  已放上桌面：$(adb shell dumpsys appwidget | grep -m1 -o 'pkg:[a-z.]*launcher[a-z0-9.]*')"
  home_shot launcher-1-v3
  update_case() { # update_case <名稱> <說明> <state 參數…>：改設定、送更新廣播、截圖、跟上一張比
    local name=$1 what=$2; shift 2
    state "$@"
    adb shell am broadcast -n $PKG/.CalendarWidget -a com.patty60723.tearoff.NEW_DAY >/dev/null
    sleep 4
    home_shot "launcher-$name"
    local diff; diff=$(screen_diff "$PREV" "launcher-$name")
    check "桌面小工具：$what" "$([ "${diff%.*}" -gt 200 ] 2>/dev/null && echo 0 || echo 1)" "跟上一張差 $diff 個像素"
    PREV="launcher-$name"
  }
  PREV=launcher-1-v3
  update_case 2-bundle-off "卸下包袱後更新" 14 RAIN true false
  update_case 3-hat-off "脫下紙帽後更新" 14 RAIN true false false
  update_case 4-page5 "換成第 5 頁後更新" 5 RAIN true
  update_case 5-snow "天氣換成雪後更新" 5 SNOW true
  update_case 6-old "關掉 V3 後換回舊火柴人" 5 SNOW false
  update_case 7-v3 "打開 V3 後換回墨丸" 14 RAIN true
  # 在 App 裡點「卸下包袱」：App 自己叫 refreshAll
  adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2; scroll_down
  dump_ui launcher-app; sleep 1; dump_ui launcher-app
  b=$(bounds_of launcher-app story_bundle)
  if [ -n "$b" ]; then
    set -- $b
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
    home_shot launcher-8-app-toggle
    diff=$(screen_diff launcher-7-v3 launcher-8-app-toggle)
    check "在 App 卸下包袱後，桌面小工具跟著換" "$([ "${diff%.*}" -gt 200 ] 2>/dev/null && echo 0 || echo 1)" "跟上一張差 $diff 個像素"
  else
    warn "找不到 App 的包袱入口，沒測 App 觸發的更新"
  fi
else
  warn "沒辦法自動把小工具放上桌面 launcher（找不到按鈕或 launcher 的對話框），這一段列為沒測"
  log "NOTTESTED  桌面 launcher 上的小工具"
fi

log ""
check "全程沒有閃退" "$(crashed && echo 1 || echo 0)" "logcat 沒有 FATAL EXCEPTION"
adb logcat -d | grep -A20 "FATAL EXCEPTION" > "$OUT/crash.txt" || true
# 手動觸發時可以故意加一項失敗，確認 CI 真的會變紅
[ "${W7_FORCE_FAIL:-0}" = 1 ] && check "故意失敗（確認退出碼）" 1 "W7_FORCE_FAIL=1"
finish
