/**
 * 视频下载工具
 * 解决 Android 12 (API 31) + IjkMediaPlayer 的 HTTP 明文限制
 *
 * 策略：
 * 1. 将 /uploads/ URL 转换为 /api/public/videos/download?path=xxx
 * 2. 主方案: plus.downloader 原生下载（绕开 JS Blob 限制）
 * 3. 备方案: uni.request + plus.io 二进制字符串写入
 * 4. 返回 App 私有目录下的本地视频路径给 <video :src> 使用
 */

import config from './config.js'

var LOG_TAG = '[videoDownloader]'
var pathCache = {}

function isAppPlatform() {
  return typeof plus !== 'undefined'
}

/**
 * 修复 URL 格式问题 (http// -> http://)
 */
function fixUrlScheme(url) {
  var fixed = url.replace(/^(https?):?\/\//i, function(m, proto) {
    return proto + '://'
  })
  if (fixed !== url) {
    console.log(LOG_TAG, 'fixed URL scheme:', url, '->', fixed)
  }
  return fixed
}

/**
 * 将 /uploads/ URL 转换为 API 代理下载 URL
 */
function toApiDownloadUrl(url) {
  if (!url) return url
  console.log(LOG_TAG, 'toApiDownloadUrl input:', url)
  url = fixUrlScheme(url)

  // 已是 API 路径
  if (url.indexOf('/api/') > -1) {
    console.log(LOG_TAG, 'already API path')
    return url
  }

  // 相对路径 /uploads/...
  if (url.indexOf('/uploads/') === 0 || url.indexOf('/upload/') === 0) {
    var base = config.API_BASE_URL || ''
    var origin = base.indexOf('/api') > -1 ? base.slice(0, base.indexOf('/api')) : base.replace(/\/$/, '')
    var path = url.replace(/^\/(upload|uploads)\//, '')
    var apiUrl = origin + '/api/public/videos/download?path=' + encodeURIComponent(path)
    console.log(LOG_TAG, 'relative rewrite:', url, '->', apiUrl)
    return apiUrl
  }

  // 绝对 URL - 提取 /uploads/ 后的路径
  var pathMatch = url.match(/\/(upload|uploads)\/(.+)$/)
  if (pathMatch) {
    var base2 = config.API_BASE_URL || ''
    var origin2 = base2.indexOf('/api') > -1 ? base2.slice(0, base2.indexOf('/api')) : base2.replace(/\/$/, '')
    var apiUrl2 = origin2 + '/api/public/videos/download?path=' + encodeURIComponent(pathMatch[2])
    console.log(LOG_TAG, 'absolute rewrite:', url, '->', apiUrl2)
    return apiUrl2
  }

  // 协议相对 URL //host/...
  if (url.indexOf('//') === 0) {
    return toApiDownloadUrl('http:' + url)
  }

  console.log(LOG_TAG, 'no rewrite')
  return url
}

/**
 * 下载远程视频到本地
 */
export function downloadVideo(url, onProgress, timeoutMs) {
  if (pathCache[url]) {
    console.log(LOG_TAG, 'cache hit:', pathCache[url])
    return Promise.resolve(pathCache[url])
  }

  timeoutMs = timeoutMs || 600

  return new Promise(function (resolve, reject) {
    console.log(LOG_TAG, 'start:', url)
    if (!url) { reject(new Error('url is empty')); return }

    // 非 App 平台：直接返回原始 URL
    if (!isAppPlatform()) {
      console.log(LOG_TAG, 'non-app, return original')
      resolve(url)
      return
    }

    // 转换为 API 代理下载 URL
    var downloadUrl = toApiDownloadUrl(url)
    console.log(LOG_TAG, 'final URL:', downloadUrl)

    // 优先使用 plus.downloader 原生下载
    tryNativeDownload(downloadUrl, onProgress, timeoutMs)
      .then(function (localPath) {
        pathCache[url] = localPath
        resolve(localPath)
      })
      .catch(function (nativeErr) {
        console.error(LOG_TAG, 'native download failed:', nativeErr.message)
        // 备方案: uni.request + plus.io 二进制字符串写入
        fallbackDownload(downloadUrl, onProgress, timeoutMs)
          .then(function (localPath) {
            pathCache[url] = localPath
            resolve(localPath)
          })
          .catch(function (fallbackErr) {
            reject(new Error('下载失败: ' + (fallbackErr.message || nativeErr.message)))
          })
      })
  })
}

/**
 * 主方案: 使用 plus.downloader 原生 API 下载
 * 优势: 原生下载, 不经过 JS Blob, 不占内存
 */
function tryNativeDownload(url, onProgress, timeoutMs) {
  return new Promise(function (resolve, reject) {
    var fileName = 'video_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6) + '.mp4'
    var localPath = '_doc/' + fileName

    console.log(LOG_TAG, 'native download to:', localPath)

    try {
      var dtask = plus.downloader.createDownload(url, {
        filename: localPath,
        timeout: timeoutMs,
        retry: 2,
        retryInterval: 3
      }, function (d, status) {
        console.log(LOG_TAG, 'native download status:', status, 'file:', d.filename)
        if (status === 200 || status === 304) {
          var localURL = plus.io.convertLocalFileSystemURL(d.filename)
          console.log(LOG_TAG, 'native download success:', localURL)
          resolve(localURL)
        } else {
          reject(new Error('HTTP ' + status))
        }
      }, function (error) {
        console.error(LOG_TAG, 'native download error:', error.message)
        reject(new Error(error.message || '下载失败'))
      })

      // 进度
      dtask.addEventListener('statechanged', function (d, state) {
        if (state === 3 && typeof onProgress === 'function') {
          if (d.totalSize > 0) {
            var pct = Math.round((d.downloadedSize / d.totalSize) * 100)
            try { onProgress(pct) } catch (_) {}
          }
        }
      })

      dtask.start()
    } catch (err) {
      reject(new Error('plus.downloader 不可用: ' + err.message))
    }
  })
}

/**
 * 备方案: uni.request + plus.io 二进制字符串写入
 * 将 ArrayBuffer 转为 binary string, 分块写入文件
 */
function fallbackDownload(url, onProgress, timeoutMs) {
  return new Promise(function (resolve, reject) {
    console.log(LOG_TAG, 'fallback download via uni.request')

    uni.request({
      url: url,
      method: 'GET',
      timeout: timeoutMs,
      responseType: 'arraybuffer',
      success: function (res) {
        console.log(LOG_TAG, 'fallback status:', res.statusCode, 'size:', res.data ? res.data.byteLength : 0)
        if (res.statusCode !== 200 && res.statusCode !== 206) {
          reject(new Error('HTTP ' + res.statusCode))
          return
        }
        if (!res.data) {
          reject(new Error('空响应数据'))
          return
        }

        var fileName = 'video_fb_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6) + '.mp4'
        writeBinaryString(res.data, fileName, onProgress)
          .then(resolve)
          .catch(reject)
      },
      fail: function (err) {
        reject(new Error('请求失败: ' + (err.errMsg || JSON.stringify(err))))
      }
    })
  })
}

/**
 * 使用 plus.io 将 ArrayBuffer 写入文件（通过二进制字符串）
 * 原理: ArrayBuffer -> Uint8Array -> binary string -> FileWriter.write(binary string)
 */
function writeBinaryString(arrayBuffer, fileName, onProgress) {
  return new Promise(function (resolve, reject) {
    console.log(LOG_TAG, 'writeBinaryString:', arrayBuffer.byteLength, 'bytes')

    var uint8 = new Uint8Array(arrayBuffer)
    var totalSize = uint8.length
    var CHUNK_SIZE = 0x4000  // 16KB per chunk for binary string

    plus.io.requestFileSystem(plus.io.PRIVATE_DOC, function (fs) {
      fs.root.getFile(fileName, { create: true }, function (fileEntry) {
        var writer = fileEntry.createWriter()
        var offset = 0
        var writeCount = 0
        var totalChunks = Math.ceil(totalSize / CHUNK_SIZE)

        function doWrite() {
          if (offset >= totalSize) {
            // 全部写入完成
            console.log(LOG_TAG, 'writeBinaryString done, total:', totalSize)
            resolve(plus.io.convertLocalFileSystemURL(fileEntry.toLocalURL()))
            return
          }
          var end = Math.min(offset + CHUNK_SIZE, totalSize)
          var chunk = String.fromCharCode.apply(null, uint8.subarray(offset, end))
          offset = end
          writeCount++
          if (typeof onProgress === 'function') {
            try { onProgress(Math.round((writeCount / totalChunks) * 100)) } catch (_) {}
          }
          try {
            writer.write(chunk)
          } catch (err) {
            reject(new Error('写入失败: ' + err.message))
          }
        }

        writer.onerror = function (e) {
          console.error(LOG_TAG, 'writer error:', e)
          reject(new Error('文件写入失败'))
        }

        writer.onwrite = function () {
          doWrite()  // 继续写下一块
        }

        // 开始写第一块
        doWrite()
      }, reject)
    }, reject)
  })
}

export default { downloadVideo: downloadVideo }
