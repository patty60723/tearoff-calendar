// W4：從定稿參考 weather.js 匯出包袱 B1 的筆畫，給 Kotlin 的 JVM 測試比對。
// 用法：node docs/prototypes/tools/export-golden-bundle.js > app/src/test/resources/mowan/golden-bundle.json
//
// 同一張畫面分別畫「有包袱」和「沒有包袱」，多出來的那一段就是包袱（布包、背帶、皺褶、布角、結、碎花）。
// 只比包袱本身，所以晴、雨也可以用（weather.js 的晴、雨環境是預覽用，跟 App 不同，見整合規格 §5.7）。
// 座標容差 0.011，不透明度容差 0.0006。
'use strict';
const api = require('./load-weather.js')();

const CASES = [
  ['normal', api.PLAIN.normal, [0]],
  ['sunny', api.PLAIN.sunny, [0, 1375, 2750, 4125]],
  ['rainy', api.PLAIN.rainy, [1050, 3150]],
  ['hot', api.SCENES.hot, [650, 1050, 2200]],
  ['cloudy', api.SCENES.cloudy, [1500]],
  ['fog', api.SCENES.fog, [1250, 2500]],
  ['thunder', api.SCENES.thunder, [2100, 9150]],
  ['cold', api.SCENES.cold, [300, 2300]],
  ['snow', api.SCENES.snow, [2000, 3300]],
];
const STRIKE = api.STRIKES[0];

function paths(svg) {
  const out = [];
  const re = /<path ([^>]*)\/>/g;
  let m;
  while ((m = re.exec(svg))) {
    const a = {};
    m[1].replace(/([\w-]+)="([^"]*)"/g, (_, k, v) => { a[k] = v; });
    out.push(a);
  }
  return out;
}

const out = { source: 'docs/prototypes/src/weather.js drawFrame（bundle B1）', page: 14, frames: [] };
const parts = api.partsAt(14), caps = api.getCapabilities(parts);
for (const [id, scene, times] of CASES) {
  times.forEach((t, i) => {
    const v = (i + id.length) % 5;
    const P = () => scene.pose(t, caps, id === 'snow' ? 'B' : 'A');
    const draw = (bundle) => paths(api.drawFrame({ P: P(), parts, v, clock: t, envList: scene.env, bundle, uid: 'g' }));
    const withB = draw('B1'), without = draw(null);
    let start = 0;
    while (start < without.length && withB[start].d === without[start].d) start++;
    const count = withB.length - without.length;
    // 包袱是連續的一段，後面的筆畫要跟沒有包袱時一樣
    for (let k = start; k < without.length; k++) if (withB[k + count].d !== without[k].d) throw new Error(`${id} ${t}: 包袱不是連續的一段`);
    const ops = withB.slice(start, start + count).map((a) => {
      const o = { d: a.d, fill: a.fill };
      for (const k of ['opacity', 'fill-rule']) if (a[k] != null) o[k] = a[k];
      return o;
    });
    out.frames.push({
      weather: id, t, variant: v,
      sinceStrike: id === 'thunder' && t >= STRIKE && t < STRIKE + 1000 ? t - STRIKE : null,
      indexInFrame: start, ops,
    });
  });
}
process.stdout.write(JSON.stringify(out));
