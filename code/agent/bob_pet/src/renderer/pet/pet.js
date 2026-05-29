const wrap = document.getElementById('drag-region');
const hint = document.getElementById('hint');
const canvas = document.getElementById('robot');
const ctx = canvas.getContext('2d');

const THEMES = {
  blue:   { primary: '#3b82f6', secondary: '#93c5fd', accent: '#1d4ed8', eye: '#fbbf24', body: '#dbeafe', dark: '#1e3a5f' },
  white:  { primary: '#6b7280', secondary: '#d1d5db', accent: '#374151', eye: '#22c55e', body: '#f3f4f6', dark: '#1f2937' },
  black:  { primary: '#a3a3a3', secondary: '#525252', accent: '#e5e5e5', eye: '#ef4444', body: '#262626', dark: '#0a0a0a' },
  green:  { primary: '#22c55e', secondary: '#86efac', accent: '#15803d', eye: '#fbbf24', body: '#dcfce7', dark: '#14532d' },
  orange: { primary: '#f97316', secondary: '#fdba74', accent: '#c2410c', eye: '#22c55e', body: '#fff7ed', dark: '#7c2d12' },
  purple: { primary: '#a855f7', secondary: '#d8b4fe', accent: '#7e22ce', eye: '#fbbf24', body: '#f3e8ff', dark: '#3b0764' },
  pink:   { primary: '#ec4899', secondary: '#f9a8d4', accent: '#be185d', eye: '#fbbf24', body: '#fce7f3', dark: '#831843' }
};

let currentTheme = THEMES.blue;
let currentState = 'idle';
let animFrame = 0;
let blinkTimer = 0;
let isBlinking = false;
let i18n = {};

function t(key) { return i18n[key] || key; }

const stateHintKeys = {
  idle: 'petIdle',
  working: 'petWorking',
  thinking: 'petThinking',
  success: 'petSuccess',
  waiting: 'petWaiting',
  rest: 'petRest'
};

function px(x, y, w, h, color) {
  ctx.fillStyle = color;
  ctx.fillRect(x, y, w, h);
}

function drawRobot(state, frame) {
  const th = currentTheme;
  ctx.clearRect(0, 0, 120, 120);

  const bounce = state === 'success' ? Math.sin(frame * 0.3) * 6 : 0;
  const sway = state === 'thinking' ? Math.sin(frame * 0.08) * 2 : 0;
  const walk = state === 'rest' ? Math.sin(frame * 0.1) * 3 : 0;

  const ox = sway + walk;
  const oy = bounce;

  // Antenna
  px(57 + ox, 4 + oy, 6, 4, th.accent);
  px(58 + ox, 0 + oy, 4, 4, th.eye);

  // Head
  px(30 + ox, 8 + oy, 60, 36, th.primary);
  px(32 + ox, 10 + oy, 56, 32, th.body);

  // Eyes
  const eyeY = 20 + oy;
  if (state === 'waiting' || isBlinking) {
    px(40 + ox, eyeY, 10, 2, th.dark);
    px(70 + ox, eyeY, 10, 2, th.dark);
  } else if (state === 'thinking') {
    px(42 + ox, eyeY, 6, 6, th.eye);
    px(72 + ox, eyeY, 6, 6, th.eye);
  } else {
    px(40 + ox, eyeY, 10, 10, th.dark);
    px(42 + ox, eyeY + 2, 6, 6, th.eye);
    px(70 + ox, eyeY, 10, 10, th.dark);
    px(72 + ox, eyeY + 2, 6, 6, th.eye);
  }

  // Mouth
  const mouthY = 34 + oy;
  if (state === 'success') {
    px(46 + ox, mouthY, 4, 4, th.accent);
    px(50 + ox, mouthY + 2, 4, 2, th.accent);
    px(54 + ox, mouthY, 4, 4, th.accent);
  } else if (state === 'working') {
    px(48 + ox, mouthY, 12, 2, th.accent);
    px(50 + ox, mouthY + 2, 8, 2, th.accent);
  } else {
    px(48 + ox, mouthY, 12, 4, th.accent);
    px(50 + ox, mouthY, 8, 2, th.body);
  }

  // Neck
  px(50 + ox, 44 + oy, 20, 4, th.secondary);

  // Body
  px(28 + ox, 48 + oy, 64, 36, th.primary);
  px(30 + ox, 50 + oy, 60, 32, th.body);

  // Chest panel
  px(42 + ox, 54 + oy, 36, 20, th.secondary);
  px(44 + ox, 56 + oy, 32, 16, th.dark);

  // Chest lights
  const lightPhase = Math.floor(frame / 15) % 3;
  px(48 + ox, 58 + oy, 4, 4, lightPhase === 0 ? th.eye : th.dark);
  px(56 + ox, 58 + oy, 4, 4, lightPhase === 1 ? th.eye : th.dark);
  px(64 + ox, 58 + oy, 4, 4, lightPhase === 2 ? th.eye : th.dark);
  px(48 + ox, 66 + oy, 4, 4, lightPhase === 2 ? '#ef4444' : th.dark);
  px(56 + ox, 66 + oy, 4, 4, '#22c55e');
  px(64 + ox, 66 + oy, 4, 4, lightPhase === 1 ? '#3b82f6' : th.dark);

  // Arms
  const armSwing = state === 'working' ? Math.sin(frame * 0.4) * 4 : 0;
  px(20 + ox, 52 + oy + armSwing, 8, 24, th.primary);
  px(22 + ox, 54 + oy + armSwing, 4, 20, th.secondary);
  px(92 + ox, 52 + oy - armSwing, 8, 24, th.primary);
  px(94 + ox, 54 + oy - armSwing, 4, 20, th.secondary);

  // Hands
  px(20 + ox, 76 + oy + armSwing, 10, 6, th.accent);
  px(90 + ox, 76 + oy - armSwing, 10, 6, th.accent);

  // Legs
  const legSwing = state === 'rest' ? Math.sin(frame * 0.12) * 3 : 0;
  px(38 + ox, 84 + oy, 12, 20, th.primary);
  px(40 + ox, 86 + oy, 8, 16, th.secondary);
  px(70 + ox, 84 + oy, 12, 20, th.primary);
  px(72 + ox, 86 + oy, 8, 16, th.secondary);

  // Feet
  px(36 + ox + legSwing, 104 + oy, 16, 6, th.accent);
  px(68 + ox - legSwing, 104 + oy, 16, 6, th.accent);

  // Thinking bubble
  if (state === 'thinking') {
    px(92 + ox, 8 + oy, 4, 4, th.accent);
    px(98 + ox, 4 + oy, 4, 4, th.accent);
    px(102 + ox, 0 + oy, 12, 10, th.secondary);
    px(104 + ox, 2 + oy, 8, 6, th.body);
    px(106 + ox, 3 + oy, 4, 4, th.accent);
  }

  // Success sparkles
  if (state === 'success') {
    const sparkle = (sx, sy, delay) => {
      if ((frame + delay) % 20 < 10) {
        px(sx + ox, sy + oy, 2, 2, th.eye);
        px(sx + 2 + ox, sy + oy, 2, 2, th.eye);
        px(sx + ox, sy + 2 + oy, 2, 2, th.eye);
      }
    };
    sparkle(10, 14, 0);
    sparkle(100, 20, 7);
    sparkle(16, 50, 14);
  }
}

