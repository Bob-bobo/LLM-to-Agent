const messagesEl = document.getElementById('messages');
const inputEl = document.getElementById('input');
const btnSend = document.getElementById('btn-send');
const thinkingPanel = document.getElementById('thinking-panel');
const btnThinking = document.getElementById('btn-thinking');
const btnClose = document.getElementById('btn-close');
const btnSettings = document.getElementById('btn-settings');

let history = [];
let streaming = false;
let showThinking = false;
let currentAssistantEl = null;
let thinkingText = '';
let i18n = {};

function t(key) { return i18n[key] || key; }

function appendMessage(role, text) {
  const div = document.createElement('div');
  div.className = `msg ${role}`;
  div.textContent = text;
  messagesEl.appendChild(div);
  messagesEl.scrollTop = messagesEl.scrollHeight;
  return div;
}

const THEME_CSS = {
  blue:   { primary:'#3b82f6', light:'#93c5fd', bg:'#eff6ff', surface:'rgba(255,255,255,0.96)', text:'#1e293b', muted:'#64748b', border:'#bfdbfe', msgBotBg:'#eff6ff', msgBotBorder:'#bfdbfe' },
  white:  { primary:'#6b7280', light:'#d1d5db', bg:'#f9fafb', surface:'rgba(255,255,255,0.96)', text:'#1f2937', muted:'#6b7280', border:'#d1d5db', msgBotBg:'#f9fafb', msgBotBorder:'#d1d5db' },
  black:  { primary:'#a3a3a3', light:'#525252', bg:'#171717', surface:'rgba(30,30,30,0.96)', text:'#e5e5e5', muted:'#a3a3a3', border:'#404040', msgBotBg:'#262626', msgBotBorder:'#404040' },
  green:  { primary:'#22c55e', light:'#86efac', bg:'#f0fdf4', surface:'rgba(255,255,255,0.96)', text:'#14532d', muted:'#166534', border:'#bbf7d0', msgBotBg:'#f0fdf4', msgBotBorder:'#bbf7d0' },
  orange: { primary:'#f97316', light:'#fdba74', bg:'#fff7ed', surface:'rgba(255,255,255,0.96)', text:'#7c2d12', muted:'#9a3412', border:'#fed7aa', msgBotBg:'#fff7ed', msgBotBorder:'#fed7aa' },
  purple: { primary:'#a855f7', light:'#d8b4fe', bg:'#faf5ff', surface:'rgba(255,255,255,0.96)', text:'#581c87', muted:'#6b21a8', border:'#e9d5ff', msgBotBg:'#faf5ff', msgBotBorder:'#e9d5ff' },
  pink:   { primary:'#ec4899', light:'#f9a8d4', bg:'#fdf2f8', surface:'rgba(255,255,255,0.96)', text:'#831843', muted:'#9d174d', border:'#fbcfe8', msgBotBg:'#fdf2f8', msgBotBorder:'#fbcfe8' }
};

function applyTheme(name) {
  const t = THEME_CSS[name] || THEME_CSS.blue;
  const r = document.documentElement.style;
  r.setProperty('--c-primary', t.primary);
  r.setProperty('--c-primary-light', t.light);
  r.setProperty('--c-bg', t.bg);
  r.setProperty('--c-surface', t.surface);
  r.setProperty('--c-text', t.text);
  r.setProperty('--c-text-muted', t.muted);
  r.setProperty('--c-border', t.border);
  r.setProperty('--c-msg-user', t.primary);
  r.setProperty('--c-msg-bot-bg', t.msgBotBg);
  r.setProperty('--c-msg-bot-border', t.msgBotBorder);
  r.setProperty('--c-msg-bot-text', t.text);
}

async function init() {
  const cfg = await window.bobpet.getConfig();
  showThinking = cfg.showThinking;
  thinkingPanel.hidden = !showThinking;
  btnThinking.classList.toggle('active', showThinking);
  applyTheme(cfg.theme || 'blue');

  const i18nData = await window.bobpet.getI18n();
  i18n = i18nData.strings;
  document.querySelector('.title').textContent = t('chatTitle');
  inputEl.placeholder = t('chatPlaceholder');
  btnSend.textContent = t('chatSend');

  const persona = await window.bobpet.getPersona(cfg.persona);
  const greeting = persona?.dialogue_style?.greeting || t('petIdle');
  appendMessage('assistant', greeting);
}

async function sendMessage() {
  const text = inputEl.value.trim();
  if (!text || streaming) return;

  inputEl.value = '';
  appendMessage('user', text);
  history.push({ role: 'user', content: text });

  streaming = true;
  btnSend.disabled = true;
  thinkingText = '';
  thinkingPanel.textContent = '';

  currentAssistantEl = appendMessage('assistant', '');
  let full = '';

  const unsub = window.bobpet.onChatChunk((chunk) => {
    if (chunk.type === 'content') {
      full += chunk.text;
      currentAssistantEl.textContent = full;
      messagesEl.scrollTop = messagesEl.scrollHeight;
    } else if (chunk.type === 'thinking') {
      thinkingText += chunk.text;
      thinkingPanel.textContent = thinkingText;
      thinkingPanel.hidden = !showThinking;
    } else if (chunk.type === 'replace') {
      full = chunk.text;
      currentAssistantEl.textContent = full;
    }
  });

  try {
    const result = await window.bobpet.chatStream({
      messages: history.slice(-20),
      query: text
    });
    const content = result?.content || full;
    currentAssistantEl.textContent = content;
    history.push({ role: 'assistant', content });
  } finally {
    unsub();
    streaming = false;
    btnSend.disabled = false;
    inputEl.focus();
  }
}

btnSend.addEventListener('click', sendMessage);
inputEl.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
  if (e.key === 'Escape') {
    window.bobpet.hideChat();
  }
});

btnClose.addEventListener('click', () => window.bobpet.hideChat());
btnSettings.addEventListener('click', () => window.bobpet.openSettings());
btnThinking.addEventListener('click', async () => {
  showThinking = !showThinking;
  thinkingPanel.hidden = !showThinking;
  btnThinking.classList.toggle('active', showThinking);
  await window.bobpet.saveConfig({ showThinking });
});

window.bobpet.onThinkingMode((v) => {
  showThinking = v;
  thinkingPanel.hidden = !showThinking;
  btnThinking.classList.toggle('active', showThinking);
});

window.bobpet.onThemeChange?.((theme) => applyTheme(theme));

window.bobpet.onLanguageChange?.((data) => {
  i18n = data.strings;
  document.querySelector('.title').textContent = t('chatTitle');
  inputEl.placeholder = t('chatPlaceholder');
  btnSend.textContent = t('chatSend');
});

init();
inputEl.focus();
