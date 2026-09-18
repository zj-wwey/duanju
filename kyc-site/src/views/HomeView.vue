<template>
  <div class="home">
    <!-- Hero -->
    <section class="hero">
      <div class="hero-glow"></div>
      <div class="container hero-content">
        <p class="kicker">{{ t('brand.kicker') }}</p>
        <h1 class="hero-title">{{ t('home.heroTitle1') }}<br /><span class="accent">{{ t('home.heroTitle2') }}</span></h1>
        <p class="hero-subtitle">
          {{ t('home.heroSubtitle') }}
        </p>
        <div class="hero-cta">
          <router-link to="/pricing" class="btn btn-gold btn-lg">{{ t('home.viewPlans') }}</router-link>
          <button class="btn btn-ghost btn-lg" @click="scrollToFeatured">{{ t('home.browseNow') }}</button>
        </div>
        <div class="hero-stats">
          <div class="stat"><strong>500+</strong><span>{{ t('home.stats.works') }}</span></div>
          <div class="stat"><strong>190+</strong><span>{{ t('home.stats.countries') }}</span></div>
          <div class="stat"><strong>200万+</strong><span>{{ t('home.stats.audience') }}</span></div>
        </div>
      </div>
    </section>

    <!-- 服务模式 -->
    <section class="section" id="models">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('home.modelsKicker') }}</p>
          <h2 class="section-title">{{ t('home.modelsTitle') }}</h2>
          <p class="section-subtitle">{{ t('home.modelsSubtitle') }}</p>
        </div>
        <div class="grid-2 model-cards">
          <div class="model-card model-vip clickable" @click="scrollToPricing">
            <div class="model-badge">{{ t('home.vipBadge') }}</div>
            <h3>{{ t('home.vipTitle') }}</h3>
            <p>{{ t('home.vipDesc') }}</p>
            <ul>
              <li v-for="(f, i) in t('home.vipFeatures')" :key="i">{{ f }}</li>
            </ul>
            <span class="model-cta">{{ t('home.vipCta') }}</span>
          </div>
          <div class="model-card model-credit clickable" @click="scrollToPricing">
            <div class="model-badge">{{ t('home.creditBadge') }}</div>
            <h3>{{ t('home.creditTitle') }}</h3>
            <p>{{ t('home.creditDesc') }}</p>
            <ul>
              <li v-for="(f, i) in t('home.creditFeatures')" :key="i">{{ f }}</li>
            </ul>
            <span class="model-cta">{{ t('home.creditCta') }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 精选剧集 -->
    <section class="section featured" id="featured">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('home.featuredKicker') }}</p>
          <h2 class="section-title">{{ t('home.featuredTitle') }}</h2>
          <p class="section-subtitle">{{ t('home.featuredSubtitle') }}</p>
        </div>
        <div class="drama-grid">
          <article
            v-for="drama in dramas"
            :key="drama.title"
            class="drama-card clickable"
            @click="openDrama(drama)"
          >
            <div
              class="drama-cover"
              :style="drama.cover ? {} : { background: drama.gradient }"
            >
              <img v-if="drama.cover" :src="drama.cover" :alt="drama.title" class="drama-cover-img" loading="lazy" />
              <div class="drama-overlay"></div>
              <div class="drama-badges">
                <span class="drama-badge genre">{{ drama.genre }}</span>
                <span v-if="drama.isAi" class="drama-badge ai">{{ t('home.aiBadge') }}</span>
                <span v-if="drama.isVip" class="drama-badge vip">VIP</span>
              </div>
              <span class="drama-play" aria-hidden="true">&#9654;</span>
              <div class="drama-title-area">
                <h4>{{ drama.title }}</h4>
                <span class="drama-meta">{{ t('home.episodes', drama.episodes) }} · {{ drama.rating }}⭐</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- 特色 -->
    <section class="section features">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('home.whyKicker') }}</p>
          <h2 class="section-title">{{ t('home.whyTitle') }}</h2>
        </div>
        <div class="grid-3">
          <FeatureSection
            v-for="(feature, i) in features"
            :key="i"
            :icon="feature.icon"
            :title="feature.title"
            :description="feature.description"
          />
        </div>
      </div>
    </section>

    <!-- 业务规模 -->
    <section class="section scale">
      <div class="container">
        <div class="section-head">
          <p class="kicker">{{ t('home.scaleKicker') }}</p>
          <h2 class="section-title">{{ t('home.scaleTitle') }}</h2>
          <p class="section-subtitle">{{ t('home.scaleSubtitle') }}</p>
        </div>
        <div class="scale-grid">
          <div class="scale-item"><strong>500+</strong><span>{{ t('home.stats.works') }}</span></div>
          <div class="scale-item"><strong>{{ t('home.scaleItems.dailyUpdate') }}</strong><span>{{ t('home.scaleItems.aiNewWorks') }}</span></div>
          <div class="scale-item"><strong>190+</strong><span>{{ t('home.stats.countries') }}</span></div>
          <div class="scale-item"><strong>99.9%</strong><span>{{ t('home.scaleItems.serviceUptime') }}</span></div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta-section">
      <div class="container cta-inner">
        <h2>{{ t('home.ctaTitle') }}</h2>
        <p>{{ t('home.ctaSubtitle') }}</p>
        <router-link to="/pricing" class="btn btn-gold btn-lg">{{ t('home.viewPricing') }}</router-link>
      </div>
    </section>

    <!-- 剧集详情弹窗 -->
    <transition name="modal">
      <div v-if="selectedDrama" class="modal-overlay" @click.self="selectedDrama = null">
        <div class="modal-card">
          <button class="modal-close" @click="selectedDrama = null" :aria-label="t('nav.closeAria')">&times;</button>
          <div
            class="modal-cover"
            :style="selectedDrama.cover ? {} : { background: selectedDrama.gradient }"
          >
            <img v-if="selectedDrama.cover" :src="selectedDrama.cover" :alt="selectedDrama.title" class="modal-cover-img" />
            <span class="modal-genre">{{ selectedDrama.genre }}</span>
          </div>
          <div class="modal-body">
            <h2 class="modal-title">{{ selectedDrama.title }}</h2>
            <div class="modal-meta">
              <span>{{ t('home.modalEpisodes', selectedDrama.episodes) }}</span>
              <span>{{ selectedDrama.rating }}⭐</span>
              <span>{{ selectedDrama.region }}</span>
              <span v-if="selectedDrama.isAi" class="ai-badge">{{ t('home.aiBadge') }}</span>
              <span v-if="selectedDrama.isVip" class="vip-badge">VIP</span>
            </div>
            <p class="modal-desc">{{ selectedDrama.description }}</p>
            <div v-if="selectedDrama.isAi" class="modal-ai-info">
              <span class="ai-info-label">{{ t('home.aiStudioLabel') }}：{{ selectedDrama.aiStudio || t('brand.companyEnName') }}</span>
              <span class="ai-info-label">{{ t('home.aiModelLabel') }}：{{ selectedDrama.aiModel || 'StarBrush / StarVoice' }}</span>
            </div>
            <div class="modal-actions">
              <button class="btn btn-gold btn-lg" @click="goPricing">{{ t('home.watchNow') }}</button>
              <button class="btn btn-ghost" @click="selectedDrama = null">{{ t('home.keepBrowsing') }}</button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import FeatureSection from '../components/FeatureSection.vue'
