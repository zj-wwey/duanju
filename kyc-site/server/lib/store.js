// JSON 文件存储工具：低频表单数据持久化，简单可靠
import { promises as fs } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DATA_DIR = join(__dirname, '..', 'data')

// 需要持久化的文件（volume 挂载到 data/submissions）
// dramas.json 等静态内容留在 data/ 根目录随镜像更新
const PERSISTENT_FILES = ['messages.json', 'subscribers.json']

function resolvePath(fileName) {
  if (PERSISTENT_FILES.includes(fileName)) {
    return join(DATA_DIR, 'submissions', fileName)
  }
  return join(DATA_DIR, fileName)
}

async function ensureDir() {
  await fs.mkdir(join(DATA_DIR, 'submissions'), { recursive: true })
}

// 读取某个 JSON 文件中的记录数组，文件不存在时返回空数组
export async function readRecords(fileName) {
  await ensureDir()
  const filePath = resolvePath(fileName)
  try {
    const content = await fs.readFile(filePath, 'utf-8')
    const parsed = JSON.parse(content)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

// 追加一条记录到 JSON 数组文件，自动补充 id 与 createdAt
export async function appendRecord(fileName, record) {
  const records = await readRecords(fileName)
  const entry = {
    ...record,
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: new Date().toISOString()
  }
  records.push(entry)
  const filePath = resolvePath(fileName)
  await fs.writeFile(filePath, JSON.stringify(records, null, 2), 'utf-8')
  return entry
}
