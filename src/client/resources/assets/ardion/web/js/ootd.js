const canvas = document.getElementById('skinCanvas');
const playerName = document.getElementById('playerName');
const status = document.getElementById('status');
const capeState = document.getElementById('capeState');

let socket = null;
let viewer = null;
let controls = null;
let lastTextureKey = '';
let zoomLevel = 0.86;
let currentModelType = 'default';

const MIN_ZOOM = 0.45;
const MAX_ZOOM = 1.45;
const DANCE_SPEED = 1.0;
const DANCE_LOOP_SECONDS = 12.0;

try {
  ensureViewer('/player-texture/skin');
} catch (error) {
  console.error(error);
}
connect();

window.addEventListener('resize', resizeViewer);
canvas.addEventListener('pointerdown', () => canvas.classList.add('dragging'));
window.addEventListener('pointerup', () => canvas.classList.remove('dragging'));
canvas.addEventListener('wheel', event => {
  if (!viewer) return;
  event.preventDefault();
  const direction = event.deltaY > 0 ? -1 : 1;
  setZoom(zoomLevel + direction * 0.08);
}, { passive: false });

function connect() {
  socket = new WebSocket(`ws://${location.host}/ws`);
  socket.onopen = () => {
    status.textContent = 'Syncing';
  };
  socket.onclose = () => {
    status.textContent = 'Reconnecting';
    setTimeout(connect, 700);
  };
  socket.onmessage = event => {
    const data = JSON.parse(event.data);
    if (data.type !== 'state') return;
    applyState(data);
  };
}

function applyState(data) {
  const player = data.player || {};
  const skin = player.skin || '/player-texture/skin';
  const cape = player.cape || '';
  const slim = Boolean(player.slim);
  currentModelType = slim ? 'slim' : 'default';
  const textureKey = `${skin}|${cape}|${slim}`;

  playerName.textContent = player.name || 'Player';
  capeState.textContent = cape ? (player.capeName || 'Cape') : 'No cape';
  status.textContent = 'Online';

  if (textureKey === lastTextureKey) return;
  lastTextureKey = textureKey;

  const skinViewer = ensureViewer(skin);
  const skinOptions = {
    model: currentModelType
  };
  applyModelType(currentModelType);
  const loadResult = skinViewer.loadSkin(skin, skinOptions);
  Promise.resolve(loadResult).finally(() => applyModelType(currentModelType));
  skinViewer.loadCape(cape || null);
}

function applyModelType(modelType) {
  if (!viewer?.playerObject?.skin) return;
  viewer.playerObject.skin.modelType = modelType;
}

function ensureViewer(initialSkin) {
  if (viewer) return viewer;
  if (!window.skinview3d) {
    status.textContent = 'Viewer failed';
    throw new Error('skinview3d is not loaded');
  }

  viewer = new skinview3d.SkinViewer({
    canvas,
    width: canvas.clientWidth,
    height: canvas.clientHeight,
    skin: initialSkin,
    model: currentModelType
  });

  viewer.background = 0x05070b;
  viewer.fov = 55;
  setZoom(zoomLevel);
  viewer.autoRotate = false;
  applyDanceAnimation();
  if (viewer.globalLight) viewer.globalLight.intensity = 2.3;
  if (viewer.cameraLight) viewer.cameraLight.intensity = 0.7;

  controls = typeof skinview3d.createOrbitControls === 'function'
    ? skinview3d.createOrbitControls(viewer)
    : viewer.controls;
  if (controls) {
    controls.enableRotate = true;
    controls.enableZoom = true;
    controls.enablePan = false;
    controls.rotateSpeed = 0.7;
    controls.zoomSpeed = 0.85;
    controls.minDistance = 18;
    controls.maxDistance = 90;
  }

  resizeViewer();
  return viewer;
}

function applyDanceAnimation() {
  if (!viewer || typeof skinview3d.FunctionAnimation !== 'function') return;
  const dance = new skinview3d.FunctionAnimation((player, progress) => {
    player.resetJoints();

    const phase = (progress % DANCE_LOOP_SECONDS) / DANCE_LOOP_SECONDS;
    const pose = interpolatePose(phase);
    applyChoreographyPose(player, pose, danceProfile());
  });
  dance.speed = DANCE_SPEED;
  viewer.animation = dance;
}

