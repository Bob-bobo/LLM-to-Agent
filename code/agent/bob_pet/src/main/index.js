const {
  app,
  BrowserWindow,
  ipcMain,
  globalShortcut,
  Tray,
  Menu,
  nativeImage,
  screen,
  shell
} = require('electron');
const path = require('path');
const fs = require('fs');
const { store, encrypt, decrypt } = require('./store');
const { t, getAvailableLanguages, translations } = require('./i18n');
const { startMonitor, onStateChange, triggerSuccess } = require('./monitor');
const {
  streamChat,
  testConnection,
  listPersonas,
  loadPersona,
  applyPersonaTemplate,
  getPersonasDir
} = require('./modelGateway');

let petWindow = null;
let chatWindow = null;
let settingsWindow = null;
let wizardWindow = null;
let tray = null;
let stopMonitor = null;
let petHidden = false;
let petSavedPos = null;

const isDev = process.argv.includes('--dev');

function getAssetPath(...segments) {
  return path.join(__dirname, '../../assets', ...segments);
}

function createPetWindow() {
  const { width, height } = screen.getPrimaryDisplay().workAreaSize;
  const saved = store.get('pet');
  const x = saved.x ?? width - 160;
  const y = saved.y ?? height - 180;

  petWindow = new BrowserWindow({
    width: 140,
    height: 160,
    x,
    y,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    resizable: false,
    hasShadow: false,
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  petWindow.setIgnoreMouseEvents(false);
  petWindow.loadFile(path.join(__dirname, '../renderer/pet/index.html'));

  petWindow.on('moved', () => {
    const [px, py] = petWindow.getPosition();
    store.set('pet.x', px);
    store.set('pet.y', py);
  });

  petWindow.on('closed', () => {
    petWindow = null;
  });

  if (!saved.visible) petWindow.hide();
}

function createChatWindow() {
  if (chatWindow) return chatWindow;

  const petBounds = petWindow?.getBounds() || { x: 100, y: 100 };
  chatWindow = new BrowserWindow({
    width: 360,
    height: 480,
    x: petBounds.x - 380,
    y: Math.max(0, petBounds.y - 200),
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    show: false,
    resizable: true,
    minWidth: 280,
    minHeight: 320,
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  chatWindow.loadFile(path.join(__dirname, '../renderer/chat/index.html'));
  chatWindow.on('closed', () => {
    chatWindow = null;
  });
  return chatWindow;
}

function toggleChat() {
  const win = createChatWindow();
  if (win.isVisible()) {
    win.hide();
  } else {
    if (petWindow) {
      const b = petWindow.getBounds();
      win.setPosition(Math.max(0, b.x - 380), Math.max(0, b.y - 200));
    }
    win.show();
    win.focus();
  }
}

function createSettingsWindow() {
  if (settingsWindow) {
    settingsWindow.focus();
    return;
  }
  settingsWindow = new BrowserWindow({
    width: 720,
    height: 560,
    title: 'BobPet 设置',
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  settingsWindow.loadFile(path.join(__dirname, '../renderer/settings/index.html'));
  settingsWindow.on('closed', () => {
    settingsWindow = null;
  });
}

function createWizardWindow() {
  wizardWindow = new BrowserWindow({
    width: 520,
    height: 480,
    resizable: false,
    title: '欢迎使用 BobPet',
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  wizardWindow.loadFile(path.join(__dirname, '../renderer/wizard/index.html'));
  wizardWindow.on('closed', () => {
    wizardWindow = null;
  });
}

function registerShortcuts() {
  globalShortcut.unregisterAll();
  const sc = store.get('shortcuts');
  try {
    globalShortcut.register(sc.toggleChat, toggleChat);
    globalShortcut.register(sc.toggleThinking, () => {
      const v = !store.get('showThinking');
      store.set('showThinking', v);
      chatWindow?.webContents.send('thinking-mode', v);
      petWindow?.webContents.send('thinking-mode', v);
    });
    globalShortcut.register(sc.togglePet, () => {
      if (!petWindow) return;
      if (petHidden) {
        showPetFromEdge();
      } else {
        hidePetToEdge();
      }
    });
  } catch (e) {
    console.error('Shortcut registration failed:', e);
  }
}

function hidePetToEdge() {
  if (!petWindow || petHidden) return;
  const { width } = screen.getPrimaryDisplay().workAreaSize;
  const [px, py] = petWindow.getPosition();
  petSavedPos = { x: px, y: py };
  petWindow.setPosition(width - 40, py);
  petHidden = true;
  store.set('pet.visible', false);
  petWindow.webContents.send('pet-edge-state', true);
}

function showPetFromEdge() {
  if (!petWindow || !petHidden) return;
  const pos = petSavedPos || {};
  const { width, height } = screen.getPrimaryDisplay().workAreaSize;
  const x = pos.x ?? width - 160;
  const y = pos.y ?? height - 180;
  petWindow.setPosition(x, y);
  petHidden = false;
  store.set('pet.visible', true);
  petWindow.webContents.send('pet-edge-state', false);
}

function createTray() {
  const iconPath = getAssetPath('tray.png');
  let icon;
  if (fs.existsSync(iconPath)) {
    icon = nativeImage.createFromPath(iconPath);
  } else {
    icon = nativeImage.createEmpty();
  }

  tray = new Tray(icon.isEmpty() ? nativeImage.createFromDataURL(
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
  ) : icon);

  const contextMenu = Menu.buildFromTemplate([
    {
      label: '显示/隐藏宠物',
      click: () => {
        if (petHidden) showPetFromEdge();
        else hidePetToEdge();
      }
    },
    { label: '打开聊天', click: toggleChat },
    { type: 'separator' },
    { label: '设置', click: createSettingsWindow },
    { type: 'separator' },
    {
      label: '退出',
      click: () => {
        app.quit();
      }
    }
  ]);
  tray.setToolTip('BobPet');
  tray.setContextMenu(contextMenu);
  tray.on('double-click', toggleChat);
}

function setupIpc() {
  ipcMain.handle('get-config', () => {
    const cfg = store.store;
    const maskProfile = (p) => ({
      ...p,
      cloud: {
        ...(p.cloud || {}),
        apiKey: p.cloud?.apiKey ? '********' : ''
      }
    });
    return {
      ...cfg,
      model: {
        ...cfg.model,
        cloud: {
          ...cfg.model.cloud,
          apiKey: cfg.model.cloud?.apiKey ? '********' : ''
        }
      },
      modelProfiles: (cfg.modelProfiles || []).map(maskProfile)
    };
  });

  ipcMain.handle('save-config', (_, partial) => {
    // Deep merge helper
    function deepMerge(target, source) {
      const result = { ...target };
      for (const key of Object.keys(source)) {
        if (
          source[key] !== null &&
          typeof source[key] === 'object' &&
          !Array.isArray(source[key]) &&
          target[key] !== null &&
          typeof target[key] === 'object' &&
          !Array.isArray(target[key])
        ) {
          result[key] = deepMerge(target[key], source[key]);
        } else {
          result[key] = source[key];
        }
      }
      return result;
    }

    // Handle API key encryption: encrypt new keys, preserve existing when masked
    if (partial.model?.cloud?.apiKey && partial.model.cloud.apiKey !== '********') {
      partial.model.cloud.apiKey = encrypt(partial.model.cloud.apiKey);
    } else if (partial.model?.cloud && partial.model.cloud.apiKey === '********') {
      // Keep the existing encrypted key
      delete partial.model.cloud.apiKey;
    }

    // Handle model profiles encryption
    if (partial.modelProfiles) {
      partial.modelProfiles.forEach((profile) => {
        if (profile.cloud?.apiKey && profile.cloud.apiKey !== '********') {
          profile.cloud.apiKey = encrypt(profile.cloud.apiKey);
        } else if (profile.cloud?.apiKey === '********') {
          const existing = (store.get('modelProfiles') || []).find((p) => p.id === profile.id);
          if (existing?.cloud?.apiKey) {
            profile.cloud.apiKey = existing.cloud.apiKey;
          } else {
            delete profile.cloud.apiKey;
          }
        }
      });
    }

    Object.entries(partial).forEach(([k, v]) => {
      if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
        const existing = store.get(k) || {};
        store.set(k, deepMerge(existing, v));
      } else {
        store.set(k, v);
      }
    });
    registerShortcuts();
    return true;
  });

  ipcMain.handle('finish-wizard', (_, config) => {
    store.set('firstRun', false);
    if (config) {
      if (config.model?.cloud?.apiKey) {
        config.model.cloud.apiKey = encrypt(config.model.cloud.apiKey);
      }
      store.set('model', config.model);
      if (config.persona) store.set('persona', config.persona);
    }
    wizardWindow?.close();
    wizardWindow = null;
    if (!petWindow) createPetWindow();
    registerShortcuts();
  });

  ipcMain.handle('detect-env', async () => {
    const os = require('os');
    const totalMemGB = Math.round(os.totalmem() / 1024 / 1024 / 1024);
    let ollamaOk = false;
    try {
      const axios = require('axios');
      await axios.get('http://127.0.0.1:11434/api/tags', { timeout: 2000 });
      ollamaOk = true;
    } catch {
      /* not running */
    }
    return {
      totalMemGB,
      platform: process.platform,
      ollamaOk,
      recommendLocal: totalMemGB >= 8
    };
  });

  ipcMain.handle('test-model', () => testConnection());
  ipcMain.handle('list-personas', () => listPersonas());
  ipcMain.handle('get-persona', (_, name) => loadPersona(name || store.get('persona')));
  ipcMain.handle('get-pet-state', () => require('./monitor').getState());
  ipcMain.handle('get-i18n', () => {
    const lang = store.get('language') || 'zh-CN';
    return { lang, strings: translations[lang] || translations['zh-CN'] };
  });
  ipcMain.handle('get-languages', () => getAvailableLanguages());

  ipcMain.handle('save-persona', (_, personaData) => {
    const yaml = require('js-yaml');
    const personaDir = path.join(app.getPath('appData'), 'BobPet', 'personas');
    if (!fs.existsSync(personaDir)) fs.mkdirSync(personaDir, { recursive: true });
    const obj = {
      name: personaData.name,
      description: personaData.description || '',
      meta: { avatar: personaData.name },
      behaviors: {
        on_idle: ['wave'],
        on_working: ['nod'],
        on_thinking: ['think'],
        on_success: ['cheer'],
        on_waiting: ['wait'],
        on_rest: ['rest']
      },
      dialogue_style: {
        greeting: personaData.greeting || '你好',
        ending: '下次见',
        system_prompt: personaData.systemPrompt || `你是桌面助手 ${personaData.name}。`,
        response_template: '{{response}}',
        keywords: personaData.keywords || {}
      }
    };
    const filePath = path.join(personaDir, `${personaData.name}.yaml`);
    fs.writeFileSync(filePath, yaml.dump(obj, { lineWidth: -1 }), 'utf8');
    return true;
  });

  ipcMain.handle('delete-persona', (_, name) => {
    const personaDir = path.join(app.getPath('appData'), 'BobPet', 'personas');
    for (const ext of ['yaml', 'json']) {
      const filePath = path.join(personaDir, `${name}.${ext}`);
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
        return true;
      }
    }
    return false;
  });

  ipcMain.handle('chat-stream', async (event, { messages, query }) => {
    const persona = loadPersona(store.get('persona'));
    let fullContent = '';
    let fullThinking = '';
    const showThinking = store.get('showThinking');

    try {
      for await (const chunk of streamChat(messages, { thinking: showThinking })) {
        if (chunk.type === 'content') {
          fullContent += chunk.text;
          event.sender.send('chat-chunk', { type: 'content', text: chunk.text });
        } else if (chunk.type === 'thinking' && showThinking) {
          fullThinking += chunk.text;
          event.sender.send('chat-chunk', { type: 'thinking', text: chunk.text });
        } else if (chunk.type === 'done') {
          event.sender.send('chat-chunk', { type: 'done' });
        }
      }
      const final = applyPersonaTemplate(persona, query, fullContent) || fullContent;
      if (final !== fullContent) {
        event.sender.send('chat-chunk', { type: 'replace', text: final });
      }
      return { ok: true, content: final, thinking: fullThinking };
    } catch (err) {
      const fallback = getOfflineReply(persona, query, err.message);
      event.sender.send('chat-chunk', { type: 'replace', text: fallback });
      return { ok: false, error: err.message, content: fallback };
    }
  });

  ipcMain.on('pet-state-request', (e) => {
    e.returnValue = require('./monitor').getState();
  });

  ipcMain.on('open-external', (_, url) => shell.openExternal(url));
  ipcMain.on('hide-chat', () => chatWindow?.hide());
  ipcMain.on('toggle-chat', () => toggleChat());
  ipcMain.on('open-settings', () => createSettingsWindow());
  ipcMain.on('celebrate', () => triggerSuccess());
  ipcMain.on('show-pet-menu', () => {
    const menu = Menu.buildFromTemplate([
      { label: '打开聊天', click: toggleChat },
      { label: '设置', click: createSettingsWindow },
      { type: 'separator' },
      {
        label: petHidden ? '显示宠物' : '隐藏宠物',
        click: () => {
          if (petHidden) showPetFromEdge();
          else hidePetToEdge();
        }
      },
      { type: 'separator' },
      { label: '退出', click: () => app.quit() }
    ]);
    menu.popup();
  });

  ipcMain.on('move-pet', (_, dx, dy) => {
    if (!petWindow) return;
    const [x, y] = petWindow.getPosition();
    petWindow.setPosition(x + dx, y + dy);
  });
}

function getOfflineReply(persona, query, errMsg) {
  const keywords = persona?.dialogue_style?.keywords || {};
  for (const [kw, replies] of Object.entries(keywords)) {
    if (query?.toLowerCase().includes(kw.toLowerCase())) {
      const pick = replies[Math.floor(Math.random() * replies.length)];
      return pick;
    }
  }
  const greeting = persona?.dialogue_style?.greeting || '你好喵～';
  return `${greeting}\n\n（模型暂未连接：${errMsg}）\n\n请先在设置中配置 Ollama 或云端 API，或安装 Ollama 后运行 \`ollama pull llama3.2\` 喵～`;
}

function ensureAppData() {
  const base = path.join(app.getPath('appData'), 'BobPet');
  const logs = path.join(base, 'logs');
  const personas = path.join(base, 'personas');
  [base, logs, personas].forEach((d) => {
    if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  });
  const srcDir = path.join(__dirname, '../../personas');
  if (fs.existsSync(srcDir)) {
    fs.readdirSync(srcDir).forEach((f) => {
      if (f.endsWith('.yaml') || f.endsWith('.json')) {
        const src = path.join(srcDir, f);
        const dest = path.join(personas, f);
        if (!fs.existsSync(dest)) fs.copyFileSync(src, dest);
      }
    });
  }
}

function setupAutoStart() {
  if (store.get('autoStart')) {
    app.setLoginItemSettings({ openAtLogin: true, name: 'BobPet' });
  }
}

app.whenReady().then(() => {
  ensureAppData();
  setupIpc();
  createTray();
  setupAutoStart();

  if (store.get('firstRun')) {
    createWizardWindow();
  } else {
    createPetWindow();
    registerShortcuts();
  }

  stopMonitor = startMonitor(2000);
  onStateChange((state) => {
    petWindow?.webContents.send('pet-state', state);
    chatWindow?.webContents.send('pet-state', state);
  });

  store.onDidChange('theme', (newTheme) => {
    petWindow?.webContents.send('theme-change', newTheme || 'blue');
    chatWindow?.webContents.send('theme-change', newTheme || 'blue');
  });

  store.onDidChange('language', (newLang) => {
    const lang = newLang || 'zh-CN';
    const strings = translations[lang] || translations['zh-CN'];
    petWindow?.webContents.send('language-change', { lang, strings });
    chatWindow?.webContents.send('language-change', { lang, strings });
    settingsWindow?.webContents.send('language-change', { lang, strings });
  });

  app.on('activate', () => {
    if (!petWindow && !store.get('firstRun')) createPetWindow();
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
  stopMonitor?.();
});

app.on('window-all-closed', (e) => {
  e.preventDefault();
});
