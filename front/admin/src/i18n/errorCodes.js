export const ERROR_CODES = {
  SERVER_BUSY: 'error.serverBusy',
  DUPLICATE_RECORD: 'error.duplicateRecord',
  INVALID_PARAMS: 'error.invalidParams',
  PARAM_TYPE_ERROR: 'error.paramTypeError',
  RESOURCE_NOT_FOUND: 'error.resourceNotFound',
  DATA_ACCESS_FAILED: 'error.dataAccessFailed',
  FORBIDDEN: 'error.forbidden',
  UNAUTHORIZED: 'error.unauthorized',
  ORDER_NOT_FOUND: 'error.orderNotFound',
  INSUFFICIENT_POINTS: 'error.insufficientPoints',
  EPISODE_ALREADY_UNLOCKED: 'error.episodeAlreadyUnlocked',
  DAILY_LIMIT_REACHED: 'error.dailyLimitReached',
  COOLDOWN_ACTIVE: 'error.cooldownActive'
}

export function resolveErrorMessage(code, i18nInstance) {
  if (!code) return 'Unknown error'
  const key = ERROR_CODES[code]
  if (key && i18nInstance && i18nInstance.global) {
    try {
      const msg = i18nInstance.global.t(key)
      if (msg && msg !== key) return msg
    } catch (e) {}
  }
  return code
}