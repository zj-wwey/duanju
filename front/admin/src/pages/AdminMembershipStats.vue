<template>
  <section class="admin-page-section">
    <div class="toolbar">
      <div>
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
      </div>
      <div class="toolbar-actions">
        <el-button type="primary" @click="loadAll">{{ adminT('refresh') }}</el-button>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <div class="metric-grid">
      <div class="metric-card">
        <span>{{ formatNumber(field(stats, 'total')) }}</span>
        <label>会员总数</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(pointStats ? field(pointStats, 'total_earned') : 0) }}</span>
        <label>总获取积分</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(pointStats ? field(pointStats, 'total_spent') : 0) }}</span>
        <label>总消费积分</label>
      </div>
      <div class="metric-card">
        <span>{{ formatNumber(pointStats ? field(pointStats, 'total_records') : 0) }}</span>
        <label>积分流水数</label>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="charts-grid">
      <!-- 等级分布饼图 -->
      <div class="chart-panel">
        <div class="panel-title"><h3>会员等级分布</h3></div>
        <div class="chart-body">
          <svg viewBox="0 0 200 200" class="donut-chart" v-if="levelData.length">
            <circle v-for="(seg, i) in pieSegments" :key="i"
              cx="100" cy="100" r="80"
              fill="none" stroke-width="30"
              :stroke="seg.color"
              :stroke-dasharray="seg.dashArray"
              :stroke-dashoffset="seg.dashOffset"
              transform="rotate(-90 100 100)"
              style="transition: stroke-dasharray 0.6s, stroke-dashoffset 0.6s" />
            <text x="100" y="96" text-anchor="middle" font-size="22" font-weight="700" fill="#EAE6E1">{{ stats ? stats.total : 0 }}</text>
            <text x="100" y="116" text-anchor="middle" font-size="11" fill="#6F6559">总会员</text>
          </svg>
          <div class="chart-legend">
            <div v-for="item in levelData" :key="item.name" class="legend-item">
              <span class="legend-dot" :style="{ background: item.color }"></span>
              <span class="legend-label">{{ item.name }}</span>
              <span class="legend-value">{{ item.value }}</span>
              <span class="legend-pct">{{ item.percent }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 等级来源分布 -->
      <div class="chart-panel">
        <div class="panel-title"><h3>等级来源分布</h3></div>
        <div class="chart-body">
          <div class="bar-chart" v-if="sourceData.length">
            <div v-for="item in sourceData" :key="item.name" class="bar-item">
              <div class="bar-label">{{ item.name }}</div>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: item.percent + '%', background: item.color }"></div>
              </div>
              <div class="bar-value">{{ item.value }}</div>
            </div>
          </div>
          <el-empty v-else :description="'暂无数据'" />
        </div>
      </div>

      <!-- 积分趋势（简化版） -->
      <div class="chart-panel wide">
        <div class="panel-title"><h3>积分概览</h3></div>
        <div class="stat-cards-row">
          <div class="stat-card earn">
            <span class="stat-icon">↑</span>
            <div>
              <strong>{{ formatNumber(pointStats ? field(pointStats, 'total_earned') : 0) }}</strong>
              <small>总获取积分</small>
            </div>
          </div>
          <div class="stat-card spend">
            <span class="stat-icon">↓</span>
            <div>
              <strong>{{ formatNumber(pointStats ? field(pointStats, 'total_spent') : 0) }}</strong>
              <small>总消费积分</small>
            </div>
          </div>
          <div class="stat-card net">
            <span class="stat-icon">=</span>
            <div>
              <strong>{{ formatNumber(netPoints) }}</strong>
              <small>净增积分</small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'

const props = defineProps({
  adminT: { type: Function, required: true },
  field: { type: Function, required: true },
  formatNumber: { type: Function, required: true },
  title: { type: String, default: '会员统计' },
  eyebrow: { type: String, default: '数据中心' }
})

const stats = ref(null)
const pointStats = ref(null)

const levelColors = {
  'NONE': '#909399',
  'SILVER': '#c0c4cc',
  'GOLD': '#e6a23c',
  'DIAMOND': '#409eff'
}

const levelNames = {
  'NONE': '普通用户',
  'SILVER': '银卡会员',
  'GOLD': '金卡会员',
  'DIAMOND': '钻石会员'
}

