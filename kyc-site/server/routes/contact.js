// 联系表单接口：校验 → 限流 → 写入文件 → 可选邮件通知
import { Router } from 'express'
import { appendRecord } from '../lib/store.js'
import { sendMail } from '../lib/mailer.js'
import { contactLimiter } from '../middleware/rateLimit.js'

const router = Router()

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function sanitize(str) {
  return String(str || '').trim().slice(0, 2000)
}

router.post('/', contactLimiter, async (req, res) => {
  try {
    const name = sanitize(req.body.name)
    const email = sanitize(req.body.email).toLowerCase()
    const subject = sanitize(req.body.subject)
    const message = sanitize(req.body.message)

    if (name.length < 2) {
      return res.status(400).json({ success: false, message: 'Name is required (min 2 characters).' })
    }
    if (!EMAIL_RE.test(email)) {
      return res.status(400).json({ success: false, message: 'A valid email address is required.' })
    }
    if (subject.length < 2) {
      return res.status(400).json({ success: false, message: 'Subject is required.' })
    }
    if (message.length < 10) {
      return res.status(400).json({ success: false, message: 'Message must be at least 10 characters.' })
    }

    const record = { name, email, subject, message }
    await appendRecord('messages.json', record)

    const notify = process.env.CONTACT_NOTIFY_EMAIL
    if (notify) {
      // 邮件发送失败不影响主流程
      await sendMail({
        to: notify,
        subject: `[Contact] ${subject}`,
        text: `From: ${name} <${email}>\n\n${message}`
      }).catch((e) => console.error('[contact] mail failed', e.message))
    }

    res.json({
      success: true,
      message: 'Thank you for reaching out. We will reply within 1-2 business days.'
    })
  } catch (e) {
    console.error('[contact] error', e)
    res.status(500).json({ success: false, message: 'Server error. Please try again later.' })
  }
})

export default router
