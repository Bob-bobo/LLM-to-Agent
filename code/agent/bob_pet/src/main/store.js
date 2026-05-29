const path = require('path');
const { app } = require('electron');
const Store = require('electron-store');
const crypto = require('crypto');

const ENCRYPTION_KEY = crypto
  .createHash('sha256')
  .update('bobpet-local-key-v1')
  .digest();

let store;

function getStore() {
  if (!store) {
    const base = path.join(app.getPath('appData'), 'BobPet');
    store = new Store({
      name: 'config',
      cwd: base,
      defaults: {
        firstRun: true,
        model: {
          type: 'cloud',
          local: {
            provider: 'ollama',
            baseUrl: 'http://127.0.0.1:11434',
            model: 'llama3.2'
          },
          cloud: {
            baseUrl: 'https://api.openai.com/v1',
            apiKey: '',
            model: 'gpt-4o-mini'
          }
        },
        persona: 'neko',
        pet: { x: null, y: null, visible: true },
        modelProfiles: [],
        shortcuts: {
          toggleChat: 'CommandOrControl+Shift+M',
          toggleThinking: 'CommandOrControl+Shift+T',
          togglePet: 'CommandOrControl+Shift+H'
        },
        theme: 'blue',
        language: 'zh-CN',
        autoStart: false,
        showThinking: false
      }
    });
  }
  return store;
}

function encrypt(text) {
  if (!text) return '';
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  return iv.toString('hex') + ':' + encrypted;
}

function decrypt(text) {
  if (!text || !text.includes(':')) return text || '';
  try {
    const [ivHex, data] = text.split(':');
    const iv = Buffer.from(ivHex, 'hex');
    const decipher = crypto.createDecipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
    let decrypted = decipher.update(data, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
  } catch {
    return '';
  }
}

module.exports = {
  get store() {
    return getStore();
  },
  encrypt,
  decrypt
};
