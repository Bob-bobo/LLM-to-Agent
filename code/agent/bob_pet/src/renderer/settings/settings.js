let modelType = 'cloud';
let selectedPersona = 'neko';
let selectedTheme = 'blue';
let i18n = {};
let currentLang = 'zh-CN';
let modelProfiles = [];
let editingProfileId = null;
let editingPersonaName = null;

const BUILTIN_PERSONAS = ['neko', 'chatty_friend', 'smiling_sister', 'funny_bro', 'cautious_mentor'];

const CLOUD_PRESETS = {
  openai:         { baseUrl: 'https://api.openai.com/v1',                        model: 'gpt-4o-mini',  keyHint: 'sk-...' },
  deepseek:       { baseUrl: 'https://api.deepseek.com/v1',                     model: 'deepseek-chat', keyHint: 'sk-...' },
  zhipu:          { baseUrl: 'https://open.bigmodel.cn/api/paas/v4',            model: 'glm-4-flash',  keyHint: 'xxx.yyy' },
  moonshot:       { baseUrl: 'https://api.moonshot.cn/v1',                      model: 'moonshot-v1-8k', keyHint: 'sk-...' },
  volcengine:     { baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',        model: 'ep-xxxxxxxx', keyHint: 'ARK API Key' },
  volcengine_plan:{ baseUrl: 'https://ark.cn-beijing.volces.com/api/plan/v3',   model: 'ep-xxxxxxxx', keyHint: 'ARK API Key' },
  aliyun:         { baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-turbo', keyHint: 'sk-...' },
  yi:             { baseUrl: 'https://api.lingyiwanwu.com/v1',                  model: 'yi-lightning', keyHint: 'sk-...' },
  minimax:        { baseUrl: 'https://api.minimax.chat/v1',                     model: 'MiniMax-Text-01', keyHint: 'sk-...' }
};

const THEMES = [
  { name: 'blue',   labelKey: 'themeBlue',   color: '#3b82f6' },
  { name: 'white',  labelKey: 'themeWhite',  color: '#6b7280' },
  { name: 'black',  labelKey: 'themeBlack',  color: '#262626' },
  { name: 'green',  labelKey: 'themeGreen',  color: '#22c55e' },
  { name: 'orange', labelKey: 'themeOrange', color: '#f97316' },
  { name: 'purple', labelKey: 'themePurple', color: '#a855f7' },
  { name: 'pink',   labelKey: 'themePink',   color: '#ec4899' }
];

function t(key) { return i18n[key] || key; }

function applyI18n() {
  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const key = el.dataset.i18n;
    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') return;
    const firstText = el.childNodes[0];
    if (firstText && firstText.nodeType === 3) {
      firstText.textContent = t(key);
    } else if (!el.querySelector('input, textarea')) {
      el.textContent = t(key);
    }
  });
  const navTexts = ['tabModel', 'tabPersona', 'tabTheme', 'tabLanguage', 'tabShortcuts', 'tabGeneral'];
  document.querySelectorAll('.nav-item').forEach((btn, i) => {
    if (navTexts[i]) btn.textContent = t(navTexts[i]);
  });
}

async function loadI18n() {
  const data = await window.bobpet.getI18n();
  i18n = data.strings;
  currentLang = data.lang;
  applyI18n();
}

async function loadConfig() {
  const cfg = await window.bobpet.getConfig();
  modelType = cfg.model?.type || 'cloud';
  selectedPersona = cfg.persona || 'neko';
  selectedTheme = cfg.theme || 'blue';
  modelProfiles = cfg.modelProfiles || [];

  document.getElementById('local-url').value = cfg.model?.local?.baseUrl || 'http://127.0.0.1:11434';
  document.getElementById('local-model').value = cfg.model?.local?.model || 'llama3.2';
  document.getElementById('cloud-url').value = cfg.model?.cloud?.baseUrl || '';
  document.getElementById('cloud-key').value = cfg.model?.cloud?.apiKey || '';
  document.getElementById('cloud-model').value = cfg.model?.cloud?.model || '';
  document.getElementById('auto-start').checked = !!cfg.autoStart;
  document.getElementById('show-thinking').checked = !!cfg.showThinking;
  document.getElementById('deep-think').checked = !!cfg.deepThink;

  updateModelPanels();
  renderPersonas();
  renderThemes();
  renderLanguages();
  renderProfiles();
  loadMultiAgentConfig();
}

