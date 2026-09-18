<template>
  <div class="pricing-card" :class="{ popular }">
    <div v-if="popular" class="popular-badge">Most Popular</div>
    <h3 class="plan-name">{{ name }}</h3>
    <div class="plan-price">
      <span class="currency">$</span>
      <span class="amount">{{ price }}</span>
      <span class="period">{{ period }}</span>
    </div>
    <p class="plan-desc">{{ description }}</p>
    <ul class="plan-features">
      <li v-for="feature in features" :key="feature">
        <span class="check" aria-hidden="true">&#10003;</span>
        {{ feature }}
      </li>
    </ul>
    <a href="/contact" class="btn" :class="popular ? 'btn-gold' : 'btn-ghost'">Choose Plan</a>
  </div>
</template>

<script setup>
defineProps({
  name: { type: String, required: true },
  price: { type: String, required: true },
  period: { type: String, default: '/mo' },
  description: { type: String, default: '' },
  features: { type: Array, default: () => [] },
  popular: { type: Boolean, default: false }
})
</script>

<style scoped>
.pricing-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 36px 28px;
  display: flex;
  flex-direction: column;
  position: relative;
  transition: transform 0.3s, border-color 0.3s;
}
.pricing-card:hover {
  transform: translateY(-6px);
}
.pricing-card.popular {
  border-color: var(--gold);
  box-shadow: 0 0 0 1px var(--gold-muted), 0 16px 48px rgba(212, 175, 104, 0.12);
}
.popular-badge {
  position: absolute;
  top: -13px;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, var(--gold), #c9a24f);
  color: #1a1612;
  font-size: 12px;
  font-weight: 700;
  padding: 5px 16px;
  border-radius: 20px;
  white-space: nowrap;
}
.plan-name {
  font-size: 20px;
  color: var(--text-main);
  margin-bottom: 12px;
}
.plan-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-bottom: 8px;
}
.currency {
  font-size: 22px;
  color: var(--gold);
  font-weight: 700;
}
.amount {
  font-size: 48px;
  font-weight: 900;
  color: var(--text-main);
  line-height: 1;
}
.period {
  font-size: 16px;
  color: var(--text-muted);
  margin-left: 4px;
}
.plan-desc {
  color: var(--text-soft);
  font-size: 14px;
  margin-bottom: 24px;
  min-height: 40px;
}
.plan-features {
  list-style: none;
  margin-bottom: 28px;
  flex: 1;
}
.plan-features li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: var(--text-soft);
  font-size: 14px;
  margin-bottom: 12px;
  line-height: 1.5;
}
.check {
  color: var(--gold);
  font-weight: 700;
  flex-shrink: 0;
}
.btn {
  width: 100%;
}
</style>