import { useI18n } from '../i18n'
import { featureIcons } from '../assets/feature-icons.js'

const { t } = useI18n()
const router = useRouter()
const selectedDrama = ref(null)
const dramas = ref([])
const loading = ref(true)
const loadError = ref(false)

// 响应式地跟随语言切换
const fallbackDramas = computed(() => t('home.fallbackDramas'))
const features = computed(() => t('home.features').map(f => ({
  ...f,
  icon: featureIcons[f.iconKey] || ''
})))

// API 加载剧集数据，失败时使用备用数据保证页面可用
async function loadDramas() {
  try {
    const res = await fetch('/api/dramas?featured=true&limit=6')
    const json = await res.json()
    if (res.ok && json.success && Array.isArray(json.data) && json.data.length > 0) {
      dramas.value = json.data
    } else {
      throw new Error('empty data')
    }
  } catch {
    loadError.value = true
    dramas.value = fallbackDramas.value
  } finally {
    loading.value = false
  }
}

function openDrama(drama) {
  selectedDrama.value = drama
  document.body.style.overflow = 'hidden'
}
function closeModal() {
  selectedDrama.value = null
  document.body.style.overflow = ''
}
function goPricing() {
  closeModal()
  router.push('/pricing')
}
function scrollToFeatured() {
  document.getElementById('featured')?.scrollIntoView({ behavior: 'smooth' })
}
function scrollToPricing() {
  router.push('/pricing')
}

