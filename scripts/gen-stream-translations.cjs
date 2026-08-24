'use strict'
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const ROOT = path.resolve(__dirname, '..')
const SHARED_I18N = path.join(ROOT, 'front', 'shared', 'i18n')
const STREAM_JS = path.join(ROOT, 'front', 'admin', 'src', 'locales', 'streamI18n.js')
const OUTPUT_DIR = path.join(SHARED_I18N, 'stream')

fs.mkdirSync(OUTPUT_DIR, { recursive: true })

function extractBraceBlock(code, startMarker) {
  const startIdx = code.indexOf(startMarker)
  if (startIdx === -1) throw new Error(`Marker not found: ${startMarker}`)
  const braceStart = code.indexOf('{', startIdx)
  let depth = 0
  for (let i = braceStart; i < code.length; i++) {
    if (code[i] === '{') depth++
    else if (code[i] === '}') {
      depth--
      if (depth === 0) return code.slice(braceStart, i + 1)
    }
  }
  throw new Error('Unmatched braces')
}

function flatten(obj, prefix, result) {
  prefix = prefix || ''
  result = result || {}
  for (const key in obj) {
    const val = obj[key]
    const flatKey = prefix ? prefix + '.' + key : key
    if (val && typeof val === 'object' && !Array.isArray(val)) {
      flatten(val, flatKey, result)
    } else {
      result[flatKey] = val
    }
  }
  return result
}

// ── 1. Extract zhCN and enUS from streamI18n.js ──
const code = fs.readFileSync(STREAM_JS, 'utf8')

const zhCNCode = extractBraceBlock(code, 'const zhCN = ')
const enUSCode = extractBraceBlock(code, 'const enUS = ')

const sb = { JSON: JSON }
vm.createContext(sb)
vm.runInContext('this.zhCN = ' + zhCNCode, sb)
const zhCN = sb.zhCN

const enUSFixed = enUSCode.replace(/\.\.\.zhCN/g, '...' + JSON.stringify(zhCN))
vm.runInContext('this.enUS = ' + enUSFixed, sb)
const enUS = sb.enUS

const flatZhCN = flatten(zhCN)
const flatEnUS = flatten(enUS)

console.log('zh-CN keys:', Object.keys(flatZhCN).length)
console.log('en-US keys:', Object.keys(flatEnUS).length)

// ── 2. Extract per-language overrides from messages object ──
const langOverrides = {}
const langBaseMap = {}

const messagesStart = code.indexOf('export const messages = {')
const messagesEnd = code.indexOf('\n}\n\nexport function currentLocale')
const messagesCode = code.slice(messagesStart, messagesEnd > 0 ? messagesEnd : undefined)

// String-aware brace extraction: skip { } inside string literals
function extractObjFromPos(text, startPos) {
  let depth = 0
  let inStr = false
  let strCh = ''
  let endIdx = -1
  for (let i = startPos; i < text.length; i++) {
    const ch = text[i]
    if (inStr) {
      if (ch === '\\') { i++ }
      else if (ch === strCh) { inStr = false }
    } else {
      if (ch === '"' || ch === "'" || ch === '`') { inStr = true; strCh = ch }
      else if (ch === '{') depth++
      else if (ch === '}') { depth--; if (depth === 0) { endIdx = i; break } }
    }
  }
  return endIdx > 0 ? text.slice(startPos, endIdx + 1) : null
}

