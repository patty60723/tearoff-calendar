"""P7-4a 診斷：把 diag.sh 存下來的原始輸出整理成摘要，並判斷「點了沒反應」屬於哪一種。

用法：
  python3 diag_summary.py summary <快照資料夾>
      一行摘要（key=value，用「; 」分隔）。缺的資料寫成 missing，不會猜。
  python3 diag_summary.py classify <點之前的快照> <點之後的快照> <點之前的狀態> <點之後的狀態>
      一行判斷：verdict=<分類>; reason=…; evidence=…
  python3 diag_summary.py classify-all <diag 資料夾>
      依序找出每一次點擊（before-tap 與之後的 after-tap／miss），逐一判斷（診斷 workflow 用）。

分類（判斷依序進行，前面的條件成立就停）：
  HANDLED              點擊後狀態有變
  INSUFFICIENT         必要的資料缺少或讀不懂（列出缺什麼）；不做任何推論
  COORDINATE_MISS      點擊座標不在目標元件裡
  OBSCURED             點擊位置最上層可以接收觸控的視窗不是我們的 App
  NOT_FOREGROUND       輸入焦點或最上層的 Activity 不是我們的 App
  APP_NOT_PROCESSING   我們視窗的輸入佇列卡住，而且有第二個證據（系統判定沒回應，或主執行緒堆疊不是閒置）
  DELIVERED_ELSEWHERE  事件記錄顯示觸控交給了別的視窗
  DELIVERED_NO_CHANGE  事件記錄顯示觸控交給了我們的視窗，App 沒有在忙，但狀態沒變
  UNKNOWN              以上都不成立；列出已知的證據
只有 WaitQueue 不是 0、沒有其他證據時，不判成 APP_NOT_PROCESSING（寫在 reason 裡，結果是 UNKNOWN）。
"""
import os
import re
import sys

APP = os.environ.get("PKG", "com.patty60723.tearoff")
LAUNCHER = os.environ.get("LAUNCHER_PKG", "com.google.android.apps.nexuslauncher")