function updateModelPanels() {
  document.querySelectorAll('[data-model-type]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.modelType === modelType);
  });
  document.getElementById('panel-local').classList.toggle('hidden', modelType !== 'local');
  document.getElementById('panel-cloud').classList.toggle('hidden', modelType !== 'cloud');
}

// --- Model Profiles ---
function renderProfiles() {
  const el = document.getElementById('profile-list');
  el.innerHTML = '';
  if (modelProfiles.length === 0) {
    el.innerHTML = `<p class="hint">${t('noProfiles') || '暂无保存的配置'}</p>`;
    return;
  }
  modelProfiles.forEach((profile) => {
    const card = document.createElement('div');
    card.className = 'profile-card';
    const typeLabel = profile.type === 'local' ? 'Ollama' : 'Cloud';
    const modelLabel = profile.type === 'local'
      ? (profile.local?.model || 'llama3.2')
      : (profile.cloud?.model || 'gpt-4o-mini');
    const roleLabel = profile.role ? ` · ${profile.role}` : '';
    card.innerHTML = `
      <div class="profile-info">
        <strong>${profile.name}</strong>
        <span>${typeLabel} / ${modelLabel}${roleLabel}</span>
      </div>
      <div class="profile-actions">
        <button type="button" class="btn-sm btn-use" title="${t('useProfile') || '使用'}">✓</button>
        <button type="button" class="btn-sm btn-edit" title="${t('editProfile') || '编辑'}">✎</button>
        <button type="button" class="btn-sm btn-del" title="${t('deleteProfile') || '删除'}">×</button>
      </div>
    `;
    card.querySelector('.btn-use').addEventListener('click', async () => {
      await window.bobpet.saveConfig({
        model: {
          type: profile.type,
          local: { ...profile.local },
          cloud: { ...profile.cloud },
          _profileId: profile.id
        }
      });
      clearTestResult();
      await loadConfig();
    });
    card.querySelector('.btn-edit').addEventListener('click', () => openProfileModal(profile));
    card.querySelector('.btn-del').addEventListener('click', async () => {
      if (confirm(`${t('deleteProfile') || '删除'}: ${profile.name}?`)) {
        modelProfiles = modelProfiles.filter((p) => p.id !== profile.id);
        await window.bobpet.saveConfig({ modelProfiles });
        renderProfiles();
      }
    });
    el.appendChild(card);
  });
}

let pfType = 'local';
function openProfileModal(profile) {
  const modal = document.getElementById('profile-modal-overlay');
  modal.hidden = false;
  editingProfileId = profile?.id || null;
  document.getElementById('profile-modal-title').textContent =
    profile ? (t('editProfile') || '编辑配置') : (t('addProfile') || '添加配置');
  document.getElementById('pf-name').value = profile?.name || '';
  document.getElementById('pf-role').value = profile?.role || '';
  pfType = profile?.type || 'local';
  document.getElementById('pf-local-url').value = profile?.local?.baseUrl || 'http://127.0.0.1:11434';
  document.getElementById('pf-local-model').value = profile?.local?.model || 'llama3.2';
  document.getElementById('pf-cloud-url').value = profile?.cloud?.baseUrl || '';
  document.getElementById('pf-cloud-key').value = profile?.cloud?.apiKey || '';
  document.getElementById('pf-cloud-model').value = profile?.cloud?.model || 'gpt-4o-mini';
  updatePfPanels();
}

function updatePfPanels() {
  document.querySelectorAll('[data-pf-type]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.pfType === pfType);
  });
  document.getElementById('pf-panel-local').classList.toggle('hidden', pfType !== 'local');
  document.getElementById('pf-panel-cloud').classList.toggle('hidden', pfType !== 'cloud');
}

document.querySelectorAll('[data-pf-type]').forEach((btn) => {
  btn.addEventListener('click', () => {
    pfType = btn.dataset.pfType;
    updatePfPanels();
  });
});

document.getElementById('btn-add-profile').addEventListener('click', () => openProfileModal(null));

document.getElementById('btn-pf-cancel').addEventListener('click', () => {
  document.getElementById('profile-modal-overlay').hidden = true;
});

