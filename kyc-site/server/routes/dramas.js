// 剧集目录接口：返回全量剧集数据，支持 featured=true 筛选和分类筛选
import { Router } from 'express'
import { readRecords } from '../lib/store.js'

const router = Router()

// GET /api/dramas
// 查询参数：
//   featured=true     只返回精选剧集
//   genre=爱情       按类型筛选
//   region=华语      按地区筛选
//   limit=6          限制返回数量
router.get('/', async (req, res) => {
  try {
    let dramas = await readRecords('dramas.json')

    if (req.query.featured === 'true') {
      dramas = dramas.filter((d) => d.featured)
    }
    if (req.query.genre) {
      dramas = dramas.filter((d) => d.genre === req.query.genre)
    }
    if (req.query.region) {
      dramas = dramas.filter((d) => d.region === req.query.region)
    }
    if (req.query.limit) {
      const n = Math.min(parseInt(req.query.limit, 10) || 0, 50)
      if (n > 0) dramas = dramas.slice(0, n)
    }

    res.json({ success: true, data: dramas, total: dramas.length })
  } catch (e) {
    console.error('[dramas] error', e)
    res.status(500).json({ success: false, message: 'Server error' })
  }
})

// GET /api/dramas/:id —— 单部剧集详情
router.get('/:id', async (req, res) => {
  try {
    const dramas = await readRecords('dramas.json')
    const drama = dramas.find((d) => d.id === req.params.id)
    if (!drama) {
      return res.status(404).json({ success: false, message: 'Drama not found' })
    }
    res.json({ success: true, data: drama })
  } catch (e) {
    console.error('[dramas] error', e)
    res.status(500).json({ success: false, message: 'Server error' })
  }
})

export default router
