#!/usr/bin/env node
/*
 * ガイド役 の パラパラ漫画（スプライト）を 作る。
 *   node guide-renderer/render_sprites.js <パック の フォルダ（manifest.json と model/）> <出力 フォルダ>
 * 例：node guide-renderer/render_sprites.js guide-renderer/default app/src/main/assets/guide/default/sprites
 *
 * いる もの：Node.js と Playwright（npm i playwright && npx playwright install chromium）
 * 3D は ヘッドレス の Chromium（ソフトウェア描画）で かく ので、GPU は いりません。
 *
 * 出力：sprites.json と <variant>/idle_00.webp…・tap_00.webp…・mask_<マテリアル名>_idle_00.png…
 *   variant：show/hide/model が ちがう きせかえ の くみあわせ（色 だけ の ちがい は マスク で アプリ が ぬる）
 */
const fs = require('fs');
const path = require('path');
const http = require('http');
const { chromium } = require('playwright');

const W = 240, H = 540;
const ANIMS = [
  { anim: 'idle', frames: 12, seconds: 2.4 },
  { anim: 'tap', frames: 16, seconds: 1.6 },
];

const [packDir, outDir] = process.argv.slice(2);
if (!packDir || !outDir) { console.error('つかいかた：node render_sprites.js <パック> <出力>'); process.exit(1); }
const manifest = JSON.parse(fs.readFileSync(path.join(packDir, 'manifest.json'), 'utf8'));

// きせかえ の くみあわせ（show/hide/model が ある スロット だけ）
const visSlots = (manifest.slots || []).filter((s) => (s.items || []).some((it) => it.show || it.hide || it.model));
let combos = [{}];
for (const s of visSlots) {
  const choices = (s.items || []).filter((it) => it.show || it.hide || it.model).map((it) => it.id);
  if (s.optional) choices.push(null);
  // show/hide の ない アイテム（色 だけ）は さいしょ の かたち と おなじ あつかい
  if ((s.items || []).some((it) => !(it.show || it.hide || it.model)) && !s.optional) choices.unshift(null);
  combos = combos.flatMap((c) => choices.map((id) => ({ ...c, [s.id]: id })));
  if (combos.length > 32) { console.error('きせかえ の くみあわせ が 32 を こえます（show/hide の スロット を へらして）'); process.exit(1); }
}
const masks = [...new Set((manifest.slots || []).flatMap((s) => (s.items || []).flatMap((it) => Object.keys(it.tint || {}))))];

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.png': 'image/png', '.vrm': 'application/octet-stream', '.glb': 'application/octet-stream' };
const server = http.createServer((req, res) => {
  const u = decodeURIComponent(req.url.split('?')[0]);
  const file = u.startsWith('/pack/') ? path.join(packDir, u.slice(6)) : path.join(__dirname, u);
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
});

(async () => {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: W, height: H } });
  page.on('pageerror', (e) => console.error('ページ の エラー：', e.message));
  await page.goto(`http://127.0.0.1:${port}/guide.html?pack=/pack/&w=${W}&h=${H}`);
  await page.waitForFunction(() => window.KH && window.KH.ready, null, { timeout: 60000 });
  fs.mkdirSync(outDir, { recursive: true });
  const variants = [];
  for (let vi = 0; vi < combos.length; vi++) {
    const choose = combos[vi];
    const items = (manifest.slots || []).flatMap((s) => s.items || []);
    const parts = Object.values(choose).filter(Boolean);
    const model = parts.map((id) => items.find((it) => it.id === id)).find((it) => it && it.model)?.model;
    const id = 'v' + vi;
    process.stdout.write(`${id} ${JSON.stringify(choose)} … `);
    const res = await page.evaluate(([v, specs]) => window.KH.renderSet(v, specs), [{ model, parts }, ANIMS.map((a) => ({ ...a, masks }))]);
    const dir = path.join(outDir, id);
    fs.mkdirSync(dir, { recursive: true });
    const dataOf = (u) => Buffer.from(u.split(',')[1], 'base64');
    let bytes = 0;
    for (const a of ANIMS) {
      res[a.anim].base.forEach((u, i) => { const b = dataOf(u); bytes += b.length; fs.writeFileSync(path.join(dir, `${a.anim}_${String(i).padStart(2, '0')}.webp`), b); });
      for (const k of masks) (res[a.anim].masks[k] || []).forEach((u, i) => {
        if (!u) return;
        const b = dataOf(u); bytes += b.length;
        fs.writeFileSync(path.join(dir, `mask_${k}_${a.anim}_${String(i).padStart(2, '0')}.png`), b);
      });
    }
    console.log(`${Math.round(bytes / 1024)} KB`);
    variants.push({ id, choose, model: model || null });
  }
  fs.writeFileSync(path.join(outDir, 'sprites.json'), JSON.stringify({
    format: 1, w: W, h: H,
    anims: Object.fromEntries(ANIMS.map((a) => [a.anim, { frames: a.frames, seconds: a.seconds }])),
    masks, variants,
  }, null, 1));
  await browser.close();
  server.close();
  console.log('できました：' + outDir);
})().catch((e) => { console.error(e); process.exit(1); });
