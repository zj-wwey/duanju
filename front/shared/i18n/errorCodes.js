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

export function resolveErrorMessage(errorCode, i18nT) {
  if (!errorCode) return null
  const i18nKey = ERROR_CODES[errorCode]
  if (i18nKey && i18nT) {
    try {
      const msg = i18nT(i18nKey)
      if (msg && msg !== i18nKey) return msg
    } catch (e) {
    }
  }
  return null
}
