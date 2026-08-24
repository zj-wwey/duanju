/**
 * @deprecated Use shared/utils/currency.js instead
 * This file re-exports from the shared module for backward compatibility
 */
export {
  CURRENCY_MAP as currencyPresets,
  getCurrencyByLocale as getCurrencyPreset,
  localeOptions
} from '../../../shared/utils/currency.js'
