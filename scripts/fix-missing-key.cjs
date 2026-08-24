'use strict'
const fs = require('fs')
const path = require('path')

const dir = path.resolve(__dirname, '..', 'front', 'shared', 'i18n', 'stream')

const translations = {
  'zh-CN': '请填写完整信息',
  'zh-TW': '請填寫完整資訊',
  'en-US': 'Please fill in all fields',
  'ja-JP': 'すべての項目を入力してください',
  'ko-KR': '모든 항목을 입력해 주세요',
  'th-TH': 'กรุณากรอกข้อมูลให้ครบถ้วน',
  'vi-VN': 'Vui lòng điền đầy đủ thông tin',
  'id-ID': 'Mohon isi semua kolom',
  'ms-MY': 'Sila isi semua medan',
  'es-ES': 'Por favor complete todos los campos',
  'fr-FR': 'Veuillez remplir tous les champs',
  'de-DE': 'Bitte alle Felder ausfüllen',
  'pt-BR': 'Por favor, preencha todos os campos',
  'ru-RU': 'Пожалуйста, заполните все поля',
  'it-IT': 'Si prega di compilare tutti i campi',
  'tr-TR': 'Lütfen tüm alanları doldurun',
  'ar-SA': 'يرجى ملء جميع الحقول'
}

const files = fs.readdirSync(dir).filter(f => f.endsWith('.json'))

for (const file of files) {
  const locale = file.replace('.json', '')
  const filePath = path.join(dir, file)
  const content = fs.readFileSync(filePath, 'utf8')
  const data = JSON.parse(content)

  if (data['common.fillAllFields']) {
    console.log(file + ': already has key, skipping')
    continue
  }

  const val = translations[locale] || translations['en-US']
  data['common.fillAllFields'] = val

  // Sort keys to match existing pattern (insert after common.deleteSuccess)
  const sortedKeys = Object.keys(data).sort()
  const sortedData = {}
  for (const k of sortedKeys) {
    sortedData[k] = data[k]
  }

  fs.writeFileSync(filePath, JSON.stringify(sortedData, null, 2) + '\n', 'utf8')
  console.log(file + ': added "' + val + '"')
}

console.log('Done!')
