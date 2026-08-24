#!/usr/bin/env node
/**
 * Translation Import Tool
 * Imports translated keys from exported files back into streamI18n.js.
 *
 * Usage:
 *   node scripts/import-translations.js --file translations-admin.json
 *   node scripts/import-translations.js --file translations-admin.csv --namespace admin
 */

const fs = require('fs')
const path = require('path')

const REPO_ROOT = path.resolve(__dirname, '..')
const STREAM_I18N_PATH = path.join(REPO_ROOT, 'front', 'admin', 'src', 'locales', 'streamI18n.js')

function parseArgs() {
  const args = process.argv.slice(2)
  const config = {
    file: null,
    namespace: null,
    dryRun: false,
    help: false
  }

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--file':
      case '-f':
        config.file = args[++i]
        break
      case '--namespace':
      case '-n':
        config.namespace = args[++i]
        break
      case '--dry-run':
      case '-d':
        config.dryRun = true
        break
      case '--help':
      case '-h':
        config.help = true
        break
    }
  }

  return config
}

function printUsage() {
  console.log(`
Translation Import Tool

Usage:
  node scripts/import-translations.js --file <path> [options]

Options:
  --file, -f <path>      Input file path (JSON or CSV)
  --namespace, -n <ns>   Import specific namespace only
  --dry-run, -d          Show changes without modifying files
  --help, -h             Show this help message

Examples:
  node scripts/import-translations.js -f translations-admin.json
  node scripts/import-translations.js -f translations-admin.csv -n admin -d
`)
}

// Extract balanced brace block starting from a position
function extractBalancedBlock(text, startPos) {
  let braceCount = 0
  let inString = false
  let stringChar = ''
  let escaped = false
  
  for (let i = startPos; i < text.length; i++) {
    const char = text[i]
    
    if (escaped) {
      escaped = false
      continue
    }
    
    if (char === '\\') {
      escaped = true
      continue
    }
    
    if (inString) {
      if (char === stringChar) {
        inString = false
      }
      continue
    }
    
    if (char === '"' || char === "'" || char === '`') {
      inString = true
      stringChar = char
      continue
    }
    
    if (char === '{') {
      braceCount++
    } else if (char === '}') {
      braceCount--
      if (braceCount === 0) {
        return text.substring(startPos, i + 1)
      }
    }
  }
  return null
}

function extractObjectExpression(content, varName) {
  const regex = new RegExp(`const\\s+${varName}\\s*=`, 'g')
  const match = regex.exec(content)
  if (!match) return null
  
  const startPos = match.index + match[0].length
  
  // Find the opening brace
  let pos = startPos
  while (pos < content.length && content[pos] !== '{') {
    pos++
  }
  
  if (pos >= content.length || content[pos] !== '{') {
    return null
  }
  
  // Extract the full expression including any spread operators before {
  let exprStart = startPos
  while (exprStart < pos && (content[exprStart] === ' ' || content[exprStart] === '\n' || content[exprStart] === '\r')) {
    exprStart++
  }
  
  const block = extractBalancedBlock(content, pos)
  if (!block) return null
  
  return {
    fullText: content.substring(exprStart, pos) + block,
    startIndex: match.index,
    endIndex: pos + block.length,
    assignmentStart: match.index,
    assignmentEnd: pos + block.length
  }
}

function parseJSON(filePath) {
  const content = fs.readFileSync(filePath, 'utf-8')
  return JSON.parse(content)
}

function parseCSV(filePath) {
  const content = fs.readFileSync(filePath, 'utf-8')
  const lines = content.split('\n').filter(line => line.trim())
  
  if (lines[0] && lines[0].startsWith('\uFEFF')) {
    lines[0] = lines[0].slice(1)
  }

  if (lines.length < 2) {
    console.error('CSV file must have at least a header and one data row')
    process.exit(1)
  }

  const headers = parseCSVLine(lines[0])
  const result = { namespaces: {} }

  for (let i = 1; i < lines.length; i++) {
    const values = parseCSVLine(lines[i])
    const row = {}
    headers.forEach((header, idx) => {
      row[header.trim()] = values[idx] || ''
    })

    const key = row.key
    if (!key) continue

    const parts = key.split('.')
    const ns = parts[0]

    if (!result.namespaces[ns]) {
      result.namespaces[ns] = {}
    }

    result.namespaces[ns][key] = {
      zhCN: row['zh-CN'] || '',
      enUS: row['en-US'] || '',
      status: row.status || 'translated',
      note: row.note || ''
    }
  }

  return result
}

