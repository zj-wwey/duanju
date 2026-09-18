// 邮件发送工具：基于 nodemailer，未配置 SMTP 时安全跳过
import nodemailer from 'nodemailer'

let transporter = null

function getTransporter() {
  if (transporter) return transporter
  const { SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS } = process.env
  if (!SMTP_HOST || !SMTP_USER) return null
  const port = Number(SMTP_PORT) || 587
  transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port,
    secure: port === 465,
    auth: { user: SMTP_USER, pass: SMTP_PASS }
  })
  return transporter
}

// 发送邮件；未配置 SMTP 时返回 false，不抛错
export async function sendMail({ to, subject, text, html }) {
  const t = getTransporter()
  if (!t) {
    console.log('[mailer] SMTP 未配置，跳过邮件发送')
    return false
  }
  const from = process.env.SMTP_FROM || process.env.SMTP_USER
  await t.sendMail({ from, to, subject, text, html })
  return true
}
