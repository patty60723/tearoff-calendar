#!/usr/bin/env bash
# tools/emulator/quiet.sh 的測試：用假的量測結果模擬各種情境，不需要模擬器。
# 用法：bash tools/emulator/test-quiet.sh（CI 的 Build APK 與 Emulator acceptance 都會跑）
set -u
cd "$(dirname "$0")"
. ./quiet.sh

failed=0
pass() { echo "ok    $1"; }
fail() { echo "FAIL  $1"; failed=1; }

# 假的量測：SEQ 是每一次的「張數,更新次數」，用空白分開
fake() { local s=($SEQ); echo "${s[$(($1 - 1))]//,/ }"; }

# scenario <名稱> <每次的結果> <預期 PASS/FAIL> <預期量了幾次>
scenario() {
  local out verdict tries lines
  SEQ=$2
  out=$(quiet_judge fake)
  verdict=$(tail -1 <<<"$out")
  tries=$(sed -n 's/.* tries=\([0-9]*\) .*/\1/p' <<<"$verdict")
  lines=$(grep -c '^try=' <<<"$out")
  if [[ "$verdict" == "verdict=$3 "* && "$tries" == "$4" && "$lines" == "$4" ]]; then pass "$1：$verdict"
  else fail "$1：預期 $3、量 $4 次，得到「$verdict」（記錄了 $lines 次）"; echo "$out" | sed 's/^/        /'; fi
}

echo "== 判斷"
scenario "沒有更新、沒有重畫" "0,0" PASS 1
scenario "沒有更新、少量重畫（門檻內）" "5,0" PASS 1
scenario "一次內容更新，之後穩定：重新量測" "6,5 0,0" PASS 2
scenario "兩次內容更新，第三次穩定" "9,5 4,1 0,0" PASS 3
scenario "持續收到更新，三次都沒有穩定區間" "6,5 3,2 5,1" FAIL 3
scenario "沒有更新卻持續重畫" "60,0" FAIL 1
scenario "沒有更新、剛好超過門檻" "6,0" FAIL 1
scenario "一次更新後，沒有更新卻持續重畫" "6,5 60,0" FAIL 2
scenario "讀不到量測結果" "x" FAIL 1

# 每一次的張數、更新次數、原因都要留下來
SEQ="6,5 3,2 0,0"
out=$(quiet_judge fake)
if grep -q '^try=1 frames=6 updates=5 note=.*收到 5 次內容更新' <<<"$out" &&
   grep -q '^try=2 frames=3 updates=2 note=.*收到 2 次內容更新' <<<"$out" &&
   grep -q '^try=3 frames=0 updates=0 note=沒有更新' <<<"$out"; then pass "每次的張數、更新次數、原因都有記錄"
else fail "每次的記錄不完整"; echo "$out"; fi

echo "== 監測範圍"
tmp=$(mktemp -d)
cat >"$tmp/host.txt" <<'EOF'
10-28 02:29:50.001 1 1 I W7HOST  : bind layout=COMPACT id=7 bound=true
10-28 02:29:50.002 1 1 I W7HOST  : bind layout=WIDE id=8 bound=true
10-28 02:29:50.003 1 1 I W7HOST  : bind layout=MEDIUM id=9 bound=false
10-28 02:29:54.000 1 1 I W7HOST  : update id=7
10-28 02:29:54.100 1 1 I W7HOST  : update id=17
EOF
ids=$(host_ids "$tmp/host.txt")
[ "$ids" = "7 8" ] && pass "只拿綁定成功的小工具（$ids）" || fail "綁定的 id：得到「$ids」"
missing=$(missing_updates "$tmp/host.txt" $ids)
[ "$missing" = "8" ] && pass "找出監測沒涵蓋的小工具（id 8 沒有任何更新；id 17 不會被當成 id 7 或 id 1）" || fail "沒涵蓋的：得到「$missing」"
echo "10-28 02:29:55.000 1 1 I W7HOST  : update id=8" >>"$tmp/host.txt"
missing=$(missing_updates "$tmp/host.txt" $ids)
[ -z "$missing" ] && pass "每個受測小工具都看得到更新時，沒有遺漏" || fail "應該沒有遺漏，得到「$missing」"

cat >"$tmp/logcat.txt" <<'EOF'
I W7HOST  : update id=7
I W7HOST  : mark win-widget-v3-1 start
I W7HOST  : update id=7
I W7HOST  : update id=99
I W7HOST  : update id=8
I W7HOST  : update id=70
I W7HOST  : mark win-widget-v3-1 end
I W7HOST  : update id=8
EOF
n=$(count_updates "$tmp/logcat.txt" "mark win-widget-v3-1 start" "mark win-widget-v3-1 end" 7 8)
[ "$n" = 2 ] && pass "只算量測期間、受測小工具的更新（id 7、8 各一次；id 99、70 和期間外的不算）" || fail "更新次數：得到 $n，應該是 2"
n=$(count_updates "$tmp/logcat.txt" "mark win-widget-v3-2 start" "mark win-widget-v3-2 end" 7 8)
[ "$n" = 0 ] && pass "沒有這次的標記時是 0（不會算到別次）" || fail "得到 $n"
rm -rf "$tmp"

echo
[ "$failed" = 0 ] && echo "全部通過" || echo "有測試失敗"
exit "$failed"
