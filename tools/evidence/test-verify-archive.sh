#!/usr/bin/env bash
# tools/evidence/verify-archive.py 的測試：正常、ZIP 被改過、少檔案、多檔案、不是 ZIP、manifest 壞掉、參數錯誤。
# 用測試自己產生的小 ZIP，不需要網路，也不需要真的歸檔。
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
PY="$HERE/verify-archive.py"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
FAILS=0
ok() { echo "ok    $1"; }
bad() { echo "FAIL  $1"; FAILS=$((FAILS + 1)); }

# 做一個小 ZIP 與它的 manifest（跟正式歸檔同樣的格式）
mkzip() { # mkzip <輸出檔名> <檔案…>：把 $T/src 下的檔案打包成 $T/<輸出檔名>，前綴一律是 ev/（跟 manifest 的 archive 名稱一樣）
  local out=$1; shift
  (cd "$T/src" && python3 -c '
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    for f in sys.argv[2:]:
        z.write(f, "ev/" + f)
' "$T/$out" "$@")
}
mkdir -p "$T/src/sub"
printf 'hello\n' > "$T/src/a.txt"; head -c 3000 /dev/urandom > "$T/src/sub/b.png"
mkzip ev.zip a.txt sub/b.png
python3 - "$T" <<'EOF'
import sys, json, hashlib, os
T = sys.argv[1]
files = []
for p in ["a.txt", "sub/b.png"]:
    d = open(os.path.join(T, "src", p), "rb").read()
    files.append({"path": p, "bytes": len(d), "sha256": hashlib.sha256(d).hexdigest()})
z = open(os.path.join(T, "ev.zip"), "rb").read()
json.dump({"archive": "ev.zip", "archive_sha256": hashlib.sha256(z).hexdigest(), "archive_bytes": len(z),
           "file_count": 2, "files": files}, open(os.path.join(T, "ev.manifest.json"), "w"))
EOF
run() { python3 -I "$PY" "$@" 2>&1; }

out=$(run "$T/ev.manifest.json" "$T/ev.zip"); rc=$?
[ $rc = 0 ] && [[ $out == OK* ]] && ok "正常：OK、結束碼 0" || bad "正常應該 OK：rc=$rc $out"

cp "$T/ev.zip" "$T/flip.zip"; printf '\x00' | dd of="$T/flip.zip" bs=1 seek=40 conv=notrunc 2>/dev/null
out=$(run "$T/ev.manifest.json" "$T/flip.zip"); rc=$?
[ $rc = 1 ] && [[ $out == *"SHA-256 不一樣"* ]] && [[ $out != *Traceback* ]] && ok "ZIP 被改了一個位元組 → FAIL（不會當掉）" || bad "改過的 ZIP 應該 FAIL：rc=$rc $out"

mkzip ev-less.zip a.txt
out=$(run "$T/ev.manifest.json" "$T/ev-less.zip"); rc=$?
[ $rc = 1 ] && [[ $out == *"少了：sub/b.png"* ]] && ok "少一個檔案 → FAIL，寫出少了哪個" || bad "少檔案應該 FAIL：rc=$rc $out"

printf 'x' > "$T/src/extra.txt"; mkzip ev-more.zip a.txt sub/b.png extra.txt
out=$(run "$T/ev.manifest.json" "$T/ev-more.zip"); rc=$?
[ $rc = 1 ] && [[ $out == *"多了：extra.txt"* ]] && ok "多一個檔案 → FAIL，寫出多了哪個" || bad "多檔案應該 FAIL：rc=$rc $out"

printf 'not a zip' > "$T/bad.zip"
out=$(run "$T/ev.manifest.json" "$T/bad.zip"); rc=$?
[ $rc = 1 ] && [[ $out == *"不是有效的 ZIP"* ]] && ok "不是 ZIP → FAIL" || bad "不是 ZIP 應該 FAIL：rc=$rc $out"

printf '{broken' > "$T/broken.json"
out=$(run "$T/broken.json" "$T/ev.zip"); rc=$?
[ $rc = 1 ] && [[ $out == *"manifest 讀不到"* ]] && ok "manifest 壞掉 → FAIL" || bad "壞 manifest 應該 FAIL：rc=$rc $out"

out=$(run "$T/ev.manifest.json" "$T/nope.zip"); rc=$?
[ $rc = 1 ] && [[ $out == *"ZIP 讀不到"* ]] && ok "ZIP 不存在 → FAIL" || bad "不存在的 ZIP 應該 FAIL：rc=$rc $out"

run >/dev/null; [ $? = 2 ] && ok "沒有參數 → 結束碼 2" || bad "沒有參數應該結束碼 2"

# 正式歸檔的 manifest 本身格式正確（不需要 ZIP）
M="$HERE/../../docs/evidence/acceptance-mowan-p5-2.manifest.json"
python3 -I -c 'import json,sys; m=json.load(open(sys.argv[1])); assert m["file_count"]==len(m["files"]) and len(m["archive_sha256"])==64' "$M" \
  && ok "正式 manifest 格式正確（檔案數、SHA-256 長度）" || bad "正式 manifest 格式不對"

echo
[ "$FAILS" = 0 ] && { echo "全部通過"; exit 0; } || { echo "失敗 $FAILS 項"; exit 1; }
