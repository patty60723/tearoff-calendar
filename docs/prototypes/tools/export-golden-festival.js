// P6-2：節日服裝與道具的靜態畫面黃金數值（原型 core.js 的 Renderer，沒有演出、沒有包袱）。
// 用法：node docs/prototypes/tools/export-golden-festival.js > app/src/test/resources/mowan/golden-festival.json
// 原型只有晴、雨、陰；這裡只匯出一般、晴、雨（陰天的雲在 Android 已經照 weather.js 改過，不跟 core.js 比）。
'use strict';
const fs = require('fs');
const path = require('path');

function fakeElement(tag) {
  return {
    tag, attrs: {}, children: [],
    setAttribute(k, v) { this.attrs[k] = String(v); },
    removeAttribute(k) { delete this.attrs[k]; },
    getAttribute(k) { return this.attrs[k]; },
    appendChild(c) { this.children.push(c); return c; },
    get id() { return this.attrs.id; },
  };
}
const document = { createElementNS: (_ns, tag) => fakeElement(tag) };
const src = ['engine.js', 'core.js'].map((f) => fs.readFileSync(path.join(__dirname, '..', 'src', f), 'utf8')).join('\n');
const api = new Function('document', src + '\nreturn { resolve, buildFrame, Renderer, HEADWEAR };')(document);
const LAYERS = ['sky', 'clouds', 'rain', 'backdrop', 'ground', 'shaft', 'body', 'scarf', 'head', 'hand', 'canopy', 'front'];

function renderOnce(ctx, v, clock, rainStep, headVariant) {
  const svg = fakeElement('svg');
  const r = new api.Renderer(svg, { px: 160 });
  const R = api.resolve(ctx);
  // 頭飾的候選戴法：把頭頂換成 B、C 版本（同一個形狀、不同的錨點）
  if (headVariant && R.S.HEAD_TOP && R.S.HEAD_TOP !== 'paperHat') R.S.HEAD_TOP = R.S.HEAD_TOP + headVariant;
  const F = api.buildFrame(ctx, R, null, clock, true);
  r.render(F, R, ctx, v, rainStep, clock, 'full');
  const groups = svg.children.filter((c) => c.tag === 'g');
  const ops = [];
  groups.forEach((g, i) => {
    for (const p of g.children) {
      if (p.attrs.display === 'none') continue;
      const a = p.attrs, op = { layer: LAYERS[i], d: a.d };
      if (a.fill) op.fill = a.fill;
      for (const k of ['opacity', 'fill-rule', 'stroke', 'stroke-width', 'stroke-linecap']) if (a[k] != null) op[k] = a[k];
      ops.push(op);
    }
  });
  const headG = groups[LAYERS.indexOf('head')], canopyG = groups[LAYERS.indexOf('canopy')];
  return { headTransform: headG.attrs.transform || '', canopyTransform: canopyG.attrs.transform || '', slots: R.S, ops };
}

const out = { source: 'docs/prototypes/src/engine.js + core.js（Renderer，節日靜態）', frames: [] };
const cases = [];
let n = 0;
for (const holiday of ['spring', 'xmas', 'moon']) {
  for (const page of [1, 2, 3, 4, 5, 8, 11, 14]) {
    for (const weather of ['none', 'sunny', 'rainy']) {
      const clock = weather === 'rainy' ? 1050 : 0;
      cases.push({ holiday, page, weather, variant: n++ % 5, clock, rainStep: Math.floor(clock / 125), hatOn: true, headwear: true, umb: 1, head: '' });
    }
  }
  // 頭飾候選：第 14 頁一般站姿
  const vs = holiday === 'moon' ? ['B'] : ['B', 'C'];
  for (const head of vs) cases.push({ holiday, page: 14, weather: 'none', variant: 0, clock: 0, rainStep: 0, hatOn: true, headwear: true, umb: 1, head });
  // 雨傘縮小候選、節日頭飾關閉（紙帽照戴）
  cases.push({ holiday, page: 14, weather: 'rainy', variant: 2, clock: 2100, rainStep: 16, hatOn: true, headwear: true, umb: 0.95, head: '' });
  cases.push({ holiday, page: 14, weather: 'none', variant: 1, clock: 0, rainStep: 0, hatOn: true, headwear: false, umb: 1, head: '' });
}
for (const c of cases) {
  const ctx = { page: c.page, weather: c.weather, holiday: c.holiday, hatOn: c.hatOn, headwear: c.headwear, umb: c.umb };
  out.frames.push({ ...c, ...renderOnce(ctx, c.variant, c.clock, c.rainStep, c.head) });
}
process.stdout.write(JSON.stringify(out));
