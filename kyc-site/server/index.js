// Express 入口：挂载 /api 路由，生产环境同时托管 dist 静态文件 + SPA fallback
import 'dotenv/config'
import express from 'express'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'
import contactRouter from './routes/contact.js'
import subscribeRouter from './routes/subscribe.js'
import dramasRouter from './routes/dramas.js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const app = express()
const PORT = process.env.PORT || 3000

app.use(express.json({ limit: '100kb' }))

// 安全头：基础防护
app.use((req, res, next) => {
  res.set('X-Content-Type-Options', 'nosniff')
  res.set('X-Frame-Options', 'SAMEORIGIN')
  next()
})

// API 路由
app.use('/api/contact', contactRouter)
app.use('/api/subscribe', subscribeRouter)
app.use('/api/dramas', dramasRouter)
app.get('/api/health', (req, res) => res.json({ status: 'ok', time: new Date().toISOString() }))

// 生产环境：托管构建后的前端 + SPA history fallback
if (process.env.NODE_ENV === 'production') {
  const distDir = join(__dirname, '..', 'dist')
  app.use(express.static(distDir))
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api/')) return next()
    res.sendFile(join(distDir, 'index.html'))
  })
}

app.listen(PORT, () => {
  console.log(`[kyc-site] Server running on http://localhost:${PORT} (${process.env.NODE_ENV || 'development'})`)
})
