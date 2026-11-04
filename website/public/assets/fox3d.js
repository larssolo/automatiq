/* Hero, 3D layer: the TextFox logo as a real object. Progressive enhancement over the SVG fox:
   loads three.js only after the page is idle, skips reduced motion, save-data and missing WebGL. */
const stage = document.getElementById('foxStage');
const TF = (window.TF = window.TF || {boom: 0});
const webgl = (() => { try { const c = document.createElement('canvas'); return !!(c.getContext('webgl2') || c.getContext('webgl')); } catch { return false; } })();
if (stage && webgl && !matchMedia('(prefers-reduced-motion: reduce)').matches && !(navigator.connection && navigator.connection.saveData)) {
  const go = () => Promise.all([import('./js/three/three.module.min.js'), import('./js/three/SVGLoader.min.js'), import('./js/three/RoomEnvironment.min.js')])
    .then(([THREE, {SVGLoader}, {RoomEnvironment}]) => boot(THREE, SVGLoader, RoomEnvironment)).catch(() => {});
  const idle = cb => ('requestIdleCallback' in window ? requestIdleCallback(cb, {timeout: 2500}) : setTimeout(cb, 900));
  if (document.readyState === 'complete') idle(go); else addEventListener('load', () => idle(go), {once: true});
}

const OUTLINE = 'M3.03,167.98l108.55,18.34c18.05,15.38,83.78,26.23,102.7,10.13l136.86-28.13c1.06-.91,4.45-1.28,2.38-4.76l-16.81-47.49L327.15,0l-99.48,74.39h-100.38S27.76,0,27.76,0l-8.57,116.06L.88,163.68c-.59.82-1,1.64-.84,2.21.25.92,1.26,1.41,3,2.1Z', HEAD = 'M351.14,168.32c-2.58-.26-5.33.02-8.35,1.16l-87.63,24.45-77.62,102.84-77.78-102.9-88.64-24.75c-3.04-.85-5.58-1.5-8.09-1.14-1.73-.69-2.74-1.18-3-2.1-.16-.57.25-1.38.84-2.21l18.32-47.61L27.76,0l99.53,74.39h100.38S327.15,0,327.15,0l9.56,116.08,16.81,47.49c2.07,3.48-1.32,3.86-2.38,4.76Z', MUZZLE = 'M351.14,168.32l-144.48,122.95c-18.92,16.1-43.45,13.15-61.5-2.22L3.03,167.98c2.51-.36,5.05.29,8.09,1.14l88.64,24.75,77.78,102.9,77.62-102.84,87.63-24.45c3.03-1.14,5.77-1.43,8.35-1.16Z';
const EYES = [121.96, 177.51, 233.04], EYE_Y = 149.52, CX = 177.09, CY = 151.02;

