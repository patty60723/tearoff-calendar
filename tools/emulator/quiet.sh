# 小工具「真的靜止」的判斷（P6-4c）。w7-acceptance.sh 用 source 載入；判斷本身不碰模擬器，
# 可以單獨測（tools/emulator/test-quiet.sh）。
#
# 規則：
# - 每次量 10 秒：宿主畫面重畫幾張、這段時間受測小工具收到幾次內容更新（RemoteViews）。
# - 收到更新的那一次不拿來判斷（一次性的內容更新，例如背景抓到新天氣，會重畫一下），記下來再量，最多 QUIET_TRIES 次。
# - 第一個「沒有收到更新」的完整 10 秒：重畫 ≤ QUIET_MAX_FRAMES 張是 PASS，超過是 FAIL（沒有更新卻在重畫）。
# - QUIET_TRIES 次都收到更新（沒有穩定的 10 秒可以量）：FAIL，不算 PASS 也不跳過。
# - 讀不到張數或更新次數：FAIL。

QUIET_TRIES=${QUIET_TRIES:-3}
QUIET_MAX_FRAMES=${QUIET_MAX_FRAMES:-5}

# quiet_judge <量一次的指令…>：指令會多收到一個參數「第幾次」，印「張數 更新次數」。
# 每次印一行「try=i frames=F updates=U note=…」，最後一行「verdict=PASS|FAIL tries=i reason=…」。
quiet_judge() {
  local i f u
  for ((i = 1; i <= QUIET_TRIES; i++)); do
    read -r f u < <("$@" "$i")
    if ! [[ "$f" =~ ^[0-9]+$ && "$u" =~ ^[0-9]+$ ]]; then
      echo "try=$i frames=${f:-?} updates=${u:-?} note=量測失敗"
      echo "verdict=FAIL tries=$i reason=第 $i 次量測讀不到張數或更新次數"
      return
    fi
    if [ "$u" -gt 0 ]; then
      echo "try=$i frames=$f updates=$u note=這 10 秒受測小工具收到 $u 次內容更新，不拿來判斷$([ "$i" -lt "$QUIET_TRIES" ] && echo "，再量一次")"
      continue
    fi
    if [ "$f" -le "$QUIET_MAX_FRAMES" ]; then
      echo "try=$i frames=$f updates=0 note=沒有更新，靜止"
      echo "verdict=PASS tries=$i reason=第 $i 次：沒有收到更新的完整 10 秒內畫了 $f 張（≤ $QUIET_MAX_FRAMES）"
    else
      echo "try=$i frames=$f updates=0 note=沒有更新卻在重畫"
      echo "verdict=FAIL tries=$i reason=第 $i 次：沒有收到更新的 10 秒內畫了 $f 張（> $QUIET_MAX_FRAMES），不是靜止"
    fi
    return
  done
  echo "verdict=FAIL tries=$QUIET_TRIES reason=$QUIET_TRIES 次量測都收到小工具更新，沒有完整 10 秒的穩定區間可以判斷"
}

# host_ids <宿主的 log>：這次宿主綁定成功的小工具 id（空白分開）
host_ids() {
  grep -o 'bind layout=[A-Z]* id=[0-9]* bound=true' "$1" | sed 's/.* id=\([0-9]*\) .*/\1/' | tr '\n' ' ' | sed 's/ $//'
}

# missing_updates <宿主的 log> <id…>：哪些受測小工具在宿主的 log 裡一次更新都沒有（表示監測沒有涵蓋它）
missing_updates() {
  local log=$1 id out=""
  shift
  for id in "$@"; do grep -qE "update id=$id([^0-9]|$)" "$log" || out="$out $id"; done
  echo "${out# }"
}

# count_updates <logcat 文字檔> <開始標記> <結束標記> <id…>：兩個標記之間，這些小工具收到幾次更新（其他 id 不算）。
# 找不到開始標記、找不到結束標記、或結束在開始之前：印「error:原因」並傳回 1（不能當成 0 次更新，quiet_judge 會判 FAIL）。
count_updates() {
  local log=$1 from=$2 to=$3
  shift 3
  awk -v from="$from" -v to="$to" -v ids=" $* " '
    !s && index($0, from) { s = NR; next }
    !e && index($0, to) { e = NR; if (!s) bad = 1; next }
    s && !e && match($0, /update id=[0-9]+/) { id = substr($0, RSTART + 10, RLENGTH - 10); if (index(ids, " " id " ")) c++ }
    END {
      if (!s) { print "error:找不到開始標記"; exit 1 }
      if (bad) { print "error:結束標記在開始標記之前"; exit 1 }
      if (!e) { print "error:找不到結束標記"; exit 1 }
      print c + 0
    }
  ' "$log"
}
