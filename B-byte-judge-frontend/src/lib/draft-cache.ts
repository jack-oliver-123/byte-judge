import type { DraftFields } from '@/api/problems'
import { emptyForm, fieldsFromForm, sameFields, type DraftForm } from '@/lib/problem-fields'

const FORMAT = 'byte-judge/draft-cache@2'
const PREFIX = 'byte-judge.draft.'

export interface DraftCacheInput {
  userId: string
  problemId: string
  problemNo: number
  draftId: string
  revision: number
  form: DraftForm
  updatedAt: string
}

interface DraftCacheRecord extends DraftCacheInput {
  format: typeof FORMAT
  savedAt: string
}

export type DraftCacheRead =
  | { kind: 'none' }
  | { kind: 'unavailable'; message: string }
  | { kind: 'corrupt'; message: string }
  | { kind: 'identity-mismatch'; message: string; draft: DraftCacheInput }
  | { kind: 'synced'; draft: DraftCacheInput }
  | { kind: 'same-revision'; draft: DraftCacheInput }
  | { kind: 'revision-changed'; draft: DraftCacheInput }

function storage() {
  return window.sessionStorage
}

function key(userId: string, problemId: string) {
  return `${PREFIX}${userId}.${problemId}`
}

function ownerPrefix(userId: string) {
  return `${PREFIX}${userId}.`
}

const isForm = (value: unknown): value is DraftForm => {
  const form = value as Partial<DraftForm> | null
  return (
    !!form &&
    typeof form === 'object' &&
    Object.keys(emptyForm()).every((field) => typeof form[field as keyof DraftForm] === 'string')
  )
}

function parse(record: Partial<DraftCacheRecord>): DraftCacheInput | null {
  if (
    typeof record.userId !== 'string' ||
    typeof record.problemId !== 'string' ||
    typeof record.draftId !== 'string' ||
    !Number.isSafeInteger(record.problemNo) ||
    !Number.isSafeInteger(record.revision) ||
    record.revision! < 0 ||
    typeof record.savedAt !== 'string' ||
    typeof record.updatedAt !== 'string' ||
    !isForm(record.form)
  )
    return null
  return {
    userId: record.userId,
    problemId: record.problemId,
    problemNo: record.problemNo!,
    draftId: record.draftId,
    revision: record.revision!,
    form: Object.fromEntries(
      Object.keys(emptyForm()).map((field) => [field, record.form![field as keyof DraftForm]]),
    ) as DraftForm,
    updatedAt: record.updatedAt,
  }
}

export function writeDraftCache(input: DraftCacheInput): string | null {
  try {
    const record: DraftCacheRecord = {
      ...input,
      format: FORMAT,
      savedAt: new Date().toISOString(),
    }
    storage().setItem(key(input.userId, input.problemId), JSON.stringify(record))
    return null
  } catch {
    return '无法在本标签页暂存输入：浏览器存储不可用或被禁用。请随时手动保存，或使用“复制全部字段”保留内容。'
  }
}

export function removeDraftCache(userId: string, problemId: string) {
  try {
    storage().removeItem(key(userId, problemId))
  } catch {}
}

export function readDraftCache(
  userId: string,
  problemId: string,
  server: { problemNo: number; draftId: string; revision: number; fields: DraftFields },
): DraftCacheRead {
  let raw: string | null
  try {
    raw = storage().getItem(key(userId, problemId))
  } catch {
    return {
      kind: 'unavailable',
      message: '无法读取本标签页暂存：浏览器存储不可用或被禁用。',
    }
  }
  if (raw === null) return { kind: 'none' }
  let record: Partial<DraftCacheRecord> | null = null
  try {
    record = JSON.parse(raw)
  } catch {
    record = null
  }
  const draft = record ? parse(record) : null
  if (!draft)
    return {
      kind: 'corrupt',
      message: '发现无法识别的本标签页暂存，已停止自动恢复。请通过“复制全部字段”保留当前输入。',
    }
  if (
    draft.userId !== userId ||
    draft.problemId !== problemId ||
    draft.problemNo !== server.problemNo
  )
    return {
      kind: 'corrupt',
      message:
        '本标签页暂存与当前题目身份不一致，已停止自动恢复。请通过“复制全部字段”保留当前输入。',
    }
  if (record?.format !== FORMAT || draft.draftId !== server.draftId)
    return {
      kind: 'identity-mismatch',
      message: '本标签页暂存的格式或草稿身份不兼容，不能直接恢复。请复制需要保留的内容后重新编辑。',
      draft,
    }
  const fields = fieldsFromForm(draft.form).fields
  if (fields && sameFields(fields, server.fields)) {
    removeDraftCache(userId, problemId)
    return { kind: 'synced', draft }
  }
  if (draft.revision === server.revision) return { kind: 'same-revision', draft }
  return { kind: 'revision-changed', draft }
}

export function clearDraftCaches(userId: string) {
  const keys: string[] = []
  try {
    for (let index = 0; index < storage().length; index++) {
      const itemKey = storage().key(index)
      if (itemKey?.startsWith(ownerPrefix(userId))) keys.push(itemKey)
    }
  } catch {
    return
  }
  for (const itemKey of keys) {
    try {
      storage().removeItem(itemKey)
    } catch {}
  }
}

export function clearOtherDraftCaches(userId: string) {
  try {
    const keys = Array.from({ length: storage().length }, (_, index) => storage().key(index))
    for (const item of keys) {
      if (item?.startsWith(PREFIX) && !item.startsWith(ownerPrefix(userId)))
        storage().removeItem(item)
    }
  } catch {}
}
