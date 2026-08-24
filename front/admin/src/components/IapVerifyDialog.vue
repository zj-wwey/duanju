<template>
  <div v-if="visible" class="iap-dialog-overlay" @click.self="close">
    <div class="iap-dialog">
      <div class="iap-dialog-header">
        <h3>{{ t('payment.iapVerifyTitle') }}</h3>
        <button class="iap-dialog-close" @click="close">✕</button>
      </div>
      <div class="iap-dialog-body">
        <p class="iap-instructions">{{ t('payment.iapInstructions') }}</p>

        <div class="iap-order-row">
          <span class="iap-order-label">{{ t('payment.iapOrderNo') }}:</span>
          <span class="iap-order-value">{{ orderNo }}</span>
          <button class="iap-copy-btn" @click="copyOrderNo">
            {{ copied ? t('payment.iapCopied') : t('payment.iapCopy') }}
          </button>
        </div>

        <div v-if="store === 'APPLE'" class="iap-form-group">
          <label>{{ t('payment.iapTransactionId') }}</label>
          <input
            v-model="form.transactionId"
            type="text"
            :placeholder="t('payment.iapAppleHint')"
            :class="{ 'input-error': errors.transactionId }"
          />
          <span v-if="errors.transactionId" class="iap-field-error">{{ t('payment.iapFieldRequired') }}</span>
        </div>

        <template v-else>
          <div class="iap-form-group">
            <label>{{ t('payment.iapPurchaseData') }}</label>
            <textarea
              v-model="form.purchaseData"
              rows="4"
              :placeholder="t('payment.iapGoogleHint')"
              :class="{ 'input-error': errors.purchaseData }"
            />
            <span v-if="errors.purchaseData" class="iap-field-error">{{ t('payment.iapFieldRequired') }}</span>
          </div>
          <div class="iap-form-group">
            <label>{{ t('payment.iapPurchaseSignature') }}</label>
            <textarea
              v-model="form.purchaseSignature"
              rows="2"
              :placeholder="t('payment.iapPurchaseSignature')"
              :class="{ 'input-error': errors.purchaseSignature }"
            />
            <span v-if="errors.purchaseSignature" class="iap-field-error">{{ t('payment.iapFieldRequired') }}</span>
          </div>
          <div class="iap-form-group">
            <label>{{ t('payment.iapPurchaseToken') }}</label>
            <input
              v-model="form.purchaseToken"
              type="text"
              :placeholder="t('payment.iapPurchaseToken')"
            />
          </div>
        </template>

        <div v-if="verifyError" class="iap-verify-error">{{ verifyError }}</div>
      </div>
      <div class="iap-dialog-footer">
        <button class="iap-cancel-btn" @click="close">{{ t('common.cancel') }}</button>
        <button class="iap-verify-btn" :disabled="verifying" @click="handleVerify">
          {{ verifying ? t('payment.iapVerifying') : t('payment.iapVerify') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useStreamI18n } from '../locales/streamI18n.js'
import { api } from '../api.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  orderNo: { type: String, default: '' },
  store: { type: String, default: 'APPLE' }
})

const emit = defineEmits(['update:visible', 'verified', 'close'])

const { t } = useStreamI18n()
const verifying = ref(false)
const copied = ref(false)
const verifyError = ref('')
const form = ref({ transactionId: '', purchaseData: '', purchaseSignature: '', purchaseToken: '' })
const errors = ref({ transactionId: false, purchaseData: false, purchaseSignature: false })

watch(() => props.visible, (val) => {
  if (val) {
    form.value = { transactionId: '', purchaseData: '', purchaseSignature: '', purchaseToken: '' }
    errors.value = { transactionId: false, purchaseData: false, purchaseSignature: false }
    verifyError.value = ''
    copied.value = false
  }
})

function close() {
  emit('update:visible', false)
  emit('close')
}

