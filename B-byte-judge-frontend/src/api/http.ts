import type { ApiEnvelope, CsrfToken } from '@/types/api'

let csrf: Promise<CsrfToken> | null = null

export async function csrfHeader(force = false): Promise<Record<string, string>> {
  const token = await getCsrf(force)
  return { [token.headerName]: token.token }
}

export async function getCsrf(force = false): Promise<CsrfToken> {
  if (force || !csrf) {
    const pending = request<CsrfToken>('/auth/csrf').catch((error) => {
      if (csrf === pending) csrf = null
      throw error
    })
    csrf = pending
  }
  return csrf
}

export function clearCsrf() {
  csrf = null
}

export type WriteFailure =
  | { kind: 'csrf'; error: ApiError }
  | { kind: 'rejected'; error: ApiError }
  | { kind: 'unknown'; error: unknown }

export function writeFailure(error: unknown): WriteFailure {
  if (!(error instanceof ApiError) || error.code <= 0) return { kind: 'unknown', error }
  if (error.status === 403 && error.code === 40301) return { kind: 'csrf', error }
  if (error.status >= 400 && error.status < 500 && error.status !== 408 && error.status !== 429)
    return { kind: 'rejected', error }
  return { kind: 'unknown', error }
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: number,
    readonly requestId: string | null,
    message: string,
    readonly data: unknown = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    method: options.method ?? 'GET',
    credentials: 'include',
    cache: 'no-store',
    headers: {
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  let payload: Partial<ApiEnvelope<T>> | null = null
  try {
    payload = (await response.json()) as Partial<ApiEnvelope<T>>
    if (
      !payload ||
      typeof payload !== 'object' ||
      !Number.isSafeInteger(payload.code) ||
      !('data' in payload) ||
      typeof payload.message !== 'string' ||
      typeof payload.requestId !== 'string'
    )
      throw new Error('Invalid API response')
  } catch {
    throw new ApiError(
      response.status,
      0,
      response.headers.get('X-Request-ID'),
      '服务器响应格式异常，请稍后重试确认。',
    )
  }

  if (!response.ok || payload.code !== 0) {
    if (response.status === 403 && payload.code === 40301) clearCsrf()
    throw new ApiError(
      response.status,
      payload.code ?? 0,
      payload.requestId ?? null,
      payload.message ?? '请求失败，请稍后重试。',
      payload.data,
    )
  }

  return payload.data as T
}