document.getElementById('btn-pf-save').addEventListener('click', async () => {
  const name = document.getElementById('pf-name').value.trim();
  if (!name) return;
  const profile = {
    id: editingProfileId || Date.now().toString(),
    name,
    role: document.getElementById('pf-role').value.trim(),
    type: pfType,
    local: {
      baseUrl: document.getElementById('pf-local-url').value.trim(),
      model: document.getElementById('pf-local-model').value.trim()
    },
    cloud: {
      baseUrl: document.getElementById('pf-cloud-url').value.trim(),
      apiKey: document.getElementById('pf-cloud-key').value,
      model: document.getElementById('pf-cloud-model').value.trim()
    }
  };
  if (editingProfileId) {
    modelProfiles = modelProfiles.map((p) => p.id === editingProfileId ? profile : p);
  } else {
    modelProfiles.push(profile);
  }
  await window.bobpet.saveConfig({ modelProfiles });
  document.getElementById('profile-modal-overlay').hidden = true;
  renderProfiles();
});

// --- Personas ---
async function renderPersonas() {
  const list = await window.bobpet.listPersonas();
  const el = document.getElementById('persona-list');
  el.innerHTML = '';
  list.forEach((p) => {
    const isBuiltin = BUILTIN_PERSONAS.includes(p.name);
    const card = document.createElement('div');
    card.className = 'persona-card' + (p.name === selectedPersona ? ' selected' : '');
    let html = `<h3>${p.name}</h3><p>${p.description || ''}</p>`;
    if (!isBuiltin) {
      html += `<div class="persona-actions">`;
      html += `<button type="button" class="btn-persona-edit" title="${t('editPersona') || '编辑'}">✎</button>`;
      html += `<button type="button" class="btn-delete" title="${t('deletePersona') || '删除'}">&times;</button>`;
      html += `</div>`;
    }
    card.innerHTML = html;
    card.addEventListener('click', async (e) => {
      if (e.target.classList.contains('btn-delete')) {
        e.stopPropagation();
        if (confirm(`${t('deletePersona') || '删除'}: ${p.name}?`)) {
          await window.bobpet.deletePersona(p.name);
          if (selectedPersona === p.name) {
            selectedPersona = 'neko';
            await window.bobpet.saveConfig({ persona: 'neko' });
          }
          renderPersonas();
        }
        return;
      }
      if (e.target.classList.contains('btn-persona-edit')) {
        e.stopPropagation();
        await openPersonaEditModal(p.name);
        return;
      }
      selectedPersona = p.name;
      await window.bobpet.saveConfig({ persona: p.name });
      renderPersonas();
    });
    el.appendChild(card);
  });
}

// --- Themes ---
function renderThemes() {
  const el = document.getElementById('theme-list');
  el.innerHTML = '';
  THEMES.forEach((th) => {
    const card = document.createElement('div');
    card.className = 'theme-card' + (th.name === selectedTheme ? ' selected' : '');
    card.innerHTML = `<div class="theme-swatch" style="background:${th.color}"></div><h3>${t(th.labelKey)}</h3>`;
    card.addEventListener('click', async () => {
      selectedTheme = th.name;
      await window.bobpet.saveConfig({ theme: th.name });
      renderThemes();
    });
    el.appendChild(card);
  });
}

// --- Languages ---
async function renderLanguages() {
  const langs = await window.bobpet.getLanguages();
  const el = document.getElementById('language-list');
  el.innerHTML = '';
  langs.forEach((l) => {
    const card = document.createElement('div');
    card.className = 'lang-card' + (l.code === currentLang ? ' selected' : '');
    card.innerHTML = `<h3>${l.label}</h3>`;
    card.addEventListener('click', async () => {
      currentLang = l.code;
      await window.bobpet.saveConfig({ language: l.code });
      await loadI18n();
      renderThemes();
      renderLanguages();
      renderPersonas();
      renderProfiles();
    });
    el.appendChild(card);
  });
}

// --- Tabs ---
document.querySelectorAll('.nav-item').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.nav-item').forEach((b) => b.classList.remove('active'));
    document.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(`tab-${btn.dataset.tab}`).classList.add('active');
  });
});

// --- Model type toggle ---
function clearTestResult() {
  const el = document.getElementById('test-result');
  el.textContent = '';
  el.className = 'result';
}

