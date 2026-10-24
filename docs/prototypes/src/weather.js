  // =====================================================================
  //  墨丸 V3 天氣與包袱：定稿的參考程式（Phase 2B 設計收斂）
  //  需要跟 src/engine.js、src/core.js 一起載入。
  //
  //  來源：mowan-weather-revision.html（第二輪審核版），加上動作一致性審核的兩個修正：
  //    - 熱：抬手、放手加了兩個中間姿勢（R_WIPE_SIDE、R_WIPE_MID），手臂不會在過程中縮成一小截
  //    - 冷：輕抱改成雙手在身體前碰在一起、不交叉（L_HUG、R_HUG）
  //
  //  Android 只移植定稿：晴、雨（已在 App）、熱、陰、霧、雷雨（SCENES.thunder，偶發版）、冷、
  //  雪 B（SCENES.snow 的 opt 'B'）、包袱 B1。
  //  下面這些只是歷史參考，不移植：SCENES.thunderOld、雪 A、熱 B、包袱 B1old / B1r1 / B2。
  // =====================================================================
  // =====================================================================
  //  天氣與包袱草案：角色本體沿用 V3（engine.js、core.js），這裡只加新的姿勢、表情與環境。
  //  全部是草案（Proposed），沒有批准。
  // =====================================================================
  const STORM = '#CFC7BC';
  // 雷雨：示範用的固定時間點（App 裡改成每次隨機間隔 15–40 秒）
  const THUNDER_LOOP = 30000, STRIKES = [9000, 23500];   // 雷雨的雲：比陰天的雲深一點（色票外，面積小）
  const SNOW_FILL = '#FFFFFF';

  // ---------- 小工具 ----------
  const ARM5 = {
    R_DEF: [[60.8, 77], [64.3, 79.6], [67.4, 83.2], [69.1, 87.8], [67.6, 93]],
    L_DEF: [[60.5, 77], [56.8, 80.6], [54, 85], [52.6, 90.2], [51.6, 96]],
    // 擦汗：手到右邊太陽穴（被頭擋住一點點，看起來像貼著頭）
    // 手沿著右臉頰外側上下擦（太陽穴在頭的後面，手伸過去會被頭擋住）
    R_WIPE_LO: [[60.8, 77], [67.5, 74.5], [74, 70.5], [79, 65], [82.4, 59.6]],
    // 抬手、放手的中間姿勢：讓手臂在過程中維持長度（直接內插會縮成一小截）
    R_WIPE_SIDE: [[60.8, 77], [65.6, 80.6], [70.8, 82.6], [76.2, 82.2], [80.8, 79.6]],
    R_WIPE_MID: [[60.8, 77], [66.6, 78.8], [72.2, 77.6], [77, 74.4], [80.6, 69.6]],
    R_WIPE_HI: [[60.8, 77], [68.5, 73], [76, 67], [82, 59.5], [85.2, 51.6]],
    // 搧風（拿紙帽）：手在臉旁邊
    R_FAN: [[60.8, 77], [68.5, 73.5], [75.5, 68.5], [80.5, 63.5], [83, 58.5]],
    // 起霧：左手往前伸、摸索
    // 起霧：左手低低地往前伸，慢慢伸出去、收回來（試探，不上下揮）
    L_PROBE_IN: [[60.5, 77], [55.2, 80.6], [49.6, 83], [44, 84.4], [39.6, 84.6]],
    L_PROBE_OUT: [[60.5, 77], [54.6, 80], [48.2, 82.2], [41.8, 83.4], [36.2, 83.4]],
    // 好冷：雙手交叉抱住自己
    // 輕抱：雙手從兩側繞到身體前面，手尖在身體線前碰在一起，不交叉
    L_HUG: [[60.5, 77], [55.2, 80.6], [54.8, 85.4], [58.2, 88], [62.6, 87.6]],
    R_HUG: [[60.8, 77], [66.2, 80.6], [66.6, 85.4], [63.2, 88.2], [58.8, 87.8]],
    // 雪 B：手臂收在身體兩側，微微夾緊
    L_TUCK: [[60.5, 77], [58.4, 81.2], [57.4, 86], [57.2, 91], [57, 95.6]],
    R_TUCK: [[60.8, 77], [63, 81], [64, 85.8], [64.2, 90.8], [64.4, 95.4]],
  };

  /** 讓身體上半部抖一下（腳不動） */
  function jitter(P, dx) {
    if (!dx) return;
    const up = (y) => clamp01((101 - y) / 33);
    for (const k of ['body', 'armL', 'armR', 'tail']) P[k] = P[k].map(([x, y]) => [x + dx * up(y), y]);
    P.headDx = (P.headDx || 0) + dx;
  }

  function basePose() {
    const N = pose('normal', 0);
    return { ...N, armL: ARM5.L_DEF, armR: ARM5.R_DEF, headAngle: 0, headDy: 0, gaze: [0, 0], face: 'normal', flick: 'normal', fx: {} };
  }

  // ---------- 各天氣的姿勢（t：毫秒） ----------
  const SCENES = {
    hot: {
      name: '熱', mood: 'HOT', line: '好熱……一直擦汗。', period: 3200, keys: [0, 900, 2000], still: 500, oldStill: 900,
      env: ['bigSun', 'heat'],
      pose(t, caps, opt) {
        const P = basePose();
        P.face = 'hot'; P.flick = 'drooped';
        P.fx.sweat = (t % 3200) / 3200;
        if (opt === 'B') {
          // B：拿下紙帽搧風
          if (caps.holdR && caps.paperHat) {
            P.armR = ARM5.R_FAN;
            P.fx.fanHat = Math.sin(TAU * t / 400) * 0.45;
            P.fx.hatOff = true;
            P.headAngle = 3;
          }
          return P;
        }
        if (caps.holdR) {
          const lo = ARM5.R_WIPE_LO, hi = ARM5.R_WIPE_HI;
          P.armR = track(t % 3200, [[0, ARM5.R_DEF], [140, ARM5.R_WIPE_SIDE], [280, ARM5.R_WIPE_MID], [500, lo], [900, hi], [1300, lo], [1700, hi], [1920, ARM5.R_WIPE_MID], [2060, ARM5.R_WIPE_SIDE], [2200, ARM5.R_DEF]]);
          P.headAngle = track(t % 3200, [[0, 0], [500, 4], [1700, 4], [2200, 0]]);
        }
        return P;
      },
    },
    cloudy: {
      name: '陰', mood: 'CLOUDY', line: '抬頭看看，雲飄過去了。', period: 6000, keys: [0, 1500, 4500], still: 1500, oldStill: 1500, r1Still: 0,
      env: ['clouds'],
      pose(t, caps) {
        const P = basePose();
        const s = Math.sin(TAU * t / 6000);
        P.headAngle = -3 + 3 * s;
        P.headDy = -0.6;
        P.gaze = caps.gaze ? [1.4 * s, -1.9] : [0, 0];
        return P;
      },
    },
    fog: {
      name: '霧', mood: 'FOG', line: '起霧了，前面看不太清楚。', period: 5000, keys: [0, 1250, 3750], still: 1250, oldStill: 1250,
      env: ['mist'],
      pose(t, caps) {
        const P = basePose();
        P.face = 'fog';
        P.headAngle = -4;
        P.headDy = 0.8;
        P.body = P.body.map(([x, y], i) => [x - [1.2, 0.8, 0.3, 0][i], y]);
        if (caps.holdL) P.armL = mixPts(ARM5.L_PROBE_IN, ARM5.L_PROBE_OUT, (1 - Math.cos(TAU * t / 5000)) / 2);
        return P;
      },
    },
    thunder: {
      name: '雷雨', mood: 'THUNDER', line: '轟隆隆！抱緊傘不要怕。', period: THUNDER_LOOP, keys: [0, STRIKES[0] + 150, 15000],
      still: 15000,
      env: ['storm', 'heavyRain', 'bolt'],
      pose(t, caps) {
        const u = t % THUNDER_LOOP;
        // 偶發：30 秒裡只打兩次雷，其他時間就是抱著傘
        let startle = 0, bolt = 0;
        for (const at of STRIKES) {
          startle = Math.max(startle, env(u, at + 20, at + 120, at + 360, at + 800));
          bolt = Math.max(bolt, env(u, at, at + 40, at + 220, at + 320));
        }
        const P = umbrellaHug(t, caps);
        P.face = startle > 0.5 ? 'startle' : 'rainy';
        P.flick = startle > 0.5 ? 'perked' : 'drooped';
        P.headAngle = (P.headAngle || 0) + 1.2 * startle;
        jitter(P, 0.35 * startle * Math.sin(TAU * u / 90));
        P.fx.bolt = bolt;
        return P;
      },
    },
    thunderOld: {
      name: '雷雨', mood: 'THUNDER', line: '轟隆隆！抱緊傘不要怕。', period: 4200, keys: [0, 1500, 2600],
      env: ['storm', 'heavyRain', 'bolt'],
      still: 1500,
      pose(t, caps) {
        const u = t % 4200;
        const startle = env(u, 1400, 1500, 1800, 2300);
        let P;
        if (caps.holdR) {
          P = { ...basePose(), ...pose('rainy', 0) };
          P.armL = ARM5.L_DEF; P.armR = pose('rainy', 0).armR;
          P.umbrellaAngle = -6 + 0.8 * Math.sin(TAU * t / 4200);
          P.hand = [80.5, 79];
          P.armR = [[60.8, 77], [66.5, 80.6], [73.5, 81.4], [79, 79.6]];
          // 左手也抓住傘柄：抱緊傘
          if (caps.holdL) {
            const g = place([[0, 6]], { x: P.hand[0], y: P.hand[1], rot: (P.umbrellaAngle * Math.PI) / 180 })[0];
            P.armL = [[60.5, 77], [66, 82], [73.5, 85.4], [g[0], g[1]]];
            P.fx.leftOnShaft = true;
          }
        } else {
          P = basePose();
        }
        P.face = startle > 0.3 ? 'startle' : 'rainy';
        P.flick = startle > 0.3 ? 'perked' : 'drooped';
        P.headAngle = (P.headAngle || 0) + 2 * startle;
        jitter(P, 0.7 * startle * Math.sin(TAU * u / 90));
        P.fx.bolt = env(u, 1380, 1420, 1650, 1750);
        return P;
      },
    },
    cold: {
      name: '冷', mood: 'COLD', line: '好冷，抱緊自己發抖。', period: 2800, keys: [0, 300, 1300], still: 2300, oldStill: 300,
      env: ['coldGround'],
      pose(t, caps) {
        const P = basePose();
        coldPose(P, t, caps);
        return P;
      },
    },
    snow: {
      name: '雪', mood: 'SNOW', line: '下雪了！冷得一直發抖。', period: 2800, keys: [0, 300, 1300], still: 2300, oldStill: 300, stillB: 2000,
      env: ['snow'],
      pose(t, caps, opt) {
        const P = basePose();
        if (opt === 'B') {
          // B：抬頭看飄下來的雪，身體微微縮起來；不接雪、沒有道具
          const w = Math.sin(TAU * t / 4000);
          P.headAngle = -4 + 1.5 * w;
          P.headDy = 0.5;
          P.gaze = caps.gaze ? [0.9 * w, -1.8] : [0, 0];
          if (caps.holdL) P.armL = ARM5.L_TUCK;
          if (caps.holdR) P.armR = ARM5.R_TUCK;
          P.fx.scarfUp = 1;
          // 每 4 秒輕輕縮一下
          const u = t % 4000;
          jitter(P, 0.15 * env(u, 3000, 3060, 3500, 3600) * Math.sin(TAU * u / 85));
          return P;
        }
        // 雪：抖得更輕、不呼白氣，讓飄雪當主角
        coldPose(P, t, caps, 0.2, false);
        return P;
      },
    },
  };

  /** 雷雨的撐傘站姿：右手撐傘，左手有空就一起抓住傘柄（左手拿著節日道具時不抓） */
  function umbrellaHug(t, caps) {
    if (!caps.holdR) return basePose();
    const P = { ...basePose(), ...pose('rainy', 0) };
    P.armL = ARM5.L_DEF;
    P.umbrellaAngle = -6 + 0.8 * Math.sin(TAU * t / 4200);
    P.hand = [80.5, 79];
    P.armR = [[60.8, 77], [66.5, 80.6], [73.5, 81.4], [79, 79.6]];
    if (caps.holdL) {
      const g = place([[0, 6]], { x: P.hand[0], y: P.hand[1], rot: (P.umbrellaAngle * Math.PI) / 180 })[0];
      P.armL = [[60.5, 77], [66, 82], [73.5, 85.4], [g[0], g[1]]];
      P.fx.leftOnShaft = true;
    }
    return P;
  }

  function coldPose(P, t, caps, amp = 0.45, breath = true) {
    const u = t % 2800;
    P.face = 'cold'; P.flick = 'drooped';
    if (caps.holdL) P.armL = ARM5.L_HUG;
    if (caps.holdR) P.armR = ARM5.R_HUG;
    // 縮脖子
    P.headDy = 0.8;
    P.fx.scarfUp = 1;
    // 一陣一陣地抖
    const burst = Math.max(env(u, 0, 60, 600, 700), env(u, 1400, 1460, 2000, 2100));
    jitter(P, amp * burst * Math.sin(TAU * u / 85));
    P.fx.breath = breath && caps.smile ? env(u, 800, 1000, 1300, 1700) : 0;
    P.fx.breathU = clamp01((u - 800) / 900);
  }

  // ---------- 繪製：一次組出整張 SVG（草案頁面不需要省 DOM） ----------
  const fx2 = (n) => n.toFixed(2);
  function drawFrame(o) {
    // o: { P, parts, v, clock, scene, bundle, simple }
    const { P, parts, v } = o;
    const caps = getCapabilities(parts);
    const k = 1, out = [];
    const path = (d, a) => out.push(`<path d="${d}"${a || ''}/>`);
    const fill = (c, op) => ` fill="${c}"${op != null && op < 1 ? ` opacity="${op.toFixed(3)}"` : ''}`;
    const ringP = (pts, f, part, w = 1.7, amp = 0.45, op) => {
      const p = ph(part, v);
      const r = ring(pts, (a) => w * k * (1 + 0.12 * Math.sin(2 * a + p[3])), (a) => loopNoise(p, a, amp));
      path(r.fill, fill(f, op));
      path(r.stroke, fill(INK, op) + ' fill-rule="evenodd"');
      return r;
    };
    const strokeP = (pts, w0, w1, part, amp = 0.15, color = INK, mode = 'both', per = 6, op) => {
      const p = ph(part, v);
      path(brush(catmull(pts, per), (t) => lerp(w0, w1, t) * k * widthVar(p, t, 0.1), (t) => openNoise(p, t, amp, mode)), fill(color, op));
    };
    const env_ = new Set(o.envList || []);
    const low = caps.growthFocus, skyOp = low ? 0.55 : 1;
    const clock = o.clock;
    const dy = caps.stand ? 0 : GROUND_DY;   // 沒有腳時整個角色落在地面上

    // ---- 天空 ----
    if (env_.has('bigSun')) {
      path(circleD(99, 4, 10.5), fill(PINK, 0.85 * skyOp));
      path(circleD(99, 4, 14.5), ` fill="none" stroke="${PINK}" stroke-width="1.2" opacity="${skyOp}"`);
    }
    if (env_.has('clouds') || env_.has('storm')) {
      const storm = env_.has('storm');
      CLOUDS.forEach((c, i) => {
        const x = storm ? c.x : ((c.x + (clock / 1000) * c.sp + 30) % 170) - 30;
        ringP(place(c.loop, { x, y: c.y - (storm ? 2 : 0) }), storm ? STORM : CLOUD, 'cloud' + i, 1.5, 0.5, low ? 0.7 : 1);
      });
    }
    if (env_.has('bolt') && P.fx.bolt > 0) {
      const bolt = [[2, 0], [8, 0], [4.2, 8], [9.5, 8], [-1.5, 24], [1.6, 12], [-3.5, 12]];
      ringP(resample(place(bolt, { x: 96, y: 14 }), 40), PINK, 'bolt', 1.1, 0.15, P.fx.bolt);
    }
    // ---- 雨、雪 ----
    if (env_.has('heavyRain')) {
      const step = Math.floor(clock / 125);
      const D = [[10, 0], [20, 52], [8, 95], [26, 30], [16, 120], [112, 18], [116, 70], [108, 110], [28, 85], [4, 60], [118, 40], [100, 128], [34, 10]];
      D.forEach(([x0, y0]) => {
        const y = ((y0 + step * 9) % 150) - 20;
        path(`M${x0} ${y}L${x0 - 2.6} ${y + 8}`, ` fill="none" stroke="${SAGE}" stroke-width="1.6" stroke-linecap="round"`);
      });
    }
    const flakes = [[12, 0, 2.0], [26, 40, 1.6], [8, 78, 2.2], [104, 12, 1.9], [114, 58, 1.6], [98, 96, 2.1], [30, 104, 1.5], [118, 120, 1.9], [44, 20, 1.5], [84, 30, 1.7],
      [20, 128, 1.7], [92, 62, 1.4], [36, 70, 1.8], [110, 88, 1.5], [70, 6, 1.6], [4, 40, 1.4]];
    if (env_.has('snow')) {
      flakes.forEach(([x0, y0, r], i) => {
        const y = ((y0 + clock / 1000 * 7) % 150) - 20, x = x0 + 2.6 * Math.sin(clock / 1100 + i);
        // 停住的畫面：雪花會一直停在那裡，所以不放在頭和臉的範圍裡
        if (o.still && x > 32 && x < 90 && y > 18 + dy && y < 72 + dy) return;
        path(circleD(x, y, r), ` fill="${SNOW_FILL}" stroke="${INK}" stroke-width="0.6"`);
      });
    }
    // ---- 霧（後面那一層） ----
    const mistBand = (y, w, h, dx, part, op) => {
      const pts = resample([[-w / 2, -h / 2], [w / 2, -h / 2], [w / 2 + h / 2, 0], [w / 2, h / 2], [-w / 2, h / 2], [-w / 2 - h / 2, 0]], 48);
      const p = ph(part, v);
      const r = ring(place(pts, { x: 60 + dx, y }), () => 0.01, (a) => loopNoise(p, a, 0.6));
      path(r.fill, fill(CLOUD, op));
    };
    if (env_.has('mist')) {
      mistBand(22, 70, 7, 18 * Math.sin(clock / 4200), 'mist0', 0.8);
      mistBand(112, 120, 9, 14 * Math.sin(clock / 3600 + 1), 'mist1', 0.85);
    }
    // ---- 地面 ----
    if (env_.has('snow') || env_.has('coldGround')) {
      if (env_.has('snow')) ringP(resample([[38, 129.5], [50, 126.4], [62, 125.8], [76, 126.4], [86, 129.5]], 48), SNOW_FILL, 'snowMound', 0.9, 0.2);
      else path(`M44 128.6a18 2.4 0 1 0 36 0a18 2.4 0 1 0 -36 0Z`, fill(INK, 0.07));
    } else if (env_.has('heavyRain')) {
      path(`M40 128.6a22 2.8 0 1 0 44 0a22 2.8 0 1 0 -44 0Z`, fill(SAGE, 0.3));
    } else {
      path(`M46 128.6a16 2.2 0 1 0 32 0a16 2.2 0 1 0 -32 0Z`, fill(INK, 0.07));
    }
    if (env_.has('heat')) {
      // 地面上的熱氣：三條往上飄的波浪線
      [[30, 0], [92, 0.4], [102, 0.75]].forEach(([x, ph0], i) => {
        const u = ((clock / 1800) + ph0) % 1;
        const y0 = 124 - 14 * u;
        const pts = Array.from({ length: 6 }, (_, j) => [x + 1.6 * Math.sin(j * 1.4 + clock / 300 + i), y0 - j * 2.2]);
        strokeP(pts, 0.9, 0.5, 'heat' + i, 0.05, PINK, 'both', 5, Math.sin(Math.PI * u) * 0.9);
      });
    }

    // ---- 包袱（B1 背在背後：在身體前面畫之前） ----
    if ((o.bundle === 'B1' || o.bundle === 'B1r1' || o.bundle === 'B1old') && parts.has('bundle')) drawBundle(o.bundle, 'back');

    // ---- 傘柄 ----
    const umbrella = P.hand && parts.has('armR');
    if (umbrella && dy) P.hand = [P.hand[0], P.hand[1] + dy];   // 沒有腳時傘跟著手一起落下
    if (umbrella) {
      const tr = (pts) => place(pts, { x: P.hand[0], y: P.hand[1], rot: (P.umbrellaAngle * Math.PI) / 180, s: 1 });
      path(brush(catmull(tr([[0, -64], [0, -30], [0, 7]]), 6), () => 2.2, () => 0) + brush(catmull(tr([[0, 7], [-1.5, 11.4], [-5, 11]]), 6), () => 2.2, () => 0), fill(INK));
    }

    // ---- 身體 ----
    const limb = (part, pts, w0, w1, amp) => {
      const p = ph(part, v);
      return brushGeom(catmull(pts, 9), (t) => (w0 + (w1 - w0) * t) * widthVar(p, t, 0.13), (t) => openNoise(p, t, amp, 'both'));
    };
    const sh = (pts) => pts.map(([x, y]) => [x, y + dy]);
    for (const [name, w0, w1] of [['legL', 3.1, 2.3], ['legR', 3.0, 2.2]]) {
      if (!parts.has(name)) continue;
      const lg = limb(name, sh(P[name]), w0, w1, 0.45);
      path(geomPath(lg), fill(INK));
      if (parts.has('redToes')) {
        const j0 = lg.n - 7, a = lg.disp[j0], b = lg.disp[lg.n - 1], id = o.uid + name;
        out.push(`<defs><linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${fx2(a[0])}" y1="${fx2(a[1])}" x2="${fx2(b[0])}" y2="${fx2(b[1])}"><stop offset="0" stop-color="${TERRA}" stop-opacity="0"/><stop offset="0.5" stop-color="${TERRA}" stop-opacity="0.7"/><stop offset="1" stop-color="${TERRA}" stop-opacity="0.95"/></linearGradient></defs>`);
        path(geomPath(lg, j0, 1.12), ` fill="url(#${id})"`);
      }
    }
    if (parts.has('body')) path(geomPath(limb('body', sh(P.body), 3.1, 2.8, 0.5)), fill(INK));
    if (parts.has('armL')) path(geomPath(limb('armL', sh(P.armL), 2.9, 2.1, 0.45)), fill(INK));
    if (parts.has('armR')) path(geomPath(limb('armR', sh(P.armR), 2.9, 2.1, 0.45)), fill(INK));

    // ---- 圍巾 ----
    if (parts.has('scarf')) {
      const pb = ph('band', v), pt = ph('tail', v), ps = ph('split', v);
      const up = P.fx.scarfUp ? -1.0 : 0, wide = P.fx.scarfUp ? 1.12 : 1, jx = P.headDx || 0;
      path(brush(catmull([[51 + jx, 68.6 + up], [55.5 + jx, 71.3 + up], [64 + jx, 71.4 + up], [70 + jx, 67.6 + up]], 8), (t) => (4.4 + 0.6 * Math.sin(Math.PI * t)) * wide * widthVar(pb, t, 0.12), (t) => openNoise(pb, t, 0.4, 'both')), fill(TERRA));
      path(brush(catmull(P.tail, 9), (t) => (3.9 - 1.9 * t) * widthVar(pt, t, 0.14), (t) => openNoise(pt, t, 0.7, 'tip')), fill(TERRA));
      const end = P.tail[P.tail.length - 1], mid = P.tail[2];
      path(brush(catmull([mid, [(mid[0] + end[0]) / 2 + 1.8, (mid[1] + end[1]) / 2 + 0.6], [end[0] + 4, end[1] + 1.2]], 6), (t) => (1.6 - 1.1 * t) * widthVar(ps, t, 0.15), (t) => openNoise(ps, t, 0.35, 'tip')), fill(TERRA));
    }
    // 包袱的背帶畫在身體前面
    if (o.bundle && parts.has('bundle')) drawBundle(o.bundle, 'front');

    // ---- 頭 ----
    const headDy = (P.headDy || 0) + dy;
    out.push(`<g transform="translate(${fx2(P.headDx || 0)} ${fx2(headDy)}) rotate(${fx2(P.headAngle || 0)} 60.5 66)">`);
    if (parts.has('head')) {
      const phd = ph('head', v), heavy = 2.35 + 0.3 * Math.sin(phd[5]);
      const head = ring(HEAD_LOOP, (a) => 2.4 * (1 + 0.13 * (0.7 * Math.sin(2 * a + phd[3]) + 0.3 * Math.sin(3 * a + phd[4]))) + 1.1 * Math.pow(Math.max(0, Math.cos(a - heavy)), 3), (a) => loopNoise(phd, a, 0.75));
      path(head.fill, fill(PAPER));
      path(head.stroke, fill(INK) + ' fill-rule="evenodd"');
      drawFace(P);
      if (parts.has('paperHat') && !P.fx.hatOff && o.hatOn !== false) {
        const hw = headwearLoops('paperHat', head.points);
        hw.out.forEach((h, i) => {
          if (h.pts) ringP(h.pts, h.fill, 'paperHat' + i, 1.35, 0.4);
          else strokeP(h.line, 1.0, 0.8, 'paperHatl' + i, 0.12, h.color);
        });
      }
      if (parts.has('flick')) {
        const root = head.points[ROOT_INDEX], pf = ph('flick', v);
        path(brush(catmull(FLICK[P.flick || 'normal'].map(([x, y]) => [root[0] + x, root[1] + y + 1.2]), 9), (t) => (0.35 + 3.6 * Math.pow(1 - t, 0.85)) * widthVar(pf, t, 0.18), (t) => openNoise(pf, t, 1.4, 'tip')), fill(INK));
      }
      // 汗滴：從左邊太陽穴往下滑
      if (P.fx.sweat != null && parts.has('eyes')) {
        const u = P.fx.sweat, yy = 38 + 8 * ease(u);
        const drop = Array.from({ length: 32 }, (_, i) => {
          const a = (i / 32) * TAU, r = 2.3;
          const pinch = Math.pow(Math.max(0, -Math.sin(a)), 3);
          return [35.4 + r * Math.cos(a) * (1 - 0.85 * pinch), yy + r * Math.sin(a) - 3.4 * pinch];
        });
        ringP(drop, SAGE, 'sweat', 0.9, 0.08, Math.min(1, 1.6 * Math.sin(Math.PI * clamp01(u * 1.1))));
      }
    }
    out.push('</g>');

    // ---- 手上：拿下來的紙帽、雪花 ----
    if (P.fx.fanHat != null && parts.has('armR')) {
      const h = sh(P.armR)[4];
      const pl = { x: h[0] + 1, y: h[1] - 1, rot: -0.5 + P.fx.fanHat };
      ringP(place(HEADWEAR.paperHat.shapes[0].pts, pl), IVORY, 'paperHat0', 1.35, 0.4);
      HEADWEAR.paperHat.lines.forEach((l, i) => strokeP(place(l, pl), 1.0, 0.8, 'paperHatl' + (i + 1), 0.12));
    }
    if (P.fx.flakeOnHand) {
      const h = sh(P.armR)[4];
      path(circleD(h[0] + 0.6, h[1] - 3.2, 1.6), ` fill="${SNOW_FILL}" stroke="${INK}" stroke-width="0.6"`);
    }

    // ---- 傘面、拳頭 ----
    if (umbrella) {
      out.push(`<g transform="translate(${P.hand[0]} ${P.hand[1]}) rotate(${fx2(P.umbrellaAngle)})">`);
      const pc = ph('canopy', v);
      const c = ring(CANOPY, (a) => 2.2 * (1 + 0.12 * Math.sin(2 * a + pc[3])), (a) => loopNoise(pc, a, 0.45));
      path(c.fill, fill(SAGE));
      path(c.stroke, fill(INK) + ' fill-rule="evenodd"');
      path('M-17,-64 Q-13,-79 0,-88 M17,-64 Q13,-79 0,-88 M0,-88 L0,-92', ` fill="none" stroke="${INK}" stroke-width="1.5" stroke-linecap="round"`);
      out.push('</g>');
      path(circleD(P.hand[0], P.hand[1], 3.2), ` fill="${PAPER}" stroke="${INK}" stroke-width="2.2"`);
      if (P.fx.leftOnShaft) {
        const g = sh(P.armL)[3];
        path(circleD(g[0], g[1], 3.0), ` fill="${PAPER}" stroke="${INK}" stroke-width="2.1"`);
      }
    }
    // ---- 前面那層霧、呼出的白氣 ----
    if (env_.has('mist')) mistBand(97, 92, 8, -16 * Math.sin(clock / 3000), 'mist2', 0.62);
    if (P.fx.breath > 0 && parts.has('mouth')) {
      const u = P.fx.breathU, s = 0.9 + 0.7 * u;
      ringP(place(cloudLoop(9, 5), { x: 73 + 8 * u + (P.headDx || 0), y: 58 + headDy - 3 * u, s }), CLOUD, 'breath', 0.7, 0.12, P.fx.breath);
    }
    return out.join('');

    // ---------- 頭裡面：五官 ----------
    function drawFace(P) {
      const gz = P.gaze || [0, 0];
      const faceBrush = (part, pts, w) => { const p = ph(part, v); path(brush(catmull(pts.map(([x, y]) => [x + gz[0] * 0.5, y + gz[1] * 0.5]), 6), (t) => w * (0.85 + 0.15 * Math.sin(Math.PI * t)) * widthVar(p, t, 0.08), (t) => openNoise(p, t, 0.12, 'both')), fill(INK)); };
      const face = caps.expression ? P.face : 'normal';
      if (parts.has('blush')) {
        const op = { sunny: 1, rainy: 0.55, hot: 0.95, cold: 0.95, startle: 0.7, fog: 0.7 }[face] ?? 0.85;
        path('M43.8 52.8a3.2 1.6 0 1 0 6.4 0a3.2 1.6 0 1 0 -6.4 0Z', fill(PINK, op));
        path('M69.3 52.3a3.2 1.6 0 1 0 6.4 0a3.2 1.6 0 1 0 -6.4 0Z', fill(PINK, op));
      }
      if (parts.has('eyes')) {
        const pe = ph('eyes', v), eyeR = 1.95 * (1 + 0.05 * Math.sin(pe[0]));
        const E = [[52, 46.8], [67, 46.3]];
        if (face === 'hot') {
          faceBrush('lidL', [[50, 47.6], [52, 47.9], [54.2, 47.5]], 1.6);
          faceBrush('lidR', [[64.9, 47.1], [67, 47.4], [69.2, 47.0]], 1.6);
        } else if (face === 'cold') {
          // 輕輕閉眼
          faceBrush('lidL', [[49.8, 47.4], [52, 48.0], [54.2, 47.3]], 1.6);
          faceBrush('lidR', [[64.8, 46.9], [67, 47.5], [69.2, 46.8]], 1.6);
        } else if (face === 'fog') {
          // 瞇眼：扁的橢圓
          for (const [i, [x, y]] of E.entries()) {
            const cx = x + gz[0] + 0.1 * Math.sin(pe[1 + i]), cy = y + gz[1] + 0.4;
            path(`M${fx2(cx - 2.3)} ${fx2(cy)}a2.3 1.0 0 1 0 4.6 0a2.3 1.0 0 1 0 -4.6 0Z`, fill(INK));
          }
        } else if (face === 'startle') {
          E.forEach(([x, y], i) => path(circleD(x + 0.1 * Math.sin(pe[1 + i]), y - 0.4, eyeR * 1.3), fill(INK)));
        } else if (face === 'sunny') {
          faceBrush('lidL', [[49.6, 47.1], [52, 48.1], [54.4, 47.1]], 1.7);
          faceBrush('lidR', [[64.6, 46.6], [67, 47.6], [69.4, 46.6]], 1.7);
        } else {
          const eyes = face === 'rainy' ? [[52.4, 47], [67.2, 46.6]] : E;
          eyes.forEach(([x, y], i) => path(circleD(x + gz[0] + 0.1 * Math.sin(pe[1 + i]), y + gz[1], eyeR), fill(INK)));
        }
        if (face === 'rainy' || face === 'cold' || face === 'startle') {
          const bw = face === 'cold' ? 1.2 : 1.5;
          faceBrush('browL', [[49.5, 43.6], [51.8, 42.9], [54, 42.1]], bw);
          faceBrush('browR', [[69.8, 43.1], [67.6, 42.4], [65.4, 41.6]], bw);
        }
      }
      if (parts.has('mouth')) {
        if (face === 'startle') {
          // 張開的小嘴（喘氣、嚇一跳）
          const p = ph('mouthO', v);
          const r = ring(ellipse(60 + gz[0] * 0.5, 55.6, 1.3, 1.5), () => 1.3, (a) => loopNoise(p, a, 0.05));
          path(r.stroke, fill(INK) + ' fill-rule="evenodd"');
        } else {
          const mouth = {
            rainy: [[57.6, 55], [60, 54.2], [62.4, 55]],
            sunny: [[57.5, 54.6], [60, 56.6], [62.5, 54.4]],
            hot: [[57.8, 55.3], [60, 55.9], [62.2, 55.2]],
            cold: [[57.6, 55.2], [58.8, 54.7], [60, 55.2], [61.2, 54.7], [62.4, 55.2]],
            fog: [[57.8, 55.5], [60, 55.8], [62.2, 55.3]],
          }[face] || [[57.4, 54.6], [60, 56.2], [62.6, 54.4]];
          faceBrush('mouth', mouth, face === 'cold' ? 1.5 : 1.9);
        }
      }
    }

    // ---------- 包袱 ----------
    function drawBundle(kind2, layer) {
      const kind = kind2 === 'B1r1' ? 'B1' : kind2;
      const jx = P.headDx || 0;
      if (kind === 'B1') {
        // 修正版：布包掛在左肩後面，四角收到上面打一個結；背帶從結繞到脖子後面、藏在圍巾下（不穿過身體）
        if (layer !== 'back') return;
        const bx = (P.body[1][0] - 61.4) * 0.8 + jx * 0.6;
        // 左手舉得越高，肩膀越往上：包袱被背帶提起一點，下緣往身體擺過來（第一輪 B1r1 是固定不動）
        const raise = kind2 === 'B1r1' || !parts.has('armL') ? 0 : clamp01((96 - P.armL[P.armL.length - 1][1]) / 50);
        const lift = -1.6 * raise, tilt = -6 * raise, pivot = [48, 73.6];
        const at = (pts) => pts.map((p) => { const q = rotAbout(p, tilt, pivot); return [q[0] + bx, q[1] + dy + lift]; });
        strokeP(at([[49.6, 72.6], [52.6, 71.2], [55.6, 70.6], [58.2, 70.8]]), 1.3, 1.1, 'strap', 0.06, INK);
        const sack = resample([[38.6, 82.6], [38.4, 78.4], [40.6, 75.2], [44.4, 73.8], [47.8, 74.6], [51.4, 73.8], [54.2, 75.8], [55.2, 79.8], [54.2, 84.4], [50.8, 87.6], [45.4, 88.4], [40.8, 86.8]], 64);
        ringP(at(sack), SAGE, 'bundle', 1.3, 0.3);
        // 布的皺褶：從結往下散開，很淡
        strokeP(at([[46.6, 75.4], [45.2, 78.6], [44.8, 82]]), 0.7, 0.4, 'fold1', 0.05, INK, 'tip', 5, 0.4);
        strokeP(at([[49.2, 75.4], [50.6, 78.4], [50.8, 81.6]]), 0.7, 0.4, 'fold2', 0.05, INK, 'tip', 5, 0.4);
        // 兩個布角＋中間的結（結壓在布包上緣，看得出是同一塊布綁起來的）
        ringP(resample(at([[46.4, 73.4], [43.4, 69.8], [44.4, 68.6], [47.8, 72.2]]), 32), SAGE, 'earL', 1.0, 0.12);
        ringP(resample(at([[49.2, 72.4], [52, 68.6], [53.4, 69.6], [50.4, 73.6]]), 32), SAGE, 'earR', 1.0, 0.12);
        ringP(at(ellipse(48, 73.6, 2.3, 1.7)), SAGE, 'knot', 1.1, 0.1);
        // 碎花：小、淡
        at([[42.4, 81.4], [48.6, 84.4], [51.4, 79.4]]).forEach(([x, y]) => path(circleD(x, y, 0.55), fill(IVORY, 0.55)));
        return;
      }
      if (kind === 'B1old') {
        // 背在背後：布包從左肩後面露出來，結在上面；背帶從右肩斜到左腰
        if (layer === 'back') {
          const body = resample([[37.5, 79], [40, 73.8], [46, 71.6], [52.4, 73], [54.6, 78.4], [53.6, 85.4], [47.6, 88.6], [40.6, 87.4], [37, 83.4]], 60);
          ringP(place(body, { x: jx * 0.5, y: dy }), SAGE, 'bundle', 1.35, 0.35);
          const knot = [[46.6, 72.2], [44, 67.6], [47.4, 68.2], [48.4, 71.6], [50.4, 67.4], [52.4, 70.4], [49.6, 73]];
          ringP(resample(place(knot, { x: jx * 0.5, y: dy }), 40), SAGE, 'bundleKnot', 1.1, 0.2);
          // 布上的小點（像碎花布）
          [[42, 80], [47.5, 77.5], [49.6, 83.4], [43.6, 85]].forEach(([x, y]) => path(circleD(x + jx * 0.5, y + dy, 0.75), fill(IVORY)));
        } else {
          strokeP(place([[64.6, 72.6], [61.6, 78], [57.6, 84.4], [53.4, 88.6]], { x: jx * 0.6, y: dy }), 1.1, 1.0, 'strap', 0.1, INK);
        }
      } else {
        // B2：斜背在右腰側，背帶從左肩斜下來
        if (layer === 'front') {
          strokeP(place([[56, 72.4], [60.6, 79.6], [65.4, 86.8], [70.2, 92.6]], { x: jx * 0.6, y: dy }), 1.1, 1.0, 'strap', 0.1, INK);
          const body = resample([[66.2, 95.2], [69.4, 91.4], [75, 90.6], [79.2, 93.6], [79.4, 99.4], [75.6, 102.8], [69.4, 102.4], [66, 99.4]], 60);
          ringP(place(body, { x: 0, y: dy }), SAGE, 'bundle', 1.35, 0.35);
          const knot = [[71.4, 91.6], [69.6, 87.6], [72.4, 88.4], [73.4, 91.2], [75.6, 87.8], [76.8, 90.6], [74.6, 92.4]];
          ringP(resample(place(knot, { x: 0, y: dy }), 40), SAGE, 'bundleKnot', 1.1, 0.2);
          [[70.6, 97.2], [75.6, 95.6], [74.8, 100.4]].forEach(([x, y]) => path(circleD(x, y + dy, 0.75), fill(IVORY)));
        }
      }
    }
  }

  // 一般站姿（包袱比較用）
  const PLAIN = {
    normal: { name: '一般', period: 0, keys: [0], env: [], pose: () => basePose() },
    sunny: { name: '晴', period: 5500, keys: [2000], env: ['bigSun'], pose: (t) => ({ ...basePose(), ...pose('sunny', t), headDy: 0, gaze: [0, 0], fx: {}, face: 'sunny', flick: 'perked' }) },
    rainy: { name: '雨', period: 4200, keys: [1050], env: ['heavyRain'], pose: (t) => ({ ...basePose(), ...pose('rainy', t), headDy: 0, gaze: [0, 0], fx: {}, face: 'rainy', flick: 'drooped' }) },
  };
