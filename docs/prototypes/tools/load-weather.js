// 載入原型（engine.js + core.js + weather.js），回傳需要的函式。給匯出與檢查工具共用。
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

module.exports = function load() {
  const document = { createElementNS: (_ns, tag) => fakeElement(tag) };
  const src = ['engine.js', 'core.js', 'weather.js'].map((f) => fs.readFileSync(path.join(__dirname, '..', 'src', f), 'utf8')).join('\n');
  const api = new Function('document', src + `
return { SCENES, PLAIN, drawFrame, basePose, ARM5, STRIKES, THUNDER_LOOP, env, pose, place, partsAt, getCapabilities,
  resolve, buildFrame, Renderer, HEAD_LOOP, TAU };`)(document);
  api.fakeElement = fakeElement;
  return api;
};