def read(d, name):
    p = os.path.join(d, name)
    try:
        with open(p, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return None


def meta(d):
    out = {}
    for line in (read(d, "meta.txt") or "").splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def ints(text):
    try:
        return [int(x) for x in text.split()]
    except (ValueError, AttributeError):
        return None


def section(text, header, stop_indent=None):
    """dumpsys 的區段：從 header 那一行開始，到縮排回到 header 同層（或更外層）為止。"""
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.strip().startswith(header):
            indent = len(line) - len(line.lstrip())
            out = []
            for nxt in lines[i + 1:]:
                if nxt.strip() and len(nxt) - len(nxt.lstrip()) <= indent:
                    break
                out.append(nxt)
            return out
    return None


NAME = re.compile(r"name='([^']*)'")
FRAME = re.compile(r"frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def parse_input(text):
    """dumpsys input → 焦點視窗、視窗清單（由上到下）、各連線的佇列、上一次 ANR 的原因。"""
    if not text or "Input Dispatcher State" not in text:
        return None
    info = {"focused_windows": [], "windows": [], "connections": {}, "touched": [], "last_anr": None}
    sec = section(text, "FocusedWindows:")
    if sec:
        info["focused_windows"] = [m.group(1) for line in sec for m in [NAME.search(line)] if m]
    sec = section(text, "TouchStatesByDisplay:")
    if sec:
        info["touched"] = [m.group(1) for line in sec for m in [NAME.search(line)] if m]
    # 視窗清單：Display 0 底下的 Windows:（由上到下）
    disp = text.find("Display: 0")
    if disp >= 0:
        sec = section(text[disp:], "Windows:")
        for line in sec or []:
            m = NAME.search(line)
            f = FRAME.search(line)
            if not m or not f:
                continue
            flags = line
            info["windows"].append({
                "name": m.group(1),
                "frame": tuple(int(x) for x in f.groups()),
                # SPY（launcher 的手勢監聽）只會收到副本、不會擋住下面的視窗
                "touchable": "NOT_TOUCHABLE" not in flags and "NOT_VISIBLE" not in flags and "SPY" not in flags,
            })
    sec = section(text, "Connections:")
    if sec:
        cur = None
        for line in sec:
            m = re.search(r"(?:channelName|inputChannelName)='([^']*)'", line)
            if m:
                cur = m.group(1)
                info["connections"][cur] = {
                    "responsive": None if "responsive=" not in line else "responsive=true" in line,
                    "wait": 0, "outbound": 0,
                }
                continue
            if cur is None:
                continue
            w = re.search(r"WaitQueue: length=(\d+)", line)
            o = re.search(r"OutboundQueue: length=(\d+)", line)
            if w:
                info["connections"][cur]["wait"] = int(w.group(1))
            if o:
                info["connections"][cur]["outbound"] = int(o.group(1))
    m = re.search(r"at time of last ANR:.*?Reason: ([^\n]*)", text, re.S)
    if m:
        info["last_anr"] = m.group(1).strip()
    return info


def conn_for(info, pkg):
    for name, c in info["connections"].items():
        if pkg in name:
            return name, c
    return None, None


def top_window_at(info, x, y):
    for w in info["windows"]:
        l, t, r, b = w["frame"]
        if w["touchable"] and l <= x < r and t <= y < b:
            return w["name"]
    return None


def proc_states(d):
    text = read(d, "ps.txt")
    if text is None:
        return None
    out = {}
    for line in text.splitlines():
        parts = line.split()
        if len(parts) >= 5:
            out[parts[-1]] = parts[2]
    return out


def events(d):
    text = read(d, "events.txt")
    if text is None:
        return None
    rows = []
    for line in text.splitlines():
        m = re.match(r"(\d\d-\d\d \d\d:\d\d:\d\d)\.\d+\s+.*?\s(am_anr|input_interaction|input_focus|am_kill|am_proc_died|am_proc_start|am_crash|wm_on_resume_called|wm_on_paused_called|am_focused_root_task)\s*:?\s*(.*)", line)
        if m:
            rows.append((m.group(1), m.group(2), m.group(3)))
    return rows


def stack_samples(d, pkg):
    """stack-<pkg>.txt 裡每一個取樣：(狀態, 最上面的 Java 位置, 是不是在 Looper 等訊息)。沒有檔案是 None。"""
    text = read(d, "stack-%s.txt" % pkg)
    if text is None:
        return None
    parts = re.split(r"^=== sample \d+\s*$", text, flags=re.M)
    samples = []
    for part in parts:
        if not part.strip():
            continue
        m = re.search(r'^"main".*?\b(Runnable|Native|Blocked|Waiting|TimedWaiting|Sleeping|Suspended|WaitingForGcToComplete|WaitingForCheckPointsToRun)\b', part, re.M)
        if not m:
            samples.append(("unreadable", "?", False))
            continue
        frame = re.search(r"^\s+at (\S+?)\(", part, re.M)
        top = frame.group(1) if frame else "?"
        samples.append((m.group(1), top, top == "android.os.MessageQueue.nativePollOnce"))
    return samples


def main_stack(d, pkg):
    """摘要用：每個取樣寫成 狀態:位置[:idle]，用 | 分開。"""
    samples = stack_samples(d, pkg)
    if samples is None:
        return None
    if not samples:
        return "unreadable"
    return "|".join("%s:%s%s" % (st, top, ":idle" if idle else "") for st, top, idle in samples)


def main_thread_stuck(d, pkg):
    """主執行緒卡住的證據：至少兩個取樣、都讀得到、都不是在等訊息、而且停在同一個 Java 位置。
    回傳 True／False；取樣不夠（例如沒有 root）是 None。"""
    samples = stack_samples(d, pkg)
    if not samples or len(samples) < 2 or any(st == "unreadable" for st, _, _ in samples):
        return None
    tops = {top for _, top, _ in samples}
    return all(not idle for _, _, idle in samples) and len(tops) == 1 and "?" not in tops


def dispatch(d):
    """InputDispatcher 的派送記錄：(時間, channel, 動作, handled)。沒有檔案是 None。"""
    text = read(d, "dispatch.txt")
    if text is None:
        return None
    rows = []
    for line in text.splitlines():
        m = re.match(r"(\d\d-\d\d \d\d:\d\d:\d\d)\.\d+.*?channel '([^']*)' ~ (\w+)(.*)", line)
        if m:
            h = re.search(r"handled=(true|false)", m.group(4))
            rows.append((m.group(1), m.group(2), m.group(3), h.group(1) if h else None))
    return rows


def frames(d):
    m = re.search(r"Total frames rendered: (\d+)", read(d, "gfx.txt") or "")
    return int(m.group(1)) if m else None


def short(name):
    if not name:
        return "none"
    if APP in name:
        return "APP"
    if "Application Not Responding" in name:
        return "ANR_DIALOG"
    if LAUNCHER in name:
        return "LAUNCHER"
    return name.split()[-1][:60] if name.split() else name[:60]


def summary(d):
    if not os.path.isdir(d):
        return {"error": "no such snapshot: %s" % d}
    s = {}
    mt = meta(d)
    s["tag"] = mt.get("tag", "missing")
    s["time"] = mt.get("device_time", "missing")
    focus = read(d, "window-focus.txt")
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}]*)\}", focus or "")
    s["focus"] = short(m.group(1)) if m else ("missing" if focus is None else "none")
    acts = read(d, "activities.txt")
    m = re.search(r"topResumedActivity=ActivityRecord\{\S+ \S+ (\S+)", acts or "")
    s["top_resumed"] = short(m.group(1)) if m else ("missing" if acts is None else "none")
    info = parse_input(read(d, "input.txt"))
    if info is None:
        s["input"] = "missing"
    else:
        s["input_focus"] = ",".join(short(w) for w in info["focused_windows"]) or "none"
        _, c = conn_for(info, APP)
        s["app_conn"] = "none" if c is None else "responsive=%s,wait=%d,out=%d" % (c["responsive"], c["wait"], c["outbound"])
        _, c = conn_for(info, LAUNCHER)
        s["launcher_conn"] = "none" if c is None else "responsive=%s,wait=%d,out=%d" % (c["responsive"], c["wait"], c["outbound"])
        tap = ints(mt.get("tap"))
        if tap and len(tap) == 2:
            s["top_window_at_tap"] = short(top_window_at(info, *tap))
        s["windows"] = len(info["windows"])
        if info["last_anr"]:
            s["last_anr"] = info["last_anr"][:120]
    ps = proc_states(d)
    if ps is None:
        s["procs"] = "missing"
    else:
        s["app_proc"] = ps.get(APP, "absent")
        s["launcher_proc"] = ps.get(LAUNCHER, "absent")
    ev = events(d)
    if ev is None:
        s["events"] = "missing"
    else:
        anrs = [e for e in ev if e[1] == "am_anr"]
        s["am_anr"] = "%d%s" % (len(anrs), (" last=" + anrs[-1][2][:80]) if anrs else "")
        inter = [e for e in ev if e[1] == "input_interaction"]
        s["last_interaction"] = (inter[-1][0] + " " + inter[-1][2][:120]) if inter else "none"
    f = frames(d)
    s["frames_2s"] = "missing" if f is None else str(f)
    for pkg, key in ((APP, "app_main"), (LAUNCHER, "launcher_main")):
        st = main_stack(d, pkg)
        if st is not None:
            s[key] = st
    return s


