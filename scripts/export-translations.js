#!/usr/bin/env node
/**
 * Translation Export Tool
 * Exports i18n keys from streamI18n.js to structured format for translation management tools.
 *
 * Usage:
 *   node scripts/export-translations.js                    Export all keys (JSON)
 *   node scripts/export-translations.js --namespace admin   Export admin namespace only
 *   node scripts/export-translations.js --format csv       Export as CSV
 *   node scripts/export-translations.js --output ./translations.json
 */

const fs = require('fs')
const path = require('path')

const REPO_ROOT = path.resolve(__dirname, '..')
const STREAM_I18N_PATH = path.join(REPO_ROOT, 'front', 'admin', 'src', 'locales', 'streamI18n.js')

function parseArgs() {
  const args = process.argv.slice(2)
  const config = {
    namespace: null,
    format: 'json',
    output: null,
    help: false
  }

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--namespace':
      case '-n':
        config.namespace = args[++i]
        break
      case '--format':
      case '-f':
        config.format = args[++i]
        break
      case '--output':
      case '-o':
        config.output = args[++i]
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
Translation Export Tool

Usage:
  node scripts/export-translations.js [options]

Options:
  --namespace, -n <ns>   Export specific namespace (e.g., admin, common)
  --format, -f <fmt>     Output format: json (default) or csv
  --output, -o <path>    Output file path (default: ./translations-<ns>.<fmt>)
  --help, -h             Show this help message

Examples:
  node scripts/export-translations.js -n admin -f json
  node scripts/export-translations.js -n admin -f csv -o ./admin-translations.csv
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

function extractObjectVariable(content, varName) {
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
  // Find the start of the expression (skip whitespace and spread)
  let exprStart = startPos
  while (exprStart < pos && (content[exprStart] === ' ' || content[exprStart] === '\n' || content[exprStart] === '\r')) {
    exprStart++
  }
  
  // Get the complete expression from exprStart to end of balanced block
  const block = extractBalancedBlock(content, pos)
  if (!block) return null
  
  // Return the full expression text (including spread operator if present)
  return content.substring(exprStart, pos) + block
}

// Parse JS object literal string into a plain object
function parseObjectLiteral(objStr) {
  // Remove outer braces
  let str = objStr.trim()
  if (str.startsWith('{')) str = str.slice(1)
  if (str.endsWith('}')) str = str.slice(0, -1)
  
  // Use Function to evaluate
  try {
    const result = new Function(`return (${objStr})`)()
    return result
  } catch (e) {
    console.error('Failed to parse object literal:', e.message)
    console.error('Object string:', objStr.substring(0, 100) + '...')
    return null
  }
}

function extractKeysFromObject(obj, prefix = '') {
  const result = {}
  for (const [key, value] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      Object.assign(result, extractKeysFromObject(value, fullKey))
    } else {
      result[fullKey] = value !== null && value !== undefined ? String(value) : ''
    }
  }
  return result
}

function parseStreamI18n() {
  const content = fs.readFileSync(STREAM_I18N_PATH, 'utf-8')

  // Extract zhCN and enUS (now returns full expression strings)
  const zhCNExpr = extractObjectVariable(content, 'zhCN')
  const enUSExpr = extractObjectVariable(content, 'enUS')

  if (!zhCNExpr) {
    console.error('Failed to extract zhCN object')
    process.exit(1)
  }
  if (!enUSExpr) {
    console.error('Failed to extract enUS object')
    process.exit(1)
  }

  // Parse zhCN first (needed for enUS spread operator)
  let zhCNObj
  try {
    zhCNObj = new Function(`return (${zhCNExpr})`)()
  } catch (e) {
    console.error('Failed to parse zhCN:', e.message)
    console.error('Expression starts with:', zhCNExpr.substring(0, 100))
    process.exit(1)
  }

  // Parse enUS with zhCN in context (for spread operator support)
  let enUSObj
  try {
    const fn = new Function('zhCN', `return (${enUSExpr})`)
    enUSObj = fn(zhCNObj)
  } catch (e) {
    console.error('Failed to parse enUS:', e.message)
    console.error('Expression starts with:', enUSExpr.substring(0, 100))
    process.exit(1)
  }

  return { zhCN: zhCNObj, enUS: enUSObj }
}