onMounted(loadDramas)
</script>

<style scoped>
.hero { position: relative; padding: 100px 0 80px; overflow: hidden; }
.hero-glow { position: absolute; top: -20%; left: 50%; transform: translateX(-50%); width: 800px; height: 800px; background: radial-gradient(circle, rgba(200, 109, 38, 0.18), transparent 60%); pointer-events: none; }
.hero-content { position: relative; text-align: center; max-width: 820px; }
.hero-title { font-size: clamp(34px, 6vw, 60px); line-height: 1.15; font-weight: 900; letter-spacing: -0.03em; margin-bottom: 24px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.hero-title .accent { background: linear-gradient(135deg, var(--gold), var(--primary)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.hero-subtitle { font-size: 19px; color: var(--text-soft); max-width: 620px; margin: 0 auto 36px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.hero-cta { display: flex; gap: 16px; justify-content: center; flex-wrap: wrap; margin-bottom: 56px; }
.hero-stats { display: flex; justify-content: center; gap: 48px; flex-wrap: wrap; }
.stat strong { font-size: 32px; font-weight: 900; color: var(--gold); display: block; }
.stat span { font-size: 14px; color: var(--text-muted); }

.section-head { text-align: center; margin-bottom: 56px; }
.section-head .section-subtitle { margin: 12px auto 0; }

.model-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); padding: 40px 32px; position: relative; transition: transform 0.3s, border-color 0.3s; }
.model-card.clickable { cursor: pointer; }
.model-card.clickable:hover { transform: translateY(-6px); border-color: var(--gold-muted); }
.model-vip { border-color: var(--gold-muted); }
.model-badge { display: inline-block; font-size: 12px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; padding: 5px 14px; border-radius: 20px; margin-bottom: 20px; }
.model-vip .model-badge { background: var(--gold-muted); color: var(--gold); }
.model-credit .model-badge { background: rgba(200, 109, 38, 0.15); color: var(--primary); }
.model-card h3 { font-size: 24px; margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.model-card p { color: var(--text-soft); font-size: 15px; margin-bottom: 20px; line-height: 1.7; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.model-card ul { list-style: none; }
.model-card li { color: var(--text-soft); font-size: 14px; padding: 8px 0; border-bottom: 1px solid var(--border); font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.model-card li::before { content: '\2713'; color: var(--gold); margin-right: 10px; font-weight: 700; }
.model-cta { display: inline-block; margin-top: 20px; color: var(--gold); font-weight: 600; font-size: 14px; }

.drama-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.drama-card { background: var(--bg-card); border-radius: 14px; overflow: hidden; border: 1px solid var(--border); transition: transform 0.3s, box-shadow 0.3s; }
.drama-card.clickable { cursor: pointer; }
.drama-card.clickable:hover { transform: translateY(-6px); box-shadow: 0 16px 40px rgba(0, 0, 0, 0.5); }
.drama-cover { aspect-ratio: 3 / 4; position: relative; overflow: hidden; border-radius: 14px 14px 0 0; }
.drama-cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 0; transition: transform 0.5s ease; }
.drama-card:hover .drama-cover-img { transform: scale(1.05); }

.drama-overlay { position: absolute; inset: 0; z-index: 1; background: linear-gradient(to bottom, rgba(0,0,0,0) 0%, rgba(0,0,0,0.15) 35%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0.92) 100%); }

.drama-badges { position: absolute; top: 12px; left: 12px; display: flex; gap: 6px; flex-wrap: wrap; z-index: 2; }
.drama-badge { font-size: 10px; font-weight: 700; letter-spacing: 0.05em; padding: 3px 10px; border-radius: 20px; line-height: 1.4; }
.drama-badge.genre { background: rgba(0, 0, 0, 0.55); color: #fff; backdrop-filter: blur(4px); }
.drama-badge.ai { background: linear-gradient(135deg, var(--primary), var(--primary-dark, #8a4d1a)); color: #fff; }
.drama-badge.vip { background: linear-gradient(135deg, var(--gold), #b8923e); color: #1a1408; }

.drama-play { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 52px; height: 52px; background: rgba(255, 255, 255, 0.18); backdrop-filter: blur(8px); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 14px; opacity: 0; transition: opacity 0.25s, transform 0.25s; border: 1px solid rgba(255, 255, 255, 0.25); z-index: 2; }
.drama-card:hover .drama-play { opacity: 1; transform: translate(-50%, -50%) scale(1.05); }

.drama-title-area { position: absolute; bottom: 0; left: 0; right: 0; padding: 16px 16px 18px; z-index: 2; }
.drama-title-area h4 { font-size: 17px; font-weight: 800; color: #fff; margin: 0 0 4px 0; font-family: 'Noto Sans SC', 'Inter', sans-serif; line-height: 1.25; text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4); display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 1; overflow: hidden; }
.drama-title-area .drama-meta { font-size: 12px; color: rgba(255, 255, 255, 0.75); font-weight: 500; letter-spacing: 0.02em; }

.scale-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 24px; }
.scale-item { text-align: center; background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); padding: 36px 16px; }
.scale-item strong { display: block; font-size: 36px; font-weight: 900; color: var(--gold); margin-bottom: 6px; }
.scale-item span { font-size: 14px; color: var(--text-muted); }

.cta-section { padding: 80px 0; text-align: center; }
.cta-inner h2 { font-size: clamp(26px, 4vw, 36px); margin-bottom: 12px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.cta-inner p { color: var(--text-soft); margin-bottom: 28px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.8); backdrop-filter: blur(8px); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 24px; }
.modal-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius); max-width: 520px; width: 100%; overflow: hidden; position: relative; }
.modal-close { position: absolute; top: 12px; right: 12px; width: 36px; height: 36px; border-radius: 50%; background: rgba(0, 0, 0, 0.5); border: none; color: #fff; font-size: 22px; cursor: pointer; z-index: 2; transition: background 0.2s; }
.modal-close:hover { background: rgba(0, 0, 0, 0.8); }
.modal-cover { height: 220px; position: relative; overflow: hidden; }
.modal-cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.modal-genre { position: absolute; top: 16px; left: 16px; font-size: 12px; font-weight: 700; color: #fff; background: rgba(0, 0, 0, 0.5); padding: 5px 14px; border-radius: 18px; backdrop-filter: blur(4px); }
.modal-body { padding: 24px 28px 28px; }
.modal-title { font-size: 24px; margin-bottom: 8px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.modal-meta { display: flex; gap: 12px; font-size: 14px; color: var(--gold); margin-bottom: 14px; flex-wrap: wrap; }
.ai-badge, .vip-badge { font-size: 11px; font-weight: 700; letter-spacing: 0.06em; padding: 2px 10px; border-radius: 12px; }
.ai-badge { background: rgba(200, 109, 38, 0.15); color: var(--primary); }
.vip-badge { background: var(--gold-muted); color: var(--gold); }
.modal-ai-info { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 20px; }
.ai-info-label { font-size: 12px; color: var(--text-muted); background: var(--bg-soft); padding: 4px 10px; border-radius: 6px; }
.modal-desc { color: var(--text-soft); font-size: 15px; line-height: 1.8; margin-bottom: 24px; font-family: 'Noto Sans SC', 'Inter', sans-serif; }
.modal-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.modal-actions .btn { flex: 1; min-width: 140px; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.25s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-active .modal-card, .modal-leave-active .modal-card { transition: transform 0.25s; }
.modal-enter-from .modal-card, .modal-leave-to .modal-card { transform: scale(0.92); }

@media (max-width: 860px) {
  .drama-grid { grid-template-columns: repeat(2, 1fr); }
  .scale-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 560px) {
  .drama-grid { grid-template-columns: 1fr; }
}
</style>