def fmt(s):
    return "; ".join("%s=%s" % kv for kv in s.items())


def classify(before, after, state_before, state_after):
    missing = []
    if not os.path.isdir(before):
        missing.append("before")
    if not os.path.isdir(after):
        missing.append("after")
    if missing:
        return "INSUFFICIENT", "snapshot missing: " + ",".join(missing), ""
    if state_before and state_after and state_before != state_after:
        return "HANDLED", "state %s -> %s" % (state_before, state_after), ""
    mb, ma = meta(before), meta(after)
    tap = ints(mb.get("tap"))
    target = ints(mb.get("target"))
    ib = parse_input(read(before, "input.txt"))
    ia = parse_input(read(after, "input.txt"))
    for name, ok in (("tap", tap and len(tap) == 2), ("target", target and len(target) == 4),
                     ("input(before)", ib is not None), ("input(after)", ia is not None),
                     ("state", bool(state_before) and bool(state_after))):
        if not ok:
            missing.append(name)
    if missing:
        return "INSUFFICIENT", "missing or unreadable: " + ",".join(missing), ""
    x, y = tap
    l, t, r, b = target
    if not (l <= x < r and t <= y < b):
        return "COORDINATE_MISS", "tap %d,%d outside target %s" % (x, y, target), ""
    if not ib["windows"]:
        return "INSUFFICIENT", "window list empty in input dump", ""
    evidence = []
    top = top_window_at(ib, x, y)
    evidence.append("top_window=%s" % short(top))
    if top is None or APP not in top:
        return "OBSCURED", "topmost touchable window at tap is %s" % short(top), "; ".join(evidence)
    sa = summary(after)
    focus_ok = APP in "".join(ib["focused_windows"]) and sa.get("top_resumed") == "APP"
    evidence.append("input_focus=%s top_resumed=%s" % (",".join(short(w) for w in ib["focused_windows"]) or "none", sa.get("top_resumed")))
    if not focus_ok:
        return "NOT_FOREGROUND", "input focus / top resumed activity is not the app", "; ".join(evidence)
    _, c = conn_for(ia, APP)
    stack = main_stack(after, APP)
    evidence.append("app_conn=%s app_main=%s frames_2s=%s" % (c, stack, frames(after)))
    if c is not None and c["wait"] > 0:
        busy_stack = main_thread_stuck(after, APP) is True
        if c["responsive"] is False or busy_stack:
            return "APP_NOT_PROCESSING", "app input queue stuck (wait=%d) and %s" % (
                c["wait"], "dispatcher marks it unresponsive" if c["responsive"] is False else "main thread stuck at the same frame in 2 samples"), "; ".join(evidence)
        unsure = "app wait queue=%d but no second evidence (responsive=%s, main=%s)" % (c["wait"], c["responsive"], stack)
    else:
        unsure = None
    t0, t1 = mb.get("device_time", ""), ma.get("device_time", "")
    # 最直接的證據：InputDispatcher 的派送記錄（診斷 workflow 才有）
    dl = [r for r in (dispatch(after) or []) if t0 <= r[0] <= t1]
    if dl:
        app_rows = [r for r in dl if APP in r[1]]
        evidence.append("dispatch=%d rows, app=%d, handled=%s" % (len(dl), len(app_rows), sorted({r[3] for r in app_rows if r[3]})))
        if app_rows:
            if unsure:
                return "UNKNOWN", unsure, "; ".join(evidence)
            return "DELIVERED_NO_CHANGE", "dispatcher delivered the touch to the app window, state unchanged", "; ".join(evidence)
        others = sorted({short(r[1]) for r in dl if "Gesture Monitor" not in r[1]})
        if others:
            return "DELIVERED_ELSEWHERE", "dispatcher delivered the touch to %s" % ",".join(others), "; ".join(evidence)
    ev = events(after) or []
    inter = [e for e in ev if e[1] == "input_interaction" and t0 <= e[0] <= t1]
    if inter:
        last = inter[-1][2]
        evidence.append("interaction=%s" % last[:120])
        if APP in last:
            if unsure:
                return "UNKNOWN", unsure, "; ".join(evidence)
            return "DELIVERED_NO_CHANGE", "touch delivered to the app window, state unchanged", "; ".join(evidence)
        return "DELIVERED_ELSEWHERE", "interaction went to %s" % short(last), "; ".join(evidence)
    return "UNKNOWN", unsure or "no interaction event between the snapshots", "; ".join(evidence)