const sourceColors = {
  'NONE': '#909399',
  'SPEND': '#67c23a',
  'PURCHASE': '#409eff'
}

const sourceNames = {
  'NONE': '无来源',
  'SPEND': '消费升级',
  'PURCHASE': '购买套餐'
}

const levelData = computed(() => {
  if (!stats.value) return []
  const levels = props.field(stats.value, 'levels') || {}
  const total = props.field(stats.value, 'total') || 1
  return Object.entries(levels).map(([key, val]) => ({
    name: levelNames[key] || key,
    value: val,
    percent: total > 0 ? Math.round((val / total) * 100) : 0,
    color: levelColors[key] || '#909399'
  }))
})

const sourceData = computed(() => {
  if (!stats.value) return []
  const sources = props.field(stats.value, 'sources') || {}
  const maxVal = Math.max(1, ...Object.values(sources).map(v => Number(v) || 0))
  return Object.entries(sources).map(([key, val]) => ({
    name: sourceNames[key] || key,
    value: val,
    percent: Math.round((val / maxVal) * 100),
    color: sourceColors[key] || '#909399'
  }))
})

const netPoints = computed(() => {
  if (!pointStats.value) return 0
  return (props.field(pointStats.value, 'total_earned') || 0) - (props.field(pointStats.value, 'total_spent') || 0)
})

// 饼图分段计算
const pieSegments = computed(() => {
  const data = levelData.value
  if (!data.length) return []
  const total = data.reduce((s, d) => s + d.value, 0) || 1
  const circumference = 2 * Math.PI * 80
  let offset = 0
  return data.map(d => {
    const ratio = d.value / total
    const length = ratio * circumference
    const seg = {
      color: d.color,
      dashArray: `${length} ${circumference - length}`,
      dashOffset: -offset
    }
    offset += length
    return seg
  })
})

async function loadAll() {
  try {
    const [mStats, pStats] = await Promise.all([
      api.adminMembershipStats(),
      api.adminPointRecordStats({})
    ])
    stats.value = mStats
    pointStats.value = pStats
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.metric-card {
  background: var(--bg-card);
  border: 1px solid var(--border-gold);
  border-radius: 10px;
  padding: 18px;
  text-align: center;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.3);
}
.metric-card span {
  font-size: 32px;
  font-weight: 850;
  color: var(--text-main);
}
.metric-card label {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.chart-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-gold);
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.3);
}
.chart-panel.wide {
  grid-column: 1 / -1;
}
.panel-title {
  margin-bottom: 16px;
}
.panel-title h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  margin: 0;
}

.chart-body {
  display: flex;
  align-items: center;
  gap: 24px;
}
.donut-chart {
  width: 160px;
  height: 160px;
  flex-shrink: 0;
}

.chart-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.legend-label {
  min-width: 70px;
  color: var(--text-soft);
}
.legend-value {
  font-weight: 600;
  color: var(--text-main);
  min-width: 40px;
}
.legend-pct {
  color: var(--text-muted);
  font-size: 12px;
}

.bar-chart {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bar-label {
  width: 70px;
  font-size: 13px;
  color: var(--text-soft);
  flex-shrink: 0;
}
.bar-track {
  flex: 1;
  height: 20px;
  background: var(--bg-soft);
  border-radius: 10px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 10px;
  transition: width 0.6s;
  min-width: 4px;
}
.bar-value {
  width: 40px;
  font-weight: 600;
  font-size: 13px;
  color: var(--text-main);
  text-align: right;
}

.stat-cards-row {
  display: flex;
  gap: 20px;
}
.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border-gold);
  border-radius: 10px;
  padding: 16px;
}
.stat-card.earn { border-left: 3px solid #67c23a; }
.stat-card.spend { border-left: 3px solid #f56c6c; }
.stat-card.net { border-left: 3px solid var(--primary); }
.stat-icon {
  font-size: 24px;
  font-weight: 700;
}
.stat-card.earn .stat-icon { color: #67c23a; }
.stat-card.spend .stat-icon { color: #f56c6c; }
.stat-card.net .stat-icon { color: var(--gold); }
.stat-card strong {
  display: block;
  font-size: 20px;
  color: var(--text-main);
}
.stat-card small {
  font-size: 12px;
  color: var(--text-muted);
}
</style>