const DANCE_POSES = [
  pose({ phase: 0.000, moveX: 0.00, moveZ: 0.00, turnY: 0.00, leanZ: 0.00, bodyX: -0.03, headX: 0.02, leftArmX: -0.08, leftArmZ: -0.46, rightArmX: 0.08, rightArmZ: 0.46, leftLegZ: 0.035, rightLegZ: -0.035 }),
  pose({ phase: 0.035, moveX: -0.08, moveZ: 0.02, turnY: -0.08, leanZ: -0.045, bodyX: -0.08, bodyY: -0.18, bodyZ: 0.10, headY: 0.14, headZ: -0.06, leftArmX: -0.72, leftArmY: 0.12, leftArmZ: -0.70, rightArmX: 0.18, rightArmY: -0.05, rightArmZ: 0.44, leftLegX: 0.09, leftLegY: -0.05, leftLegZ: 0.10, rightLegX: -0.08, rightLegZ: -0.08 }),
  pose({ phase: 0.070, moveX: -0.20, moveZ: 0.04, turnY: -0.02, leanZ: 0.015, bodyX: 0.10, bodyY: -0.06, bodyZ: -0.04, headX: -0.04, headY: 0.04, leftArmX: -0.20, leftArmY: 0.05, leftArmZ: -0.40, rightArmX: -0.82, rightArmY: -0.10, rightArmZ: 0.68, leftLegX: -0.04, leftLegZ: 0.04, rightLegX: 0.09, rightLegZ: -0.05 }),
  pose({ phase: 0.110, moveX: -0.06, moveZ: 0.01, turnY: 0.10, leanZ: 0.065, bodyX: -0.10, bodyY: 0.22, bodyZ: -0.12, headY: -0.16, headZ: 0.07, leftArmX: 0.30, leftArmY: 0.06, leftArmZ: -0.48, rightArmX: -0.70, rightArmY: -0.16, rightArmZ: 0.78, leftLegX: -0.09, leftLegZ: 0.10, rightLegX: 0.05, rightLegY: 0.05, rightLegZ: -0.09 }),
  pose({ phase: 0.150, moveX: 0.16, moveZ: -0.02, turnY: 0.14, leanZ: 0.020, bodyX: 0.07, bodyY: 0.06, bodyZ: 0.04, headX: -0.035, headY: -0.06, leftArmX: -0.92, leftArmY: -0.08, leftArmZ: -0.62, rightArmX: -0.20, rightArmY: 0.10, rightArmZ: 0.54, leftLegX: 0.07, leftLegZ: 0.045, rightLegX: -0.06, rightLegZ: -0.06 }),
  pose({ phase: 0.190, moveX: 0.24, moveZ: -0.03, turnY: -0.10, leanZ: -0.065, bodyX: -0.10, bodyY: -0.24, bodyZ: 0.13, headY: 0.18, headZ: -0.07, leftArmX: -0.24, leftArmY: -0.06, leftArmZ: -0.42, rightArmX: 0.42, rightArmY: 0.06, rightArmZ: 0.68, leftLegX: 0.035, leftLegY: -0.05, leftLegZ: 0.10, rightLegX: -0.09, rightLegZ: -0.11 }),
  pose({ phase: 0.230, moveX: 0.06, moveZ: 0.01, turnY: -0.02, leanZ: 0.020, bodyX: 0.11, bodyY: -0.05, bodyZ: -0.05, headX: 0.03, headY: 0.04, leftArmX: -0.82, leftArmY: 0.16, leftArmZ: -0.70, rightArmX: 0.10, rightArmY: -0.10, rightArmZ: 0.46, leftLegX: -0.05, leftLegZ: 0.05, rightLegX: 0.09, rightLegZ: -0.04 }),
  pose({ phase: 0.270, moveX: -0.14, moveZ: 0.03, turnY: 0.10, leanZ: 0.060, bodyX: -0.09, bodyY: 0.22, bodyZ: -0.12, headY: -0.16, headZ: 0.065, leftArmX: 0.28, leftArmY: 0.08, leftArmZ: -0.50, rightArmX: -0.82, rightArmY: -0.14, rightArmZ: 0.74, leftLegX: -0.09, leftLegZ: 0.10, rightLegX: 0.05, rightLegY: 0.05, rightLegZ: -0.09 }),
  pose({ phase: 0.315, moveX: 0.00, moveZ: 0.00, turnY: 0.00, leanZ: 0.00, bodyX: 0.00, bodyY: 0.00, bodyZ: 0.00, headX: -0.02, leftArmX: -0.54, leftArmY: 0.08, leftArmZ: -0.62, rightArmX: -0.54, rightArmY: -0.08, rightArmZ: 0.62, leftLegZ: 0.04, rightLegZ: -0.04 }),
  pose({ phase: 0.355, moveX: 0.02, moveZ: -0.05, turnY: 0.04, leanZ: 0.00, bodyX: -0.18, bodyY: 0.04, bodyZ: 0.00, headX: 0.10, leftArmX: -1.02, leftArmY: 0.10, leftArmZ: -0.58, rightArmX: -1.02, rightArmY: -0.10, rightArmZ: 0.58, leftLegX: 0.03, leftLegZ: 0.04, rightLegX: -0.03, rightLegZ: -0.04 }),
  pose({ phase: 0.395, moveX: 0.16, moveZ: -0.02, turnY: 0.18, leanZ: 0.070, bodyX: -0.06, bodyY: 0.28, bodyZ: -0.15, headY: -0.18, headZ: 0.08, leftArmX: -0.08, leftArmY: 0.12, leftArmZ: -0.46, rightArmX: -0.96, rightArmY: -0.18, rightArmZ: 0.82, leftLegX: -0.10, leftLegZ: 0.105, rightLegX: 0.06, rightLegZ: -0.09 }),
  pose({ phase: 0.440, moveX: 0.22, moveZ: 0.02, turnY: 0.02, leanZ: -0.020, bodyX: 0.12, bodyY: 0.06, bodyZ: 0.05, headX: -0.05, leftArmX: -0.94, leftArmY: -0.12, leftArmZ: -0.68, rightArmX: -0.12, rightArmY: 0.12, rightArmZ: 0.48, leftLegX: 0.08, leftLegZ: 0.045, rightLegX: -0.07, rightLegZ: -0.06 }),
  pose({ phase: 0.485, moveX: 0.04, moveZ: 0.04, turnY: -0.14, leanZ: -0.070, bodyX: -0.09, bodyY: -0.26, bodyZ: 0.15, headY: 0.19, headZ: -0.08, leftArmX: -0.72, leftArmY: 0.16, leftArmZ: -0.74, rightArmX: 0.28, rightArmY: -0.06, rightArmZ: 0.48, leftLegX: 0.06, leftLegY: -0.05, leftLegZ: 0.09, rightLegX: -0.09, rightLegZ: -0.10 }),
  pose({ phase: 0.530, moveX: -0.18, moveZ: 0.00, turnY: -0.10, leanZ: 0.020, bodyX: 0.10, bodyY: -0.05, bodyZ: -0.04, headX: -0.035, leftArmX: -0.10, leftArmY: 0.04, leftArmZ: -0.46, rightArmX: -0.90, rightArmY: -0.12, rightArmZ: 0.76, leftLegX: -0.05, leftLegZ: 0.045, rightLegX: 0.085, rightLegZ: -0.05 }),
  pose({ phase: 0.575, moveX: -0.22, moveZ: -0.04, turnY: 0.12, leanZ: 0.070, bodyX: -0.11, bodyY: 0.24, bodyZ: -0.14, headY: -0.18, headZ: 0.08, leftArmX: 0.32, leftArmY: 0.06, leftArmZ: -0.52, rightArmX: -0.76, rightArmY: -0.16, rightArmZ: 0.78, leftLegX: -0.10, leftLegZ: 0.11, rightLegX: 0.06, rightLegY: 0.05, rightLegZ: -0.09 }),
  pose({ phase: 0.625, moveX: 0.00, moveZ: -0.06, turnY: 0.00, leanZ: 0.00, bodyX: 0.12, bodyY: 0.00, bodyZ: 0.00, headX: -0.08, leftArmX: -1.18, leftArmY: 0.06, leftArmZ: -0.64, rightArmX: -1.18, rightArmY: -0.06, rightArmZ: 0.64, leftLegX: 0.04, leftLegZ: 0.04, rightLegX: -0.04, rightLegZ: -0.04 }),
  pose({ phase: 0.675, moveX: 0.18, moveZ: -0.02, turnY: 0.20, leanZ: 0.075, bodyX: -0.10, bodyY: 0.28, bodyZ: -0.16, headY: -0.20, headZ: 0.08, leftArmX: -0.12, leftArmY: 0.14, leftArmZ: -0.48, rightArmX: -1.02, rightArmY: -0.20, rightArmZ: 0.86, leftLegX: -0.10, leftLegZ: 0.11, rightLegX: 0.07, rightLegZ: -0.10 }),
  pose({ phase: 0.725, moveX: 0.23, moveZ: 0.03, turnY: 0.04, leanZ: -0.018, bodyX: 0.13, bodyY: 0.04, bodyZ: 0.05, headX: -0.04, leftArmX: -0.96, leftArmY: -0.14, leftArmZ: -0.70, rightArmX: -0.16, rightArmY: 0.14, rightArmZ: 0.50, leftLegX: 0.08, leftLegZ: 0.05, rightLegX: -0.07, rightLegZ: -0.06 }),
  pose({ phase: 0.775, moveX: 0.00, moveZ: 0.06, turnY: -0.18, leanZ: -0.075, bodyX: -0.10, bodyY: -0.28, bodyZ: 0.16, headY: 0.20, headZ: -0.08, leftArmX: -0.78, leftArmY: 0.18, leftArmZ: -0.78, rightArmX: 0.30, rightArmY: -0.06, rightArmZ: 0.48, leftLegX: 0.07, leftLegY: -0.05, leftLegZ: 0.095, rightLegX: -0.10, rightLegZ: -0.11 }),
  pose({ phase: 0.825, moveX: -0.18, moveZ: 0.02, turnY: -0.12, leanZ: 0.020, bodyX: 0.11, bodyY: -0.05, bodyZ: -0.04, headX: -0.03, leftArmX: -0.08, leftArmY: 0.05, leftArmZ: -0.48, rightArmX: -0.96, rightArmY: -0.14, rightArmZ: 0.78, leftLegX: -0.055, leftLegZ: 0.05, rightLegX: 0.09, rightLegZ: -0.055 }),
  pose({ phase: 0.875, moveX: -0.24, moveZ: -0.02, turnY: 0.12, leanZ: 0.070, bodyX: -0.11, bodyY: 0.24, bodyZ: -0.15, headY: -0.18, headZ: 0.08, leftArmX: 0.30, leftArmY: 0.08, leftArmZ: -0.52, rightArmX: -0.84, rightArmY: -0.16, rightArmZ: 0.82, leftLegX: -0.10, leftLegZ: 0.105, rightLegX: 0.06, rightLegY: 0.05, rightLegZ: -0.09 }),
  pose({ phase: 0.925, moveX: -0.04, moveZ: 0.00, turnY: 0.02, leanZ: -0.016, bodyX: 0.08, bodyY: 0.02, bodyZ: 0.025, headX: -0.02, leftArmX: -0.72, leftArmY: -0.08, leftArmZ: -0.60, rightArmX: -0.24, rightArmY: 0.08, rightArmZ: 0.50, leftLegX: 0.06, leftLegZ: 0.04, rightLegX: -0.055, rightLegZ: -0.05 }),
  pose({ phase: 0.965, moveX: 0.00, moveZ: 0.00, turnY: 0.00, leanZ: 0.00, bodyX: -0.035, headX: 0.02, leftArmX: -0.14, leftArmZ: -0.46, rightArmX: 0.14, rightArmZ: 0.46, leftLegZ: 0.035, rightLegZ: -0.035 }),
  pose({ phase: 1.000, moveX: 0.00, moveZ: 0.00, turnY: 0.00, leanZ: 0.00, bodyX: -0.03, headX: 0.02, leftArmX: -0.08, leftArmZ: -0.46, rightArmX: 0.08, rightArmZ: 0.46, leftLegZ: 0.035, rightLegZ: -0.035 })
];

