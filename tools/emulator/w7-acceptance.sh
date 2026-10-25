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
check() { # check <名稱> <條件結果 0/1> <說明>
  if [ "$2" = 0 ]; then log "PASS  $1 — $3"; else log "FAIL  $1 — $3"; fi
}

adb install -r "$APK" >/dev/null
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
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

# 畫面上元件的位置：uiautomator dump。畫面一直在動時 uiautomator 等不到 idle 會失敗，
# 所以先暫時關掉動畫（墨丸停住）、dump 完再打開。之後要錄動畫的話要重開 App（setup）。
dump_ui() { # dump_ui <名稱>
  adb shell settings put global animator_duration_scale 0
  sleep 1.5
  local ok=1
  for _ in 1 2 3; do
    if adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui/$1.xml" >/dev/null 2>&1; then ok=0; break; fi
    sleep 1
  done
  adb shell settings put global animator_duration_scale 1
  [ $ok = 0 ] || log "  （$1：uiautomator dump 失敗）"
  return $ok
}
bounds_of() { # bounds_of <ui.xml> <resource-id 或文字>：輸出 "x1 y1 x2 y2"
  [ -f "$1" ] || return 0
  python3 - "$1" "$2" <<'PYEOF'
import re, sys
import xml.etree.ElementTree as ET
key = sys.argv[2]
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("resource-id", "").endswith("/" + key) or n.get("text") == key:
        print(*map(int, re.findall(r"\d+", n.get("bounds"))))
        break
PYEOF
}

# 故事卡的範圍（墨丸上緣到包袱入口或故事文字下緣），從 dump 出來的 xml 算
CARD=""
card_region() { # card_region <ui.xml 的名稱>
  local f="$OUT/ui/$1.xml" m b t
  m=$(bounds_of "$f" story_mowan); t=$(bounds_of "$f" story_text); b=$(bounds_of "$f" story_bundle)
  [ -n "$m" ] || return 1
  set -- $m
  local top=$(( $2 - 60 )) bottom=$4
  for r in "$t" "$b"; do [ -n "$r" ] && set -- $r && [ "$4" -gt "$bottom" ] && bottom=$4; done
  bottom=$(( bottom + 40 )); [ "$top" -lt 0 ] && top=0; [ "$bottom" -gt "$H" ] && bottom=$H
  CARD="${W}x$(( bottom - top ))+0+$top"
}

shot() { # shot <名稱>：整個畫面，加上裁切後的故事卡
  adb exec-out screencap -p > "$OUT/shots/$1-full.png"
  crop "$1"
}
crop() { [ -n "$CARD" ] && convert "$OUT/shots/$1-full.png" -crop "$CARD" +repage "$OUT/shots/$1.png"; }

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
  [ -n "$CARD" ] || return
  local w h x y fps=${2:-12} gw=${3:-480}
  w=${CARD%%x*}; h=${CARD#*x}; h=${h%%+*}; y=${CARD##*+}; x=0
  ffmpeg -loglevel error -y -i "$OUT/video/$1-full.mp4" -vf "crop=$w:$h:$x:$y,fps=$fps,scale=$gw:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=bayer" -loop 0 "$OUT/video/$1.gif"
  ffmpeg -loglevel error -y -i "$OUT/video/$1-full.mp4" -vf "crop=$w:$h:$x:$y" -c:v libx264 -pix_fmt yuv420p -an "$OUT/video/$1.mp4"
  rm -f "$OUT/video/$1-full.mp4"
}

crashed() { adb logcat -d | grep -q "FATAL EXCEPTION" ; }

log ""
log "== 1. 8 種天氣（第 14 頁）=="
setup 14 SUNNY
log "  寫入的設定：$(adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | tr '\n' ' ')"
dump_ui layout-14 && card_region layout-14
log "  故事卡範圍：${CARD:-找不到}"
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
    b=$(bounds_of "$OUT/ui/page-$p.xml" story_bundle)
    if [ "$p" = 14 ]; then check "第 $p 頁包袱入口" "$([ -n "$b" ] && echo 0 || echo 1)" "應該出現（$b）"
    else check "第 $p 頁包袱入口" "$([ -z "$b" ] && echo 0 || echo 1)" "不應該出現"; fi
  fi
