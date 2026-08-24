const isDev = process.env.NODE_ENV !== 'production'

// 开发环境下根据运行平台选择 API 地址
// H5 浏览器在主机上，127.0.0.1 可直接访问
// App 端（模拟器/真机）127.0.0.1 指向设备自身，需通过特殊 IP 访问主机
//
// 配置优先级：
//   1. 环境变量 VITE_API_BASE_URL（如果设置）
//   2. 平台默认地址
//
// MuMu Android 12 特别说明：
//   Android 9+ 对媒体组件 (video/image) 强制 HTTP 明文限制，即使 usesCleartextTraffic=true
//   也可能被系统组件拦截。解决方案：使用 adb reverse 将 127.0.0.1 映射到宿主机，
//   因为 Android 始终允许 localhost 明文通信。
//
//   步骤:
//     1. 启动模拟器后执行: adb reverse tcp:8080 tcp:8080
//     2. App 中使用 http://127.0.0.1:8080/api 访问后端
//     3. 所有静态资源通过 127.0.0.1:8080/uploads/ 加载
//
//   如果 adb reverse 不可用，改用宿主机局域网 IP (如 192.168.x.x)
//   或使用 10.0.2.2 (标准 Android 模拟器，MuMu 可能不支持)

function resolveDevBaseUrl() {
  // 如果设置了环境变量，优先使用
  const envUrl = process.env.VITE_API_BASE_URL
  if (envUrl) {
    return envUrl
  }

  // #ifdef APP-PLUS
  // MuMu Android 12: 使用 127.0.0.1 + adb reverse 绕过 HTTP 明文限制
  // 标准模拟器可改用: http://10.0.2.2:8080/api
  // 真机可改用: http://192.168.x.x:8080/api  (宿主机局域网 IP)
  return 'http://127.0.0.1:8080/api'
  // #endif
  // #ifdef H5
  return 'http://127.0.0.1:8080/api'
  // #endif
  // #ifndef H5 || APP-PLUS
  return 'http://127.0.0.1:8080/api'
  // #endif
}

const config = {
  API_BASE_URL: isDev ? resolveDevBaseUrl() : '/api'
}

export default config