function boot(THREE, SVGLoader, RoomEnvironment) {
  const small = innerWidth < 700, lo = small && (navigator.hardwareConcurrency || 4) <= 4;
  const canvas = document.createElement('canvas');
  canvas.className = 'fox3d'; canvas.setAttribute('aria-hidden', 'true');
  let renderer;
  try { renderer = new THREE.WebGLRenderer({canvas, alpha: true, antialias: !lo, powerPreference: 'high-performance'}); } catch { return; }
  renderer.setPixelRatio(Math.min(devicePixelRatio, lo ? 1.25 : small ? 1.75 : 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.NoToneMapping;                 // keep the brand green saturated

  const scene = new THREE.Scene();
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), .04).texture;
  scene.environmentIntensity = .32;
  // the canvas is 1.5× the stage so exploding parts can leave it; the fov keeps the fox the same size
  const DIST = 9, VIEW = 4.82 * 1.5;
  const camera = new THREE.PerspectiveCamera(2 * Math.atan(VIEW / 2 / DIST) * 180 / Math.PI, 1, .1, 60);
  camera.position.set(0, 0, DIST);

  scene.add(new THREE.HemisphereLight('#d8ffeb', '#041009', .35));
  const key = new THREE.DirectionalLight('#ffffff', 1.5); key.position.set(3, 4, 6); scene.add(key);
  const rim = new THREE.DirectionalLight('#42D1CA', 4); rim.position.set(-5, 2, -4); scene.add(rim);
  const warm = new THREE.DirectionalLight('#FFB300', .7); warm.position.set(-3, -4, 3); scene.add(warm);

  // ---- the fox, extruded from the logo paths -------------------------------------------------
  const S = 2.1 / 354.18;                                   // SVG units → world units (fox ≈ 2.1 wide)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg"><g transform="scale(1,-1)"><path d="${OUTLINE}"/><path d="${HEAD}"/><path d="${MUZZLE}"/></g></svg>`;
  const [pOut, pHead, pMuz] = new SVGLoader().parse(svg).paths;
  const ext = (path, depth, bevel) => {
    const g = new THREE.ExtrudeGeometry(SVGLoader.createShapes(path), {depth, bevelEnabled: true, bevelThickness: bevel, bevelSize: bevel * .55, bevelSegments: lo ? 3 : 7, curveSegments: lo ? 6 : 14});
    g.translate(-CX, CY, -depth / 2); g.computeVertexNormals(); return g;
  };
  const M = {
    green: new THREE.MeshPhysicalMaterial({color: '#00E676', roughness: .34, metalness: 0, clearcoat: 1, clearcoatRoughness: .12, emissive: '#00E676', emissiveIntensity: .16}),
    cream: new THREE.MeshPhysicalMaterial({color: '#DCEDE2', roughness: .45, clearcoat: .6, clearcoatRoughness: .25}),
    dark: new THREE.MeshPhysicalMaterial({color: '#141b17', roughness: .45, metalness: .5, clearcoat: .4}),
    eye: new THREE.MeshPhysicalMaterial({color: '#050807', roughness: .05, metalness: .25, clearcoat: 1, clearcoatRoughness: .02}),
  };
  const fox = new THREE.Group(); fox.position.y = .1; scene.add(fox);
  const part = (geo, mat, z) => { const holder = new THREE.Group(), mesh = new THREE.Mesh(geo, mat); mesh.scale.setScalar(S); mesh.position.z = z * S; holder.add(mesh); fox.add(holder); return holder; };
  const outline = part(ext(pOut, 26, 4), M.dark, -16);
  const head = part(ext(pHead, 44, 11), M.green, 0);
  const muzzle = part(ext(pMuz, 44, 11), M.cream, 5);
  const eyeGeo = new THREE.SphereGeometry(19.4, 40, 24); eyeGeo.scale(1, 1, .62);
  const eyes = EYES.map(x => { const h = new THREE.Group(), m = new THREE.Mesh(eyeGeo, M.eye); m.scale.setScalar(S); h.position.set((x - CX) * S, (CY - EYE_Y) * S, 29 * S); h.add(m); fox.add(h); h.userData.base = h.position.clone(); return h; });

  // exploded view: where each part goes when the fox comes apart
  const BLAST = [
    [outline, new THREE.Vector3(-.4, -.3, -2.6), new THREE.Euler(.2, 1.1, -.35)],
    [head, new THREE.Vector3(.9, 1.15, .5), new THREE.Euler(-.45, -.75, .45)],
    [muzzle, new THREE.Vector3(-1.1, -1.35, .7), new THREE.Euler(1.1, .5, -.5)],
    ...eyes.map((e, i) => [e, new THREE.Vector3((i - 1) * 1.9, .9 + .35 * i, 1.2 + .3 * i), new THREE.Euler(i * 2 - 1, 2.5, i)]),
  ];
  BLAST.forEach(([o]) => { o.userData.base = o.userData.base || o.position.clone(); });

  // ---- glow, orbit, ball, dust ---------------------------------------------------------------
  const radial = (inner, outer) => { const c = document.createElement('canvas'); c.width = c.height = 128; const g = c.getContext('2d'), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64); gr.addColorStop(0, inner); gr.addColorStop(.35, outer); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, 128, 128); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t; };
  const glowTex = radial('rgba(0,230,118,.55)', 'rgba(0,230,118,.16)'), dotTex = radial('rgba(255,255,255,1)', 'rgba(120,255,190,.5)');
  const glow = new THREE.Sprite(new THREE.SpriteMaterial({map: glowTex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending}));
  glow.scale.setScalar(4.6); glow.position.z = -1.2; scene.add(glow);

  const R = 2.12;
  const orbit = new THREE.Group(); orbit.rotation.set(.61, 0, -9 * Math.PI / 180, 'ZYX'); scene.add(orbit);
  const ringMat = new THREE.MeshBasicMaterial({color: '#00E676', transparent: true, opacity: .55, depthWrite: false});
  const ringGlowMat = new THREE.MeshBasicMaterial({color: '#00E676', transparent: true, opacity: .12, depthWrite: false, blending: THREE.AdditiveBlending});
  const ringGeo = new THREE.TorusGeometry(R, .013, 8, 260); ringGeo.rotateX(Math.PI / 2);
  const ringGlowGeo = new THREE.TorusGeometry(R, .05, 8, 200); ringGlowGeo.rotateX(Math.PI / 2);
  const ringHolder = new THREE.Group(); orbit.add(ringHolder);
  ringHolder.add(new THREE.Mesh(ringGeo, ringMat), new THREE.Mesh(ringGlowGeo, ringGlowMat));

  const ball = new THREE.Group(); ringHolder.add(ball);
  ball.add(new THREE.Mesh(new THREE.SphereGeometry(.065, 20, 14), new THREE.MeshBasicMaterial({color: '#eafff3'})));
  const halo = new THREE.Sprite(new THREE.SpriteMaterial({map: dotTex, color: '#00E676', transparent: true, depthWrite: false, blending: THREE.AdditiveBlending})); halo.scale.setScalar(.6); ball.add(halo);
  const ballLight = new THREE.PointLight('#5dffb0', 9, 4.5, 1.6); ball.add(ballLight);
  const TRAIL = 16, trail = Array.from({length: TRAIL}, (_, i) => { const s = new THREE.Sprite(new THREE.SpriteMaterial({map: dotTex, color: '#00E676', transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, opacity: .5 * (1 - i / TRAIL)})); s.scale.setScalar(.22 * (1 - i / TRAIL) + .04); ringHolder.add(s); return s; });
  const hist = [];

  const shock = new THREE.Mesh(new THREE.RingGeometry(.96, 1, 96).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({color: '#7dffbd', transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending}));
  orbit.add(shock); let shockT = -1;

  const N = lo ? 120 : small ? 180 : 320, pos = new Float32Array(N * 3);
  for (let i = 0; i < N; i++) { const r = 2.4 + Math.random() * 3.2, a = Math.random() * 6.283, h = (Math.random() - .5) * 3.4; pos.set([Math.cos(a) * r, h, Math.sin(a) * r - 1], i * 3); }
  const dustGeo = new THREE.BufferGeometry(); dustGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({map: dotTex, color: '#3dffa0', size: .055, transparent: true, opacity: .55, depthWrite: false, blending: THREE.AdditiveBlending}));
  scene.add(dust);

  // ---- layout + projection shared with the HTML texts in home.js -----------------------------
  let W = 0;
  const resize = () => { W = stage.offsetWidth * 1.5; renderer.setSize(W, W, false); orbit.scale.x = stage.offsetWidth < 520 ? .7 : 1; };
  resize(); addEventListener('resize', resize);
  const v = new THREE.Vector3();
  TF.project3d = a => { v.set(Math.cos(a) * R, 0, Math.sin(a) * R).applyMatrix4(orbit.matrixWorld).project(camera); return {x: v.x * W / 2, y: -v.y * W / 2}; };

  // ---- behaviour -----------------------------------------------------------------------------
  let typing = -1, hop = -1;
  TF.onSend = () => { typing = 0; };
  TF.onLaunch = () => { hop = 0; shockT = 0; };
  const ptr = {x: 0, y: 0, tx: 0, ty: 0, last: -9};
  addEventListener('pointermove', e => { if (e.pointerType !== 'mouse') return; ptr.tx = e.clientX / innerWidth * 2 - 1; ptr.ty = e.clientY / innerHeight * 2 - 1; ptr.last = performance.now() / 1000; }, {passive: true});

  const ease = x => x * x * (3 - 2 * x);
  let visible = true, t0 = performance.now(), last = t0, oa = 0, slow = 0, frames = 0;
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; }).observe(stage);

  function frame(now) {
    requestAnimationFrame(frame);
    if (!visible || document.hidden) { last = now; return; }
    const dt = Math.min(.05, (now - last) / 1000); last = now;
    const t = (now - t0) / 1000;
    // intro: the flat logo pops into 3D — parts fly together while the fox spins once
    const intro = Math.max(0, 1 - t / 1.9), ie = intro * intro * (3 - 2 * intro);
    const e = ease(Math.min(1, Math.max(TF.boom, ie * .7)));

    // pointer (desktop) or a slow idle sway, so the depth always reads
    const idle = now / 1000 - ptr.last > 2.5;
    ptr.x += ((idle ? 0 : ptr.tx) - ptr.x) * .06; ptr.y += ((idle ? 0 : ptr.ty) - ptr.y) * .06;
    let ry = ptr.x * .6 + (idle ? Math.sin(t * .55) * .38 + Math.sin(t * .21) * .12 : 0);
    let rx = ptr.y * .28 + Math.sin(t * .7) * .05;
    ry += -2 * Math.PI * ie;                                   // intro spin
    // hop when a text is launched
    let hy = 0, sq = 1;
    if (hop >= 0) { hop += dt; const p = Math.min(1, hop / .7); hy = Math.sin(p * Math.PI) * .22; sq = 1 + Math.sin(p * Math.PI * 2) * .05; if (p >= 1) hop = -1; }
    fox.rotation.set(rx + e * .5, ry + e * 1.3, Math.sin(t * .9) * .03 - e * .3);
    fox.position.y = .1 + Math.sin(t * 1.1) * .07 + hy;
    fox.scale.set(1 / sq, sq, 1).multiplyScalar(1 + e * .15);

    BLAST.forEach(([o, p, r]) => { o.position.copy(o.userData.base).addScaledVector(p, e); o.rotation.set(r.x * e, r.y * e, r.z * e); });
    // eyes bounce like a typing indicator
    if (typing >= 0) { typing += dt; if (typing > 2.2) typing = -1; }
    eyes.forEach((h, i) => { const k = typing >= 0 ? Math.max(0, Math.sin((typing - i * .12) / .9 * Math.PI * 2)) : 0; h.position.y += k * .09; h.position.z += k * .04; });

    // orbit + ball: faster along the front than the back
    oa += dt * (.55 + .45 * Math.sin(oa)) * 1.25;
    ball.position.set(Math.cos(oa) * R, 0, Math.sin(oa) * R);
    hist.unshift(ball.position.clone()); hist.length = Math.min(hist.length, TRAIL * 2);
    trail.forEach((s, i) => { const p = hist[Math.min(hist.length - 1, i * 2)]; s.position.copy(p); });
    ringHolder.scale.setScalar(1 + e * 1.6); ringHolder.rotation.y = e * 1.4;
    ringMat.opacity = .55 * (1 - e); ringGlowMat.opacity = .12 * (1 - e);
    ballLight.intensity = 9 * (1 - e);
    if (shockT >= 0) { shockT += dt; const p = Math.min(1, shockT / 1.1); shock.scale.setScalar(.25 + p * R * 1.15); shock.material.opacity = .8 * (1 - p) ** 2; if (p >= 1) shockT = -1; }

    glow.material.opacity = (.85 + Math.sin(t * 1.6) * .15) * (1 - e);
    dust.rotation.y = t * .03 + ptr.x * .15; dust.rotation.x = ptr.y * .08;
    dust.material.opacity = .55 * (1 - e * .6);
    canvas.style.opacity = TF.boom > .02 ? String(Math.max(0, 1 - Math.pow(TF.boom / .7, 2))) : '';   // gone before the parts get huge

    renderer.render(scene, camera);
    if (++frames === 1) stage.classList.add('is-3d');
    // keep it smooth on slow phones: drop resolution if frames are long
    if (frames < 90) { slow += dt > .034 ? 1 : 0; if (frames === 89 && slow > 45) renderer.setPixelRatio(1); }
  }
  stage.appendChild(canvas);
  requestAnimationFrame(frame);
}
