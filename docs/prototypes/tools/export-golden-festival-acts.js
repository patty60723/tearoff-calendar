// P6-3：節日演出的黃金數值（原型 core.js 的 ACTIONS + buildFrame）。
// 用法：node docs/prototypes/tools/export-golden-festival-acts.js > app/src/test/resources/mowan/golden-festival-acts.json
// poses：每個演出 × 層級 × 時間點的姿勢與道具動作量（小）；frames：幾張完整畫面（每一條筆畫），確認繪製也一樣。
// 沒有包袱（原型的節日邏輯沒有包袱）；中秋下雨的「看月餅版」是 Android 新加的，原型沒有，不在這裡。
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
const api = new Function('document', src + '\nreturn { resolve, buildFrame, Renderer, HOLIDAYS, levelFor };')(document);
const LAYERS = ['sky', 'clouds', 'rain', 'backdrop', 'ground', 'shaft', 'body', 'scarf', 'head', 'hand', 'canopy', 'front'];

const ctxOf = (holiday, page, weather) => ({ page, weather, holiday, hatOn: true, headwear: true, umb: 1 });
function frameOf(ctx, kind, t, held) {
  const R = api.resolve(ctx), H = api.HOLIDAYS[ctx.holiday];
  const action = kind === 'main' ? H.main : H.idle;
  const F = api.buildFrame(ctx, R, { action, start: 0, held }, t, true);
  return { R, F, action };
}

const out = { source: 'docs/prototypes/src/engine.js + core.js（ACTIONS v2、buildFrame）', poses: [], frames: [] };
const plan = {
  spring: { pages: [2, 4, 5, 14], weathers: ['none', 'rainy'], main: [0, 300, 700, 1200, 1600, 2300, 2800, 3350, 4100], idle: [0, 500, 900, 1600, 2100] },
  xmas: { pages: [2, 4, 6, 14], weathers: ['none', 'sunny'], main: [0, 200, 400, 700, 1200, 1700, 2300, 2700, 3600, 4300], idle: [0, 600, 900, 1600] },
  moon: { pages: [2, 4, 5, 14], weathers: ['none', 'sunny'], main: [0, 400, 800, 1500, 2000, 2900, 3500, 4100], idle: [0, 700, 1200, 2200] },
};
for (const [holiday, p] of Object.entries(plan)) {
  for (const page of p.pages) for (const weather of p.weathers) for (const kind of ['main', 'idle']) {
    for (const t of p[kind]) {
      const { R, F, action } = frameOf(ctxOf(holiday, page, weather), kind, t, false);
      out.poses.push({ holiday, page, weather, kind, t, held: false, level: F.level, P: F.P, props: F.props, groundDy: F.groundDy });
    }
    // 關閉動畫：停在代表姿勢
    const { F } = frameOf(ctxOf(holiday, page, weather), kind, 999, true);
    out.poses.push({ holiday, page, weather, kind, t: 999, held: true, level: F.level, P: F.P, props: F.props, groundDy: F.groundDy });
  }
}
// 完整畫面：每個節日的主演出在停留時間、待機在代表時間、雨天拜年、襪子擺動中、月光最亮
const frames = [
  ['spring', 14, 'none', 'main', 2300], ['spring', 14, 'rainy', 'main', 1600], ['spring', 2, 'none', 'main', 400], ['spring', 14, 'none', 'idle', 900],
  ['xmas', 14, 'none', 'main', 1300], ['xmas', 14, 'sunny', 'main', 2700], ['xmas', 4, 'none', 'main', 350], ['xmas', 14, 'none', 'idle', 900],
  ['moon', 14, 'none', 'main', 3000], ['moon', 4, 'sunny', 'main', 2900], ['moon', 14, 'none', 'idle', 1200], ['moon', 2, 'none', 'main', 2000],
];
for (const [holiday, page, weather, kind, t] of frames) {
  const ctx = ctxOf(holiday, page, weather);
  const { R, F } = frameOf(ctx, kind, t, false);
  const svg = fakeElement('svg');
  const r = new api.Renderer(svg, { px: 160 });
  const v = (page + t / 100) % 5 | 0, rainStep = Math.floor(t / 125);
  r.render(F, R, ctx, v, rainStep, t, 'full');
  const groups = svg.children.filter((c) => c.tag === 'g');
  const ops = [];
  groups.forEach((g, i) => {
    for (const el of g.children) {
      if (el.attrs.display === 'none') continue;
      const a = el.attrs, op = { layer: LAYERS[i], d: a.d };
      if (a.fill) op.fill = a.fill;
      for (const k of ['opacity', 'fill-rule', 'stroke', 'stroke-width']) if (a[k] != null) op[k] = a[k];
      ops.push(op);
    }
  });
  out.frames.push({ holiday, page, weather, kind, t, variant: v, rainStep, level: F.level,
    headTransform: groups[LAYERS.indexOf('head')].attrs.transform || '', canopyTransform: groups[LAYERS.indexOf('canopy')].attrs.transform || '', ops });
}
process.stdout.write(JSON.stringify(out));
