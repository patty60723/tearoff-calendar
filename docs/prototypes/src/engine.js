  const INK = '#453D38', TERRA = '#B96F62', PAPER = '#FCFAF5', SAGE = '#91A49B', PINK = '#D8ADA5';

  // ---------- 預先決定的筆觸影格 ----------
  // 每個部位、每張影格都有一組固定的相位，第一次用到時算好就存起來，之後完全重現。
  const FRAMES = 5;
  const SEQUENCE = [0, 3, 1, 4, 2];
  const BOIL_MS = 1000 / 6;
  const RAIN_MS = 1000 / 8;

  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function hash(s) { let h = 2166136261; for (const c of s) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; }
  const phaseCache = new Map();
  function ph(part, v) {
    const key = part + '#' + v;
    if (!phaseCache.has(key)) {
      const r = mulberry32(hash(part) + v * 7919);
      phaseCache.set(key, Array.from({ length: 6 }, () => r() * Math.PI * 2));
    }
    return phaseCache.get(key);
  }

  const TAU = Math.PI * 2;
  // 開放筆畫的位移：兩端（關節、腳底）為 0，中間最大；'tip' 模式只有根部固定
  function openNoise(p, t, amp, mode) {
    const env = mode === 'tip' ? Math.pow(t, 1.1) : Math.pow(Math.sin(Math.PI * t), 0.8);
    return amp * env * (0.6 * Math.sin(TAU * t + p[0]) + 0.3 * Math.sin(2 * TAU * t + p[1]) + 0.15 * Math.sin(3 * TAU * t + p[2]));
  }
  function widthVar(p, t, k) {
    return 1 + k * (0.7 * Math.sin(TAU * 1.3 * t + p[3]) + 0.3 * Math.sin(TAU * 2.7 * t + p[4]));
  }
  // 封閉輪廓的位移：只用 2、3、5 次諧波，所以中心和大小不會跑掉
  function loopNoise(p, a, amp) {
    return amp * (0.55 * Math.sin(2 * a + p[0]) + 0.3 * Math.sin(3 * a + p[1]) + 0.15 * Math.sin(5 * a + p[2]));
  }

  // ---------- 幾何 ----------
  function catmull(pts, perSeg) {
    const out = [], n = pts.length;
    for (let i = 0; i < n - 1; i++) {
      const p0 = pts[Math.max(i - 1, 0)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(i + 2, n - 1)];
      for (let s = 0; s < perSeg; s++) {
        const t = s / perSeg, t2 = t * t, t3 = t2 * t;
        const f = (k) => 0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
        out.push([f(0), f(1)]);
      }
    }
    out.push(pts[n - 1].slice());
    return out;
  }
  function normalAt(pts, i, closed) {
    const n = pts.length;
    const a = closed ? pts[(i - 1 + n) % n] : pts[Math.max(i - 1, 0)];
    const b = closed ? pts[(i + 1) % n] : pts[Math.min(i + 1, n - 1)];
    const tx = b[0] - a[0], ty = b[1] - a[1], l = Math.hypot(tx, ty) || 1;
    return [-ty / l, tx / l];
  }
  const fmt = (p) => p[0].toFixed(2) + ' ' + p[1].toFixed(2);

  /** 有粗細的筆畫，畫成實心形狀：中心線、寬度、垂直位移都隨位置 t 變 */
  function brush(center, widthAt, offsetAt) {
    const n = center.length;
    const disp = center.map((c, i) => {
      const t = i / (n - 1), nm = normalAt(center, i, false), o = offsetAt(t);
      return [c[0] + nm[0] * o, c[1] + nm[1] * o];
    });
    const L = [], R = [];
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1), nm = normalAt(disp, i, false), w = widthAt(t) / 2;
      L.push([disp[i][0] + nm[0] * w, disp[i][1] + nm[1] * w]);
      R.push([disp[i][0] - nm[0] * w, disp[i][1] - nm[1] * w]);
    }
    const we = Math.max(widthAt(1) / 2, 0.05), ws = Math.max(widthAt(0) / 2, 0.05);
    let d = 'M' + fmt(L[0]);
    for (let i = 1; i < n; i++) d += 'L' + fmt(L[i]);
    d += `A${we.toFixed(2)} ${we.toFixed(2)} 0 0 0 ${fmt(R[n - 1])}`;
    for (let i = n - 2; i >= 0; i--) d += 'L' + fmt(R[i]);
    d += `A${ws.toFixed(2)} ${ws.toFixed(2)} 0 0 0 ${fmt(L[0])}Z`;
    return d;
  }

  /** 封閉的輪廓：回傳 [填色用的中心線, 輪廓筆畫（環）] */
  function ring(loop, widthAt, offsetAt) {
    const n = loop.length;
    const disp = loop.map((c, i) => {
      const a = (i / n) * TAU, nm = normalAt(loop, i, true), o = offsetAt(a);
      return [c[0] + nm[0] * o, c[1] + nm[1] * o];
    });
    const outer = [], inner = [];
    for (let i = 0; i < n; i++) {
      const a = (i / n) * TAU, nm = normalAt(disp, i, true), w = widthAt(a) / 2;
      outer.push([disp[i][0] + nm[0] * w, disp[i][1] + nm[1] * w]);
      inner.push([disp[i][0] - nm[0] * w, disp[i][1] - nm[1] * w]);
    }
    const loopD = (pts) => 'M' + pts.map(fmt).join('L') + 'Z';
    return { fill: loopD(disp), stroke: loopD(outer) + loopD(inner.slice().reverse()), points: disp };
  }

  // ---------- 墨丸的造型 ----------
  const HEAD_C = [60, 47.5];
  const HEAD_N = 72;
  function headBase(a) {
    // 不對稱的手繪頭型：左下略寬，右上（一筆的根部附近）微微鼓起
    const bump = 1.1 * Math.pow(Math.max(0, Math.cos(a + 1.15)), 4);
    const rx = 22.5 * (1 + 0.035 * Math.sin(2 * a + 0.5)) + bump;
    const ry = 20 * (1 + 0.03 * Math.cos(3 * a + 0.2)) + bump;
    return [HEAD_C[0] + rx * Math.cos(a), HEAD_C[1] + ry * Math.sin(a)];
  }
  const HEAD_LOOP = Array.from({ length: HEAD_N }, (_, i) => headBase((i / HEAD_N) * TAU));
  const ROOT_INDEX = Math.round(((-1.78 + TAU) % TAU) / TAU * HEAD_N); // 一筆的根部，在頭頂偏左

  // 姿勢：每個場景、每個時間點的骨架控制點（主要動作，平滑）
  function pose(scene, time) {
    const P = {};
    if (scene === 'normal') {
      P.headAngle = 0;
      P.body = [[60.5, 68.6], [61.4, 78], [59.6, 90], [61, 101]];
      P.armL = [[60.5, 77], [55.5, 82.5], [52.6, 89], [51.6, 96]];
      P.armR = [[60.8, 77], [65.8, 80.8], [68.8, 86.2], [67.6, 93]];
      P.legL = [[61, 101], [59, 109], [57.6, 118], [57, 127], [52.4, 127.6]];
      P.legR = [[61, 101], [64.8, 108], [66.8, 114], [69.2, 119.6], [70.6, 124.2], [71, 126.6], [75, 125.6]];
      P.tail = [[54, 70.6], [53.2, 76], [53.4, 81.2], [51.8, 87.2]];
      P.flick = 'normal'; P.face = 'normal';
    } else if (scene === 'sunny') {
      // 慢慢伸懶腰：5.5 秒一個來回
      const s = (1 - Math.cos(TAU * time / 5500)) / 2;
      P.headAngle = -4 - 8 * s;
      P.body = [[60.5, 68.6], [62.6 + 1.2 * s, 78], [61.6 + 0.6 * s, 90], [61, 101]];
      // 雙手往斜上方打開伸展，手一直在頭的外側
      P.armL = [[60.5, 77], [53, 72 - 1.5 * s], [44.5 - 1.5 * s, 65 - 3 * s], [36.5 - 2.5 * s, 57 - 5 * s], [31 - 3 * s, 52 - 6 * s]];
      P.armR = P.armL.map(([x, y], i) => i === 0 ? [60.8, 77] : [121.2 - x, y]);
      P.legL = [[61, 101], [59, 109], [57.6, 118], [57, 127], [52.4, 127.6]];
      P.legR = [[61, 101], [64.8, 108], [66.8, 114], [69.2, 119.6], [70.6, 124.2], [71, 126.6], [75, 125.6]];
      const sway = (30 + 6 * Math.sin(TAU * time / 2600)) * Math.PI / 180;
      const base = [[54, 70.6], [53.2, 76], [53.4, 81.2], [51.8, 87.2]];
      P.tail = base.map(([x, y]) => {
        const dx = x - 54, dy = y - 70.6;
        return [54 + dx * Math.cos(sway) + dy * Math.sin(sway), 70.6 - dx * Math.sin(sway) + dy * Math.cos(sway)];
      });
      P.flick = 'perked'; P.face = 'sunny';
    } else {
      P.headAngle = 3;
      P.umbrellaAngle = -10 + 1.8 * Math.sin(TAU * time / 4200); // 以手為支點輕輕晃
      P.hand = [82, 78];
      P.body = [[60.5, 68.6], [59.1, 79], [59.6, 90], [61, 101]];
      P.armL = [[60.5, 77], [55, 80.2], [53.6, 85.6], [57.6, 87.6]];
      P.armR = [[60.8, 77], [67, 80.6], [75, 81.4], [80.5, 79]];
      P.legL = [[61, 101], [59.6, 109], [58.6, 118], [58, 127], [53.5, 127.5]];
      P.legR = [[61, 101], [63, 109], [63.8, 118], [64.5, 127], [69, 127]];
      P.tail = [[54, 70.6], [53.4, 76], [53.8, 81.6], [53.6, 88]];
      P.flick = 'drooped'; P.face = 'rainy';
    }
    return P;
  }

  const FLICK = {
    normal: [[0, 0], [-0.8, -6.5], [3.5, -12], [11, -14.5]],
    perked: [[0, 0], [-0.5, -8], [4, -14], [11.5, -16.5]],
    drooped: [[0, 0], [-2, -4], [-7, -5.5], [-12.5, -3]],
  };

  // 傘面的中心線（以握傘的手為原點）
  const CANOPY = (() => {
    const pts = [];
    for (let i = 0; i <= 24; i++) {
      const u = i / 24;
      pts.push([-34 * Math.cos(Math.PI * u), -64 - 24 * Math.pow(Math.sin(Math.PI * u), 0.85)]);
    }
    for (let i = 1; i < 32; i++) {
      const s = i / 32;
      pts.push([34 - 68 * s, -64 - 3.2 * Math.sin(Math.PI * ((4 * s) % 1))]);
    }
    return pts;
  })();

  // ---------- 一個墨丸（建一次 DOM，之後只改屬性） ----------
  const NS = 'http://www.w3.org/2000/svg';
  function el(parent, tag, attrs) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    parent.appendChild(e);
    return e;
  }