const STAGE_DANCE_POSES = [
  pose({ phase: 0.000, turnY: 0.00, bodyX: -0.03, headX: 0.02, leftArmX: -0.10, leftArmZ: -0.44, rightArmX: 0.10, rightArmZ: 0.44, leftLegZ: 0.03, rightLegZ: -0.03 }),
  pose({ phase: 0.050, moveX: -0.14, turnY: -0.08, leanZ: -0.05, bodyX: -0.08, bodyY: -0.16, bodyZ: 0.09, headY: 0.13, leftArmX: -0.70, leftArmY: 0.12, leftArmZ: -0.66, rightArmX: 0.18, rightArmZ: 0.42, leftLegX: 0.08, leftLegZ: 0.08, rightLegX: -0.07, rightLegZ: -0.07 }),
  pose({ phase: 0.100, moveX: -0.20, turnY: -0.02, leanZ: 0.02, bodyX: 0.08, bodyY: -0.04, bodyZ: -0.04, headX: -0.03, leftArmX: -0.18, leftArmZ: -0.38, rightArmX: -0.78, rightArmY: -0.10, rightArmZ: 0.64, leftLegX: -0.04, rightLegX: 0.08 }),
  pose({ phase: 0.150, moveX: 0.00, turnY: 0.12, leanZ: 0.07, bodyX: -0.09, bodyY: 0.22, bodyZ: -0.12, headY: -0.16, leftArmX: 0.30, leftArmZ: -0.48, rightArmX: -0.68, rightArmY: -0.15, rightArmZ: 0.76, leftLegX: -0.08, leftLegZ: 0.10, rightLegX: 0.05, rightLegZ: -0.09 }),
  pose({ phase: 0.200, moveX: 0.18, moveZ: -0.03, turnY: 0.10, leanZ: 0.02, bodyX: 0.06, bodyZ: 0.04, headX: -0.04, leftArmX: -0.82, leftArmY: -0.08, leftArmZ: -0.60, rightArmX: -0.20, rightArmY: 0.10, rightArmZ: 0.50, leftLegX: 0.07, rightLegX: -0.06 }),
  pose({ phase: 0.250, moveX: 0.22, turnY: -0.10, leanZ: -0.07, bodyX: -0.10, bodyY: -0.24, bodyZ: 0.14, headY: 0.18, leftArmX: -0.24, leftArmZ: -0.42, rightArmX: 0.42, rightArmZ: 0.68, leftLegX: 0.03, leftLegZ: 0.10, rightLegX: -0.09, rightLegZ: -0.11 }),
  pose({ phase: 0.300, moveZ: -0.04, turnY: 0.00, bodyX: -0.18, headX: 0.10, leftArmX: -1.05, leftArmY: 0.12, leftArmZ: -0.60, rightArmX: -1.05, rightArmY: -0.12, rightArmZ: 0.60, leftLegX: 0.03, rightLegX: -0.03 }),
  pose({ phase: 0.340, moveX: 0.08, moveZ: -0.02, turnY: 0.20, leanZ: 0.08, bodyX: -0.06, bodyY: 0.30, bodyZ: -0.16, headY: -0.20, leftArmX: -0.08, leftArmY: 0.14, leftArmZ: -0.48, rightArmX: -0.98, rightArmY: -0.20, rightArmZ: 0.84, leftLegX: -0.10, leftLegZ: 0.11, rightLegX: 0.07, rightLegZ: -0.10 }),
  pose({ phase: 0.375, moveX: 0.02, moveY: 0.08, turnY: 1.57, leanZ: 0.03, bodyX: -0.02, bodyY: 0.08, bodyZ: -0.04, headY: -0.06, leftArmX: -1.22, leftArmY: 0.10, leftArmZ: -0.72, rightArmX: -1.22, rightArmY: -0.10, rightArmZ: 0.72, leftLegX: -0.12, rightLegX: 0.05, rightLegZ: -0.06 }),
  pose({ phase: 0.410, moveX: 0.02, moveY: 0.08, turnY: 3.14, leanZ: -0.03, bodyX: -0.02, bodyY: -0.08, bodyZ: 0.04, headY: 0.06, leftArmX: -1.22, leftArmY: 0.10, leftArmZ: -0.72, rightArmX: -1.22, rightArmY: -0.10, rightArmZ: 0.72, leftLegX: 0.05, rightLegX: -0.12, leftLegZ: 0.06 }),
  pose({ phase: 0.445, moveX: 0.02, moveY: 0.08, turnY: 4.71, leanZ: 0.03, bodyX: -0.02, bodyY: 0.08, bodyZ: -0.04, headY: -0.06, leftArmX: -1.22, leftArmY: 0.10, leftArmZ: -0.72, rightArmX: -1.22, rightArmY: -0.10, rightArmZ: 0.72, leftLegX: -0.12, rightLegX: 0.05, rightLegZ: -0.06 }),
  pose({ phase: 0.480, moveX: 0.02, turnY: 6.28, bodyX: 0.00, headX: -0.03, leftArmX: -0.92, leftArmZ: -0.64, rightArmX: -0.92, rightArmZ: 0.64, leftLegX: 0.04, rightLegX: -0.04 }),
  pose({ phase: 0.520, moveX: -0.10, turnY: 6.28, leanZ: -0.04, bodyX: -0.04, bodyY: -0.12, bodyZ: 0.08, headY: 0.08, leftArmX: -1.15, leftArmY: 0.05, leftArmZ: -0.58, rightArmX: 0.20, rightArmZ: 0.48, leftLegX: 0.02, rightLegX: -0.12, rightLegZ: -0.08 }),
  pose({ phase: 0.555, moveX: -0.10, turnY: 6.28, leanZ: -0.02, bodyX: -0.02, headX: 0.04, headY: 0.02, leftArmX: -1.48, leftArmY: 0.06, leftArmZ: -0.30, rightArmX: 0.10, rightArmZ: 0.48, leftLegX: 0.02, rightLegX: -0.10 }),
  pose({ phase: 0.590, moveX: -0.08, turnY: 6.28, leanZ: -0.04, bodyX: -0.04, headX: 0.06, leftArmX: -1.35, leftArmY: 0.05, leftArmZ: -0.36, rightArmX: 0.06, rightArmZ: 0.46, leftLegX: 0.02, rightLegX: -0.08 }),
  pose({ phase: 0.635, moveX: 0.00, turnY: 6.28, bodyX: -0.02, headX: -0.02, leftArmX: -1.05, leftArmY: 0.10, leftArmZ: -0.72, rightArmX: -1.05, rightArmY: -0.10, rightArmZ: 0.72, leftLegZ: 0.04, rightLegZ: -0.04 }),
  pose({ phase: 0.680, moveX: 0.05, turnY: 6.28, leanZ: 0.02, bodyX: -0.04, bodyY: 0.06, bodyZ: -0.05, headY: -0.05, leftArmX: -1.25, leftArmY: 0.16, leftArmZ: -0.88, rightArmX: -1.25, rightArmY: -0.16, rightArmZ: 0.88, leftLegX: -0.04, leftLegZ: 0.05, rightLegX: 0.08, rightLegZ: -0.05 }),
  pose({ phase: 0.720, moveX: 0.05, moveY: 0.04, turnY: 6.28, leanZ: 0.04, bodyX: 0.02, bodyY: 0.04, bodyZ: -0.08, headY: -0.05, leftArmX: -1.36, leftArmY: 0.18, leftArmZ: -0.92, rightArmX: -1.36, rightArmY: -0.18, rightArmZ: 0.92, leftLegX: -0.10, leftLegY: 0.06, leftLegZ: 0.08, rightLegX: 0.38, rightLegY: -0.08, rightLegZ: -0.08 }),
  pose({ phase: 0.760, turnY: 6.28, bodyX: -0.02, headX: -0.02, leftArmX: -1.10, leftArmZ: -0.66, rightArmX: -1.10, rightArmZ: 0.66, leftLegZ: 0.04, rightLegZ: -0.04 }),
  pose({ phase: 0.805, turnY: 6.28, bodyX: 0.55, headX: 0.35, leftArmX: 0.22, leftArmY: 0.04, leftArmZ: -0.46, rightArmX: 0.22, rightArmY: -0.04, rightArmZ: 0.46, leftLegX: -0.02, rightLegX: -0.02 }),
  pose({ phase: 0.850, turnY: 6.28, bodyX: 0.72, headX: 0.45, leftArmX: 0.36, leftArmZ: -0.40, rightArmX: 0.36, rightArmZ: 0.40, leftLegX: -0.04, rightLegX: -0.04 }),
  pose({ phase: 0.900, turnY: 6.28, bodyX: 0.20, headX: 0.10, leftArmX: 0.02, leftArmZ: -0.44, rightArmX: 0.02, rightArmZ: 0.44, leftLegZ: 0.035, rightLegZ: -0.035 }),
  pose({ phase: 0.950, turnY: 6.28, bodyX: -0.03, headX: 0.02, leftArmX: -0.10, leftArmZ: -0.44, rightArmX: 0.10, rightArmZ: 0.44, leftLegZ: 0.03, rightLegZ: -0.03 }),
  pose({ phase: 1.000, turnY: 6.28, bodyX: -0.03, headX: 0.02, leftArmX: -0.10, leftArmZ: -0.44, rightArmX: 0.10, rightArmZ: 0.44, leftLegZ: 0.03, rightLegZ: -0.03 })
];

