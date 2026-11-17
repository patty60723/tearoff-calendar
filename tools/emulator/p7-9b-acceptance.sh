#!/usr/bin/env bash
# P7-9b 驗收：在 Android Emulator 上實際操作 App 的天氣接力彩蛋（點天氣資訊、紙角撕頁、播放中被打斷），錄影並量位置。
# 用法：tools/emulator/p7-9b-acceptance.sh <APK> <輸出資料夾>
#
# 位置不是從截圖猜的：debug 版的 App 每個事件（顯示、捲動、點擊、開始、每半秒、結束）都在 logcat 印出
# 各元件在螢幕上的實際位置（tag WeatherEgg，見 MainActivity.eggTrace）。這裡讀那些紀錄來判斷「位置不變」與點哪裡。
# 411dp＝密度 420、320dp＝密度 540（1080px 寬的模擬器）。錄影是即時的（正常速度）；模擬器用軟體繪圖，流暢度不代表實機。
set -u
APK=$1
OUT=$2
PKG=com.patty60723.tearoff
mkdir -p "$OUT/shots" "$OUT/video" "$OUT/logs"
LOG="$OUT/report.txt"
: > "$LOG"
log() { echo "$*" | tee -a "$LOG"; }
PASS_N=0
FAIL_N=0
WARN_N=0
# 4 種設定 × 3 種天氣 × 3 項 ＋ 4 種設定 × 互動 7 項 ＋ Reduced Motion 2 項 ＋ 沒有閃退 1 項
EXPECTED_CHECKS=$((4 * 3 * 3 + 4 * 7 + 2 + 1))
check() { # check <名稱> <0 通過／1 失敗> <說明>
  if [ "$2" = 0 ]; then PASS_N=$((PASS_N + 1)); log "PASS  $1 — $3"
  else FAIL_N=$((FAIL_N + 1)); log "FAIL  $1 — $3"; fi
}
warn() { WARN_N=$((WARN_N + 1)); log "WARN  $*"; }
finish() {
  log ""
  local ran=$((PASS_N + FAIL_N))
  [ "$ran" -ge "$EXPECTED_CHECKS" ] || { FAIL_N=$((FAIL_N + 1)); log "FAIL  必要檢查只跑了 $ran 項（應該 $EXPECTED_CHECKS 項）"; }
  log "結果：PASS $PASS_N、FAIL $FAIL_N、WARN $WARN_N（WARN 是素材或參考數字，不影響結果）"
  [ "$FAIL_N" = 0 ] && exit 0 || exit 1
}

