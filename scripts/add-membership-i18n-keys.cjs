'use strict'
const fs = require('fs')
const path = require('path')

const dir = path.resolve(__dirname, '..', 'front', 'shared', 'i18n', 'stream')

const newKeys = {
  'membership.adFreeFull': {
    'zh-CN': '全免', 'zh-TW': '全免', 'en-US': 'All Free',
    'ja-JP': '全免除', 'ko-KR': '전체 무료', 'th-TH': 'ฟรีทั้งหมด',
    'vi-VN': 'Miễn phí toàn bộ', 'id-ID': 'Semua Gratis', 'ms-MY': 'Semua Percuma',
    'es-ES': 'Todo gratis', 'fr-FR': 'Tout gratuit', 'de-DE': 'Alles kostenlos',
    'pt-BR': 'Tudo grátis', 'ru-RU': 'Всё бесплатно', 'it-IT': 'Tutto gratis',
    'tr-TR': 'Tamamen ücretsiz', 'ar-SA': 'مجاني بالكامل'
  },
  'membership.adFreePartial': {
    'zh-CN': '部分', 'zh-TW': '部分', 'en-US': 'Partial',
    'ja-JP': '一部', 'ko-KR': '일부', 'th-TH': 'บางส่วน',
    'vi-VN': 'Một phần', 'id-ID': 'Sebagian', 'ms-MY': 'Sebahagian',
    'es-ES': 'Parcial', 'fr-FR': 'Partiel', 'de-DE': 'Teilweise',
    'pt-BR': 'Parcial', 'ru-RU': 'Частично', 'it-IT': 'Parziale',
    'tr-TR': 'Kısmi', 'ar-SA': 'جزئي'
  },
  'membership.unlimited': {
    'zh-CN': '无限制', 'zh-TW': '無限制', 'en-US': 'Unlimited',
    'ja-JP': '無制限', 'ko-KR': '무제한', 'th-TH': 'ไม่จำกัด',
    'vi-VN': 'Không giới hạn', 'id-ID': 'Tak terbatas', 'ms-MY': 'Tanpa had',
    'es-ES': 'Ilimitado', 'fr-FR': 'Illimité', 'de-DE': 'Unbegrenzt',
    'pt-BR': 'Ilimitado', 'ru-RU': 'Безлимитно', 'it-IT': 'Illimitato',
    'tr-TR': 'Sınırsız', 'ar-SA': 'غير محدود'
  },
  'membership.daysUnit': {
    'zh-CN': '天', 'zh-TW': '天', 'en-US': 'days',
    'ja-JP': '日', 'ko-KR': '일', 'th-TH': 'วัน',
    'vi-VN': 'ngày', 'id-ID': 'hari', 'ms-MY': 'hari',
    'es-ES': 'días', 'fr-FR': 'jours', 'de-DE': 'Tage',
    'pt-BR': 'dias', 'ru-RU': 'дн.', 'it-IT': 'giorni',
    'tr-TR': 'gün', 'ar-SA': 'يوم'
  },
  'membership.renewalDiscount': {
    'zh-CN': '（续费9折）', 'zh-TW': '（續費9折）', 'en-US': '(10% renewal discount)',
    'ja-JP': '（継続割引10%）', 'ko-KR': '(갱신 10% 할인)', 'th-TH': '(ส่วนลดต่ออายุ 10%)',
    'vi-VN': '(Giảm 10% khi gia hạn)', 'id-ID': '(Diskon perpanjangan 10%)', 'ms-MY': '(Diskaun pembaharuan 10%)',
    'es-ES': '(10% descuento por renovación)', 'fr-FR': '(10% de remise renouvellement)', 'de-DE': '(10% Verlängerungsrabatt)',
    'pt-BR': '(10% desconto na renovação)', 'ru-RU': '(Скидка 10% при продлении)', 'it-IT': '(Sconto rinnovo 10%)',
    'tr-TR': '(%10 yenileme indirimi)', 'ar-SA': '(خصم تجديد 10%)'
  },
  'membership.cancelAutoRenewalConfirm': {
    'zh-CN': '确定取消自动续费？', 'zh-TW': '確定取消自動續費？', 'en-US': 'Confirm cancel auto-renewal subscription?',
    'ja-JP': '自動更新を解除してもよろしいですか？', 'ko-KR': '자동 갱신을 해지하시겠습니까?', 'th-TH': 'ยืนยันยกเลิกการต่ออายุอัตโนมัติ?',
    'vi-VN': 'Xác nhận hủy gia hạn tự động?', 'id-ID': 'Konfirmasi batalkan perpanjangan otomatis?', 'ms-MY': 'Sahkan batalkan pembaharuan automatik?',
    'es-ES': '¿Confirmar cancelación de renovación automática?', 'fr-FR': 'Confirmer l\'annulation du renouvellement automatique ?', 'de-DE': 'Automatische Verlängerung wirklich kündigen?',
    'pt-BR': 'Confirmar cancelamento da renovação automática?', 'ru-RU': 'Подтвердить отмену автопродления?', 'it-IT': 'Confermare annullamento rinnovo automatico?',
    'tr-TR': 'Otomatik yenileme iptal edilsin mi?', 'ar-SA': 'تأكيد إلغاء التجديد التلقائي؟'
  }
}

const files = fs.readdirSync(dir).filter(f => f.endsWith('.json'))

for (const file of files) {
  const locale = file.replace('.json', '')
  const filePath = path.join(dir, file)
  const data = JSON.parse(fs.readFileSync(filePath, 'utf8'))

  let changed = false
  for (const [key, translations] of Object.entries(newKeys)) {
    if (!data[key]) {
      data[key] = translations[locale] || translations['en-US']
      changed = true
    }
  }

  if (changed) {
    const sortedKeys = Object.keys(data).sort()
    const sortedData = {}
    for (const k of sortedKeys) {
      sortedData[k] = data[k]
    }
    fs.writeFileSync(filePath, JSON.stringify(sortedData, null, 2) + '\n', 'utf8')
    console.log(file + ': added ' + Object.keys(newKeys).filter(k => !data[k]).join(', '))
  } else {
    console.log(file + ': all keys already exist')
  }
}

console.log('Done!')