function parseCSVLine(line) {
  const result = []
  let current = ''
  let inQuotes = false

  for (let i = 0; i < line.length; i++) {
    const char = line[i]
    if (char === '"') {
      inQuotes = !inQuotes
    } else if (char === ',' && !inQuotes) {
      result.push(current)
      current = ''
    } else {
      current += char
    }
  }
  result.push(current)

  return result.map(v => {
    if (v.startsWith('"') && v.endsWith('"')) {
      return v.slice(1, -1).replace(/""/g, '"')
    }
    return v
  })
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

function getNestedValue(obj, dotPath) {
  const parts = dotPath.split('.')
  let current = obj
  for (const part of parts) {
    if (current == null || typeof current !== 'object') return undefined
    current = current[part]
  }
  return current
}

function formatObjectLiteral(obj, indent = 0) {
  const spaces = '  '.repeat(indent)
  const childSpaces = '  '.repeat(indent + 1)
  
  if (obj === null || obj === undefined) return 'null'
  if (typeof obj !== 'object') return JSON.stringify(obj)
  if (Array.isArray(obj)) return JSON.stringify(obj)
  
  const entries = Object.entries(obj)
  if (entries.length === 0) return '{}'
  
  const lines = []
  for (const [key, value] of entries) {
    const formatted = formatObjectLiteral(value, indent + 1)
    lines.push(`${childSpaces}${key}: ${formatted}`)
  }
  
  return '{\n' + lines.join(',\n') + '\n' + spaces + '}'
}

function updateStreamI18n(importData, namespace, dryRun) {
  let content = fs.readFileSync(STREAM_I18N_PATH, 'utf-8')

  // Extract zhCN and enUS expressions
  const zhCNInfo = extractObjectExpression(content, 'zhCN')
  const enUSInfo = extractObjectExpression(content, 'enUS')

  if (!zhCNInfo || !enUSInfo) {
    console.error('Failed to parse streamI18n.js')
    process.exit(1)
  }

  // Parse current values
  let zhCNObj, enUSObj
  try {
    zhCNObj = new Function(`return (${zhCNInfo.fullText})`)()
    enUSObj = new Function('zhCN', `return (${enUSInfo.fullText})`)(zhCNObj)
  } catch (e) {
    console.error('Failed to parse translation objects:', e.message)
    process.exit(1)
  }

  // Track changes
  const changes = []

  for (const [ns, keys] of Object.entries(importData.namespaces)) {
    if (namespace && ns !== namespace) continue

    for (const [key, value] of Object.entries(keys)) {
      const oldZhCN = getNestedValue(zhCNObj, key)
      const oldEnUS = getNestedValue(enUSObj, key)

      const newZhCN = value.zhCN || ''
      const newEnUS = value.enUS || ''

      if (oldZhCN !== newZhCN || oldEnUS !== newEnUS) {
        changes.push({
          key,
          oldZhCN: oldZhCN || '(missing)',
          newZhCN: newZhCN || '(empty)',
          oldEnUS: oldEnUS || '(missing)',
          newEnUS: newEnUS || '(empty)'
        })

        setNestedValue(zhCNObj, key, newZhCN)
        setNestedValue(enUSObj, key, newEnUS)
      }
    }
  }

  if (changes.length === 0) {
    console.log('[OK] No changes needed')
    return
  }

  // Print changes summary
  console.log(`\n[INFO] ${changes.length} change(s) detected:\n`)
  for (const change of changes) {
    console.log(`  ${change.key}:`)
    console.log(`    zh-CN: "${change.oldZhCN}" → "${change.newZhCN}"`)
    console.log(`    en-US: "${change.oldEnUS}" → "${change.newEnUS}"`)
    console.log('')
  }

  if (dryRun) {
    console.log('[DRY RUN] No files were modified. Run without --dry-run to apply changes.')
    return
  }

  // Generate updated content
  const newZhCN = formatObjectLiteral(zhCNObj)
  const newENUSBase = formatObjectLiteral(enUSObj)
  
  // Rebuild enUS with spread operator for maintainability
  // Find the actual overrides (what differs from zhCN)
  const overrides = {}
  for (const key of Object.keys(enUSObj)) {
    if (enUSObj[key] !== zhCNObj[key]) {
      overrides[key] = enUSObj[key]
    }
  }
  
  const enUSOverridesStr = formatObjectLiteral(overrides)
  const newContent = content.substring(0, zhCNInfo.assignmentStart) +
    `const zhCN = ${newZhCN};\n\nconst enUS = {\n  ...zhCN,\n  ...${enUSOverridesStr}\n}` +
    content.substring(enUSInfo.assignmentEnd)

  fs.writeFileSync(STREAM_I18N_PATH, newContent, 'utf-8')
  console.log(`[OK] Updated ${STREAM_I18N_PATH}`)
  console.log(`[OK] ${changes.length} change(s) applied`)
}

function main() {
  const config = parseArgs()

  if (config.help) {
    printUsage()
    return
  }

  if (!config.file) {
    console.error('Error: --file option is required')
    printUsage()
    process.exit(1)
  }

  console.log('=== Translation Import Tool ===\n')

  const inputPath = path.resolve(REPO_ROOT, config.file)

  if (!fs.existsSync(inputPath)) {
    console.error(`File not found: ${inputPath}`)
    process.exit(1)
  }

  // Detect format
  const ext = path.extname(inputPath).toLowerCase()
  let importData

  try {
    if (ext === '.json') {
      importData = parseJSON(inputPath)
    } else if (ext === '.csv') {
      importData = parseCSV(inputPath)
    } else {
      console.error(`Unsupported format: ${ext}. Use .json or .csv`)
      process.exit(1)
    }
  } catch (e) {
    console.error(`Failed to parse input file: ${e.message}`)
    process.exit(1)
  }

  if (!importData.namespaces || Object.keys(importData.namespaces).length === 0) {
    console.error('Invalid import format: no namespaces found')
    process.exit(1)
  }

  // Summary
  const totalKeys = Object.values(importData.namespaces)
    .reduce((sum, ns) => sum + Object.keys(ns).length, 0)
  const namespaceCount = Object.keys(importData.namespaces).length
  console.log(`[INFO] Loaded ${totalKeys} keys from ${namespaceCount} namespace(s)`)

  if (config.namespace) {
    console.log(`[INFO] Filtering by namespace: ${config.namespace}`)
  }

  if (config.dryRun) {
    console.log('[DRY RUN] Running in dry-run mode (no files will be modified)\n')
  }

  // Import to streamI18n.js
  updateStreamI18n(importData, config.namespace, config.dryRun)

  console.log('\n=== Done ===')
}

main()
