// キッズホームの ガイド役（VRM）を うごかす。
// ?pack=/pack/guide/ の manifest.json と モデル を よむ。&view=full|bust
// Android からは window.KH.setParts([...]) などで うごかす。うごいたら KidsHomeGuide.onReady / onTap で しらせる。
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

const params = new URLSearchParams(location.search);
const pack = params.get('pack') || '/pack/guide/';
const view = params.get('view') || 'full';
const bridge = window.KidsHomeGuide || { onReady() {}, onTap() {}, onError(m) { console.error(m); }, onStage() {} };
const stage = (s) => { try { bridge.onStage(s); } catch (e) { /* ふるい アプリ */ } };
stage('スクリプト');

window.addEventListener('error', (e) => bridge.onError(String(e.message || e)));
window.addEventListener('unhandledrejection', (e) => bridge.onError(String(e.reason && e.reason.message || e.reason)));
// WebGL が つかえるか（つかえないと 3D は でない）
(() => {
  const c = document.createElement('canvas');
  if (!c.getContext('webgl2') && !c.getContext('webgl')) bridge.onError('この 端末の WebView で WebGL（3D）が つかえません');
})();
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
// ぬりかた（MToon）の シェーダー が この 端末 で うごかない ときは、かんたん な ぬりかた に する
let simple = params.get('simple') === '1';
let lastProblem = '';
renderer.debug.onShaderError = (gl, program, vs, fs) => {
  const log = (gl.getProgramInfoLog(program) || '') + (gl.getShaderInfoLog(fs) || '') + (gl.getShaderInfoLog(vs) || '');
  lastProblem = 'シェーダー：' + log.trim().split('\n')[0].slice(0, 120);
  if (!simple) { simple = true; simplify(); }
};
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
// 背景：へや の 絵（bgimg）か 色（bg）を ページ に ぬって、3D は その うえ に（WebView 自体 は すけさせない）
const bg = params.get('bg');
const bgimg = params.get('bgimg');
renderer.setClearColor(0x000000, 0);
if (bgimg) document.documentElement.style.background = `url("${bgimg}") 0 0 / 100% 100% no-repeat`;
else if (bg) document.documentElement.style.background = '#' + bg;
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(28, window.innerWidth / window.innerHeight, 0.05, 50);
scene.add(new THREE.AmbientLight(0xffffff, 1.6));
const sun = new THREE.DirectionalLight(0xffffff, 1.6);
sun.position.set(0.6, 1.5, 2.5);
scene.add(sun);

let vrm = null;
let root = null;
let manifest = {};
const clock = new THREE.Clock();
let wave = 0;      // てを ふる のこり時間
let jump = 0;
let happy = 0;
let blinkAt = 2;
let lastFrame = 0;

function frame() {
  if (!root) return;
  const box = new THREE.Box3().setFromObject(root, true);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const h = view === 'bust' ? size.y * 0.5 : size.y * 1.08;
  const cy = view === 'bust' ? box.max.y - size.y * 0.24 : center.y;
  const fov = THREE.MathUtils.degToRad(camera.fov);
  let dist = (h / 2) / Math.tan(fov / 2);
  // よこ が せまい ときも はいるように
  const wNeed = (view === 'bust' ? size.x * 0.6 : size.x * 1.15) / 2 / Math.tan(fov / 2) / camera.aspect;
  dist = Math.max(dist, wNeed);
  camera.position.set(center.x, cy, center.z + dist);
  camera.lookAt(center.x, cy, center.z);
}

/** いまの 大きさ に あわせる（高さ・はば の どちらか が 0 の ときは まつ） */
let lastW = 0, lastH = 0;
function syncSize() {
  const w = window.innerWidth || document.documentElement.clientWidth;
  const h = window.innerHeight || document.documentElement.clientHeight;
  if (!w || !h || (w === lastW && h === lastH)) return;
  lastW = w; lastH = h;
  renderer.setSize(w, h);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
  frame();
  info();
}
if (window.ResizeObserver) new ResizeObserver(syncSize).observe(document.documentElement);
let frames = 0;
let blank = 0;
let center = '?';
function info() {
  const r = renderer.info.render;
  const msg = `${lastW}×${lastH}・${renderer.capabilities.isWebGL2 ? 'WebGL2' : 'WebGL1'}・${frames} コマ・三角 ${r.triangles}・まんなか ${center}${simple ? '・かんたん' : ''}${lastProblem ? '・' + lastProblem : ''}`;
  try { bridge.onInfo && bridge.onInfo(msg); } catch (e) { /* ふるい アプリ */ }
}