async function copyOrderNo() {
  try {
    await navigator.clipboard.writeText(props.orderNo)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch (e) {
    const input = document.createElement('input')
    input.value = props.orderNo
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  }
}

function validate() {
  errors.value = { transactionId: false, purchaseData: false, purchaseSignature: false }
  if (props.store === 'APPLE') {
    if (!form.value.transactionId.trim()) {
      errors.value.transactionId = true
      return false
    }
  } else {
    if (!form.value.purchaseData.trim()) {
      errors.value.purchaseData = true
      return false
    }
    if (!form.value.purchaseSignature.trim()) {
      errors.value.purchaseSignature = true
      return false
    }
  }
  return true
}

async function handleVerify() {
  if (verifying.value) return
  if (!validate()) return

  verifying.value = true
  verifyError.value = ''
  try {
    const payload = { orderNo: props.orderNo, store: props.store }
    if (props.store === 'APPLE') {
      payload.transactionId = form.value.transactionId.trim()
    } else {
      payload.purchaseData = form.value.purchaseData.trim()
      payload.purchaseSignature = form.value.purchaseSignature.trim()
      payload.purchaseToken = form.value.purchaseToken.trim()
    }
    const result = await api.userVerifyOrder(payload)
    if (result?.status === 'PAID') {
      emit('verified', result)
      emit('update:visible', false)
    } else {
      verifyError.value = t('payment.iapVerifyFailed')
    }
  } catch (err) {
    verifyError.value = err.message || t('payment.iapVerifyFailed')
  } finally {
    verifying.value = false
  }
}
</script>

<style scoped>
.iap-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}
.iap-dialog {
  background: #1a1a2e;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
}
.iap-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.iap-dialog-header h3 {
  margin: 0;
  font-size: 18px;
  color: #e0d5c2;
}
.iap-dialog-close {
  padding: 4px 8px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #999;
  font-size: 14px;
  cursor: pointer;
}
.iap-dialog-body {
  padding: 20px 24px;
}
.iap-instructions {
  font-size: 13px;
  color: #aaa;
  line-height: 1.6;
  margin: 0 0 12px 0;
}
.iap-order-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
}
.iap-order-label {
  font-size: 12px;
  color: #888;
  white-space: nowrap;
}
.iap-order-value {
  flex: 1;
  font-size: 13px;
  color: #e0d5c2;
  font-family: monospace;
  word-break: break-all;
}
.iap-copy-btn {
  padding: 4px 10px;
  background: rgba(212, 175, 104, 0.15);
  border: 1px solid rgba(212, 175, 104, 0.3);
  border-radius: 6px;
  color: #d4af68;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}
.iap-copy-btn:hover {
  background: rgba(212, 175, 104, 0.25);
}
.iap-form-group {
  margin-bottom: 14px;
}
.iap-form-group label {
  display: block;
  font-size: 13px;
  color: #e0d5c2;
  margin-bottom: 6px;
}
.iap-form-group input,
.iap-form-group textarea {
  width: 100%;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #e0d5c2;
  font-size: 13px;
  font-family: monospace;
  box-sizing: border-box;
  transition: border-color 0.2s;
}
.iap-form-group textarea {
  resize: vertical;
}
.iap-form-group input:focus,
.iap-form-group textarea:focus {
  outline: none;
  border-color: #d4af68;
}
.iap-form-group input.input-error,
.iap-form-group textarea.input-error {
  border-color: #ef5350;
}
.iap-field-error {
  display: block;
  font-size: 12px;
  color: #ef5350;
  margin-top: 4px;
}
.iap-verify-error {
  margin-top: 8px;
  padding: 10px 12px;
  background: rgba(239, 83, 80, 0.1);
  border: 1px solid rgba(239, 83, 80, 0.3);
  border-radius: 8px;
  font-size: 13px;
  color: #ef5350;
}
.iap-dialog-footer {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  justify-content: flex-end;
}
.iap-cancel-btn {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  color: #aaa;
  font-size: 14px;
  cursor: pointer;
}
.iap-verify-btn {
  padding: 10px 24px;
  background: #d4af68;
  border: none;
  border-radius: 8px;
  color: #1a1a2e;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.iap-verify-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
