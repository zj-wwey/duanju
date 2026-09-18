// PM2 生产配置（非 Docker 方案）
// 用法：pm2 start ecosystem.config.js --env production
module.exports = {
  apps: [{
    name: 'marastel-kyc',
    script: './server/index.js',
    cwd: __dirname,
    exec_mode: 'fork',
    instances: 1,
    env_production: {
      NODE_ENV: 'production',
      PORT: 3000
    },
    env_development: {
      NODE_ENV: 'development',
      PORT: 3000
    },
    autorestart: true,
    watch: false,
    max_memory_restart: '1G',
    error_file: './logs/error.log',
    out_file: './logs/out.log',
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z'
  }]
};