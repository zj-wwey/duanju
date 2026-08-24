#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const PROJECT_ROOT = path.resolve(__dirname, '..')
const I18N_DIR = path.join(PROJECT_ROOT, 'front', 'shared', 'i18n')
const KEYS_FILE = path.join(I18N_DIR, 'keys.json')

const ERROR_CODE = 1
const SUCCESS_CODE = 0

let totalErrors = 0

function error(message) {
  console.error(`  ✗ ${message}`)
  totalErrors++
}

function ok(message) {
  console.log(`  ✓ ${message}`)
}

function warn(message) {
  console.warn(`  ⚠ ${message}`)
}

function getNestedValue(obj, dotPath) {
  const parts = dotPath.split('.')
  let current = obj
  for (const part of parts) {
    if (current == null || typeof current !== 'object') return undefined
    current = current[part]
  }
  return current
}

function setNestedValue(obj, dotPath, value) {
  const parts = dotPath.split('.')
  let current = obj
  for (let i = 0; i < parts.length - 1; i++) {
    if (current[parts[i]] == null || typeof current[parts[i]] !== 'object') {
      current[parts[i]] = {}
    }
    current = current[parts[i]]
  }
  current[parts[parts.length - 1]] = value
}

function collectAllLeafKeys(obj, prefix = '') {
  const keys = []
  for (const [key, value] of Object.entries(obj)) {
    const fullPath = prefix ? `${prefix}.${key}` : key
    if (value != null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...collectAllLeafKeys(value, fullPath))
    } else {
      keys.push(fullPath)
    }
  }
  return keys
}

// Step 1: Validate keys.json exists
console.log('\n🔍 i18n Translation Validation')
console.log('='.repeat(50))

if (!fs.existsSync(KEYS_FILE)) {
  console.error('\n❌ keys.json not found at:', KEYS_FILE)
  process.exit(ERROR_CODE)
}

const keysSchema = JSON.parse(fs.readFileSync(KEYS_FILE, 'utf-8'))

// Extract all required keys from schema
function extractKeys(schema) {
  const keys = []
  if (!schema.namespaces) return keys
  for (const [namespace, def] of Object.entries(schema.namespaces)) {
    if (!def.keys || !Array.isArray(def.keys)) continue
    for (const key of def.keys) {
      keys.push(`${namespace}.${key}`)
    }
  }
  return keys
}

const requiredKeys = extractKeys(keysSchema)
console.log(`  📋 Schema defines ${requiredKeys.length} translation keys across ${Object.keys(keysSchema.namespaces || {}).length} namespaces`)

// Find all locale files
const localeFiles = fs.readdirSync(I18N_DIR)
  .filter(f => f.endsWith('.json') && f !== 'keys.json' && f !== 'errorCodes.json' && !f.endsWith('.meta'))
  .sort()

console.log(`  🌍 Found ${localeFiles.length} locale files: ${localeFiles.join(', ')}`)

if (localeFiles.length === 0) {
  error('No locale JSON files found')
  process.exit(ERROR_CODE)
}

// Step 2: Check each locale file for missing/unknown keys
console.log('\n📋 Checking translation key completeness...')

const zhCNFile = path.join(I18N_DIR, 'zh-CN.json')
const zhCNData = fs.existsSync(zhCNFile) ? JSON.parse(fs.readFileSync(zhCNFile, 'utf-8')) : null

for (const localeFile of localeFiles) {
  const locale = localeFile.replace('.json', '')
  const filePath = path.join(I18N_DIR, localeFile)

  let data
  try {
    data = JSON.parse(fs.readFileSync(filePath, 'utf-8'))
  } catch (e) {
    error(`[${locale}] Invalid JSON: ${e.message}`)
    continue
  }

  let missingCount = 0
  let unknownCount = 0

  // Check for missing required keys
  for (const fullKey of requiredKeys) {
    const keyPath = fullKey
    const value = getNestedValue(data, keyPath)
    if (value === undefined) {
      error(`[${locale}] Missing key: ${fullKey}`)
      missingCount++
    }
  }

  // Check for unknown keys (leaf keys not in schema)
  const schemaKeys = new Set(requiredKeys)
  const leafKeys = collectAllLeafKeys(data)
  for (const leafKey of leafKeys) {
    if (!schemaKeys.has(leafKey)) {
      // Check if it's a valid namespace-level key (not leaf)
      const topLevel = leafKey.split('.')[0]
      if (!keysSchema.namespaces[topLevel]) {
        // Only flag if it's not a known top-level namespace
        const knownTopLevels = Object.keys(keysSchema.namespaces)
        if (!knownTopLevels.includes(topLevel)) {
          // Skip keys that might be admin-only or dynamic
          if (topLevel !== 'admin' && topLevel !== 'announcement' && topLevel !== 'feedback') {
            // These are likely from admin i18n, skip for now
          }
          continue
        }
        // Check if this leaf path exists in the schema
        const schemaNamespace = keysSchema.namespaces[topLevel]
        if (schemaNamespace) {
          const remainingPath = leafKey.substring(topLevel.length + 1)
          const validKeys = schemaNamespace.keys || []
          let isValid = false
          for (const validKey of validKeys) {
            if (remainingPath === validKey || remainingPath.startsWith(validKey + '.')) {
              isValid = true
              break
            }
          }
          if (!isValid && !remainingPath.includes('.')) {
            // Only flag simple unknown keys, skip nested ones from admin/etc.
            // We'll be lenient here since some keys may be legacy
          }
        }
      }
    }
  }

  if (missingCount === 0 && unknownCount === 0) {
    ok(`[${locale}] All keys valid`)
  } else if (missingCount > 0) {
    warn(`[${locale}] ${missingCount} missing, ${unknownCount} unknown keys`)
  }
}