function pose(values) {
  return {
    phase: values.phase,
    moveX: values.moveX ?? 0.0,
    moveY: values.moveY ?? 0.0,
    moveZ: values.moveZ ?? 0.0,
    turnY: values.turnY ?? 0.0,
    leanZ: values.leanZ ?? 0.0,
    bodyX: values.bodyX ?? 0.0,
    bodyY: values.bodyY ?? 0.0,
    bodyZ: values.bodyZ ?? 0.0,
    headX: values.headX ?? 0.0,
    headY: values.headY ?? 0.0,
    headZ: values.headZ ?? 0.0,
    leftArmX: values.leftArmX ?? 0.0,
    leftArmY: values.leftArmY ?? 0.0,
    leftArmZ: values.leftArmZ ?? -0.35,
    rightArmX: values.rightArmX ?? 0.0,
    rightArmY: values.rightArmY ?? 0.0,
    rightArmZ: values.rightArmZ ?? 0.35,
    leftLegX: values.leftLegX ?? 0.0,
    leftLegY: values.leftLegY ?? 0.0,
    leftLegZ: values.leftLegZ ?? 0.0,
    rightLegX: values.rightLegX ?? 0.0,
    rightLegY: values.rightLegY ?? 0.0,
    rightLegZ: values.rightLegZ ?? 0.0
  };
}

