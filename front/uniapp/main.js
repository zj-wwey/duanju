import Vue from 'vue'
import App from './App'

Vue.config.productionTip = false

App.mpType = 'app'

import uView from "uview-ui";
Vue.use(uView);

import api from './utils/api.js'
Vue.prototype.$api = api

import AppTabBar from './components/app-tab-bar/app-tab-bar.vue'
Vue.component('AppTabBar', AppTabBar)

import { getTextDirection, getLocale } from './utils/i18n-loader.js'

function applyDocumentDir() {
	try {
		const locale = getLocale()
		const dir = getTextDirection(locale)
		if (typeof document !== 'undefined') {
			document.documentElement.dir = dir
			document.documentElement.lang = locale || 'zh-CN'
		}
	} catch (e) {
		// ignore during SSR
	}
}

applyDocumentDir()

Vue.mixin({
	computed: {
		isRtl() {
			try {
				const locale = uni.getStorageSync('locale') || ''
				const base = locale.split('-')[0]
				return ['ar', 'he', 'fa', 'ur', 'ku', 'ps', 'syc', 'dv'].indexOf(base) !== -1
			} catch (e) {
				return false
			}
		}
	},
	watch: {
		isRtl(newVal) {
			const dir = newVal ? 'rtl' : 'ltr'
			if (typeof document !== 'undefined') {
				document.documentElement.dir = dir
			}
		}
	}
})

const app = new Vue({
    ...App
})


app.$mount()
