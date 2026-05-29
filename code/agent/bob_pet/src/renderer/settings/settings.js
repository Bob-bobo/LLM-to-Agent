let modelType = 'cloud';
let selectedPersona = 'neko';
let selectedTheme = 'blue';
let i18n = {};
let currentLang = 'zh-CN';
let modelProfiles = [];
let editingProfileId = null;

const BUILTIN_PERSONAS = ['neko', 'chatty_friend', 'smiling_sister', 'funny_bro', 'cautious_mentor'];

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

  updateModelPanels();
  renderPersonas();
  renderThemes();
  renderLanguages();
  renderProfiles();
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
    card.innerHTML = `
      <div class="profile-info">
        <strong>${profile.name}</strong>
        <span>${typeLabel} / ${modelLabel}</span>
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
          cloud: { ...profile.cloud }
        }
      });
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
      html += `<button type="button" class="btn-delete" title="${t('deletePersona')}">&times;</button>`;
    }
    card.innerHTML = html;
    card.addEventListener('click', async (e) => {
      if (e.target.classList.contains('btn-delete')) {
        e.stopPropagation();
        if (confirm(`${t('deletePersona')}: ${p.name}?`)) {
          await window.bobpet.deletePersona(p.name);
          if (selectedPersona === p.name) {
            selectedPersona = 'neko';
            await window.bobpet.saveConfig({ persona: 'neko' });
          }
          renderPersonas();
        }
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
document.querySelectorAll('[data-model-type]').forEach((btn) => {
  btn.addEventListener('click', () => {
    modelType = btn.dataset.modelType;
    updateModelPanels();
  });
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
    showThinking: document.getElementById('show-thinking').checked
  });
  alert(t('save') + ' ✓');
});

// --- Ollama link ---
document.getElementById('link-ollama').addEventListener('click', (e) => {
  e.preventDefault();
  window.bobpet.openExternal('https://ollama.com/download');
});

// --- Persona modal ---
const personaModal = document.getElementById('modal-overlay');
document.getElementById('btn-add-persona').addEventListener('click', () => {
  personaModal.hidden = false;
  document.getElementById('m-name').value = '';
  document.getElementById('m-desc').value = '';
  document.getElementById('m-greeting').value = '';
  document.getElementById('m-prompt').value = '';
  document.getElementById('m-keywords').value = '';
});

document.getElementById('btn-modal-cancel').addEventListener('click', () => {
  personaModal.hidden = true;
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
  selectedPersona = name;
  await window.bobpet.saveConfig({ persona: name });
  personaModal.hidden = true;
  renderPersonas();
});

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
