import { createRouter, createWebHistory } from 'vue-router'
import StreamLayout from '../layouts/StreamLayout.vue'
import HomePage from '../pages/HomePage.vue'
import CategoryPage from '../pages/CategoryPage.vue'
import DetailPage from '../pages/DetailPage.vue'
import PlayerPage from '../pages/PlayerPage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import VipPage from '../pages/VipPage.vue'
import RechargePage from '../pages/RechargePage.vue'
import MembershipPage from '../pages/MembershipPage.vue'
import ShopPage from '../pages/ShopPage.vue'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: StreamLayout,
      children: [
        { path: '', name: 'home', component: HomePage },
        { path: 'category', name: 'category', component: CategoryPage },
        { path: 'profile', name: 'profile', component: ProfilePage },
        { path: 'vip', name: 'vip', component: VipPage },
        { path: 'recharge', name: 'recharge', component: RechargePage },
        { path: 'membership', name: 'membership', component: MembershipPage },
        { path: 'shop', name: 'shop', component: ShopPage },
        { path: 'drama/:id', name: 'detail', component: DetailPage, props: true },
        { path: 'play/:dramaId/:episodeId?', name: 'player', component: PlayerPage, props: true }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

const AUTH_REQUIRED_ROUTES = ['profile', 'vip', 'recharge', 'membership', 'shop', 'player']

router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('adminToken') || sessionStorage.getItem('userToken')
  if (AUTH_REQUIRED_ROUTES.includes(to.name) && !token) {
    next({ name: 'home' })
  } else {
    next()
  }
})