function applyChoreographyPose(player, pose, profile) {
  const armOut = profile.armOut;
  const armSweep = profile.armSweep;
  const foot = profile.foot;

  player.position.x = pose.moveX * foot;
  player.position.y = pose.moveY * foot;
  player.position.z = pose.moveZ * foot;
  player.rotation.x = 0.0;
  player.rotation.y = pose.turnY;
  player.rotation.z = pose.leanZ;

  player.skin.body.rotation.x = pose.bodyX;
  player.skin.body.rotation.y = pose.bodyY;
  player.skin.body.rotation.z = pose.bodyZ;
  player.skin.head.rotation.x = pose.headX;
  player.skin.head.rotation.y = pose.headY;
  player.skin.head.rotation.z = pose.headZ;

  player.skin.leftArm.rotation.x = pose.leftArmX * armSweep;
  player.skin.leftArm.rotation.y = pose.leftArmY * armSweep;
  player.skin.leftArm.rotation.z = pose.leftArmZ * armOut;
  player.skin.rightArm.rotation.x = pose.rightArmX * armSweep;
  player.skin.rightArm.rotation.y = pose.rightArmY * armSweep;
  player.skin.rightArm.rotation.z = pose.rightArmZ * armOut;

  player.skin.leftLeg.rotation.x = pose.leftLegX * foot;
  player.skin.leftLeg.rotation.y = pose.leftLegY * foot;
  player.skin.leftLeg.rotation.z = pose.leftLegZ * foot;
  player.skin.rightLeg.rotation.x = pose.rightLegX * foot;
  player.skin.rightLeg.rotation.y = pose.rightLegY * foot;
  player.skin.rightLeg.rotation.z = pose.rightLegZ * foot;
}

