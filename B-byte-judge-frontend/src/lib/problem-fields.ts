import type { DraftFields } from '@/api/problems'
import type { ArtifactManifest, ProblemDetail } from '@/types/api'

export type DraftForm = {
  title: string
  statement: string
  inputDescription: string
  outputDescription: string
  difficulty: string
  tags: string
  timeLimitCpuMs: string
  timeLimitWallMs: string
  memoryLimitMiB: string
}

export type FieldKey = keyof DraftFields
export type FieldErrors = Partial<Record<FieldKey, string>>

export const emptyForm = (): DraftForm => ({
  title: '',
  statement: '',
  inputDescription: '',
  outputDescription: '',
  difficulty: '',
  tags: '',
  timeLimitCpuMs: '',
  timeLimitWallMs: '',
  memoryLimitMiB: '',
})

export const textFields = [
  { key: 'statement', label: '题面' },
  { key: 'inputDescription', label: '输入说明' },
  { key: 'outputDescription', label: '输出说明' },
] as const

export const resourceFields = [
  { key: 'timeLimitCpuMs', label: 'CPU 时限（ms）', min: 100, max: 10000 },
  { key: 'timeLimitWallMs', label: '墙钟时限（ms）', min: 100, max: 30000 },
  { key: 'memoryLimitMiB', label: '内存限制（MiB）', min: 64, max: 2048 },
] as const

export const fieldLabels: Record<FieldKey, string> = {
  title: '标题',
  statement: '题面',
  inputDescription: '输入说明',
  outputDescription: '输出说明',
  difficulty: '难度',
  tags: '标签',
  timeLimitCpuMs: 'CPU 时限',
  timeLimitWallMs: '墙钟时限',
  memoryLimitMiB: '内存限制',
}

export const difficultyLabels: Record<string, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
}

export function strip(value: string) {
  const points = Array.from(value)
  const whitespace = (point: string) => {
    const codePoint = point.codePointAt(0)!
    return (
      (codePoint >= 9 && codePoint <= 13) ||
      (codePoint >= 28 && codePoint <= 31) ||
      /^\p{Z}$/u.test(point)
    )
  }
  let start = 0
  let end = points.length
  while (start < end && whitespace(points[start]!)) start++
  while (end > start && whitespace(points[end - 1]!)) end--
  return points.slice(start, end).join('')
}

export function formFromFields(value: DraftFields): DraftForm {
  return {
    title: value.title,
    statement: value.statement ?? '',
    inputDescription: value.inputDescription ?? '',
    outputDescription: value.outputDescription ?? '',
    difficulty: value.difficulty ?? '',
    tags: (value.tags ?? []).join('\n'),
    timeLimitCpuMs: value.timeLimitCpuMs === null ? '' : String(value.timeLimitCpuMs),
    timeLimitWallMs: value.timeLimitWallMs === null ? '' : String(value.timeLimitWallMs),
    memoryLimitMiB: value.memoryLimitMiB === null ? '' : String(value.memoryLimitMiB),
  }
}

const serializeField = (value: DraftFields[FieldKey]): string =>
  value === null || value === '' || value === undefined ? 'null' : JSON.stringify(value)

function fieldsSignature(value: DraftFields) {
  return (Object.keys(fieldLabels) as FieldKey[]).map((key) => [key, serializeField(value[key])])
}

export function normalizeGiven(value: DraftFields): DraftFields {
  return { ...value, tags: value.tags ?? [] }
}

export function sameFields(left: DraftFields, right: DraftFields) {
  return (
    JSON.stringify(fieldsSignature(left)) === JSON.stringify(fieldsSignature(normalizeGiven(right)))
  )
}

export function differingFields(local: DraftFields, given: DraftFields): FieldKey[] {
  const server = normalizeGiven(given)
  return (Object.keys(fieldLabels) as FieldKey[]).filter(
    (key) => serializeField(local[key]) !== serializeField(server[key]),
  )
}

export function metadataSignature(value: ProblemDetail) {
  return JSON.stringify({
    artifacts: value.artifacts,
    everPublished: value.everPublished,
    id: value.id,
    draftId: value.draftId,
    status: value.status,
    updatedAt: value.updatedAt,
  })
}

export function manifestsChange(left: ProblemDetail, right: ProblemDetail) {
  return JSON.stringify(left.artifacts) !== JSON.stringify(right.artifacts)
}

const safeInteger = (value: string) => /^\d+$/.test(value)

