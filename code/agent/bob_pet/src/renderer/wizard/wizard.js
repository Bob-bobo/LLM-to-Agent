let chosenType = 'cloud';
let i18n = {};

function t(key) { return i18n[key] || key; }

async function runDetect() {
  const i18nData = await window.bobpet.getI18n();
  i18n = i18nData.strings;

  const env = await window.bobpet.detectEnv();
  const list = document.getElementById('env-list');
  list.innerHTML = `
    <li>${t('wizardOS')}: Windows (${env.platform})</li>
    <li>${t('wizardMem')}: ${env.totalMemGB} GB ${env.recommendLocal ? '（' + t('wizardMemRecommend') + '）' : '（' + t('wizardMemCloud') + '）'}</li>
    <li>Ollama: ${env.ollamaOk ? t('wizardOllamaYes') : t('wizardOllamaNo')}</li>
  `;
  setTimeout(() => {
    document.getElementById('step-detect').classList.remove('active');
    document.getElementById('step-choose').classList.add('active');
  }, 1500);
}

document.querySelectorAll('.choice').forEach((btn) => {
  btn.addEventListener('click', () => {
    chosenType = btn.dataset.type;
    document.getElementById('step-choose').classList.remove('active');
    document.getElementById('step-config').classList.add('active');
    document.getElementById('config-local').classList.toggle('hidden', chosenType !== 'local');
    document.getElementById('config-cloud').classList.toggle('hidden', chosenType !== 'cloud');
  });
});

document.getElementById('btn-finish').addEventListener('click', async () => {
  const config = {
    persona: 'neko',
    model: {
      type: chosenType,
      local: {
        baseUrl: document.getElementById('w-local-url').value.trim(),
        model: document.getElementById('w-local-model').value.trim()
      },
      cloud: {
        baseUrl: document.getElementById('w-cloud-url').value.trim(),
        apiKey: document.getElementById('w-cloud-key').value,
        model: document.getElementById('w-cloud-model').value.trim()
      }
    }
  };
  await window.bobpet.finishWizard(config);
});

runDetect();