def classify_all(root):
    if not os.path.isdir(root):
        print("verdict=INSUFFICIENT; reason=no diag folder %s" % root)
        return
    snaps = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
    for i, d in enumerate(snaps):
        if not d.endswith("-before-tap"):
            continue
        key = d.split("-", 1)[1][: -len("-before-tap")]
        after = next((s for s in snaps[i + 1:] if s.split("-", 1)[1] in (key + "-after-tap", key + "-miss")), None)
        sb = read(os.path.join(root, d), "state.txt")
        sa = read(os.path.join(root, after), "state.txt") if after else None
        v, reason, ev = classify(os.path.join(root, d), os.path.join(root, after or "none"), (sb or "").strip(), (sa or "").strip())
        print("%s: verdict=%s; reason=%s; evidence=%s" % (key, v, reason, ev))


def main(argv):
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    cmd = argv[1]
    if cmd == "summary" and len(argv) == 3:
        print(fmt(summary(argv[2])))
        return 0
    if cmd == "classify" and len(argv) == 6:
        v, reason, ev = classify(*argv[2:6])
        print("verdict=%s; reason=%s; evidence=%s" % (v, reason, ev))
        return 0
    if cmd == "classify-all" and len(argv) == 3:
        classify_all(argv[2])
        return 0
    print("usage error: %s" % " ".join(argv[1:]), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
