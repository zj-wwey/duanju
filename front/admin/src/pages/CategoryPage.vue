<template>
  <section class="stream-page category-page">
    <section class="category-switchboard">
      <div>
        <p class="stream-kicker">{{ t('category.kicker') }}</p>
        <h1>{{ t('category.title') }}</h1>
      </div>
      <div class="content-type-selector" :aria-label="t('category.contentType')">
        <button
          v-for="item in contentTypeOptions"
          :key="item.key"
          :class="{ active: contentType === item.key }"
          type="button"
          @click="switchContentType(item.key)"
        >
          {{ optionLabel(item, 'contentType') }}
        </button>
      </div>
    </section>

    <p v-if="keyword" class="stream-kicker">{{ t('nav.search') }} · {{ keyword }}</p>

    <section class="category-filter-grid">
      <article v-for="group in filterGroups" :key="group.key" class="filter-cluster">
        <header>
          <strong>{{ groupLabel(group) }}</strong>
          <span>{{ selectedLabel(group) }}</span>
        </header>
        <div>
          <button
            v-for="item in group.options"
            :key="item.key"
            :class="{ active: isSelected(group.key, item.key) }"
            type="button"
            @click="toggleFilter(group.key, item.key)"
          >
            {{ optionLabel(item, group.key) }}
          </button>
        </div>
      </article>
    </section>

    <div v-if="store.loading.value" class="skeleton-grid">
      <div v-for="item in 12" :key="item" class="skeleton-card"></div>
    </div>
    <section v-else class="category-results">
      <DramaCard v-for="item in filteredDramas" :key="item.id" :drama="item" @select="openDetail" />
      <div v-if="!filteredDramas.length" class="stream-empty">{{ t('common.noMatches') }}</div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStreamI18n } from '../locales/streamI18n.js'
import { ElMessage } from 'element-plus'
import DramaCard from '../components/drama/DramaCard.vue'
import { useDramaStore } from '../user/store.js'
import { api } from '../api.js'

const route = useRoute()
const router = useRouter()
const { t, locale } = useStreamI18n()
const store = useDramaStore()
const contentType = ref(String(route.query.contentType || 'real'))
const selectedFilters = reactive({})
const categoryFilterGroups = ref([])

const keyword = computed(() => String(route.query.keyword || '').trim())
const filteredDramas = computed(() => store.dramas.value)
const contentTypeOptions = computed(() => categoryFilterGroups.value.find(group => group.key === 'contentType')?.options || [])
const filterGroups = computed(() => categoryFilterGroups.value.filter(group => group.key !== 'contentType'))

onMounted(loadPage)
watch(() => [route.query.keyword, route.query.contentType], () => {
  contentType.value = String(route.query.contentType || contentTypeOptions.value[0]?.key || 'real')
  loadPage()
})
watch(locale, () => {
  categoryFilterGroups.value = []
  loadPage()
})

function isSelected(key, value) {
  return (selectedFilters[key] || ['all']).includes(value)
}

async function toggleFilter(key, value) {
  selectedFilters[key] = [value]
  await loadPage()
}

async function switchContentType(next) {
  contentType.value = next
  await router.replace({
    name: 'category',
    query: {
      ...route.query,
      contentType: next,
      keyword: keyword.value || undefined
    }
  })
  await loadPage()
}

function filterParams() {
  const params = Object.entries(selectedFilters).reduce((result, [key, values]) => {
    const value = values[0]
    if (value && value !== 'all') {
      result[key] = value
    }
    return result
  }, {})
  params.contentType = contentType.value
  if (keyword.value) {
    params.keyword = keyword.value
  }
  return params
}

async function loadPage() {
  try {
    await loadCategoryFilters()
  } catch (err) {
    console.error('[CategoryPage] loadCategoryFilters failed', err)
    ElMessage.error(`${t('category.filterLoadFailed') || '分类加载失败'}: ${err.message}`)
    return
  }
  try {
    await store.loadDramas(filterParams())
  } catch (err) {
    console.error('[CategoryPage] loadDramas failed', err)
    ElMessage.error(`${t('category.dramaLoadFailed') || '短剧加载失败'}: ${err.message}`)
  }
}

async function loadCategoryFilters() {
  if (categoryFilterGroups.value.length) {
    return
  }
  categoryFilterGroups.value = await api.publicCategoryFilters() || []
  const firstContentType = contentTypeOptions.value[0]?.key
  if (!route.query.contentType && firstContentType) {
    contentType.value = firstContentType
  }
  for (const group of filterGroups.value) {
    if (!selectedFilters[group.key]) {
      selectedFilters[group.key] = [group.options.some(item => item.key === 'all') ? 'all' : group.options[0]?.key]
    }
  }
}

function selectedLabel(group) {
  const selected = (selectedFilters[group.key] || ['all'])[0]
  const option = group.options.find(item => item.key === selected)
  return option ? optionLabel(option, group.key) : t('category.options.all')
}

const CONTENT_TYPE_KEY_MAP = { ai: 'aiDrama', real: 'realDrama' }

function groupLabel(group) {
  const key = 'category.' + group.key
  const translated = t(key)
  return translated !== key ? translated : (group.label || group.key)
}

function optionLabel(item, groupKey) {
  let i18nKey
  if (groupKey === 'contentType') {
    i18nKey = 'category.' + (CONTENT_TYPE_KEY_MAP[item.key] || item.key)
  } else {
    i18nKey = 'category.options.' + item.key
  }
  const translated = t(i18nKey)
  if (translated !== i18nKey) return translated
  return item.labelLocalized || item.label || item.key
}

function openDetail(drama) {
  router.push({ name: 'detail', params: { id: drama.id } })
}
</script>
