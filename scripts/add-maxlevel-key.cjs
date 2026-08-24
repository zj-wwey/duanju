const fs = require('fs');
const path = require('path');

const translations = {
  'ar-SA': 'تم الوصول لأعلى مستوى',
  'de-DE': 'Höchste Stufe erreicht',
  'en-US': 'Max level reached',
  'es-ES': 'Nivel máximo alcanzado',
  'fr-FR': 'Niveau maximum atteint',
  'id-ID': 'Level maksimal tercapai',
  'it-IT': 'Livello massimo raggiunto',
  'ja-JP': '最高レベルに到達',
  'ko-KR': '최고 레벨 달성',
  'ms-MY': 'Tahap maksimum dicapai',
  'pt-BR': 'Nível máximo atingido',
  'ru-RU': 'Достигнут максимальный уровень',
  'th-TH': 'ถึงระดับสูงสุดแล้ว',
  'tr-TR': 'Maksimum seviyeye ulasildi',
  'vi-VN': 'Da dat cap cao nhat',
  'zh-CN': '已达最高等级',
  'zh-TW': '已達最高等級'
};

const dir = 'c:\\Users\\Administrator\\Desktop\\AICode\\duanju-master\\front\\shared\\i18n\\stream';

let added = 0;
let skipped = 0;

for (const [lang, value] of Object.entries(translations)) {
  const file = path.join(dir, `${lang}.json`);
  const json = JSON.parse(fs.readFileSync(file, 'utf8'));

  if (!json.membership) {
    json.membership = {};
  }

  if (json.membership.maxLevelReached) {
    console.log(`SKIP: ${lang}.json (key already exists)`);
    skipped++;
    continue;
  }

  json.membership.maxLevelReached = value;
  fs.writeFileSync(file, JSON.stringify(json, null, 2) + '\n', 'utf8');
  console.log(`ADD:  ${lang}.json`);
  added++;
}

console.log(`\nDone. Added: ${added}, Skipped: ${skipped}`);