function animate() {
  animFrame++;
  blinkTimer++;
  if (blinkTimer > 120 && !isBlinking) {
    isBlinking = true;
    blinkTimer = 0;
  }
  if (isBlinking && blinkTimer > 6) {
    isBlinking = false;
    blinkTimer = 0;
  }
  drawRobot(currentState, animFrame);
  requestAnimationFrame(animate);
}

function setState(state) {
  currentState = state;
  wrap.dataset.state = state;
  hint.textContent = t(stateHintKeys[state] || 'petHintDefault');
  if (state === 'success') {
    window.bobpet?.celebrate?.();
    setTimeout(() => window.bobpet?.getPetState().then(setState), 4000);
  }
}

function applyTheme(themeName) {
  currentTheme = THEMES[themeName] || THEMES.blue;
}

window.bobpet?.getPetState().then(setState);
window.bobpet?.onPetState(setState);

// Custom drag: mousedown starts tracking, mousemove moves window via IPC, mouseup ends.
// Short click (no significant move) = toggle chat. Right click = menu.
let dragging = false;
let dragStart = null;

wrap.addEventListener('mousedown', (e) => {
  if (e.button === 0) {
    dragging = false;
    dragStart = { x: e.screenX, y: e.screenY };
    e.preventDefault();
  }
});

document.addEventListener('mousemove', (e) => {
  if (!dragStart) return;
  const dx = e.screenX - dragStart.x;
  const dy = e.screenY - dragStart.y;
  if (!dragging && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
    dragging = true;
  }
  if (dragging) {
    window.bobpet?.movePet?.(dx, dy);
    dragStart = { x: e.screenX, y: e.screenY };
  }
});

document.addEventListener('mouseup', (e) => {
  if (e.button === 0 && dragStart) {
    if (!dragging) {
      window.bobpet?.toggleChat?.();
    }
    dragStart = null;
    dragging = false;
  }
});

wrap.addEventListener('contextmenu', (e) => {
  e.preventDefault();
  window.bobpet?.showPetMenu?.();
});

// Listen for edge-hide state from main
window.bobpet?.onPetEdgeState?.((hidden) => {
  wrap.classList.toggle('hidden-edge', hidden);
});

window.bobpet?.getConfig?.().then((cfg) => {
  applyTheme(cfg.theme || 'blue');
});
window.bobpet?.onThemeChange?.((theme) => applyTheme(theme));

window.bobpet?.getI18n?.().then((data) => {
  i18n = data.strings;
  hint.textContent = t('petHintDefault');
});
window.bobpet?.onLanguageChange?.((data) => {
  i18n = data.strings;
  hint.textContent = t(stateHintKeys[currentState] || 'petHintDefault');
});

animate();
