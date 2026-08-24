# 短剧 UniApp 用户端

这是改造后的用户端工程，使用 UniApp + uView。

## 页面

- `pages/index/index.vue`：竖屏播放器、分类、选集、收藏、积分解锁、播放进度记录。
- `pages/login/login.vue`：注册登录。
- `pages/mine/mine.vue`：我的、签到、积分流水。
- `pages/mine/favorites.vue`：收藏/追剧。
- `pages/mine/history.vue`：观看历史。

## 接口配置

修改：

```text
utils/config.js
```

开发默认：

```js
API_BASE_URL: 'http://127.0.0.1:8080/api'
```

## 多端说明

H5 / Android / iOS / 小程序统一使用 `pages/index/index.vue`，不再保留原示例中的 `index.nvue`、`index_IOS.nvue` 分支。