export function fieldsFromForm(form: DraftForm): {
  fields: DraftFields | null
  errors: FieldErrors
} {
  const found: FieldErrors = {}
  const title = strip(form.title)
  if (form.difficulty && !['EASY', 'MEDIUM', 'HARD'].includes(form.difficulty))
    found.difficulty = '请选择有效的难度。'
  if (!title || Array.from(title).length > 100) found.title = '标题需为 1–100 个 Unicode 字符。'
  const tags = form.tags === '' ? [] : form.tags.split('\n').map(strip)
  if (tags.length > 5 || tags.some((tag) => !tag || Array.from(tag).length > 20))
    found.tags = '每行一个标签，最多 5 个，每个 1–20 个字符，不可为空。'
  const bytes = textFields.reduce(
    (total, field) => total + new TextEncoder().encode(form[field.key]).length,
    0,
  )
  if (bytes > 102400) found.statement = '题面与输入、输出说明合计不得超过 102400 UTF-8 字节。'
  const resources = {
    timeLimitCpuMs: null,
    timeLimitWallMs: null,
    memoryLimitMiB: null,
  } as Pick<DraftFields, 'timeLimitCpuMs' | 'timeLimitWallMs' | 'memoryLimitMiB'>
  for (const resource of resourceFields) {
    const raw = form[resource.key].trim()
    if (!raw) continue
    const value = Number(raw)
    if (
      !safeInteger(raw) ||
      !Number.isSafeInteger(value) ||
      value < resource.min ||
      value > resource.max
    )
      found[resource.key] = `请输入 ${resource.min}–${resource.max} 的整数，或留空。`
    else resources[resource.key] = value
  }
  if (
    resources.timeLimitCpuMs !== null &&
    resources.timeLimitWallMs !== null &&
    resources.timeLimitWallMs < resources.timeLimitCpuMs
  )
    found.timeLimitWallMs = '墙钟时限不能小于 CPU 时限。'
  if (Object.keys(found).length) return { fields: null, errors: found }
  return {
    fields: {
      title,
      tags,
      statement: form.statement === '' ? null : form.statement,
      inputDescription: form.inputDescription === '' ? null : form.inputDescription,
      outputDescription: form.outputDescription === '' ? null : form.outputDescription,
      difficulty: form.difficulty === '' ? null : (form.difficulty as DraftFields['difficulty']),
      ...resources,
    },
    errors: found,
  }
}

export function validDetail(value: unknown): value is ProblemDetail {
  const detail = value as Partial<ProblemDetail> | null
  return (
    !!detail &&
    typeof detail === 'object' &&
    typeof detail.id === 'string' &&
    typeof detail.draftId === 'string' &&
    Number.isSafeInteger(detail.problemNo) &&
    Number.isSafeInteger(detail.revision) &&
    detail.revision! >= 0 &&
    typeof detail.title === 'string' &&
    ['statement', 'inputDescription', 'outputDescription'].every((key) => {
      const field = detail[key as 'statement']
      return field === null || field === undefined || typeof field === 'string'
    }) &&
    (detail.difficulty === null ||
      detail.difficulty === undefined ||
      ['EASY', 'MEDIUM', 'HARD'].includes(detail.difficulty)) &&
    (detail.tags === null ||
      detail.tags === undefined ||
      (Array.isArray(detail.tags) && detail.tags.every((tag) => typeof tag === 'string'))) &&
    resourceFields.every(
      (resource) => detail[resource.key] === null || Number.isSafeInteger(detail[resource.key]),
    ) &&
    Array.isArray(detail.artifacts)
  )
}

export function describeArtifact(artifact: ArtifactManifest) {
  return `${artifact.kind} · 第 ${artifact.caseOrder} 组 · ${artifact.fileName} · ${artifact.byteSize} 字节 · 展示顺序 ${artifact.displayOrder}`
}

export function displayFieldValue(key: FieldKey, value: DraftFields[FieldKey]) {
  if (key === 'tags')
    return value === null || (value as string[]).length === 0
      ? '（无标签）'
      : (value as string[]).join('、')
  if (key === 'difficulty') return difficultyLabels[String(value)] ?? '未设置'
  return value === null || value === '' ? '（空）' : String(value)
}

export function fieldUnit(key: FieldKey) {
  if (key === 'timeLimitCpuMs' || key === 'timeLimitWallMs') return ' ms'
  if (key === 'memoryLimitMiB') return ' MiB'
  return ''
}
