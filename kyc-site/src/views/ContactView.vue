<template>
  <div class="contact-page">
    <section class="page-hero">
      <div class="container">
        <p class="kicker">{{ t('contact.kicker') }}</p>
        <h1 class="page-title">{{ t('contact.title') }}</h1>
        <p class="page-subtitle">
          {{ t('contact.subtitle') }}
        </p>
      </div>
    </section>

    <section class="section contact-section">
      <div class="container contact-grid">
        <!-- Contact info -->
        <div class="contact-info">
          <h2>{{ t('contact.directTitle') }}</h2>
          <div class="info-item">
            <div class="info-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
            </div>
            <div>
              <h4>{{ t('contact.emailLabel') }}</h4>
              <a :href="`mailto:${t('company.email')}`">{{ t('company.email') }}</a>
            </div>
          </div>
          <div class="info-item">
            <div class="info-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.5 19.5 0 01-6-6 19.79 19.79 0 01-3.07-8.67A2 2 0 014.11 2h3a2 2 0 012 1.72c.127.96.36 1.9.7 2.81a2 2 0 01-.45 2.11L8.09 9.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45c.91.34 1.85.573 2.81.7A2 2 0 0122 16.92z"/></svg>
            </div>
            <div>
              <h4>{{ t('contact.phoneLabel') }}</h4>
              <p>{{ t('company.phone') }}</p>
            </div>
          </div>
          <div class="info-item">
            <div class="info-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/><circle cx="12" cy="10" r="3"/></svg>
            </div>
            <div>
              <h4>{{ t('contact.addressLabel') }}</h4>
              <p>{{ t('company.name') }}<br />{{ t('company.address') }}</p>
            </div>
          </div>
          <div class="info-item">
            <div class="info-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
            </div>
            <div>
              <h4>{{ t('contact.hoursLabel') }}</h4>
              <p style="white-space: pre-line;">{{ t('contact.hoursDetail') }}</p>
            </div>
          </div>
        </div>

        <!-- Contact form -->
        <div class="contact-form-wrap">
          <form v-if="status !== 'success'" class="contact-form" @submit.prevent="submit">
            <h2>{{ t('contact.formTitle') }}</h2>

            <div class="form-row">
              <label class="form-field">
                <span class="label">{{ t('contact.nameLabel') }}</span>
                <input
                  v-model="form.name"
                  type="text"
                  :placeholder="t('contact.namePlaceholder')"
                  :disabled="loading"
                  required
                  minlength="2"
                />
              </label>
              <label class="form-field">
                <span class="label">{{ t('contact.emailField') }}</span>
                <input
                  v-model="form.email"
                  type="email"
                  :placeholder="t('contact.emailPlaceholder')"
                  :disabled="loading"
                  required
                />
              </label>
            </div>

            <label class="form-field">
              <span class="label">{{ t('contact.subjectLabel') }}</span>
              <input
                v-model="form.subject"
                type="text"
                :placeholder="t('contact.subjectPlaceholder')"
                :disabled="loading"
                required
                minlength="2"
              />
            </label>

            <label class="form-field">
              <span class="label">{{ t('contact.messageLabel') }}</span>
              <textarea
                v-model="form.message"
                rows="6"
                :placeholder="t('contact.messagePlaceholder')"
                :disabled="loading"
                required
                minlength="10"
              ></textarea>
            </label>

            <button type="submit" class="btn btn-gold btn-lg submit-btn" :disabled="loading">
              <span v-if="loading" class="spinner"></span>
              {{ loading ? t('contact.sending') : t('contact.sendButton') }}
            </button>

            <p v-if="error" class="form-error">{{ error }}</p>
          </form>

          <div v-else class="form-success">
            <div class="success-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6L9 17l-5-5"/></svg>
            </div>
            <h2>{{ t('contact.successTitle') }}</h2>
            <p>{{ successMessage }}</p>
            <button class="btn btn-ghost" @click="resetForm">{{ t('contact.resend') }}</button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useI18n } from '../i18n'

const { t } = useI18n()

const form = reactive({
  name: '',
  email: '',
  subject: '',
  message: ''
})

const status = ref('idle')
const loading = ref(false)
const error = ref('')
const successMessage = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  status.value = 'loading'

  try {
    const res = await fetch('/api/contact', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...form })
    })
    const data = await res.json()

    if (res.ok && data.success) {
      status.value = 'success'
      successMessage.value = data.message
    } else {
      status.value = 'error'
      error.value = data.message || t('contact.errorDefault')
    }
  } catch {
    status.value = 'error'
    error.value = t('contact.errorNetwork')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.email = ''
  form.subject = ''
  form.message = ''
  status.value = 'idle'
  error.value = ''
}
</script>

<style scoped>
.page-hero {
  padding: 64px 0 24px;
  text-align: center;
}
.page-title {
  font-size: clamp(30px, 5vw, 44px);
  font-weight: 900;
  letter-spacing: -0.02em;
  margin-bottom: 16px;
}
.page-subtitle {
  font-size: 18px;
  color: var(--text-soft);
  max-width: 520px;
  margin: 0 auto;
}
.contact-section {
  padding-top: 40px;
}
.contact-grid {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 48px;
}

.contact-info h2 {
  font-size: 24px;
  margin-bottom: 28px;
}
.info-item {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
}
.info-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gold-muted);
  border-radius: 12px;
  color: var(--gold);
}
.info-icon svg {
  width: 22px;
  height: 22px;
}
.info-item h4 {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.info-item p,
.info-item a {
  font-size: 15px;
  color: var(--text-soft);
  line-height: 1.6;
}
.info-item a:hover {
  color: var(--gold);
}

.contact-form-wrap {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 36px 32px;
}
.contact-form h2,
.form-success h2 {
  font-size: 24px;
  margin-bottom: 24px;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.form-field {
  display: block;
  margin-bottom: 18px;
}
.label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-soft);
  margin-bottom: 8px;
}
.form-field input,
.form-field textarea {
  width: 100%;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 13px 16px;
  color: var(--text-main);
  font-size: 15px;
  font-family: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.form-field input::placeholder,
.form-field textarea::placeholder {
  color: var(--text-muted);
}
.form-field input:focus,
.form-field textarea:focus {
  outline: none;
  border-color: var(--gold);
  box-shadow: 0 0 0 3px var(--gold-muted);
}
.form-field textarea {
  resize: vertical;
  min-height: 120px;
}
.submit-btn {
  width: 100%;
  margin-top: 8px;
}
.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(26, 22, 18, 0.3);
  border-top-color: #1a1612;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  display: inline-block;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.form-error {
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(255, 80, 80, 0.1);
  border: 1px solid rgba(255, 80, 80, 0.3);
  border-radius: var(--radius-sm);
  color: #ff8a8a;
  font-size: 14px;
}

.form-success {
  text-align: center;
  padding: 24px 0;
}
.success-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(212, 175, 104, 0.15);
  border: 2px solid var(--gold);
  border-radius: 50%;
  color: var(--gold);
}
.success-icon svg {
  width: 32px;
  height: 32px;
}
.form-success p {
  color: var(--text-soft);
  margin-bottom: 24px;
  font-size: 16px;
}

@media (max-width: 860px) {
  .contact-grid {
    grid-template-columns: 1fr;
    gap: 36px;
  }
}
@media (max-width: 560px) {
  .form-row {
    grid-template-columns: 1fr;
  }
  .contact-form-wrap {
    padding: 28px 20px;
  }
}
</style>