const langPattern = /'([a-z]{2}-[A-Z]{2})':\s*clone\((\w+),\s*/g
let m
while ((m = langPattern.exec(messagesCode)) !== null) {
  const locale = m[1]
  const baseVar = m[2]
  langBaseMap[locale] = baseVar
  const overrideStart = m.index + m[0].length
  const overrideCode = extractObjFromPos(messagesCode, overrideStart)
  if (!overrideCode) { console.warn('  No object found for', locale); continue }
  try {
    const overrideSb = { enUS: enUS, zhCN: zhCN, JSON: JSON }
    vm.createContext(overrideSb)
    vm.runInContext('this._r = ' + overrideCode, overrideSb)
    langOverrides[locale] = flatten(overrideSb._r)
  } catch (e) {
    console.warn('  Failed override for', locale, e.message)
  }
}

console.log('Language overrides found:', Object.keys(langOverrides).length)

// ── 3. Extract admin translations from src/i18n.js (has real fr/de/it) ──
const ADMIN_JS = path.join(ROOT, 'front', 'admin', 'src', 'i18n.js')
const adminCode = fs.readFileSync(ADMIN_JS, 'utf8')

const adminEnCode = extractBraceBlock(adminCode, 'const en = ')
const adminSb = { JSON: JSON }
vm.createContext(adminSb)
vm.runInContext('this.en = ' + adminEnCode, adminSb)
const adminEn = adminSb.en

// Extract per-language admin translations from src/i18n.js
const adminVarNames = {
  'zh-CN': 'zhCN', 'zh-TW': 'zhHant', 'en-US': 'en',
  'ja-JP': 'ja', 'ko-KR': 'ko', 'th-TH': 'th',
  'vi-VN': 'vi', 'id-ID': 'id', 'ms-MY': 'ms',
  'es-ES': 'es', 'fr-FR': 'fr', 'de-DE': 'de',
  'pt-BR': 'pt', 'ru-RU': 'ru', 'it-IT': 'it',
  'tr-TR': 'tr', 'ar-SA': 'ar'
}

const adminTransByLocale = {}
for (const locale in adminVarNames) {
  const varName = adminVarNames[locale]
  const marker = 'const ' + varName + ' = clone(en, '
  const markerIdx = adminCode.indexOf(marker)
  if (markerIdx === -1) continue
  const objStart = adminCode.indexOf('{', markerIdx + marker.length)
  const objCode = extractObjFromPos(adminCode, objStart)
  if (!objCode) continue
  try {
    const sb2 = { en: adminEn, JSON: JSON }
    vm.createContext(sb2)
    vm.runInContext('this._r = ' + objCode, sb2)
    // Merge: start with en as base, apply overrides
    const merged = {}
    for (const k in adminEn) merged[k] = adminEn[k]
    for (const k in sb2._r) merged[k] = sb2._r[k]
    adminTransByLocale[locale] = merged
  } catch (e) {
    console.warn('  Failed admin extract for', locale, e.message)
  }
}
// en-US is just adminEn
adminTransByLocale['en-US'] = adminEn
console.log('Admin translations extracted:', Object.keys(adminTransByLocale).length, 'locales')

// Also read shared JSON as secondary source
const sharedAdminByLocale = {}
for (const locale in adminVarNames) {
  try {
    const f = path.join(SHARED_I18N, locale + '.json')
    sharedAdminByLocale[locale] = JSON.parse(fs.readFileSync(f, 'utf8'))
  } catch (e) {}
}

// Build reverse map: English value → admin key (from src/i18n.js en object)
const enValToAdminKey = {}
for (const k in adminEn) {
  const v = adminEn[k]
  if (typeof v === 'string' && v.length > 3) {
    enValToAdminKey[v] = k
  }
}
// Also add from shared JSON
const sharedEnUS = sharedAdminByLocale['en-US'] || {}
for (const k in sharedEnUS) {
  const v = sharedEnUS[k]
  if (typeof v === 'string' && v.length > 3 && !enValToAdminKey[v]) {
    enValToAdminKey[v] = k
  }
}

// Map stream flat keys → admin flat keys
const streamToAdmin = {}
for (const sk in flatEnUS) {
  const sv = flatEnUS[sk]
  if (typeof sv === 'string' && sv.length > 3) {
    const ak = enValToAdminKey[sv]
    if (ak) streamToAdmin[sk] = ak
  }
}
console.log('Stream→Admin mapped keys:', Object.keys(streamToAdmin).length)

// ── 4. Generate translations for each language ──
const locales = [
  'zh-CN', 'zh-TW', 'en-US', 'ja-JP', 'ko-KR', 'th-TH', 'vi-VN', 'id-ID', 'ms-MY',
  'es-ES', 'fr-FR', 'de-DE', 'pt-BR', 'ru-RU', 'it-IT', 'tr-TR', 'ar-SA'
]

const localeToAdminLocale = {
  'zh-CN': 'zh-CN', 'zh-TW': 'zh-TW', 'en-US': 'en-US',
  'ja-JP': 'ja-JP', 'ko-KR': 'ko-KR', 'th-TH': 'th-TH',
  'vi-VN': 'vi-VN', 'id-ID': 'id-ID', 'ms-MY': 'ms-MY',
  'es-ES': 'es-ES', 'fr-FR': 'fr-FR', 'de-DE': 'de-DE',
  'pt-BR': 'pt-BR', 'ru-RU': 'ru-RU', 'it-IT': 'it-IT',
  'tr-TR': 'tr-TR', 'ar-SA': 'ar-SA'
}

for (const locale of locales) {
  let translations

  if (locale === 'zh-CN') {
    translations = Object.assign({}, flatZhCN)
  } else if (locale === 'en-US') {
    translations = Object.assign({}, flatEnUS)
  } else {
    translations = Object.assign({}, flatEnUS)

    // Primary source: src/i18n.js per-language translations
    const adminTrans = adminTransByLocale[locale] || {}
    // Secondary source: shared JSON
    const sharedTrans = sharedAdminByLocale[locale] || {}

    let mapped = 0
    let skipped = 0
    for (const sk in streamToAdmin) {
      const ak = streamToAdmin[sk]
      // Try src/i18n.js first (has real fr/de/it translations)
      let av = adminTrans[ak]
      let source = 'i18n.js'
      // If i18n.js value equals English, try shared JSON
      if (!av || av === adminEn[ak]) {
        const sv = sharedTrans[ak]
        if (sv && sv !== sharedEnUS[ak]) {
          av = sv
          source = 'shared'
        }
      }
      if (av && typeof av === 'string' && av !== adminEn[ak] && av !== sharedEnUS[ak]) {
        translations[sk] = av
        mapped++
      } else {
        skipped++
      }
    }
    console.log('  ' + locale + ': mapped=' + mapped + ' skipped=' + skipped)

    // Apply existing stream overrides (nav items, etc.)
    if (langOverrides[locale]) {
      const ov = langOverrides[locale]
      for (const k in ov) {
        translations[k] = ov[k]
      }
    }
  }

  const outPath = path.join(OUTPUT_DIR, locale + '.json')
  fs.writeFileSync(outPath, JSON.stringify(translations, null, 2))
  console.log('Written: ' + locale + '.json (' + Object.keys(translations).length + ' keys)')
}

console.log('\nDone! Stream translation files generated in:', OUTPUT_DIR)
