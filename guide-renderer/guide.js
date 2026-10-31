// キッズホーム の ガイド役：VRM を 3D で うごかして、パラパラ漫画（連番の 絵）に する。
// アプリ は この 絵 を ならべて 見せるだけ（端末 の WebView・3D に たよらない）。
// tools/render_sprites.mjs が この ページ を ひらいて window.KH.renderSet() を よぶ。
// ?pack=/pack/&parts=[...] で ためしに 見る ことも できる（うごき つき）。
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

const params = new URLSearchParams(location.search);
const pack = params.get('pack') || '/pack/';
const W = +(params.get('w') || 240);
const H = +(params.get('h') || 540);

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.setSize(W, H);
renderer.setClearColor(0x000000, 0);
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(28, W / H, 0.05, 50);
scene.add(new THREE.AmbientLight(0xffffff, 1.6));
const sun = new THREE.DirectionalLight(0xffffff, 1.6);
sun.position.set(0.6, 1.5, 2.5);
scene.add(sun);

let vrm = null;
let root = null;
let manifest = {};

function bone(name) { return vrm && vrm.humanoid ? vrm.humanoid.getNormalizedBoneNode(name) : null; }
function expr(name, v) { if (vrm && vrm.expressionManager && vrm.expressionManager.getExpression(name)) vrm.expressionManager.setValue(name, v); }

function frame() {
  const box = new THREE.Box3().setFromObject(root, true);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  // ジャンプ・ゆれ の ぶん すこし よゆう
  const h = size.y * 1.14;
  const fov = THREE.MathUtils.degToRad(camera.fov);
  let dist = (h / 2) / Math.tan(fov / 2);
  dist = Math.max(dist, (size.x * 1.2) / 2 / Math.tan(fov / 2) / camera.aspect);
  camera.position.set(center.x, center.y + size.y * 0.03, center.z + dist);
  camera.lookAt(center.x, center.y + size.y * 0.03, center.z);
}

function objectsNamed(name) {
  const out = [];
  root.traverse((o) => { if (o.name === name || (o.isMesh && o.parent && o.parent.name === name)) out.push(o); });
  return out;
}

/** show / hide（色 は ここでは つけない。アプリ が マスク で つける） */
function setVisibility(ids) {
  const chosen = new Set(ids || []);
  for (const s of manifest.slots || []) for (const it of s.items || []) for (const n of it.show || []) objectsNamed(n).forEach((o) => { o.visible = false; });
  for (const s of manifest.slots || []) {
    let item = (s.items || []).find((it) => chosen.has(it.id));
    if (!item && !s.optional) item = (s.items || [])[0];
    if (!item) continue;
    for (const n of item.show || []) objectsNamed(n).forEach((o) => { o.visible = true; });
    for (const n of item.hide || []) objectsNamed(n).forEach((o) => { o.visible = false; });
  }
}

/**
 * ポーズ：t（びょう）、wave（0〜1 てを ふる つよさ）、jump（高さ 0〜1）、happy（0〜1）、blink（0〜1）
 * idle は 周期 P で ちょうど くりかえす ように sin(2πt/P) を つかう
 */
function pose(t, P, wave, jump, happy, blink) {
  const w = (2 * Math.PI) / P;
  const spine = bone('spine'); if (spine) spine.rotation.x = Math.sin(t * w) * 0.025;
  const head = bone('head'); if (head) { head.rotation.y = Math.sin(t * w) * 0.1; head.rotation.z = Math.sin(t * w + 1) * 0.04; }
  const hips = bone('hips');
  if (hips) {
    if (hips.userData.baseY === undefined) hips.userData.baseY = hips.position.y;
    hips.position.y = hips.userData.baseY + jump * 0.08 + Math.sin(t * w * 2) * 0.004;
  }
  const lu = bone('leftUpperArm'); if (lu) lu.rotation.z = -1.3 + Math.sin(t * w) * 0.04;
  const ru = bone('rightUpperArm'); const rl = bone('rightLowerArm');
  if (ru) ru.rotation.z = (1.3 - Math.sin(t * w) * 0.04) * (1 - wave) + (-0.45) * wave;
  if (rl) rl.rotation.z = wave * (-0.6 + Math.sin(t * 14) * 0.5);
  expr('blink', happy > 0.05 ? 0 : blink);
  expr('happy', happy);
}

async function load(path) {
  const loader = new GLTFLoader();
  loader.register((p) => new VRMLoaderPlugin(p));
  const gltf = await loader.loadAsync(pack + path);
  if (root) { scene.remove(root); VRMUtils.deepDispose(root); }
  vrm = gltf.userData.vrm || null;
  if (vrm) {
    VRMUtils.removeUnnecessaryVertices(gltf.scene);
    VRMUtils.combineSkeletons(gltf.scene);
    VRMUtils.rotateVRM0(vrm);
    root = vrm.scene;
  } else root = gltf.scene;
  root.traverse((o) => { o.frustumCulled = false; });
  scene.add(root);
  if (vrm) { pose(0, 2, 0, 0, 0, 0); vrm.update(0); }
  root.updateMatrixWorld(true);
  frame();
}

