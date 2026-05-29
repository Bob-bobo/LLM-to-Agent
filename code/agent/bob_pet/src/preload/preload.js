const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('bobpet', {
  getConfig: () => ipcRenderer.invoke('get-config'),
  saveConfig: (cfg) => ipcRenderer.invoke('save-config', cfg),
  detectEnv: () => ipcRenderer.invoke('detect-env'),
  finishWizard: (cfg) => ipcRenderer.invoke('finish-wizard', cfg),
  testModel: () => ipcRenderer.invoke('test-model'),
  listPersonas: () => ipcRenderer.invoke('list-personas'),
  getPersona: (name) => ipcRenderer.invoke('get-persona', name),
  getPetState: () => ipcRenderer.invoke('get-pet-state'),
  getI18n: () => ipcRenderer.invoke('get-i18n'),
  getLanguages: () => ipcRenderer.invoke('get-languages'),
  savePersona: (data) => ipcRenderer.invoke('save-persona', data),
  deletePersona: (name) => ipcRenderer.invoke('delete-persona', name),
  chatStream: (payload) => ipcRenderer.invoke('chat-stream', payload),
  onChatChunk: (cb) => {
    const handler = (_, data) => cb(data);
    ipcRenderer.on('chat-chunk', handler);
    return () => ipcRenderer.removeListener('chat-chunk', handler);
  },
  onPetState: (cb) => {
    const handler = (_, state) => cb(state);
    ipcRenderer.on('pet-state', handler);
    return () => ipcRenderer.removeListener('pet-state', handler);
  },
  onThinkingMode: (cb) => {
    const handler = (_, v) => cb(v);
    ipcRenderer.on('thinking-mode', handler);
    return () => ipcRenderer.removeListener('thinking-mode', handler);
  },
  onThemeChange: (cb) => {
    const handler = (_, theme) => cb(theme);
    ipcRenderer.on('theme-change', handler);
    return () => ipcRenderer.removeListener('theme-change', handler);
  },
  onLanguageChange: (cb) => {
    const handler = (_, data) => cb(data);
    ipcRenderer.on('language-change', handler);
    return () => ipcRenderer.removeListener('language-change', handler);
  },
  hideChat: () => ipcRenderer.send('hide-chat'),
  toggleChat: () => ipcRenderer.send('toggle-chat'),
  openSettings: () => ipcRenderer.send('open-settings'),
  openExternal: (url) => ipcRenderer.send('open-external', url),
  celebrate: () => ipcRenderer.send('celebrate'),
  showPetMenu: () => ipcRenderer.send('show-pet-menu'),
  movePet: (dx, dy) => ipcRenderer.send('move-pet', dx, dy),
  onPetEdgeState: (cb) => {
    const handler = (_, hidden) => cb(hidden);
    ipcRenderer.on('pet-edge-state', handler);
    return () => ipcRenderer.removeListener('pet-edge-state', handler);
  }
});