function exportTranslations(namespace) {
  const { zhCN, enUS } = parseStreamI18n()

  const result = {
    source: 'streamI18n.js',
    timestamp: new Date().toISOString(),
    namespaces: {}
  }

  const zhCNKeys = extractKeysFromObject(zhCN)
  const enUSKeys = extractKeysFromObject(enUS)

  // Group by namespace
  const allKeys = new Set([...Object.keys(zhCNKeys), ...Object.keys(enUSKeys)])

  for (const key of allKeys) {
    const parts = key.split('.')
    const ns = parts[0]

    if (namespace && ns !== namespace) continue

    if (!result.namespaces[ns]) {
      result.namespaces[ns] = {}
    }

    result.namespaces[ns][key] = {
      zhCN: zhCNKeys[key] || '',
      enUS: enUSKeys[key] || '',
      status: enUSKeys[key] ? 'translated' : 'pending',
      note: ''
    }
  }

  return result
}

function exportToCSV(data) {
  const rows = [['key', 'zh-CN', 'en-US', 'status', 'note']]

  for (const [ns, keys] of Object.entries(data.namespaces)) {
    for (const [key, value] of Object.entries(keys)) {
      rows.push([
        key,
        value.zhCN || '',
        value.enUS || '',
        value.status || '',
        value.note || ''
      ])
    }
  }

  // Sort by key
  const header = rows[0]
  const sorted = rows.slice(1).sort((a, b) => a[0].localeCompare(b[0]))
  return [header, ...sorted]
}

function escapeCSV(value) {
  if (typeof value !== 'string') return String(value)
  if (value.includes('"') || value.includes(',') || value.includes('\n')) {
    return '"' + value.replace(/"/g, '""') + '"'
  }
  return value
}

function toCSVString(rows) {
  return rows.map(row => row.map(escapeCSV).join(',')).join('\n')
}

function main() {
  const config = parseArgs()

  if (config.help) {
    printUsage()
    return
  }

  console.log('=== Translation Export Tool ===\n')

  // Export from streamI18n.js
  const data = exportTranslations(config.namespace)

  // Determine output path
  const nsSuffix = config.namespace || 'all'
  const ext = config.format === 'csv' ? 'csv' : 'json'
  const outputPath = config.output || path.join(REPO_ROOT, `translations-${nsSuffix}.${ext}`)

  // Generate output
  let outputContent
  if (config.format === 'csv') {
    const csvRows = exportToCSV(data)
    outputContent = '\uFEFF' + toCSVString(csvRows) // BOM for Excel
  } else {
    outputContent = JSON.stringify(data, null, 2)
  }

  // Write output
  fs.writeFileSync(outputPath, outputContent, 'utf-8')

  // Print summary
  const totalKeys = Object.values(data.namespaces)
    .reduce((sum, ns) => sum + Object.keys(ns).length, 0)
  const namespaceCount = Object.keys(data.namespaces).length

  console.log(`[OK] Exported ${totalKeys} keys across ${namespaceCount} namespace(s)`)
  console.log(`[OK] Output: ${outputPath}`)

  if (config.namespace) {
    console.log(`\nFiltered by namespace: ${config.namespace}`)
  }

  // Print namespaces summary
  console.log('\nNamespaces:')
  for (const [ns, keys] of Object.entries(data.namespaces)) {
    const translated = Object.values(keys).filter(v => v.status === 'translated').length
    console.log(`  ${ns}: ${Object.keys(keys).length} keys (${translated} translated)`)
  }

  console.log('\n=== Done ===')
}

main()
