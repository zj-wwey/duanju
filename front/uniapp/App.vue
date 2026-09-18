<script>
	export default {
		onLaunch: function() {
			
		},
		onShow: function() {
		},
		onHide: function() {
		}
	}
</script>

<style lang="scss">
	/* 注意要写在第一行，同时给style标签加入lang="scss"属性 */
	@import "uview-ui/index.scss";
	@import 'components/colorui/main.css';
	@import 'components/colorui/icon.css';
	@import 'static/css/rtl.css';
/* 全局字体：中文优先 */
page {
  font-family: -apple-system, BlinkMacSystemFont,
               "PingFang SC", "HarmonyOS Sans",
               "Noto Sans SC", "Source Han Sans SC",
               "Microsoft YaHei", "Hiragino Sans GB",
               "Segoe UI", sans-serif;
}
/* #ifdef H5 */
/* H5 端整体放大 4%，字体和图标同步放大，不影响底部原生 tabbar */
page {
  zoom: 1.04;
}
/* #endif */
</style>