/** いま の 絵 を WebP（色）で */
function grab() { return renderer.domElement.toDataURL('image/webp', 0.86); }

const black = new Map();
const white = new THREE.MeshBasicMaterial({ color: 0xffffff });
/** マテリアル名 に key を ふくむ ところ だけ 白、ほか は 黒（かくれる ところ は かくれる）→ 白 を 不透明 に した マスク PNG */
function grabMask(key) {
  const saved = [];
  root.traverse((o) => {
    if (!o.isMesh) return;
    saved.push([o, o.material]);
    const conv = (m) => {
      if (m && m.name && m.name.includes(key)) return white;
      if (!black.has(m)) black.set(m, new THREE.MeshBasicMaterial({ color: 0x000000, map: m && m.map || null, alphaTest: m && (m.alphaTest || (m.transparent ? 0.5 : 0)) || 0, side: m ? m.side : THREE.FrontSide }));
      return black.get(m);
    };
    o.material = Array.isArray(o.material) ? o.material.map(conv) : conv(o.material);
  });
  renderer.render(scene, camera);
  saved.forEach(([o, m]) => { o.material = m; });
  // あかるさ → アルファ
  const c = document.createElement('canvas');
  c.width = W; c.height = H;
  const g = c.getContext('2d');
  g.drawImage(renderer.domElement, 0, 0);
  const img = g.getImageData(0, 0, W, H);
  let any = false;
  for (let i = 0; i < img.data.length; i += 4) {
    const a = img.data[i + 3] ? img.data[i] : 0;
    img.data[i] = img.data[i + 1] = img.data[i + 2] = 255;
    img.data[i + 3] = a;
    if (a) any = true;
  }
  g.putImageData(img, 0, 0);
  return any ? c.toDataURL('image/png') : null;
}

/**
 * 1 しゅるい の うごき を 連番 に する
 * spec: { anim: 'idle' | 'tap', frames, seconds, masks: [マテリアル名の 一部…] }
 */
function renderAnim(spec) {
  const out = { base: [], masks: {} };
  const N = spec.frames, P = spec.seconds, dt = P / N;
  // ゆれもの を おちつかせる
  for (let i = 0; i < N * 2; i++) { stepPose(spec.anim, (i % N) * dt, P); if (vrm) vrm.update(dt); }
  for (let i = 0; i < N; i++) {
    stepPose(spec.anim, i * dt, P);
    if (vrm) vrm.update(dt);
    renderer.render(scene, camera);
    out.base.push(grab());
    for (const k of spec.masks || []) (out.masks[k] = out.masks[k] || []).push(grabMask(k));
  }
  return out;
}

function stepPose(anim, t, P) {
  if (anim === 'tap') {
    const u = t / P; // 0〜1
    const wave = u < 0.12 ? u / 0.12 : u > 0.85 ? (1 - u) / 0.15 : 1;
    const jump = u < 0.3 ? Math.sin(u / 0.3 * Math.PI) : 0;
    pose(t, P, wave, jump, Math.min(1, wave * 1.2), 0);
  } else {
    // idle：さいご の ほう で 1 かい まばたき
    const u = t / P;
    const blink = Math.max(0, 1 - Math.abs(u - 0.8) / 0.05);
    pose(t, P, 0, 0, 0, blink);
  }
}

window.KH = {
  ready: false,
  manifest: () => manifest,
  /** variant：{ model, parts:[…] } → { idle:{base,masks}, tap:{…} } */
  async renderSet(variant, specs) {
    await load(variant.model || manifest.model || 'model/guide.vrm');
    setVisibility(variant.parts || []);
    const res = {};
    for (const s of specs) res[s.anim] = renderAnim(s);
    return res;
  },
};

// ためしに 見る（うごき つき）
async function preview() {
  try { manifest = await (await fetch(pack + 'manifest.json')).json(); } catch (e) { manifest = {}; }
  window.KH.ready = true;
  if (params.get('preview') !== '1') return;
  await load(manifest.model || 'model/guide.vrm');
  setVisibility(JSON.parse(params.get('parts') || '[]'));
  const clock = new THREE.Clock();
  const loop = () => {
    requestAnimationFrame(loop);
    const t = clock.getElapsedTime();
    stepPose('idle', t % 2.4, 2.4);
    if (vrm) vrm.update(clock.getDelta());
    renderer.render(scene, camera);
  };
  loop();
}
preview();
