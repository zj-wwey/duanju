<template>
  <div class="feature-card">
    <div class="feature-icon" v-html="icon"></div>
    <h3 class="feature-title">{{ title }}</h3>
    <p class="feature-desc">{{ description }}</p>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, required: true }
})
</script>

<style scoped>
.feature-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 36px 28px;
  text-align: center;
  transition: transform 0.3s, border-color 0.3s, box-shadow 0.3s;
  position: relative;
  overflow: hidden;
}
.feature-card::before {
  content: '';
  position: absolute;
  top: -40px; left: 50%;
  transform: translateX(-50%);
  width: 120px; height: 120px;
  background: radial-gradient(circle, rgba(212, 175, 104, 0.12), transparent 70%);
  pointer-events: none;
}
.feature-card:hover {
  transform: translateY(-4px);
  border-color: var(--gold-muted);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
}
.feature-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(247, 198, 106, 0.18), rgba(200, 109, 38, 0.10));
  border: 1px solid rgba(212, 175, 104, 0.25);
  border-radius: 16px;
  color: var(--gold);
  transition: transform 0.3s, box-shadow 0.3s;
}
.feature-card:hover .feature-icon {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(247, 198, 106, 0.25);
}
.feature-icon :deep(svg) {
  width: 30px;
  height: 30px;
}
.feature-title {
  font-size: 19px;
  margin-bottom: 10px;
  color: var(--text-main);
  font-weight: 700;
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
.feature-desc {
  color: var(--text-soft);
  font-size: 14.5px;
  line-height: 1.75;
  font-family: 'Noto Sans SC', 'Inter', sans-serif;
}
</style>
