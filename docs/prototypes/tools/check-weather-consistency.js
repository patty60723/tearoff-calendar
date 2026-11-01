// W1：確認定稿參考 weather.js 的 drawFrame，跟 App 已上線的晴、雨、一般站姿（core.js 的 Renderer）畫出一樣的角色。
// 只比角色本身（傘柄、身體、圍巾、頭、手上的東西、傘面、拳頭）；天空、雨、地面這些環境不比。
// 用法：node docs/prototypes/tools/check-weather-consistency.js
'use strict';
const api = require('./load-weather.js')();
const TOL = 0.011;
const SKIP_LAYERS = new Set(['sky', 'clouds', 'rain', 'backdrop', 'ground']);
const LAYERS = ['sky', 'clouds', 'rain', 'backdrop', 'ground', 'shaft', 'body', 'scarf', 'head', 'hand', 'canopy', 'front'];

function fromRenderer(page, weather, clock, v) {
  const svg = api.fakeElement('svg');
  const r = new api.Renderer(svg, { px: 160 });
  const ctx = { page, weather, holiday: 'none', hatOn: true, headwear: true, umb: 1 };
  const R = api.resolve(ctx);
  const F = api.buildFrame(ctx, R, null, clock, true);
  r.render(F, R, ctx, v, 0, clock, 'full');
  const groups = svg.children.filter((c) => c.tag === 'g');
  const ops = [];
  groups.forEach((g, i) => {
    if (SKIP_LAYERS.has(LAYERS[i])) return;
    for (const p of g.children) if (p.attrs.display !== 'none') ops.push({ d: p.attrs.d, fill: p.attrs.fill && p.attrs.fill.startsWith('url') ? 'gradient' : p.attrs.fill, op: p.attrs.opacity || '1', rule: p.attrs['fill-rule'] || '' });
  });
  const head = groups[LAYERS.indexOf('head')].attrs.transform;
  const canopy = groups[LAYERS.indexOf('canopy')].attrs.transform || '';
  return { ops, head, canopy, P: F.P };
}

function fromWeather(page, scene, clock, v) {
  const parts = api.partsAt(page), caps = api.getCapabilities(parts);
  const P = scene.pose(clock, caps, 'A');
  const svg = api.drawFrame({ P, parts, v, clock, envList: [], bundle: null, uid: 'x' });
  const ops = [];
  const re = /<path ([^>]*)\/>/g;
  let m;
  while ((m = re.exec(svg))) {
    const a = {};
    m[1].replace(/([\w-]+)="([^"]*)"/g, (_, k, val) => { a[k] = val; });
    ops.push({ d: a.d, fill: a.fill && a.fill.startsWith('url') ? 'gradient' : a.fill, op: a.opacity || '1', rule: a['fill-rule'] || '' });
  }
  ops.shift();   // 第一筆是地面的影子
  const gs = [...svg.matchAll(/<g transform="([^"]*)">/g)].map((x) => x[1]);
  return { ops, head: gs[0], canopy: gs[1] || '', P };
}

const nums = (d) => (d.match(/-?\d*\.?\d+(?:e-?\d+)?/g) || []).map(Number);
const cmds = (d) => d.replace(/[^A-Za-z]/g, '');
function same(a, b) {
  if (a.ops.length !== b.ops.length) return `筆畫數 ${a.ops.length} vs ${b.ops.length}`;
  for (let i = 0; i < a.ops.length; i++) {
    const x = a.ops[i], y = b.ops[i];
    if (x.fill !== y.fill || x.rule !== y.rule || Math.abs(+x.op - +y.op) > 1e-6) return `第 ${i} 筆的顏色或不透明度不同（${x.fill} ${x.op} vs ${y.fill} ${y.op}）`;
    if (cmds(x.d) !== cmds(y.d)) return `第 ${i} 筆的路徑指令不同`;
    const p = nums(x.d), q = nums(y.d);
    if (p.length !== q.length) return `第 ${i} 筆的數字個數不同`;
    for (let j = 0; j < p.length; j++) if (Math.abs(p[j] - q[j]) > TOL) return `第 ${i} 筆第 ${j} 個數字 ${p[j]} vs ${q[j]}`;
  }
  const ht = (s) => nums(s);
  const ha = ht(a.head), hb = ht(b.head);
  // Renderer：translate(0 dy) rotate(a 60.5 66)；weather.js：translate(dx dy) rotate(a 60.5 66)
  const ra = ha.length === 5 ? ha : [0, ...ha];
  const rb = hb.length === 5 ? hb : [0, ...hb];
  for (let j = 0; j < 5; j++) if (Math.abs(ra[j] - rb[j]) > TOL) return `頭的變換不同（${a.head} vs ${b.head}）`;
  const ca = nums(a.canopy).slice(0, 3), cb = nums(b.canopy).slice(0, 3);
  if (ca.length !== cb.length) return `傘面變換不同（${a.canopy} vs ${b.canopy}）`;
  for (let j = 0; j < ca.length; j++) if (Math.abs(ca[j] - cb[j]) > TOL) return `傘面變換不同（${a.canopy} vs ${b.canopy}）`;
  return null;
}

const cases = [];
for (let page = 1; page <= 14; page++) {
  cases.push(['一般', 'none', api.PLAIN.normal, page, 0]);
  for (const t of [0, 1375, 2750, 4400]) cases.push(['晴', 'sunny', api.PLAIN.sunny, page, t]);
  for (const t of [0, 1050, 2100, 3500]) cases.push(['雨', 'rainy', api.PLAIN.rainy, page, t]);
}
const fails = {};
let ok = 0;
for (const [name, weather, scene, page, t] of cases) {
  const v = (page + t) % 5;
  const diff = same(fromRenderer(page, weather, t, v), fromWeather(page, scene, t, v));
  if (diff) (fails[`${name} 第 ${page} 頁`] ||= []).push(`${t}ms：${diff}`);
  else ok++;
}
console.log(`角色筆畫一致：${ok} / ${cases.length}`);
for (const [k, v] of Object.entries(fails)) console.log(`不一致：${k}\n  ${v.join('\n  ')}`);
