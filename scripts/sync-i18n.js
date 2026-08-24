#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const REPO_ROOT = path.resolve(__dirname, '..')

const SHARED_SOURCE = path.join(REPO_ROOT, 'front', 'shared', 'i18n', 'uniapp-data.js')
const UNIAPP_TARGET = path.join(REPO_ROOT, 'front', 'uniapp', 'utils', 'uniapp-data.js')

const UNIAPP_STATIC_I18N = path.join(REPO_ROOT, 'front', 'uniapp', 'static', 'i18n')
const SHARED_I18N = path.join(REPO_ROOT, 'front', 'shared', 'i18n')

const banner = `// AUTO-GENERATED FILE — DO NOT EDIT BY HAND
// Source: front/shared/i18n/uniapp-data.js
// Run: node scripts/sync-i18n.js
// Timestamp: ${new Date().toISOString()}\n\n`

function ensureDir(filePath) {
  const dir = path.dirname(filePath)
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true })
  }
}

function syncUniappData() {
  if (!fs.existsSync(SHARED_SOURCE)) {
    console.error(`[FAIL] Source not found: ${SHARED_SOURCE}`)
    process.exit(1)
  }

  let content = fs.readFileSync(SHARED_SOURCE, 'utf-8')

  content = banner + content

  let changed = false
  if (fs.existsSync(UNIAPP_TARGET)) {
    const existing = fs.readFileSync(UNIAPP_TARGET, 'utf-8')
    changed = existing !== content
  } else {
    changed = true
  }

  if (changed) {
    ensureDir(UNIAPP_TARGET)
    fs.writeFileSync(UNIAPP_TARGET, content, 'utf-8')
  }

  console.log(`[${changed ? 'OK' : 'SKIP'}] uniapp-data.js → front/uniapp/utils/uniapp-data.js`)
  return changed
}

function syncJsonFiles() {
  if (!fs.existsSync(SHARED_I18N)) {
    console.error(`[FAIL] Shared i18n dir not found: ${SHARED_I18N}`)
    process.exit(1)
  }

  if (!fs.existsSync(UNIAPP_STATIC_I18N)) {
    fs.mkdirSync(UNIAPP_STATIC_I18N, { recursive: true })
  }

  const sharedFiles = fs.readdirSync(SHARED_I18N).filter(f => f.endsWith('.json'))
  let syncCount = 0

  for (const file of sharedFiles) {
    const srcPath = path.join(SHARED_I18N, file)
    const destPath = path.join(UNIAPP_STATIC_I18N, file)

    const srcContent = fs.readFileSync(srcPath, 'utf-8')
    let destContent = ''
    let exists = fs.existsSync(destPath)
    if (exists) destContent = fs.readFileSync(destPath, 'utf-8')

    if (!exists || srcContent !== destContent) {
      fs.writeFileSync(destPath, srcContent, 'utf-8')
      console.log(`[OK] ${file}`)
      syncCount++
    } else {
      console.log(`[SKIP] ${file} (up-to-date)`)
    }
  }

  const targetFiles = fs.readdirSync(UNIAPP_STATIC_I18N).filter(f => f.endsWith('.json'))
  const sharedSet = new Set(sharedFiles)

  for (const file of targetFiles) {
    if (!sharedSet.has(file)) {
      fs.unlinkSync(path.join(UNIAPP_STATIC_I18N, file))
      console.log(`[REMOVED] ${file} (not in shared)`)
    }
  }

  console.log(`\nJSON sync: ${syncCount} file(s) updated`)
}

function validateJsonIntegrity() {
  const sharedFiles = fs.readdirSync(SHARED_I18N).filter(f => f.endsWith('.json') && f !== 'keys.json')
  const targetFiles = fs.readdirSync(UNIAPP_STATIC_I18N).filter(f => f.endsWith('.json') && f !== 'keys.json')

  const errors = []
  for (const file of sharedFiles) {
    const srcPath = path.join(SHARED_I18N, file)
    const destPath = path.join(UNIAPP_STATIC_I18N, file)
    if (!fs.existsSync(destPath)) {
      errors.push(`Missing in uniapp/static: ${file}`)
      continue
    }
    try {
      JSON.parse(fs.readFileSync(srcPath, 'utf-8'))
      JSON.parse(fs.readFileSync(destPath, 'utf-8'))
    } catch (e) {
      errors.push(`Invalid JSON: ${file} — ${e.message}`)
    }
  }

  if (errors.length > 0) {
    console.log('\n[WARN] Validation errors:')
    errors.forEach(e => console.log(`  - ${e}`))
  } else {
    console.log('\n[OK] All JSON files valid')
  }

  return errors.length === 0
}

function printUsage() {
  console.log(`
i18n Sync Tool

Usage:
  node scripts/sync-i18n.js           Sync all (data + JSON)
  node scripts/sync-i18n.js data       Sync uniapp-data.js only
  node scripts/sync-i18n.js json       Sync JSON files only
  node scripts/sync-i18n.js validate   Validate JSON integrity
`)
}

function main() {
  const mode = process.argv[2] || 'all'

  switch (mode) {
    case 'data':
      syncUniappData()
      break
    case 'json':
      syncJsonFiles()
      break
    case 'validate':
      validateJsonIntegrity()
      break
    case 'all':
    default:
      console.log('=== i18n Sync Tool ===\n')
      syncUniappData()
      console.log('')
      syncJsonFiles()
      console.log('')
      validateJsonIntegrity()
      console.log('\n=== Done ===')
  }
}

if (process.argv.includes('--help') || process.argv.includes('-h')) {
  printUsage()
} else {
  main()
}