adb install -r "$APK" >/dev/null || { check "安裝 APK" 1 "$APK"; finish; }
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 1; done
adb shell settings put secure anr_show_background 0
SIZE=$(adb shell wm size | tr -d '\r' | awk '{print $NF}')
W=${SIZE%x*}
H=${SIZE#*x}
TODAY=$(adb shell date +%F | tr -d '\r')
YESTERDAY=$(date -d "$TODAY -1 day" +%F)
log "裝置：$(adb shell getprop ro.product.model | tr -d '\r')，Android $(adb shell getprop ro.build.version.release | tr -d '\r')，螢幕 $SIZE，今天 $TODAY"

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
story_page() { adb shell run-as $PKG cat shared_prefs/story.xml 2>/dev/null | tr -d '\r' | grep -o 'name="page" value="[0-9]*"' | tr -dc '0-9'; }

# open_app <最後撕頁的日期> <天氣>：關掉 App、寫入狀態（第 14 頁、包袱與紙帽）、清掉紀錄、打開
open_app() {
  adb shell am force-stop $PKG
  write_prefs story "  <int name=\"page\" value=\"14\" />
  <string name=\"lastTornDate\">$1</string>
  <string name=\"lastText\">$(story_text 14)</string>"
  write_prefs mowan "  <boolean name=\"paperHatOn\" value=\"true\" />
  <boolean name=\"bundleOn\" value=\"true\" />"
  write_prefs weather "  <string name=\"fakeMood\">$2</string>"
  write_prefs ui "  <boolean name=\"tearGuideDone\" value=\"true\" />"
  adb logcat -c
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 2.5
}

# App 的紀錄
egg_log() { adb logcat -d -s WeatherEgg:I -v raw 2>/dev/null | tr -d '\r' | grep -v '^-----'; }
last_line() { egg_log | grep -E "^($1)" | tail -1; }
field() { # field <紀錄的一行> <名稱>：x1,y1,x2,y2 或值
  echo "$1" | tr ' ' '\n' | grep "^$2=" | head -1 | cut -d= -f2-
}
center_of() { # center_of x1,y1,x2,y2 → "x y"
  echo "$1" | awk -F, '{ printf "%d %d", ($1 + $3) / 2, ($2 + $4) / 2 }'
}
# 位置的指紋：日曆頁、操作區、故事卡、標題、故事文字、大墨丸
layout_of() { local l=$1 out=""; for k in page slot card title text mowan; do out="$out $k=$(field "$l" $k)"; done; echo "$out"; }

# 捲到天氣資訊與故事卡墨丸都在畫面裡（放不下時以天氣資訊為準）。慢慢拖，不要甩動
frame_both() {
  local l sun mowan sy my need max s
  l=$(last_line "resume|show|scroll")
  sun=$(field "$l" sun); mowan=$(field "$l" mowan)
  [ -n "$sun" ] && [ "$sun" != "-" ] || return 0
  sy=$(echo "$sun" | cut -d, -f2); my=$(echo "$mowan" | cut -d, -f4)
  need=$(( my - (H - H / 12) ))
  max=$(( sy - H / 6 ))
  s=$need; [ "$s" -gt "$max" ] && s=$max
  [ "$s" -le 0 ] && return 0
  # ScrollView 開始拖動前會吃掉 touchSlop（約 8dp）
  local slop=$(( 8 * $(adb shell wm density | tr -d '\r' | awk '{print $NF}' | tail -1) / 160 ))
  adb shell input swipe $((W / 2)) $((H * 4 / 5)) $((W / 2)) $(( H * 4 / 5 - s - slop )) 1800
  sleep 1.2
}

tap_sun() {
  local l sun
  l=$(last_line "resume|show|scroll|tap|end")
  sun=$(field "$l" sun)
  [ -n "$sun" ] && [ "$sun" != "-" ] || { warn "找不到天氣資訊的位置"; return 1; }
  adb shell input tap $(center_of "$sun")
}

# ---------- 每種設定 ----------
for fs in 1.0 2.0; do
for dpi in 420 540; do
  wdp=$(( W * 160 / dpi ))
  fpct=$([ "$fs" = 2.0 ] && echo 200 || echo 100)
  cfg="${wdp}dp-${fpct}"
  adb shell wm density $dpi
  adb shell settings put system font_scale $fs
  log ""
  log "== ${wdp}dp・字體 ${fpct}% =="

  # 1. 三種天氣：點天氣資訊、播完；位置不變；靜態墨丸與動畫同一格切換
  for mood in SUNNY RAIN SNOW; do
    n="$cfg-$(echo $mood | tr 'A-Z' 'a-z')"
    open_app "$TODAY" "$mood"
    frame_both
    adb exec-out screencap -p > "$OUT/shots/$n-0-before.png"
    adb shell screenrecord --time-limit 13 /sdcard/egg.mp4 &
    rec=$!
    sleep 1
    t0=$(date +%s%N)
    tap_sun
    # 關鍵畫面：點擊後約 1、2、3.5、5、6.5 秒（截圖本身要 0.3–0.6 秒，實際時間寫在檔名）
    for at in 1000 2000 3500 5000 6500; do
      while [ $(( ($(date +%s%N) - t0) / 1000000 )) -lt $at ]; do sleep 0.05; done
      ms=$(( ($(date +%s%N) - t0) / 1000000 ))
      adb exec-out screencap -p > "$OUT/shots/$n-k$(printf %05d $ms).png"
    done
    wait $rec
    adb pull /sdcard/egg.mp4 "$OUT/video/$n.mp4" >/dev/null 2>&1 || warn "錄影 $n 沒有拉回來"
    sleep 1
    adb exec-out screencap -p > "$OUT/shots/$n-9-after.png"
    egg_log > "$OUT/logs/$n.txt"
    tap=$(grep -m1 '^tap ' "$OUT/logs/$n.txt"); endl=$(grep -m1 '^end ' "$OUT/logs/$n.txt")
    check "$cfg $mood：點天氣資訊播完整段" "$(echo "$tap" | grep -q '^tap PLAY' && grep -q '^start ' "$OUT/logs/$n.txt" && echo "$endl" | grep -q 'reason=done' && echo 0 || echo 1)" "${tap%% scroll*}；${endl%% scroll*}"
    # 位置：點擊、播放中每半秒、結束時都一樣（同一個捲動位置）
    base=$(layout_of "$tap"); moved=0; samples=0
    while read -r l; do
      samples=$((samples + 1))
      [ "$(layout_of "$l")" = "$base" ] || { moved=1; log "  位置不同：$l"; }
      [ "$(field "$l" scroll)" = "$(field "$tap" scroll)" ] || { moved=1; log "  捲動位置不同：$l"; }
    done < <(grep -E '^(start|frame|end) ' "$OUT/logs/$n.txt")
    check "$cfg $mood：日曆頁、操作區、故事卡、文字、大墨丸 0px 位移" "$([ "$moved" = 0 ] && [ "$samples" -ge 10 ] && echo 0 || echo 1)" "比對 $samples 筆（App 量的螢幕位置）；$base"
    # 靜態墨丸：小墨丸在動時藏起來（4＝INVISIBLE），換回靜態圖後顯示（0），結束時疊層清空
    bad=0
    while read -r l; do
      case "$l" in
        *small=anim*) [ "$(field "$l" stillVis)" = 4 ] && [ "$(field "$l" overlay)" = true ] || bad=1 ;;
        *small=still*) [ "$(field "$l" stillVis)" = 0 ] || bad=1 ;;
      esac
    done < <(grep '^frame ' "$OUT/logs/$n.txt")
    [ "$(field "$endl" stillVis)" = 0 ] && [ "$(field "$endl" overlay)" = false ] || bad=1
    check "$cfg $mood：同一時間只有一隻小墨丸，結束後靜態墨丸回來、疊層清空" "$bad" "frame $(grep -c '^frame ' "$OUT/logs/$n.txt") 筆；結束 stillVis=$(field "$endl" stillVis) overlay=$(field "$endl" overlay)"
    # 參考：故事文字那一塊在播放中有沒有被畫到（大墨丸畫不出自己的範圍，只有接力素材會經過）
    text=$(field "$tap" text)
    if [ -n "$text" ] && [ "$text" != "-" ]; then
      IFS=, read -r x1 y1 x2 y2 <<< "$text"
      for k in "$OUT/shots/$n"-k*.png; do
        convert "$OUT/shots/$n-0-before.png" -crop "$((x2 - x1))x$((y2 - y1))+$x1+$y1" +repage /tmp/egg-a.png 2>/dev/null
        convert "$k" -crop "$((x2 - x1))x$((y2 - y1))+$x1+$y1" +repage /tmp/egg-b.png 2>/dev/null
        d=$(compare -metric AE -fuzz 6% /tmp/egg-a.png /tmp/egg-b.png null: 2>&1 | awk '{print $1}')
        log "  故事文字範圍內和點擊前不同的像素：$(basename "$k" .png) → ${d:-?}"
      done
    fi
    # 小墨丸放大（正常大小 ×1 與 ×3）：辨識度
    still=$(field "$tap" still)
    if [ -n "$still" ] && [ "$still" != "-" ]; then
      IFS=, read -r x1 y1 x2 y2 <<< "$still"
      g="$((x2 - x1 + 80))x$((y2 - y1 + 60))+$((x1 - 40))+$((y1 - 40))"
      convert "$OUT/shots/$n-0-before.png" "$OUT/shots/$n"-k*.png "$OUT/shots/$n-9-after.png" -crop "$g" +repage +append "$OUT/shots/$n-small-x1.png" 2>/dev/null || warn "小墨丸放大 $n 失敗"
      convert "$OUT/shots/$n-small-x1.png" -filter point -resize 300% "$OUT/shots/$n-small-x3.png" 2>/dev/null || true
    fi
  done

  # 2. 互動：紙角撕頁 → 點天氣 → 播放中從紙角掀 → 再點 → 連點 → 回到桌面 → 回來 → 從天氣資訊拖 → 再點、播完
  n="$cfg-interact"
  open_app "$YESTERDAY" SUNNY
  adb shell screenrecord --time-limit 45 /sdcard/act.mp4 &
  rec=$!
  sleep 1
  l=$(last_line "resume|show"); pg=$(field "$l" page)
  p0=$(story_page)
  IFS=, read -r px1 py1 px2 py2 <<< "${pg:-0,0,0,0}"
  adb shell input swipe $((px2 - 24)) $((py2 - 24)) $((px1 + 40)) $((py1 + 40)) 600
  sleep 3
  p1=$(story_page)
  check "$cfg 互動：從紙角拖曳正常撕頁（不觸發彩蛋）" "$([ -n "$p0" ] && [ "$p1" = $((p0 + 1)) ] && ! egg_log | grep -q '^tap ' && echo 0 || echo 1)" "頁數 ${p0:-?} → ${p1:-?}"
  frame_both
  tap_sun; sleep 2.5
  # 播放中從右下紙角掀一點（今天那頁撕過了不能撕，紙會彈回去）
  l=$(last_line "resume|show|scroll|tap"); pg=$(field "$l" page)
  IFS=, read -r px1 py1 px2 py2 <<< "${pg:-0,0,0,0}"
  adb shell input swipe $((px2 - 20)) $((py2 - 20)) $((px2 - (px2 - px1) / 3)) $((py2 - (py2 - py1) / 5)) 500
  sleep 2
  tap_sun; sleep 1
  tap_sun; sleep 0.3; tap_sun; sleep 0.3; tap_sun
  sleep 1.5
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 2.5
  # 從天氣資訊上開始拖（超過 touchSlop）：撕頁手勢，不是點擊
  l=$(last_line "resume|show|scroll"); sun=$(field "$l" sun)
  before_drag=$(egg_log | grep -c '^tap ')
  if [ -n "$sun" ] && [ "$sun" != "-" ]; then
    set -- $(center_of "$sun")
    adb shell input swipe $1 $2 $(( $1 + W / 4 )) $(( $2 - H / 8 )) 500
  fi
  sleep 2
  after_drag=$(egg_log | grep -c '^tap ')
  tap_sun
  sleep 10
  wait $rec
  adb pull /sdcard/act.mp4 "$OUT/video/$n.mp4" >/dev/null 2>&1 || warn "錄影 $n 沒有拉回來"
  egg_log > "$OUT/logs/$n.txt"
  ev=$(grep -oE '^(tap [A-Z_]+|start|end reason=[a-z]+|resume)' "$OUT/logs/$n.txt" | tr '\n' ' ')
  log "  事件順序：$ev"
  seq=$(echo "$ev" | tr -s ' ')
  check "$cfg 互動：單純點天氣資訊播放、不撕頁" "$(echo "$seq" | grep -q 'tap PLAY start' && [ "$(story_page)" = "$p1" ] && echo 0 || echo 1)" "頁數維持 $(story_page)"
  check "$cfg 互動：播放中開始掀紙角，彩蛋取消" "$(echo "$seq" | grep -qE 'tap PLAY start (resume )?end reason=peel' && echo 0 || echo 1)" "$(echo "$seq" | grep -oE 'end reason=peel' | head -1)"
  check "$cfg 互動：取消 1 秒後可以再播；播放中連點忽略" "$(echo "$seq" | grep -qE 'end reason=peel tap PLAY start tap IGNORED tap IGNORED tap IGNORED' && echo 0 || echo 1)" "連點 $(grep -c '^tap IGNORED' "$OUT/logs/$n.txt") 次忽略"
  check "$cfg 互動：回到桌面取消，回來不續播" "$(echo "$seq" | grep -qE 'end reason=pause resume' && ! echo "$seq" | grep -qE 'resume start' && echo 0 || echo 1)" "$(echo "$seq" | grep -oE 'end reason=pause resume' | head -1)"
  check "$cfg 互動：從天氣資訊拖曳超過 touchSlop 不觸發彩蛋" "$([ "$before_drag" = "$after_drag" ] && echo 0 || echo 1)" "拖之前 $before_drag 次點擊、拖完 $after_drag 次"
  check "$cfg 互動：最後一次點擊播完、清乾淨" "$(echo "$seq" | grep -qE 'tap PLAY start end reason=done $' && [ "$(field "$(grep '^end ' "$OUT/logs/$n.txt" | tail -1)" overlay)" = false ] && echo 0 || echo 1)" "$(grep '^end ' "$OUT/logs/$n.txt" | tail -1 | cut -c1-60)"