function danceProfile() {
  return currentModelType === 'slim'
    ? { armOut: 1.10, armSweep: 0.92, foot: 0.95 }
    : { armOut: 1.0, armSweep: 1.0, foot: 1.0 };
}

function interpolatePose(phase) {
  for (let i = 0; i < STAGE_DANCE_POSES.length - 1; i++) {
    const from = STAGE_DANCE_POSES[i];
    const to = STAGE_DANCE_POSES[i + 1];
    if (phase < from.phase || phase > to.phase) continue;
    const local = smoothstep((phase - from.phase) / (to.phase - from.phase));
    return mixPose(from, to, local);
  }
  return STAGE_DANCE_POSES[0];
}

function mixPose(from, to, amount) {
  const mixed = {};
  for (const key of Object.keys(from)) {
    if (key === 'phase') continue;
    mixed[key] = from[key] + (to[key] - from[key]) * amount;
  }
  return mixed;
}

function smoothstep(value) {
  const t = Math.max(0.0, Math.min(1.0, value));
  return t * t * (3.0 - 2.0 * t);
}

function resizeViewer() {
  if (!viewer) return;
  viewer.width = Math.max(1, canvas.clientWidth);
  viewer.height = Math.max(1, canvas.clientHeight);
}

function setZoom(value) {
  zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
  if (viewer) viewer.zoom = zoomLevel;
}
