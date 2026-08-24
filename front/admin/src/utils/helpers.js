/**
 * Admin 端通用工具函数
 * 实际实现已迁移到 shared/utils/format.js 和 shared/utils/validate.js
 * 此处保留以向后兼容
 */
export {
  field,
  toCamel,
  formatNumber,
  formatMoney,
  formatDate,
  planPoints,
  planBonusPoints,
  productPoints,
  LEVEL_MAP,
  levelColor,
  levelGradient,
  formatDuration,
  formatPercent,
  formatDateTime
} from '../../../shared/utils/format.js'

export {
  validatePassword,
  validateUsername
} from '../../../shared/utils/validate.js'
