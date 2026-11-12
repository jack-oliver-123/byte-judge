import { request } from '@/api/http'
import type { ProblemDetail, ProblemPage } from '@/types/api'

export interface ProblemQuery {
  title?: string
  difficulty?: string
  includeDeleted?: boolean
  pageNum: number
  pageSize: number
}

export interface CreateProblemResult {
  id: string
  draftId: string
  problemNo: number
  revision: number
}

export function listProblems(query: ProblemQuery): Promise<ProblemPage> {
  const params = new URLSearchParams({
    pageNum: String(query.pageNum),
    pageSize: String(query.pageSize),
  })
  if (query.title) params.set('title', query.title)
  if (query.difficulty) params.set('difficulty', query.difficulty)
  if (query.includeDeleted) params.set('includeDeleted', 'true')
  return request<ProblemPage>(`/admin/problems?${params}`)
}

export function getProblem(id: string): Promise<ProblemDetail> {
  return request<ProblemDetail>(`/admin/problems/${id}`)
}

export type DraftFields = Pick<
  ProblemDetail,
  | 'title'
  | 'statement'
  | 'inputDescription'
  | 'outputDescription'
  | 'difficulty'
  | 'tags'
  | 'timeLimitCpuMs'
  | 'timeLimitWallMs'
  | 'memoryLimitMiB'
>

export async function saveProblem(
  id: string,
  fields: DraftFields,
  expectedRevision: number,
  headers: Record<string, string>,
): Promise<ProblemDetail> {
  return request<ProblemDetail>(`/admin/problems/${id}/draft`, {
    method: 'PATCH',
    body: { ...fields, expectedRevision },
    headers,
  })
}

export async function createProblem(
  title: string,
  headers: Record<string, string>,
): Promise<CreateProblemResult> {
  return request<CreateProblemResult>('/admin/problems', {
    method: 'POST',
    body: { title },
    headers,
  })
}

export function deleteProblem(
  id: string,
  expectedRevision: number,
  headers: Record<string, string>,
): Promise<unknown> {
  return request(`/admin/problems/${id}?expectedRevision=${expectedRevision}`, {
    method: 'DELETE',
    headers,
  })
}
