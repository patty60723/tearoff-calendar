"""從 `adb shell dumpsys activity top` 的 View 階層算出某個 id 在螢幕上的位置。

用法：python3 view_bounds.py <dump.txt> <id> [top]，輸出 "x1 y1 x2 y2 V|G|I"（找不到就沒有輸出）。
ScrollView 假設已經捲到最底（驗收腳本會先捲到底），捲動量 = 內容高度 − ScrollView 高度；
第三個參數是 top 時假設捲在最上面（捲動量 0，P7-7 第 8b 段量導覽列與日曆頁）。
"""
import re
import sys

LINE = re.compile(r"^(\s*)([\w.$]+)\{\S+ (\S)\S* \S+ (-?\d+),(-?\d+)-(-?\d+),(-?\d+)(?: #\w+)?(?: (\S+))?")


def main(path, key, at_top=False):
    text = open(path, encoding="utf-8", errors="replace").read()
    # 只看最上面那個 Activity 的 View 階層
    act = text.find("ACTIVITY com.patty60723.tearoff/")
    start = text.find("View Hierarchy:", max(act, 0))
    lines = text[start:].splitlines()[1:] if start >= 0 else text.splitlines()
    stack = []  # (縮排, 絕對 x, 絕對 y, 寬, 高, 類別, 看不看得到)
    for line in lines:
        m = LINE.match(line)
        if not m:
            continue
        indent = len(m.group(1))
        cls, vis = m.group(2), m.group(3)
        l, t, r, b = (int(m.group(i)) for i in range(4, 8))
        ident = (m.group(8) or "").rstrip("}")
        while stack and stack[-1][0] >= indent:
            stack.pop()
        px = py = 0
        if stack:
            pindent, px, py, pw, ph, pcls, pvis = stack[-1]
            if pcls.endswith("ScrollView") and not at_top:
                py -= max(0, (b - t) - ph)
            if pvis != "V":
                vis = pvis
        x, y = px + l, py + t
        stack.append((indent, x, y, r - l, b - t, cls, vis))
        if ident.endswith("/" + key):
            print(x, y, x + r - l, y + b - t, vis)
            return


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], len(sys.argv) > 3 and sys.argv[3] == "top")
