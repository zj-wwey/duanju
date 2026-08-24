'use strict'
const fs = require('fs')
const path = require('path')

const dir = path.resolve(__dirname, '..', 'front', 'shared', 'i18n', 'stream')
const enUS = JSON.parse(fs.readFileSync(path.join(dir, 'en-US.json'), 'utf8'))
const keepAsIs = new Set(['VIP', 'pts', 'PayPal', 'WeChat Pay', 'Alipay', 'Stripe'])

for (const locale of ['de-DE', 'fr-FR', 'it-IT']) {
  const data = JSON.parse(fs.readFileSync(path.join(dir, locale + '.json'), 'utf8'))
  console.log('\n=== ' + locale + ' ===')
  for (const key of Object.keys(enUS).sort()) {
    if (key === 'brand') continue
    if (data[key] === enUS[key] && !keepAsIs.has(enUS[key])) {
      console.log('  ' + key + ' = "' + enUS[key] + '"')
    }
  }
}