done
done

# 3. Reduced Motion（411dp、字體 100%）：不播動作，只換一句話
adb shell wm density 420
adb shell settings put system font_scale 1.0
log ""
log "== Reduced Motion（411dp・字體 100%）=="
for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 0; done
open_app "$TODAY" RAIN
frame_both
adb exec-out screencap -p > "$OUT/shots/reduced-0-before.png"
tap_sun
sleep 1
adb exec-out screencap -p > "$OUT/shots/reduced-1-after.png"
egg_log > "$OUT/logs/reduced.txt"
check "Reduced Motion：點天氣資訊只換一句話、不播動作" "$(grep -q '^tap TEXT_ONLY' "$OUT/logs/reduced.txt" && ! grep -q '^start ' "$OUT/logs/reduced.txt" && echo 0 || echo 1)" "$(grep -m1 '^tap ' "$OUT/logs/reduced.txt" | cut -c1-40)"
l=$(grep -m1 '^tap ' "$OUT/logs/reduced.txt")
check "Reduced Motion：靜態墨丸一直顯示、沒有疊層" "$([ "$(field "$l" stillVis)" = 0 ] && [ "$(field "$l" overlay)" = false ] && echo 0 || echo 1)" "stillVis=$(field "$l" stillVis) overlay=$(field "$l" overlay)"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 1; done

adb shell wm density reset
adb shell settings put system font_scale 1.0
check "整段沒有閃退" "$(adb logcat -d -b crash | grep -q "$PKG" && echo 1 || echo 0)" "logcat crash buffer"

# 影片縮小（給人看：寬 540、H.264）
for v in "$OUT"/video/*.mp4; do
  ffmpeg -loglevel error -y -i "$v" -vf "scale=540:-2" -c:v libx264 -crf 26 -pix_fmt yuv420p -an "${v%.mp4}-540.mp4" && mv "${v%.mp4}-540.mp4" "$v" || warn "縮小 $v 失敗"
done
finish