/** MToon を ふつう の いろ と もよう だけ の ぬりかた に かえる */
function simplify() {
  if (!root) return;
  root.traverse((o) => {
    if (!o.isMesh) return;
    const conv = (m) => {
      if (!m || m.userData.simple) return m;
      const n = new THREE.MeshBasicMaterial({
        map: m.map || null, color: m.color ? m.color.clone() : new THREE.Color(0xffffff),
        transparent: !!m.transparent, opacity: m.opacity ?? 1, alphaTest: m.alphaTest || 0, side: m.side ?? THREE.FrontSide,
        depthWrite: m.depthWrite ?? true,
      });
      n.name = m.name; n.userData.simple = true;
      return n;
    };
    o.material = Array.isArray(o.material) ? o.material.map(conv) : conv(o.material);
  });
}

/** まんなか の 1 ドット が かけているか（かけていなければ かんたん な ぬりかた へ） */
function checkCenter() {
  const gl = renderer.getContext();
  const px = new Uint8Array(4);
  const w = gl.drawingBufferWidth, h = gl.drawingBufferHeight;
  // むね の たかさ（下 から 7 わり）の 5 か所 の どこか に かけていれば OK
  let hit = null;
  for (const fx of [0.5, 0.45, 0.55, 0.4, 0.6]) {
    gl.readPixels(Math.floor(w * fx), Math.floor(h * 0.7), 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
    if (px[3] > 0) { hit = [px[0], px[1], px[2]]; break; }
  }
  center = hit ? `#${hit.map((v) => v.toString(16).padStart(2, '0')).join('')}` : 'から';
  if (!hit && root) {
    blank++;
    if (blank === 3 && !simple) { simple = true; simplify(); lastProblem = lastProblem || 'まんなか が から なので かんたん に'; }
  } else blank = 0;
}
setInterval(info, 5000);

window.addEventListener('resize', () => {
  renderer.setSize(window.innerWidth, window.innerHeight);
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  frame();
});

function bone(name) { return vrm && vrm.humanoid ? vrm.humanoid.getNormalizedBoneNode(name) : null; }
function expr(name, v) { if (vrm && vrm.expressionManager && vrm.expressionManager.getExpression(name)) vrm.expressionManager.setValue(name, v); }

/** なまえ で みつける（そのもの か、おや の なまえ） */
function objectsNamed(name) {
  const out = [];
  root.traverse((o) => { if (o.name === name || (o.isMesh && o.parent && o.parent.name === name)) out.push(o); });
  return out;
}

/** パーツの なまえ（メッシュ・ノード）いちらん。おうちのひと が manifest を かくため */
function partNames() {
  const names = new Set();
  root.traverse((o) => { if (o.isMesh || o.isSkinnedMesh) { names.add(o.name); if (o.parent && o.parent !== root) names.add(o.parent.name); } });
  return [...names].filter(Boolean).sort();
}

const tinted = new Map(); // material → もとの いろ

/** えらんだ アイテム（id の ならび）を きせる */
function setParts(ids) {
  if (!root) return;
  const chosen = new Set(ids || []);
  const slots = manifest.slots || [];
  // まず どの スロット の アイテム も いったん かくす → えらんだものを みせる
  for (const s of slots) for (const it of s.items || []) for (const n of it.show || []) objectsNamed(n).forEach((o) => { o.visible = false; });
  for (const [k, c] of tinted) (k.isColor ? k : k.color).copy(c);
  for (const s of slots) {
    let item = (s.items || []).find((it) => chosen.has(it.id));
    if (!item && !s.optional) item = (s.items || [])[0];
    if (!item) continue;
    for (const n of item.show || []) objectsNamed(n).forEach((o) => { o.visible = true; });
    for (const n of item.hide || []) objectsNamed(n).forEach((o) => { o.visible = false; });
    for (const [matName, hex] of Object.entries(item.tint || {})) {
      root.traverse((o) => {
        if (!o.isMesh) return;
        for (const m of Array.isArray(o.material) ? o.material : [o.material]) {
          if (!m || !m.name || !m.name.includes(matName)) continue;
          const target = m.color || (m.uniforms && m.uniforms.litFactor && m.uniforms.litFactor.value);
          if (!target) continue;
          if (!tinted.has(m)) tinted.set(m, target.clone());
          target.set(hex);
          // かげ の いろ も すこし こく あわせる（MToon）
          if (m.shadeColorFactor) {
            if (!tinted.has(m.shadeColorFactor)) tinted.set(m.shadeColorFactor, m.shadeColorFactor.clone());
            m.shadeColorFactor.set(hex).multiplyScalar(0.8);
          }
        }
      });
    }
  }
}

function tap() {
  wave = 1.6; jump = 0.5; happy = 1.8;
  bridge.onTap();
}

renderer.domElement.addEventListener('pointerdown', tap);

window.KH = {
  setParts: (json) => dress(typeof json === 'string' ? JSON.parse(json) : json),
  tap,
  setView: (v) => { location.search = `?pack=${encodeURIComponent(pack)}&view=${v}`; },
};

function animate(now) {
  requestAnimationFrame(animate);
  if (document.hidden) return;
  if (now - lastFrame < 1000 / 31) return; // 30 コマ で じゅうぶん（でんち の ため）
  lastFrame = now;
  syncSize();
  if (!lastW || !lastH) return;
  frames++;
  const dt = Math.min(clock.getDelta(), 0.1);
  const t = clock.elapsedTime;
  if (vrm) {
    // いき・ゆれ
    const spine = bone('spine'); if (spine) spine.rotation.x = Math.sin(t * 1.6) * 0.02;
    const head = bone('head'); if (head) { head.rotation.y = Math.sin(t * 0.5) * 0.12; head.rotation.z = Math.sin(t * 0.7) * 0.04; }
    const hips = bone('hips');
    if (hips) {
      if (hips.userData.baseY === undefined) hips.userData.baseY = hips.position.y;
      hips.position.y = hips.userData.baseY + (jump > 0 ? Math.sin((0.5 - jump) / 0.5 * Math.PI) * 0.08 : 0);
    }
    // うで は した に（Tポーズ を やめる）。てを ふる ときは みぎて を うえに
    const lu = bone('leftUpperArm'); if (lu) lu.rotation.z = -1.3 + Math.sin(t * 1.6) * 0.03;
    const ru = bone('rightUpperArm'); const rl = bone('rightLowerArm');
    if (ru) ru.rotation.z = wave > 0 ? -0.4 + Math.sin(t * 2) * 0.05 : 1.3 - Math.sin(t * 1.6) * 0.03;
    if (rl) rl.rotation.z = wave > 0 ? -0.6 + Math.sin(t * 14) * 0.45 : 0;
    // まばたき・えがお
    blinkAt -= dt;
    const b = blinkAt < 0 ? Math.max(0, 1 - Math.abs(blinkAt + 0.08) / 0.08) : 0;
    if (blinkAt < -0.16) blinkAt = 2 + Math.random() * 3;
    expr('blink', happy > 0 ? 0 : b);
    expr('happy', happy > 0 ? Math.min(1, happy * 2) : 0);
    vrm.update(dt);
  }
  wave = Math.max(0, wave - dt); jump = Math.max(0, jump - dt); happy = Math.max(0, happy - dt);
  renderer.render(scene, camera);
  if (root && frames % 30 === 5) checkCenter();
}

/** えらんだ アイテム の なかに "model" が あれば その モデル（VRoid で 服ごとに 書き出した とき） */
function modelFor(ids) {
  const chosen = new Set(ids || []);
  for (const sl of manifest.slots || []) for (const it of sl.items || []) if (it.model && chosen.has(it.id)) return it.model;
  return manifest.model || 'model/guide.vrm';
}

let currentModel = null;
let loading = null;
let wantParts = [];

function loadModel(path) {
  if (path === currentModel) return Promise.resolve();
  currentModel = path;
  const loader = new GLTFLoader();
  loader.register((p) => new VRMLoaderPlugin(p));
  stage('モデル');
  loading = new Promise((resolve) => loader.load(pack + path, (gltf) => {
    stage('ひょうじ');
    if (path !== currentModel) { resolve(); return; } // とちゅうで べつの ふく に なった
    if (root) { scene.remove(root); VRMUtils.deepDispose(root); tinted.clear(); }
    vrm = gltf.userData.vrm || null;
    if (vrm) {
      VRMUtils.removeUnnecessaryVertices(gltf.scene);
      VRMUtils.combineSkeletons(gltf.scene);
      VRMUtils.rotateVRM0(vrm);
      root = vrm.scene;
    } else root = gltf.scene;
    root.traverse((o) => { o.frustumCulled = false; });
    scene.add(root);
    // うで を おろして から 大きさ を はかる
    if (vrm) {
      const lu = bone('leftUpperArm'); if (lu) lu.rotation.z = -1.3;
      const ru = bone('rightUpperArm'); if (ru) ru.rotation.z = 1.3;
      vrm.update(0);
    }
    root.updateMatrixWorld(true);
    frame();
    if (simple) simplify();
    setParts(wantParts);
    bridge.onReady(JSON.stringify({ parts: partNames(), expressions: vrm && vrm.expressionManager ? vrm.expressionManager.expressions.map((e) => e.expressionName) : [] }));
    resolve();
  }, (ev) => { if (ev && ev.total) stage('モデル ' + Math.round(ev.loaded / ev.total * 100) + '%'); }, (e) => { bridge.onError(String(e && e.message || e)); resolve(); }));
  return loading;
}

/** きせかえ：モデル が かわるなら よみなおして から パーツ */
function dress(ids) {
  wantParts = ids || [];
  const m = modelFor(wantParts);
  if (m !== currentModel) loadModel(m); else setParts(wantParts);
}

async function start() {
  stage('せってい');
  try {
    manifest = await (await fetch(pack + 'manifest.json')).json();
  } catch (e) { manifest = {}; bridge.onError('manifest.json が よめない：' + e); }
  dress(JSON.parse(params.get('parts') || '[]'));
  requestAnimationFrame(animate);
}
start();
