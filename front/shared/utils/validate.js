/**
 * Shared validation utilities for frontend (admin + uniapp)
 */

export function validatePassword(password) {
  if (!password) return true
  return /^(?=\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]).{8,64}$/.test(password)
}

export function validateUsername(username) {
  if (!username) return false
  return /^[A-Za-z][A-Za-z0-9_]{4,31}$/.test(username)
}
