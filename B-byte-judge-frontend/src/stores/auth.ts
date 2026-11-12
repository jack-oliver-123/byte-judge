import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAdminMe, getMe, login as loginRequest, logout as logoutRequest } from '@/api/auth'
import type { AuthIdentity } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const identity = ref<AuthIdentity | null>(null)
  const ready = ref(false)

  async function restore(): Promise<boolean> {
    try {
      const me = await getMe()
      if (me.role !== 'admin') {
        identity.value = null
        return false
      }
      await getAdminMe()
      identity.value = me
      return true
    } catch {
      identity.value = null
      return false
    } finally {
      ready.value = true
    }
  }

  async function login(account: string, password: string): Promise<AuthIdentity> {
    const me = await loginRequest(account, password)
    if (me.role !== 'admin') {
      await logoutRequest().catch(() => undefined)
      throw new Error('此账户没有管理员权限，请使用管理员账户登录。')
    }
    await getAdminMe()
    identity.value = me
    ready.value = true
    return me
  }

  async function logout(): Promise<void> {
    await logoutRequest()
    identity.value = null
    ready.value = true
  }

  return { identity, ready, restore, login, logout }
})