done
CARD=$CARD14

log ""
log "== 4. 包袱入口：點擊、狀態保存、畫面更新 =="
setup 14 SUNNY true
dump_ui bundle-before
b=$(bounds_of "$OUT/ui/bundle-before.xml" "卸下包袱 ›")
if [ -n "$b" ]; then
  set -- $b
  bx=$(( ($1 + $3) / 2 )); by=$(( ($2 + $4) / 2 ))
  log "  入口位置 $b（高 $(( $4 - $2 )) px = $(( ($4 - $2) * 160 / 420 )) dp）"
  setup 14 SUNNY true   # 重開，讓墨丸重新動起來
  shot bundle-1-on
  # 錄影：3 秒時卸下、6 秒時背上、9 秒時再卸下；伸懶腰不應該重來
  adb shell screenrecord --time-limit 11 /sdcard/rec.mp4 &
  REC=$!
  sleep 3; adb shell input tap $bx $by
  sleep 3; adb shell input tap $bx $by
  sleep 3; adb shell input tap $bx $by
  wait $REC
  adb pull /sdcard/rec.mp4 "$OUT/video/bundle-toggle-full.mp4" >/dev/null 2>&1
  to_gif bundle-toggle
  check "包袱點擊不閃退" "$(crashed && echo 1 || echo 0)" "點三下"
  shot bundle-2-off
  dump_ui bundle-after
  check "點完變成「背上包袱」" "$([ -n "$(bounds_of "$OUT/ui/bundle-after.xml" "背上包袱 ›")" ] && echo 0 || echo 1)" "文字跟著換"
  stored=$(adb shell run-as $PKG cat shared_prefs/mowan.xml | tr -d '\r' | grep bundleOn)
  check "包袱狀態存起來" "$(echo "$stored" | grep -q 'value="false"' && echo 0 || echo 1)" "$stored"
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 2; scroll_down
  shot bundle-3-restart
  dump_ui bundle-restart
  check "重開 App 還是卸下" "$([ -n "$(bounds_of "$OUT/ui/bundle-restart.xml" "背上包袱 ›")" ] && echo 0 || echo 1)" "重開後的文字"
else
  check "找到包袱入口" 1 "uiautomator 沒找到「卸下包袱 ›」"
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
log "  INFO  關閉動畫、V3 故事卡：10 秒內畫了 $f 張"
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
log "  INFO  留在前景、關掉又打開動畫：之後 5 秒畫了 $f 張"
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
      b=$(bounds_of "$OUT/ui/density-$d-font-$fs.xml" story_bundle)
      t=$(bounds_of "$OUT/ui/density-$d-font-$fs.xml" story_text)
      [ -n "$b" ] && [ -n "$t" ] || { check "密度 $d、字體 $fs 的包袱入口" 1 "找不到入口或故事文字"; CARD=$CARD_SAVE; continue; }
      set -- $b; bh=$(( $4 - $2 )); set -- $t; tb=$4; set -- $b
      ok=$([ "$bh" -ge $(( 48 * d / 160 - 1 )) ] && [ "$2" -ge "$tb" ] && [ "$3" -le "$W" ] && echo 0 || echo 1)
      check "密度 $d、字體 $fs 的包袱入口" "$ok" "入口 $b（高 $bh px），故事文字下緣 $tb"
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
check "舊火柴人沒有包袱入口" "$([ -f "$OUT/ui/stickman.xml" ] && [ -z "$(bounds_of "$OUT/ui/stickman.xml" "卸下包袱 ›")" ] && echo 0 || echo 1)" ""

log ""
check "全程沒有閃退" "$(crashed && echo 1 || echo 0)" "logcat 沒有 FATAL EXCEPTION"
adb logcat -d | grep -A20 "FATAL EXCEPTION" > "$OUT/crash.txt" || true
exit 0