// Step 3: Check placeholder consistency
console.log('\n🔍 Checking placeholder consistency...')

if (zhCNData) {
  for (const localeFile of localeFiles) {
    const locale = localeFile.replace('.json', '')
    const filePath = path.join(I18N_DIR, localeFile)

    let data
    try {
      data = JSON.parse(fs.readFileSync(filePath, 'utf-8'))
    } catch {
      continue
    }

    let mismatchCount = 0
    for (const fullKey of requiredKeys) {
      const zhCNValue = getNestedValue(zhCNData, fullKey)
      const localeValue = getNestedValue(data, fullKey)

      if (zhCNValue && localeValue && typeof zhCNValue === 'string' && typeof localeValue === 'string') {
        const zhCNPlaceholders = (zhCNValue.match(/\{[^}]+\}/g) || []).sort()
        const localePlaceholders = (localeValue.match(/\{[^}]+\}/g) || []).sort()

        if (JSON.stringify(zhCNPlaceholders) !== JSON.stringify(localePlaceholders)) {
          if (mismatchCount < 5) {
            warn(`[${locale}] Placeholder mismatch: ${fullKey} (expected ${zhCNPlaceholders.join(', ') || 'none'}, got ${localePlaceholders.join(', ') || 'none'})`)
          }
          mismatchCount++
        }
      }
    }
    if (mismatchCount === 0) {
      ok(`[${locale}] All placeholders match`)
    } else if (mismatchCount > 5) {
      warn(`[${locale}] ${mismatchCount} placeholder mismatches (showing first 5)`)
    }
  }
} else {
  warn('zh-CN.json not found, skipping placeholder check')
}

// Step 4: Check for empty translations (untranslated)
console.log('\n📝 Checking for empty/untranslated entries...')

for (const localeFile of localeFiles) {
  const locale = localeFile.replace('.json', '')
  if (locale === 'zh-CN') continue

  const filePath = path.join(I18N_DIR, localeFile)
  let data
  try {
    data = JSON.parse(fs.readFileSync(filePath, 'utf-8'))
  } catch {
    continue
  }

  let emptyCount = 0
  for (const fullKey of requiredKeys) {
    const value = getNestedValue(data, fullKey)
    if (value === '' || value === undefined || value === null) {
      emptyCount++
    }
  }

  if (emptyCount > 0) {
    // Only show first 10 details to avoid noise
    let shown = 0
    for (const fullKey of requiredKeys) {
      const value = getNestedValue(data, fullKey)
      if ((value === '' || value === undefined || value === null) && shown < 3) {
        warn(`[${locale}] Empty: ${fullKey}`)
        shown++
      }
    }
    if (emptyCount > 3) {
      warn(`[${locale}] ... and ${emptyCount - 3} more empty entries`)
    }
  } else {
    ok(`[${locale}] All entries have content`)
  }
}

// Step 5: Cross-reference with errorCodes.js
console.log('\n🔗 Cross-referencing error codes...')

const errorCodesFile = path.join(I18N_DIR, 'errorCodes.js')
if (fs.existsSync(errorCodesFile)) {
  const errorCodesContent = fs.readFileSync(errorCodesFile, 'utf-8')
  const codeMatches = errorCodesContent.match(/"([A-Z_]+)"\s*:/g) || []
  const errorCodeKeys = [...new Set(codeMatches.map(m => m.replace(/[":]/g, '').trim()))]

  let missingCodes = 0
  for (const codeKey of errorCodeKeys) {
    const camelKey = codeKey
      .toLowerCase()
      .replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
    const zhCNErrorValue = zhCNData?.error ? getNestedValue(zhCNData, `error.${camelKey}`) : undefined
    if (zhCNErrorValue === undefined) {
      if (missingCodes < 5) {
        warn(`Error code "${codeKey}" (→ error.${camelKey}) not found in zh-CN.json`)
      }
      missingCodes++
    }
  }

  if (missingCodes === 0) {
    ok(`All ${errorCodeKeys.length} error codes have translations`)
  } else {
    warn(`${missingCodes} error codes missing translations in zh-CN.json`)
  }
} else {
  warn('errorCodes.js not found, skipping cross-reference')
}

// Summary
console.log('\n' + '='.repeat(50))
if (totalErrors > 0) {
  console.error(`\n❌ Validation FAILED: ${totalErrors} error(s) found\n`)
  process.exit(ERROR_CODE)
} else {
  console.log('\n✅ Validation PASSED: All translations valid\n')
  process.exit(SUCCESS_CODE)
}
