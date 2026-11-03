"""驗收證據歸檔的檢查：拿到的 ZIP 跟 manifest 完全一致才算歸檔可用。

用法：
  python3 -I tools/evidence/verify-archive.py <manifest.json> <下載回來的 zip>

檢查：
  1. ZIP 的 SHA-256 與大小跟 manifest 一樣
  2. 解壓後的檔案數一樣，沒有多、沒有少
  3. 每個檔案的大小與 SHA-256 都一樣
任何一項不符就印出原因、結束碼 1；全部符合印 OK、結束碼 0；參數錯誤結束碼 2。
只讀檔案，不解壓到磁碟（避免 ZIP 裡的路徑寫到別處）。
"""
import hashlib
import json
import os
import sys
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def verify(manifest_path, zip_path):
    problems = []
    try:
        m = json.load(open(manifest_path, encoding="utf-8"))
    except (OSError, ValueError) as e:
        return ["manifest 讀不到：%s" % e]
    try:
        blob = open(zip_path, "rb").read()
    except OSError as e:
        return ["ZIP 讀不到：%s" % e]
    if sha256(blob) != m.get("archive_sha256"):
        problems.append("ZIP 的 SHA-256 不一樣：%s（manifest %s）" % (sha256(blob), m.get("archive_sha256")))
    if len(blob) != m.get("archive_bytes"):
        problems.append("ZIP 大小不一樣：%d（manifest %s）" % (len(blob), m.get("archive_bytes")))
    try:
        z = zipfile.ZipFile(zip_path)
    except zipfile.BadZipFile as e:
        return problems + ["不是有效的 ZIP：%s" % e]
    prefix = m.get("archive", "").removesuffix(".zip") + "/"
    entries = {n[len(prefix):] if n.startswith(prefix) else n: n for n in z.namelist() if not n.endswith("/")}
    want = {f["path"]: f for f in m.get("files", [])}
    if len(entries) != m.get("file_count"):
        problems.append("檔案數不一樣：%d（manifest %s）" % (len(entries), m.get("file_count")))
    for p in sorted(set(want) - set(entries)):
        problems.append("少了：%s" % p)
    for p in sorted(set(entries) - set(want)):
        problems.append("多了：%s" % p)
    for p in sorted(set(want) & set(entries)):
        try:
            data = z.read(entries[p])
        except Exception as e:  # CRC 錯誤、壓縮資料壞掉：照樣是「內容不一致」，不能讓檢查本身當掉
            problems.append("%s 讀不出來（%s）" % (p, e))
            continue
        if len(data) != want[p]["bytes"]:
            problems.append("%s 大小不一樣：%d（manifest %d）" % (p, len(data), want[p]["bytes"]))
        if sha256(data) != want[p]["sha256"]:
            problems.append("%s 內容不一樣（SHA-256）" % p)
    return problems


def main(argv):
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    problems = verify(argv[1], argv[2])
    if problems:
        for p in problems:
            print("FAIL  " + p)
        return 1
    m = json.load(open(argv[1], encoding="utf-8"))
    print("OK  %s：SHA-256 %s、%d 個檔案、內容全部一致" % (m["archive"], m["archive_sha256"], m["file_count"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
