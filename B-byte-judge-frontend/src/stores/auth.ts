import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAdminMe, getMe, login as loginRequest, logout as logoutRequest } from '@/api/auth'
import { clearDraftCaches, clearOtherDraftCaches } from '@/lib/draft-cache'
import type { AuthIdentity } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const identity = ref<AuthIdentity | null>(null)
  const ready = ref(false)
  const leaving = ref(false)
  let generation = 0

  async function restore(): Promise<boolean> {
    const current = ++generation
    try {
      const me = await getMe()
      if (current !== generation) return false
      if (me.role !== 'admin') {
        identity.value = null
        return false
      }
      const admin = await getAdminMe()
      if (current !== generation) return false
      clearOtherDraftCaches(admin.userId)
      identity.value = admin
      return true
    } catch {
      if (current === generation) identity.value = null
      return false
    } finally {
      if (current === generation) ready.value = true
    }
  }

  async function login(account: string, password: string): Promise<AuthIdentity> {
    const current = ++generation
    const me = await loginRequest(account, password)
    if (current !== generation) throw new Error('登录状态已变化，请重新确认。')
    clearOtherDraftCaches(me.userId)
    if (me.role !== 'admin') {
      identity.value = null
      await logoutRequest().catch(() => undefined)
      throw new Error('此账户没有管理员权限，请使用管理员账户登录。')
    }
    const admin = await getAdminMe()
    if (current !== generation) throw new Error('登录状态已变化，请重新确认。')
    if (admin.userId !== me.userId) {
      identity.value = null
      throw new Error('登录身份已变化，请重新登录。')
    }
    identity.value = me
    ready.value = true
    return me
  }

  async function logout(): Promise<void> {
    const current = ++generation
    leaving.value = true
    const owner = identity.value?.userId
    try {
      await logoutRequest()
    } finally {
      if (owner) clearDraftCaches(owner)
      if (current === generation) {
        identity.value = null
        ready.value = true
        leaving.value = false
      }
    }
  }

  return { identity, ready, leaving, restore, login, logout }
})