document.querySelectorAll('[data-model-type]').forEach((btn) => {
  btn.addEventListener('click', () => {
    modelType = btn.dataset.modelType;
    updateModelPanels();
    clearTestResult();
  });
});

// Clear test result when any model config field changes
['local-url', 'local-model', 'cloud-url', 'cloud-key', 'cloud-model'].forEach((id) => {
  document.getElementById(id).addEventListener('input', clearTestResult);
});

// --- Cloud preset provider ---
document.getElementById('cloud-preset').addEventListener('change', (e) => {
  const preset = CLOUD_PRESETS[e.target.value];
  if (!preset) return;
  document.getElementById('cloud-url').value = preset.baseUrl;
  document.getElementById('cloud-model').value = preset.model;
  document.getElementById('cloud-key').placeholder = preset.keyHint;
  clearTestResult();
});

// --- Save model ---
document.getElementById('btn-save-model').addEventListener('click', async () => {
  await window.bobpet.saveConfig({
    model: {
      type: modelType,
      local: {
        baseUrl: document.getElementById('local-url').value.trim(),
        model: document.getElementById('local-model').value.trim()
      },
      cloud: {
        baseUrl: document.getElementById('cloud-url').value.trim(),
        apiKey: document.getElementById('cloud-key').value,
        model: document.getElementById('cloud-model').value.trim()
      }
    }
  });
  alert(t('save') + ' ✓');
});

// --- Test connection ---
document.getElementById('btn-test').addEventListener('click', async () => {
  await window.bobpet.saveConfig({
    model: {
      type: modelType,
      local: {
        baseUrl: document.getElementById('local-url').value.trim(),
        model: document.getElementById('local-model').value.trim()
      },
      cloud: {
        baseUrl: document.getElementById('cloud-url').value.trim(),
        apiKey: document.getElementById('cloud-key').value,
        model: document.getElementById('cloud-model').value.trim()
      }
    }
  });
  const res = await window.bobpet.testModel();
  const el = document.getElementById('test-result');
  el.textContent = res.message;
  el.className = 'result ' + (res.ok ? 'ok' : 'err');
});

// --- Save general ---
document.getElementById('btn-save-general').addEventListener('click', async () => {
  await window.bobpet.saveConfig({
    autoStart: document.getElementById('auto-start').checked,
    showThinking: document.getElementById('show-thinking').checked,
    deepThink: document.getElementById('deep-think').checked
  });
  alert(t('save') + ' ✓');
});

// --- Ollama link ---
document.getElementById('link-ollama').addEventListener('click', (e) => {
  e.preventDefault();
  window.bobpet.openExternal('https://ollama.com/download');
});

// --- Multi-Agent ---
function renderMultiAgent() {
  const agentList = document.getElementById('ma-agent-list');
  const summarySelect = document.getElementById('ma-summary');
  const ma = (typeof window !== 'undefined' && window.__maConfig) || {};

  agentList.innerHTML = '';
  summarySelect.innerHTML = '<option value="">-- 无总结 --</option>';

  if (modelProfiles.length === 0) {
    agentList.innerHTML = '<p class="hint">请先在模型配置中添加配置</p>';
    return;
  }

  const selectedIds = ma.agentIds || [];
  modelProfiles.forEach((profile) => {
    const card = document.createElement('div');
    card.className = 'ma-agent-card' + (selectedIds.includes(profile.id) ? ' selected' : '');
    const typeLabel = profile.type === 'local' ? 'Ollama' : 'Cloud';
    const modelLabel = profile.type === 'local'
      ? (profile.local?.model || 'llama3.2')
      : (profile.cloud?.model || 'gpt-4o-mini');
    card.innerHTML = `
      <input type="checkbox" class="ma-agent-check" data-id="${profile.id}" ${selectedIds.includes(profile.id) ? 'checked' : ''} />
      <div class="ma-agent-info">
        <strong>${profile.name}</strong>
        <span>${typeLabel} / ${modelLabel}</span>
      </div>
    `;
    card.querySelector('.ma-agent-check').addEventListener('change', () => {
      card.classList.toggle('selected', card.querySelector('.ma-agent-check').checked);
    });
    agentList.appendChild(card);

    // Add to summary dropdown
    const opt = document.createElement('option');
    opt.value = profile.id;
    opt.textContent = profile.name;
    summarySelect.appendChild(opt);
  });

  if (ma.summaryId) {
    summarySelect.value = ma.summaryId;
  }
}

