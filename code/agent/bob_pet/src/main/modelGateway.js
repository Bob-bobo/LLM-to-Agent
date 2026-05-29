const axios = require('axios');
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const { app } = require('electron');
const { store, decrypt } = require('./store');

function getPersonasDir() {
  const userDir = path.join(app.getPath('appData'), 'BobPet', 'personas');
  if (!fs.existsSync(userDir)) {
    fs.mkdirSync(userDir, { recursive: true });
  }
  const bundled = path.join(process.resourcesPath || '', 'personas');
  const devBundled = path.join(__dirname, '../../personas');
  return { userDir, bundled: fs.existsSync(bundled) ? bundled : devBundled };
}

function loadPersona(name) {
  const { userDir, bundled } = getPersonasDir();
  for (const dir of [userDir, bundled]) {
    const yamlPath = path.join(dir, `${name}.yaml`);
    const jsonPath = path.join(dir, `${name}.json`);
    if (fs.existsSync(yamlPath)) {
      return yaml.load(fs.readFileSync(yamlPath, 'utf8'));
    }
    if (fs.existsSync(jsonPath)) {
      return JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
    }
  }
  return null;
}

function listPersonas() {
  const { userDir, bundled } = getPersonasDir();
  const names = new Set();
  for (const dir of [bundled, userDir]) {
    if (!fs.existsSync(dir)) continue;
    fs.readdirSync(dir).forEach((f) => {
      const m = f.match(/^(.+)\.(yaml|json)$/);
      if (m) names.add(m[1]);
    });
  }
  return Array.from(names).map((name) => {
    const p = loadPersona(name);
    return { name, description: p?.description || name };
  });
}

function buildSystemPrompt(persona) {
  const style = persona?.dialogue_style || {};
  return (
    style.system_prompt ||
    `你是 ${persona?.name || 'BobPet'} 桌面助手。${persona?.description || ''}`
  );
}

async function testConnection() {
  const cfg = store.get('model');
  try {
    if (cfg.type === 'local') {
      const base = cfg.local?.baseUrl || 'http://127.0.0.1:11434';
      const res = await axios.get(`${base}/api/tags`, { timeout: 5000 });
      return { ok: true, message: `已连接 Ollama，模型数: ${res.data?.models?.length || 0}` };
    }
    const base = (cfg.cloud?.baseUrl || '').replace(/\/$/, '');
    const key = decrypt(cfg.cloud?.apiKey || '');
    const res = await axios.get(`${base}/models`, {
      headers: { Authorization: `Bearer ${key}` },
      timeout: 8000
    });
    return { ok: true, message: `连接成功，可用模型 ${res.data?.data?.length || '若干'}` };
  } catch (err) {
    return { ok: false, message: err.message || '连接失败' };
  }
}

async function* streamChat(messages, options = {}) {
  const cfg = store.get('model');
  const personaName = store.get('persona') || 'neko';
  const persona = loadPersona(personaName);
  const systemPrompt = buildSystemPrompt(persona);

  const fullMessages = [
    { role: 'system', content: systemPrompt },
    ...messages
  ];

  if (cfg.type === 'local') {
    yield* streamOllama(fullMessages, cfg.local, options);
  } else {
    yield* streamOpenAI(fullMessages, cfg.cloud, options);
  }
}

async function* streamOllama(messages, localCfg, options) {
  const base = localCfg?.baseUrl || 'http://127.0.0.1:11434';
  const model = localCfg?.model || 'llama3.2';
  const body = {
    model,
    messages,
    stream: true
  };
  if (options.thinking) {
    body.options = { ...(body.options || {}), num_predict: 2048 };
  }

  const res = await axios.post(`${base}/api/chat`, body, {
    responseType: 'stream',
    timeout: 120000
  });

  let buffer = '';
  for await (const chunk of res.data) {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const json = JSON.parse(line);
        if (json.message?.content) {
          yield { type: 'content', text: json.message.content };
        }
        if (json.message?.thinking) {
          yield { type: 'thinking', text: json.message.thinking };
        }
        if (json.done) yield { type: 'done' };
      } catch {
        /* skip malformed */
      }
    }
  }
}

async function* streamOpenAI(messages, cloudCfg, options) {
  const base = (cloudCfg?.baseUrl || 'https://api.openai.com/v1').replace(/\/$/, '');
  const apiKey = decrypt(cloudCfg?.apiKey || '');
  const model = cloudCfg?.model || 'gpt-4o-mini';

  const res = await axios.post(
    `${base}/chat/completions`,
    { model, messages, stream: true },
    {
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      responseType: 'stream',
      timeout: 120000
    }
  );

  let buffer = '';
  for await (const chunk of res.data) {
    buffer += chunk.toString();
    const parts = buffer.split('\n');
    buffer = parts.pop() || '';
    for (const part of parts) {
      const line = part.trim();
      if (!line.startsWith('data:')) continue;
      const data = line.slice(5).trim();
      if (data === '[DONE]') {
        yield { type: 'done' };
        continue;
      }
      try {
        const json = JSON.parse(data);
        const delta = json.choices?.[0]?.delta;
        if (delta?.content) yield { type: 'content', text: delta.content };
        if (delta?.reasoning_content && options.thinking) {
          yield { type: 'thinking', text: delta.reasoning_content };
        }
      } catch {
        /* skip */
      }
    }
  }
}

function applyPersonaTemplate(persona, query, response) {
  const tpl = persona?.dialogue_style?.response_template;
  if (!tpl) return response;
  return tpl.replace('{{query}}', query).replace('{{response}}', response);
}

module.exports = {
  streamChat,
  testConnection,
  loadPersona,
  listPersonas,
  applyPersonaTemplate,
  getPersonasDir
};
