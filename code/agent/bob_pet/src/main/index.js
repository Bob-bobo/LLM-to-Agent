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
  streamChatWithProfile,
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

    // Handle API key encryption: encrypt new keys, resolve masked keys
    if (partial.model?.cloud?.apiKey && partial.model.cloud.apiKey !== '********') {
      partial.model.cloud.apiKey = encrypt(partial.model.cloud.apiKey);
    } else if (partial.model?.cloud && partial.model.cloud.apiKey === '********') {
      // Resolve the real encrypted key from modelProfiles by profileId
      const profileId = partial.model._profileId;
      if (profileId) {
        const profiles = store.get('modelProfiles') || [];
        const matched = profiles.find((p) => p.id === profileId);
        if (matched?.cloud?.apiKey) {
          partial.model.cloud.apiKey = matched.cloud.apiKey;
        }
      }
      // If still masked and not resolved, keep the existing encrypted key
      if (partial.model.cloud.apiKey === '********') {
        delete partial.model.cloud.apiKey;
      }
    }
    // Always clean up internal field so it never gets stored
    if (partial.model) delete partial.model._profileId;

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
    const deepThink = store.get('deepThink');

    try {
      for await (const chunk of streamChat(messages, { thinking: showThinking, deepThink })) {
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

  ipcMain.handle('multi-agent-stream', async (event, { messages, query, agentIds, summaryId, discussionMode, rounds }) => {
    const profiles = store.get('modelProfiles') || [];
    const selectedAgents = profiles.filter((p) => agentIds.includes(p.id));
    if (selectedAgents.length === 0) {
      return { ok: false, error: '未选择任何智能体' };
    }

    const persona = loadPersona(store.get('persona'));
    const showThinking = store.get('showThinking');
    const deepThink = store.get('deepThink');
    const allResponses = [];
    // Ensure rounds is at least 1
    const totalRounds = Math.max(1, rounds || 1);

    // Helper: run a single agent and stream results
    // roundInfo is appended to agent-start so the UI can show which round
    async function runAgent(agent, agentMessages, roundInfo) {
      const agentId = agent.id;
      const agentName = agent.name || agent.cloud?.model || agent.local?.model || 'Agent';
      const agentRole = agent.role || '';
      event.sender.send('chat-chunk', {
        type: 'agent-start', agentId, name: agentName, role: agentRole,
        round: roundInfo
      });

      let agentContent = '';
      try {
        for await (const chunk of streamChatWithProfile(agentMessages, agent, { thinking: showThinking, deepThink })) {
          if (chunk.type === 'content') {
            agentContent += chunk.text;
            event.sender.send('chat-chunk', { type: 'agent-content', agentId, text: chunk.text });
          } else if (chunk.type === 'thinking' && showThinking) {
            event.sender.send('chat-chunk', { type: 'agent-thinking', agentId, text: chunk.text });
          } else if (chunk.type === 'done') {
            event.sender.send('chat-chunk', { type: 'agent-done', agentId });
          }
        }
      } catch (err) {
        agentContent = `（${agentName} 响应失败：${err.message}）`;
        event.sender.send('chat-chunk', { type: 'agent-content', agentId, text: agentContent });
        event.sender.send('chat-chunk', { type: 'agent-done', agentId });
      }
      return { id: agentId, name: agentName, content: agentContent, round: roundInfo };
    }

    if (discussionMode) {
      // Discussion mode: multi-round sequential discussion
      // Each round: agents answer sequentially, each sees previous agents' answers in this round
      // Between rounds: all agents' answers from previous round are injected as context
      let discussionContext = [...messages];

      for (let round = 1; round <= totalRounds; round++) {
        const roundInfo = totalRounds > 1 ? `第${round}轮` : '';

        // At the start of round 2+, inject all previous round answers as context
        if (round > 1) {
          const prevRoundResponses = allResponses.filter((r) => r.round === `第${round - 1}轮`);
          if (prevRoundResponses.length > 0) {
            const prevSummary = prevRoundResponses
              .map((r) => `[讨论中 ${r.name} 的回答]\n${r.content}`)
              .join('\n\n');
            discussionContext = [
              ...discussionContext,
              { role: 'user', content: `—— 上一轮讨论结果 ——\n\n${prevSummary}` }
            ];
          }
        }

        // Each agent in this round sees the accumulated discussion context
        for (const agent of selectedAgents) {
          const result = await runAgent(agent, discussionContext, roundInfo);
          allResponses.push(result);
          // Inject this agent's answer as context for the next agent in the same round
          // Use 'user' role with clear labeling so the model treats it as
          // discussion input from another participant, not its own past output
          discussionContext = [
            ...discussionContext,
            { role: 'user', content: `[讨论中 ${result.name} 的回答]\n${result.content}` }
          ];
        }
      }
    } else {
      // Independent mode: parallel execution, all agents see the same messages
      // If rounds > 1, run multiple rounds in parallel (each round is independent)
      for (let round = 1; round <= totalRounds; round++) {
        const roundInfo = totalRounds > 1 ? `第${round}轮` : '';
        const results = await Promise.allSettled(
          selectedAgents.map((agent) => runAgent(agent, messages, roundInfo))
        );
        for (const r of results) {
          if (r.status === 'fulfilled' && r.value) {
            allResponses.push(r.value);
          }
        }
      }
    }

    // Summary by a designated model
    if (summaryId) {
      const summaryAgent = profiles.find((p) => p.id === summaryId);
      if (summaryAgent) {
        event.sender.send('chat-chunk', { type: 'agent-start', agentId: 'summary', name: '总结', role: '' });
        const summaryPrompt = allResponses.map((r) => {
          const roundLabel = r.round ? `（${r.round}）` : '';
          return `**${r.name}${roundLabel}**:\n${r.content}`;
        }).join('\n\n---\n\n');
        const customSummaryPrompt = store.get('multiAgent')?.summaryPrompt || '';
        const summaryInstruction = customSummaryPrompt
          || '以下是多个智能体对同一问题的回答，请综合各方观点给出一个更好的总结回答：';
        const summaryMessages = [
          ...messages,
          { role: 'user', content: `${summaryInstruction}\n\n${summaryPrompt}` }
        ];
        let summaryContent = '';
        try {
          for await (const chunk of streamChatWithProfile(summaryMessages, summaryAgent, { thinking: showThinking, deepThink })) {
            if (chunk.type === 'content') {
              summaryContent += chunk.text;
              event.sender.send('chat-chunk', { type: 'agent-content', agentId: 'summary', text: chunk.text });
            } else if (chunk.type === 'done') {
              event.sender.send('chat-chunk', { type: 'agent-done', agentId: 'summary' });
            }
          }
        } catch (err) {
          event.sender.send('chat-chunk', { type: 'agent-content', agentId: 'summary', text: `（总结失败：${err.message}）` });
          event.sender.send('chat-chunk', { type: 'agent-done', agentId: 'summary' });
        }
      }
    }

    return { ok: true, responses: allResponses };
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
