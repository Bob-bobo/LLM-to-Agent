const { execSync } = require('child_process');
const { powerMonitor } = require('electron');

const IDE_PATTERNS = [
  /cursor/i,
  /code\.exe/i,
  /codex/i,
  /claude/i,
  /devenv/i,
  /idea64/i,
  /webstorm/i,
  /pycharm/i
];

let state = 'rest';
let stateListeners = [];

function onStateChange(fn) {
  stateListeners.push(fn);
  return () => {
    stateListeners = stateListeners.filter((f) => f !== fn);
  };
}

function emitState(newState) {
  if (newState !== state) {
    state = newState;
    stateListeners.forEach((fn) => fn(state));
  }
}

function isIdeRunning() {
  try {
    const out = execSync('tasklist /NH', {
      encoding: 'utf8',
      windowsHide: true,
      timeout: 3000
    });
    return IDE_PATTERNS.some((re) => re.test(out));
  } catch {
    return false;
  }
}

function evaluateState() {
  const idleSeconds = powerMonitor.getSystemIdleTime();
  const ideRunning = isIdeRunning();

  if (!ideRunning) {
    emitState('rest');
    return state;
  }

  if (idleSeconds >= 300) {
    emitState('waiting');
    return state;
  }

  if (idleSeconds < 30) {
    emitState('working');
  } else {
    emitState('thinking');
  }
  return state;
}

function triggerSuccess() {
  emitState('success');
  setTimeout(() => evaluateState(), 4000);
}

function startMonitor(intervalMs = 2000) {
  const timer = setInterval(() => evaluateState(), intervalMs);
  evaluateState();
  return () => clearInterval(timer);
}

module.exports = {
  startMonitor,
  onStateChange,
  getState: () => state,
  triggerSuccess
};
