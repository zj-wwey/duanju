/**
 * @deprecated Use shared/utils/currency.js instead
 * This file re-exports from the shared module for backward compatibility
 */
export {
  CURRENCY_MAP as localeCurrencyMap,
  getCurrencyByLocale,
  getCurrencyByCode,
  formatPrice,
  getCurrencyOptions
} from '../../shared/utils/currency.js'
