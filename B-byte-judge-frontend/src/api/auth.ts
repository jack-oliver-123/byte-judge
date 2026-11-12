import { clearCsrf, csrfHeader, getCsrf, request } from '@/api/http'
import type { AuthIdentity } from '@/types/api'

export { getCsrf }

export async function login(account: string, password: string): Promise<AuthIdentity> {
  try {
    const identity = await request<AuthIdentity>('/auth/login', {
      method: 'POST',
      body: { account, password },
      headers: await csrfHeader(),
    })
    clearCsrf()
    return identity
  } catch (error) {
    clearCsrf()
    throw error
  }
}

export function getMe(): Promise<AuthIdentity> {
  return request<AuthIdentity>('/users/me')
}

export function getAdminMe(): Promise<AuthIdentity> {
  return request<AuthIdentity>('/admin/users/me')
}

export async function logout(): Promise<void> {
  try {
    await request<null>('/auth/logout', {
      method: 'POST',
      headers: await csrfHeader(),
    })
  } finally {
    clearCsrf()
  }
}