async function loadMultiAgentConfig() {
  const cfg = await window.bobpet.getConfig();
  const ma = cfg.multiAgent || {};
  window.__maConfig = ma;
  document.getElementById('ma-enabled').checked = !!ma.enabled;
  document.getElementById('ma-mode').value = ma.mode || 'independent';
  document.getElementById('ma-rounds').value = ma.rounds || 1;
  document.getElementById('ma-summary-prompt').value = ma.summaryPrompt || '';
  renderMultiAgent();
}

document.getElementById('btn-save-ma').addEventListener('click', async () => {
  const agentIds = [];
  document.querySelectorAll('.ma-agent-check:checked').forEach((cb) => {
    agentIds.push(cb.dataset.id);
  });
  const summaryId = document.getElementById('ma-summary').value || null;
  await window.bobpet.saveConfig({
    multiAgent: {
      enabled: document.getElementById('ma-enabled').checked,
      agentIds,
      summaryId,
      mode: document.getElementById('ma-mode').value,
      rounds: parseInt(document.getElementById('ma-rounds').value, 10) || 1,
      summaryPrompt: document.getElementById('ma-summary-prompt').value.trim()
    }
  });
  alert(t('save') + ' ✓');
});

loadMultiAgentConfig();

// --- Persona modal ---
const personaModal = document.getElementById('modal-overlay');
document.getElementById('btn-add-persona').addEventListener('click', () => {
  editingPersonaName = null;
  personaModal.hidden = false;
  document.getElementById('modal-title').textContent = t('addPersona') || '添加人格';
  document.getElementById('m-name').value = '';
  document.getElementById('m-name').disabled = false;
  document.getElementById('m-desc').value = '';
  document.getElementById('m-greeting').value = '';
  document.getElementById('m-prompt').value = '';
  document.getElementById('m-keywords').value = '';
});

document.getElementById('btn-modal-cancel').addEventListener('click', () => {
  personaModal.hidden = true;
  editingPersonaName = null;
  document.getElementById('m-name').disabled = false;
});

document.getElementById('btn-modal-save').addEventListener('click', async () => {
  const name = document.getElementById('m-name').value.trim();
  if (!name) return;
  const desc = document.getElementById('m-desc').value.trim();
  const greeting = document.getElementById('m-greeting').value.trim();
  const prompt = document.getElementById('m-prompt').value.trim();
  const keywordsRaw = document.getElementById('m-keywords').value.trim();

  const keywords = {};
  if (keywordsRaw) {
    keywordsRaw.split('\n').forEach((line) => {
      const eq = line.indexOf('=');
      if (eq > 0) {
        const kw = line.slice(0, eq).trim();
        const replies = line.slice(eq + 1).split('|').map((s) => s.trim()).filter(Boolean);
        if (kw && replies.length) keywords[kw] = replies;
      }
    });
  }

  await window.bobpet.savePersona({ name, description: desc, greeting, systemPrompt: prompt, keywords });
  if (!editingPersonaName) {
    selectedPersona = name;
    await window.bobpet.saveConfig({ persona: name });
  }
  personaModal.hidden = true;
  editingPersonaName = null;
  renderPersonas();
});

async function openPersonaEditModal(name) {
  const persona = await window.bobpet.getPersona(name);
  if (!persona) return;
  editingPersonaName = name;
  personaModal.hidden = false;
  document.getElementById('modal-title').textContent = t('editPersona') || '编辑人格';
  document.getElementById('m-name').value = persona.name || name;
  document.getElementById('m-name').disabled = true;
  document.getElementById('m-desc').value = persona.description || '';
  document.getElementById('m-greeting').value = persona.dialogue_style?.greeting || '';
  document.getElementById('m-prompt').value = persona.dialogue_style?.system_prompt || '';
  const kw = persona.dialogue_style?.keywords || {};
  document.getElementById('m-keywords').value = Object.entries(kw)
    .map(([k, v]) => `${k}=${Array.isArray(v) ? v.join('|') : v}`)
    .join('\n');
}

// --- Language change listener ---
window.bobpet.onLanguageChange?.(async (data) => {
  i18n = data.strings;
  currentLang = data.lang;
  applyI18n();
  renderThemes();
  renderLanguages();
  renderProfiles();
});

loadI18n();
loadConfig();
