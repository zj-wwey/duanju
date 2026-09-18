// 邮件订阅接口：邮箱校验 → 去重 → 写入文件
import { Router } from 'express'
import { readRecords, appendRecord } from '../lib/store.js'
import { subscribeLimiter } from '../middleware/rateLimit.js'

const router = Router()

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

router.post('/', subscribeLimiter, async (req, res) => {
  try {
    const email = String(req.body.email || '').trim().toLowerCase()
    if (!EMAIL_RE.test(email)) {
      return res.status(400).json({ success: false, message: 'A valid email address is required.' })
    }

    const existing = await readRecords('subscribers.json')
    if (existing.some((s) => s.email === email)) {
      return res.json({ success: true, message: 'You are already subscribed. Thank you!' })
    }

    await appendRecord('subscribers.json', { email })
    res.json({ success: true, message: 'Subscription successful. Welcome aboard!' })
  } catch (e) {
    console.error('[subscribe] error', e)
    res.status(500).json({ success: false, message: 'Server error. Please try again later.' })
  }
})

export default router
