// W1：從定稿參考 weather.js 匯出天氣姿勢的黃金數值，給 Kotlin 的 JVM 測試比對。
// 用法：node docs/prototypes/tools/export-golden-weather.js > app/src/test/resources/mowan/golden-weather.json
//
// 這裡只匯出「姿勢」（骨架控制點、頭的角度與位移、表情、一筆、傘的握點、動作量），不含環境與筆畫外形；
// 那些在 W3 匯出。座標跟 golden.json 一樣：viewBox 0 −20 120 160，y 朝下，頭的轉動中心 (60.5, 66)。
// 姿勢還沒套用「沒有腳時落到地面」（GROUND_DY），那是畫的時候才做的。
'use strict';
const api = require('./load-weather.js')();

const PAGES = [1, 2, 3, 4, 5, 6, 8, 10, 14];
const pts = (a) => a.map(([x, y]) => [x, y]);
function record(P) {
  return {
    body: pts(P.body), armL: pts(P.armL), armR: pts(P.armR), legL: pts(P.legL), legR: pts(P.legR), tail: pts(P.tail),
    headAngle: P.headAngle || 0, headDy: P.headDy || 0, headDx: P.headDx || 0,
    gaze: P.gaze || [0, 0], face: P.face, flick: P.flick,
    hand: P.hand || null, umbrellaAngle: P.hand ? P.umbrellaAngle : null,
    fx: {
      sweat: P.fx.sweat ?? null, scarfUp: !!P.fx.scarfUp, leftOnShaft: !!P.fx.leftOnShaft,
      breath: P.fx.breath || 0, breathU: P.fx.breathU ?? 0, bolt: P.fx.bolt || 0,
    },
  };
}

// 雷雨的驚嚇在 App 裡是事件：傳入「打雷後過了幾毫秒」。原型用固定的 9000 ms，9000 是 90 的倍數，
// 所以原型裡的抖動相位跟「打雷後的時間」完全一樣，可以直接比。
const STRIKE = api.STRIKES[0];
const sinceStrike = (t) => (t >= STRIKE && t < STRIKE + 1000 ? t - STRIKE : null);

const TIMES = {
  hot: [0, 70, 160, 325, 500, 650, 850, 1050, 1450, 1850, 2000, 2200, 2325, 2500, 2800, 3700],
  cloudy: [0, 1500, 3000, 4500, 7000],
  fog: [0, 1250, 2500, 3750, 5600],
  thunder: [0, 2100, 9000, 9020, 9100, 9150, 9300, 9500, 9800, 15000],
  cold: [0, 30, 300, 650, 1000, 1450, 1700, 2300, 3100],
  snow: [0, 1000, 2000, 3030, 3300, 4500],
};
const out = {
  source: 'docs/prototypes/src/weather.js（定稿參考）＋ engine.js、core.js',
  coordinates: 'viewBox 0 -20 120 160，y 朝下；頭以 (60.5, 66) 為中心轉 headAngle 度，再位移 (headDx, headDy)',
  tolerance: 1e-6,
  notes: [
    '雪只有 B（抬頭看雪、身體微縮）。雪 A、熱 B 不匯出。',
    '雷雨的 sinceStrike 是打雷後經過的毫秒數；null 表示沒有在驚嚇。',
    '姿勢還沒套用 GROUND_DY。',
  ],
  weather: [],
  shipped: [],
};
for (const [id, times] of Object.entries(TIMES)) {
  const scene = api.SCENES[id];
  for (const page of PAGES) {
    const caps = api.getCapabilities(api.partsAt(page));
    for (const t of times) {
      const P = scene.pose(t, caps, id === 'snow' ? 'B' : 'A');
      const rec = { weather: id, page, t, ...record(P) };
      if (id === 'thunder') rec.sinceStrike = sinceStrike(t);
      out.weather.push(rec);
    }
  }
}
// 已上線的晴、雨：原型 engine.js 的 pose()，給 Android 現有的 Poses.sunny / rainy 比對
for (const [id, times] of [['sunny', [0, 700, 1375, 2750, 4125, 5500, 8000]], ['rainy', [0, 525, 1050, 2100, 3150, 4200, 6000]]]) {
  for (const t of times) {
    const P = api.pose(id, t);
    out.shipped.push({ weather: id, t, ...record({ ...P, fx: {} }) });
  }
}
process.stdout.write(JSON.stringify(out));
