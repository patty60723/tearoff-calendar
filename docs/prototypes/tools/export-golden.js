// 從原型原始碼（docs/prototypes/src/engine.js + core.js）匯出黃金數值，給 Android 的 JVM 測試比對。
// 用法：node docs/prototypes/tools/export-golden.js > app/src/test/resources/mowan/golden.json
// 原型是 SVG/DOM 程式，這裡用一個極小的假 DOM 收集它畫出來的每一條 path。
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
const api = new Function('document', src + '\nreturn { resolve, buildFrame, Renderer, partsAt, HEAD_LOOP, ROOT_INDEX, mulberry32, hash, ph };')(document);

const LAYERS = ['sky', 'clouds', 'rain', 'backdrop', 'ground', 'shaft', 'body', 'scarf', 'head', 'hand', 'canopy', 'front'];

function renderOnce(ctx, v, clock, rainStep) {
  const svg = fakeElement('svg');
  const r = new api.Renderer(svg, { px: 160 });
  const R = api.resolve(ctx);
  const F = api.buildFrame(ctx, R, null, clock, true);
  r.render(F, R, ctx, v, rainStep, clock, 'full');
  const groups = svg.children.filter((c) => c.tag === 'g');
  const defs = svg.children.find((c) => c.tag === 'defs');
  const grads = {};
  for (const g of defs.children) grads[g.attrs.id] = { x1: +g.attrs.x1, y1: +g.attrs.y1, x2: +g.attrs.x2, y2: +g.attrs.y2 };
  const ops = [];
  groups.forEach((g, i) => {
    for (const p of g.children) {
      if (p.attrs.display === 'none') continue;
      const a = p.attrs, op = { layer: LAYERS[i], d: a.d };
      if (a.fill && a.fill.startsWith('url(#')) op.gradient = grads[a.fill.slice(5, -1)];
      else if (a.fill) op.fill = a.fill;
      for (const k of ['opacity', 'fill-rule', 'stroke', 'stroke-width', 'stroke-linecap']) if (a[k] != null) op[k] = a[k];
      ops.push(op);
    }
  });
  const headG = groups[LAYERS.indexOf('head')];
  const canopyG = groups[LAYERS.indexOf('canopy')];
  return { headTransform: headG.attrs.transform || '', canopyTransform: canopyG.attrs.transform || '', ops };
}

const out = {
  source: 'docs/prototypes/src/engine.js + core.js',
  rng: { hashMowan: api.hash('mowan'), mulberry: (() => { const r = api.mulberry32(api.hash('head') + 3 * 7919); return Array.from({ length: 6 }, r); })() },
  headLoop: api.HEAD_LOOP,
  rootIndex: api.ROOT_INDEX,
  frames: [],
};
// 一般站姿：第 1–14 頁各一張（筆觸影格 0）、第 14 頁的另外 4 張筆觸影格、第 14 頁脫下紙帽
const cases = [];
for (let page = 1; page <= 14; page++) cases.push({ page, variant: 0, hatOn: true, weather: 'none', clock: 0, rainStep: 0 });
for (let v = 1; v < 5; v++) cases.push({ page: 14, variant: v, hatOn: true, weather: 'none', clock: 0, rainStep: 0 });
cases.push({ page: 14, variant: 0, hatOn: false, weather: 'none', clock: 0, rainStep: 0 });
// 晴天伸懶腰（5.5 秒）、雨天撐傘（4.2 秒）：不同成長階段、不同時間點
for (const page of [1, 3, 5, 9, 14]) {
  [0, 1375, 2750, 4400].forEach((clock, i) => cases.push({ page, variant: (page + i) % 5, hatOn: true, weather: 'sunny', clock, rainStep: 0 }));
  [0, 1050, 2100, 3500].forEach((clock, i) => cases.push({ page, variant: (page + i) % 5, hatOn: true, weather: 'rainy', clock, rainStep: Math.floor(clock / 125) }));
}
cases.push({ page: 4, variant: 2, hatOn: true, weather: 'rainy', clock: 700, rainStep: 5 });
cases.push({ page: 14, variant: 1, hatOn: false, weather: 'rainy', clock: 1600, rainStep: 13 });
for (const c of cases) {
  const ctx = { page: c.page, weather: c.weather, holiday: 'none', hatOn: c.hatOn, headwear: true, umb: 1 };
  out.frames.push({ ...c, ...renderOnce(ctx, c.variant, c.clock, c.rainStep) });
}
process.stdout.write(JSON.stringify(out));
