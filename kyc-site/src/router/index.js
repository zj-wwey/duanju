import { createRouter, createWebHistory } from 'vue-router'
import { useI18n } from '../i18n'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { titleKey: 'router.home' }
  },
  {
    path: '/pricing',
    name: 'pricing',
    component: () => import('../views/PricingView.vue'),
    meta: { titleKey: 'router.pricing' }
  },
  {
    path: '/vip',
    name: 'vip',
    component: () => import('../views/VipView.vue'),
    meta: { titleKey: 'router.vip' }
  },
  {
    path: '/credits',
    name: 'credits',
    component: () => import('../views/CreditsView.vue'),
    meta: { titleKey: 'router.credits' }
  },
  {
    path: '/contact',
    name: 'contact',
    component: () => import('../views/ContactView.vue'),
    meta: { titleKey: 'router.contact' }
  },
  {
    path: '/privacy',
    name: 'privacy',
    component: () => import('../views/PrivacyView.vue'),
    meta: { titleKey: 'router.privacy' }
  },
  {
    path: '/refund',
    name: 'refund',
    component: () => import('../views/RefundView.vue'),
    meta: { titleKey: 'router.refund' }
  },
  {
    path: '/terms',
    name: 'terms',
    component: () => import('../views/TermsView.vue'),
    meta: { titleKey: 'router.terms' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.afterEach((to) => {
  const { t } = useI18n()
  const brand = t('brand.fullName')
  const pageTitle = to.meta.titleKey ? t(to.meta.titleKey) : ''
  document.title = pageTitle ? `${pageTitle} - ${brand}` : brand
})

export default router
