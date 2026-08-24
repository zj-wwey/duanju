'use strict'
const fs = require('fs')
const path = require('path')

const dir = path.resolve(__dirname, '..', 'front', 'shared', 'i18n', 'stream')
const files = fs.readdirSync(dir).filter(f => f.endsWith('.json'))

const enUS = JSON.parse(fs.readFileSync(path.join(dir, 'en-US.json'), 'utf8'))
const enKeys = Object.keys(enUS).sort()
const enKeySet = new Set(enKeys)

const placeholderRe = /\{(\w+)\}/g

console.log('Reference: en-US.json with ' + enKeys.length + ' keys')
console.log('='.repeat(60))

let allPass = true

for (const file of files.sort()) {
  const filePath = path.join(dir, file)
  try {
    const content = fs.readFileSync(filePath, 'utf8')
    const data = JSON.parse(content)
    const keys = Object.keys(data)
    const keySet = new Set(keys)

    const keyCount = keys.length
    const countMatch = keyCount === enKeys.length

    const missing = enKeys.filter(k => !keySet.has(k))
    const extra = keys.filter(k => !enKeySet.has(k))

    let placeholderIssues = []
    for (const key of enKeys) {
      const enVal = enUS[key] || ''
      const enPlaceholders = (enVal.match(placeholderRe) || []).sort()
      if (enPlaceholders.length === 0) continue

      const val = data[key] || ''
      const valPlaceholders = (val.match(placeholderRe) || []).sort()
      if (JSON.stringify(enPlaceholders) !== JSON.stringify(valPlaceholders)) {
        placeholderIssues.push({ key, en: enPlaceholders, val: valPlaceholders })
      }
    }

    let untranslated = 0
    const keepAsIs = new Set(['VIP', 'pts', 'PayPal', 'WeChat Pay', 'Alipay', 'Stripe'])
    for (const key of enKeys) {
      if (key === 'brand') continue
      if (data[key] === enUS[key] && !keepAsIs.has(enUS[key])) {
        untranslated++
      }
    }

    const status = countMatch && missing.length === 0 && extra.length === 0 && placeholderIssues.length === 0
    if (!status) allPass = false

    console.log(
      file + ': ' + keyCount + ' keys | ' +
      (countMatch ? 'OK' : 'MISMATCH') + ' | ' +
      'missing:' + missing.length + ' | extra:' + extra.length + ' | ' +
      'ph_issues:' + placeholderIssues.length + ' | ' +
      'untranslated:' + untranslated + ' | ' +
      (status ? 'PASS' : 'FAIL')
    )

    if (missing.length > 0) console.log('  Missing: ' + missing.slice(0, 5).join(', ') + (missing.length > 5 ? '...' : ''))
    if (extra.length > 0) console.log('  Extra: ' + extra.slice(0, 5).join(', ') + (extra.length > 5 ? '...' : ''))
    if (placeholderIssues.length > 0) {
      placeholderIssues.slice(0, 3).forEach(pi => {
        console.log('  PH: ' + pi.key + ' en=' + JSON.stringify(pi.en) + ' val=' + JSON.stringify(pi.val))
      })
    }
  } catch (err) {
    allPass = false
    console.log(file + ': JSON ERROR - ' + err.message)
  }
}

console.log('='.repeat(60))
console.log('Total files: ' + files.length)
console.log('All pass: ' + allPass)
