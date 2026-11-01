// W3：從定稿參考 weather.js 的 drawFrame 匯出新天氣的整張畫面（每一條筆畫），給 Kotlin 的 JVM 測試比對。
// 用法：node docs/prototypes/tools/export-golden-weather-render.js > app/src/test/resources/mowan/golden-weather-render.json
//
// 順序跟 Android 的圖層順序一致（天空 → 雲 → 雨雪 → 後面的霧 → 地面 → 傘柄 → 身體 → 圍巾 → 頭 → 傘面 → 前景），
// 所以直接逐筆比對。座標跟 golden.json 一樣，容差 0.011（原型輸出小數兩位）；不透明度輸出小數三位，容差 0.0006。
'use strict';
const api = require('./load-weather.js')();

const PAGES = [1, 3, 4, 5, 8, 14];
const TIMES = {
  hot: [650, 1050, 2200],
  cloudy: [1500, 4000],
  fog: [1250, 3000],
  thunder: [2100, 9100, 9150],
  cold: [300, 1150, 2300],
  snow: [2000, 3300],
};
const STRIKE = api.STRIKES[0];

function parse(svg) {
  const ops = [];
  const grads = {};
  const groups = [];
  let head = '', canopy = '';
  const re = /<defs><linearGradient id="([^"]*)"[^>]*x1="([^"]*)" y1="([^"]*)" x2="([^"]*)" y2="([^"]*)">.*?<\/defs>|<g transform="([^"]*)">|<\/g>|<path ([^>]*)\/>/g;
  let m;
  while ((m = re.exec(svg))) {
    if (m[1]) { grads[m[1]] = { x1: +m[2], y1: +m[3], x2: +m[4], y2: +m[5] }; continue; }
    if (m[6] != null) {
      groups.push(head ? 'canopy' : 'head');
      if (!head) head = m[6]; else canopy = m[6];
      continue;
    }
    if (m[0] === '</g>') { groups.pop(); continue; }
    const a = {};
    m[7].replace(/([\w-]+)="([^"]*)"/g, (_, k, v) => { a[k] = v; });
    const op = { d: a.d, group: groups[groups.length - 1] || '' };
    if (a.fill && a.fill.startsWith('url(#')) op.gradient = grads[a.fill.slice(5, -1)];
    else op.fill = a.fill || 'none';
    for (const k of ['opacity', 'fill-rule', 'stroke', 'stroke-width', 'stroke-linecap']) if (a[k] != null) op[k] = a[k];
    ops.push(op);
  }
  return { ops, headTransform: head, canopyTransform: canopy };
}

const out = { source: 'docs/prototypes/src/weather.js drawFrame', frames: [] };
for (const [id, times] of Object.entries(TIMES)) {
  const scene = api.SCENES[id];
  for (const page of PAGES) {
    const parts = api.partsAt(page), caps = api.getCapabilities(parts);
    times.forEach((t, i) => {
      for (const still of id === 'snow' && t === 2000 ? [false, true] : [false]) {
        const v = (page + i) % 5;
        const P = scene.pose(t, caps, id === 'snow' ? 'B' : 'A');
        const svg = api.drawFrame({ P, parts, v, clock: t, envList: scene.env, bundle: null, still, uid: 'g' });
        out.frames.push({
          weather: id, page, t, variant: v, still,
          rainStep: Math.floor(t / 125),
          sinceStrike: id === 'thunder' && t >= STRIKE && t < STRIKE + 1000 ? t - STRIKE : null,
          ...parse(svg),
        });
      }
    });
  }
}
process.stdout.write(JSON.stringify(out));
