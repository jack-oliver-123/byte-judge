<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import ProblemConflictPanel from '@/components/ProblemConflictPanel.vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { getProblem, saveProblem, type DraftFields } from '@/api/problems'
import { ApiError, clearCsrf, csrfHeader, writeFailure } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { ProblemDetail } from '@/types/api'
import {
  clearDraftCaches,
  readDraftCache,
  removeDraftCache,
  writeDraftCache,
} from '@/lib/draft-cache'
import { setEditingState } from '@/lib/editing-guard'
import {
  emptyForm,
  fieldsFromForm,
  formFromFields,
  fieldLabels,
  resourceFields,
  sameFields,
  textFields,
  validDetail,
  displayFieldValue,
  type FieldKey,
  type DraftForm,
  type FieldErrors,
} from '@/lib/problem-fields'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const detail = ref<ProblemDetail | null>(null)
const loading = ref(true)
const saving = ref(false)
const stopped = ref(false)
const csrfExpired = ref(false)
const message = ref('')
const saved = ref(false)
const errors = ref<FieldErrors>({})
const saveError = shallowRef<ApiError | null>(null)
const formElement = ref<HTMLFormElement | null>(null)
const form = reactive<DraftForm>(emptyForm())
const conflict = ref<{
  local: DraftFields
  localRevision: number
  server: ProblemDetail
  reviewed: ProblemDetail | null
} | null>(null)
const conflictVisible = ref(true)
const cacheNotice = ref('')
const cached = ref(false)
const cacheBlocked = ref(false)
const copied = ref('')
const restore = shallowRef<{
  form: DraftForm
  draft: { revision: number; updatedAt: string }
  reviewed: ProblemDetail
} | null>(null)
const copyFallback = ref(false)
const pending = shallowRef<{
  request: DraftFields
  problemId: string
  requestId: string | null
} | null>(null)
const authLost = ref(false)
const verifying = ref(false)
const restoredRevision = ref<number | null>(null)
const incompatible = ref<DraftForm | null>(null)
let cacheTimer: number | undefined
let cacheFailure = ''
let baseline = ''
let sequence = 0

function formSignature(value: DraftForm) {
  return JSON.stringify(fieldsFromForm(value).fields ?? value)
}
const dirty = computed(() => detail.value !== null && formSignature(form) !== baseline)
const currentFields = computed<DraftFields | null>(() => {
  const value = fieldsFromForm(form)
  return value.fields
})
const copyText = computed(() => {
  if (incompatible.value)
    return `题号：${detail.value?.problemNo ?? '—'}（不兼容的暂存，仅供复制）\n${Object.entries(
      incompatible.value,
    )
      .map(([key, value]) => `${fieldLabels[key as FieldKey]}：${value}`)
      .join('\n')}`
  if (conflict.value)
    return `题号：${detail.value?.problemNo ?? '—'}\n${Object.entries(conflict.value.local)
      .map(
        ([key, value]) =>
          `${fieldLabels[key as FieldKey]}：${displayFieldValue(key as FieldKey, value)}`,
      )
      .join('\n')}`
  const value = currentFields.value
  const lines = [`题号：${detail.value?.problemNo ?? '—'}`]
  if (value) {
    for (const key of Object.keys(fieldLabels) as FieldKey[]) {
      lines.push(
        `${fieldLabels[key]}：${key === 'tags' ? (value.tags ?? []).join('\n    ') : displayFieldValue(key, value[key])}`,
      )
    }
  } else {
    lines.push('（当前输入未通过字段校验，以下为原始文本）')
    for (const [key, text] of Object.entries(form)) lines.push(`${key}：${text}`)
  }
  return lines.join('\n')
})
const status = computed(() =>
  saving.value
    ? '正在保存到服务器…'
    : pending.value
      ? '保存结果待确认，正在等待先读取服务器内容'
      : authLost.value
        ? '登录已失效，输入已尽量暂存，请重新登录后回来恢复'
        : conflict.value
          ? '修订冲突待处理，尚未写入服务器'
          : restore.value
            ? '发现本标签页暂存，等待你确认是否恢复'
            : stopped.value
              ? '保存已停止，请保留输入'
              : dirty.value
                ? cacheBlocked.value
                  ? '有尚未保存到服务器的修改，但未能暂存到本标签页'
                  : cached.value
                    ? '有尚未保存到服务器的修改（已暂存到本标签页）'
                    : '有尚未保存到服务器的修改'
                : saved.value
                  ? '已保存到服务器'
                  : '已读取服务器草稿',
)
const frozen = computed(
  () => saving.value || pending.value !== null || authLost.value || restore.value !== null,
)

function install(value: ProblemDetail) {
  detail.value = value
  Object.assign(form, formFromFields(value))
  baseline = formSignature(form)
  conflict.value = null
  restore.value = null
  pending.value = null
  restoredRevision.value = null
  incompatible.value = null
}

function describe(error: unknown) {
  if (!(error instanceof ApiError))
    return '无法确认服务器是否已保存。请保留当前输入，先重试确认服务器内容。'
  const descriptions: Record<number, string> = {
    40000: '服务器拒绝了字段内容，请检查输入后手动重试。',
    40100: '登录已失效，已停止保存。请保留输入后重新登录。',
    40300: '当前账号没有管理员权限，已停止保存。',
    40301: '安全校验已失效，当前输入已保留。请重试保存。',
    40400: '题目已不存在或不可编辑，已停止保存。',
    40910: '服务器草稿已有新修订，已停止保存。当前输入已保留，请复制后对照服务器内容。',
  }
  return `${descriptions[error.code] ?? '请求未完成，请保留输入。'}${error.requestId ? ` 请求编号：${error.requestId}` : ''}`
}

async function load() {
  const current = ++sequence
  window.clearTimeout(cacheTimer)
  loading.value = true
  detail.value = null
  stopped.value = false
  csrfExpired.value = false
  saved.value = false
  message.value = ''
  errors.value = {}
  saveError.value = null
  restore.value = null
  copied.value = ''
  cached.value = false
  cacheBlocked.value = false
  cacheNotice.value = ''
  cacheFailure = ''
  incompatible.value = null
  pending.value = null
  conflict.value = null
  authLost.value = false
  restoredRevision.value = null
  const owner = auth.identity?.userId
  try {
    if (!(await auth.restore())) {
      if (current !== sequence) return
      authLost.value = true
      throw new ApiError(401, 40100, null, '当前身份验证失败')
    }
    if (current !== sequence || owner !== auth.identity?.userId) return
    const value = await getProblem(String(route.params.id))
    if (current !== sequence || owner !== auth.identity?.userId) return
    if (!validDetail(value)) throw new Error('草稿响应格式异常')
    install(value)
    if (!owner) return
    const cached = readDraftCache(owner, value.id, {
      problemNo: value.problemNo,
      draftId: value.draftId,
      revision: value.revision,
      fields: value,
    })
    if (cached.kind === 'unavailable' || cached.kind === 'corrupt') {
      cacheNotice.value = cached.message
      cacheBlocked.value = true
      copyFallback.value = true
    } else if (cached.kind === 'synced') {
      cacheNotice.value = '本标签页暂存内容与服务器一致，已清理冗余暂存。'
    } else if (cached.kind === 'identity-mismatch') {
      cacheNotice.value = cached.message
      cacheBlocked.value = true
      incompatible.value = cached.draft.form
      copyFallback.value = true
    } else if (cached.kind === 'same-revision') {
      restore.value = {
        form: cached.draft.form,
        draft: { revision: cached.draft.revision, updatedAt: cached.draft.updatedAt },
        reviewed: value,
      }
    } else if (cached.kind === 'revision-changed') {
      const fields = fieldsFromForm(cached.draft.form).fields
      if (fields) openConflict(fields, cached.draft.revision, value)
      else restore.value = { form: cached.draft.form, draft: cached.draft, reviewed: value }
    }
  } catch (error) {
    if (current === sequence) {
      if (error instanceof ApiError && error.code === 40100) {
        authLost.value = true
        message.value = '登录已失效，无法读取草稿。请重新登录后返回本页。'
      } else {
        message.value = error instanceof ApiError ? describe(error) : '草稿读取失败，请稍后重试。'
      }
    }
  } finally {
    if (current === sequence) loading.value = false
  }
}

function fields(): DraftFields | null {
  const result = fieldsFromForm(form)
  errors.value = result.errors
  return result.fields
}

function cacheNow() {
  window.clearTimeout(cacheTimer)
  cacheTimer = undefined
  const owner = auth.identity?.userId
  const current = detail.value
  if (!owner || !current || auth.leaving || restore.value || incompatible.value) return
  const source = conflict.value ? formFromFields(conflict.value.local) : { ...form }
  if (!dirty.value && !conflict.value && !pending.value && restoredRevision.value === null) {
    removeDraftCache(owner, current.id)
    if (!cacheBlocked.value) {
      cacheFailure = ''
      cacheNotice.value = ''
    }
    cached.value = false
    return
  }
  const failure = writeDraftCache({
    userId: owner,
    problemId: current.id,
    problemNo: current.problemNo,
    draftId: current.draftId,
    revision: conflict.value?.localRevision ?? restoredRevision.value ?? current.revision,
    form: source,
    updatedAt: current.updatedAt ?? '',
  })
  if (failure) {
    cacheFailure = failure
    cacheNotice.value = failure
    cached.value = false
    cacheBlocked.value = true
    copyFallback.value = true
  } else {
    cached.value = true
    cacheBlocked.value = false
    if (!cacheFailure) return
    cacheFailure = ''
    cacheNotice.value = ''
    copied.value = '本标签页暂存已恢复可用。'
  }
}

function restoreDraft() {
  if (!restore.value) return
  restoredRevision.value = restore.value.draft.revision
  Object.assign(form, restore.value.form)
  restore.value = null
  saved.value = false
  message.value =
    restoredRevision.value !== detail.value?.revision
      ? '暂存基于不同修订，请先修正无效输入，再点击保存进入差异对照。不会直接提交。'
      : '已恢复本标签页暂存内容，尚未保存到服务器。请检查后手动保存。'
  cacheNotice.value = ''
  cacheNow()
}

function discardRestore() {
  const owner = auth.identity?.userId
  if (owner && detail.value) removeDraftCache(owner, detail.value.id)
  restore.value = null
  message.value = '已放弃本标签页暂存，当前显示服务器内容。'
}

function discardIncompatible() {
  if (!window.confirm('确认放弃这份不兼容的暂存？建议先复制需要保留的内容。')) return
  if (auth.identity && detail.value) removeDraftCache(auth.identity.userId, detail.value.id)
  incompatible.value = null
  cacheBlocked.value = false
  cacheNotice.value = ''
  cacheFailure = ''
  cacheNow()
}

async function copyAll() {
  try {
    await navigator.clipboard.writeText(copyText.value)
    copied.value = '已复制全部编辑字段。'
  } catch {
    copyFallback.value = true
    copied.value = '无法访问剪贴板，请在下方文本框中全选并复制。'
  }
}

async function save() {
  if (!detail.value || frozen.value || stopped.value || conflict.value || !dirty.value) return
  const value = fields()
  if (!value) {
    await nextTick()
    Array.from(formElement.value?.querySelectorAll<HTMLElement>('[aria-invalid="true"]') ?? [])
      .find((element) => element.getClientRects().length > 0)
      ?.focus()
    return
  }
  if (restoredRevision.value !== null && restoredRevision.value !== detail.value.revision) {
    openConflict(value, restoredRevision.value, detail.value)
    return
  }
  cacheNow()
  saving.value = true
  message.value = ''
  saveError.value = null
  const previous = detail.value
  const owner = auth.identity?.userId
  const current = sequence
  try {
    const headers = await csrfHeader(csrfExpired.value)
    if (current !== sequence || owner !== auth.identity?.userId) return
    const result = await saveProblem(previous.id, value, previous.revision, headers)
    if (current !== sequence || owner !== auth.identity?.userId) return
    if (
      !validDetail(result) ||
      result.id !== previous.id ||
      result.draftId !== previous.draftId ||
      result.revision <= previous.revision
    )
      throw new Error('无法确认保存响应')
    install(result)
    if (owner) removeDraftCache(owner, result.id)
    cacheNotice.value = ''
    cacheFailure = ''
    saved.value = true
  } catch (error) {
    if (current !== sequence || owner !== auth.identity?.userId) return
    saveError.value = error instanceof ApiError ? error : null
    const failure = writeFailure(error)
    if (
      error instanceof ApiError &&
      error.status === 409 &&
      error.code === 40910 &&
      validDetail(error.data) &&
      error.data.id === previous.id &&
      error.data.draftId === previous.draftId
    ) {
      openConflict(value, previous.revision, error.data)
    } else if (error instanceof ApiError && error.code === 40100) {
      openLoginRequired(error.requestId)
    } else if (failure.kind === 'csrf') {
      clearCsrf()
      csrfExpired.value = true
      stopped.value = false
      const request =
        error instanceof ApiError && error.requestId ? ` 请求编号：${error.requestId}` : ''
      try {
        await csrfHeader(true)
        if (current !== sequence || owner !== auth.identity?.userId) return
        csrfExpired.value = false
        message.value = `安全校验已失效，安全凭据已更新。当前输入已保留，请手动重试保存。${request}`
      } catch {
        if (current !== sequence || owner !== auth.identity?.userId) return
        message.value = `安全校验已失效，且暂时无法更新安全凭据。当前输入已保留，请稍后手动重试保存。${request}`
      }
    } else if (
      error instanceof ApiError &&
      failure.kind === 'rejected' &&
      (error.code === 40300 || error.code === 40400)
    ) {
      clearCsrf()
      stopped.value = true
      message.value = describe(error)
      copyFallback.value = true
    } else if (failure.kind === 'unknown') {
      clearCsrf()
      stopped.value = true
      copyFallback.value = true
      await beginPending(
        value,
        previous,
        error instanceof ApiError ? error.requestId : null,
        current,
        owner,
      )
    } else {
      clearCsrf()
      message.value = describe(error)
      stopped.value = failure.error.status !== 400
    }
  } finally {
    if (current === sequence) saving.value = false
  }
}

function openConflict(local: DraftFields, localRevision: number, server: ProblemDetail) {
  clearCsrf()
  stopped.value = false
  message.value = ''
  authLost.value = false
  pending.value = null
  conflict.value = { local, localRevision, server, reviewed: null }
  conflictVisible.value = true
  cacheNow()
}

function openLoginRequired(requestId: string | null) {
  clearCsrf()
  stopped.value = true
  authLost.value = true
  pending.value = null
  cacheNow()
  if (cacheBlocked.value) copyFallback.value = true
  message.value = `登录已失效，已停止保存。${
    cacheBlocked.value ? '当前输入未能暂存到本标签页，请先复制全部字段。' : '输入已暂存到本标签页。'
  }请重新登录后返回本页恢复。${requestId ? ` 请求编号：${requestId}` : ''}`
}

function relogin() {
  cacheNow()
  const target = route.fullPath
  router.push({ name: 'login', query: { redirect: target, relogin: '1' } })
}

async function beginPending(
  request: DraftFields,
  previous: ProblemDetail,
  requestId: string | null,
  current: number,
  owner: string | undefined,
) {
  pending.value = { request, problemId: previous.id, requestId }
  message.value = `保存结果待确认：网络或服务异常，无法判断本次保存是否已被服务器接受。不会自动重发原保存请求，正在先读取服务器内容。${requestId ? ` 请求编号：${requestId}` : ''}`
  await verifyPending(previous, current, owner)
}

async function verifyPending(previous: ProblemDetail, current: number, owner: string | undefined) {
  const snapshot = pending.value
  if (!snapshot) return
  verifying.value = true
  const requestId = saveError.value?.requestId ?? snapshot.requestId
  try {
    const result = await getProblem(snapshot.problemId)
    if (current !== sequence || owner !== auth.identity?.userId || !pending.value) return
    if (
      !validDetail(result) ||
      result.id !== snapshot.problemId ||
      result.draftId !== previous.draftId
    )
      throw new Error('无法确认服务器内容')
    pending.value = null
    if (sameFields(result, snapshot.request)) {
      rebase(result)
      saved.value = false
      if (owner) removeDraftCache(owner, result.id)
      cached.value = false
      cacheBlocked.value = false
      message.value = `已与服务器同步：服务器当前内容与本次提交的字段一致，但不代表该次请求一定已被接受。修订号为 ${result.revision}，请按需继续编辑。`
    } else if (result.revision === previous.revision) {
      detail.value = result
      stopped.value = false
      saved.value = false
      message.value = `服务器仍为修订号 ${result.revision}，内容与本次提交不同，未确认本次保存生效。当前输入已保留，请手动重试保存。`
    } else {
      openConflict(snapshot.request, previous.revision, result)
    }
  } catch (error) {
    if (current !== sequence || owner !== auth.identity?.userId || !pending.value) return
    stopped.value = true
    if (error instanceof ApiError && error.code === 40100) {
      authLost.value = true
      message.value = `登录已失效，无法确认保存结果。${
        cacheBlocked.value ? '请先复制全部字段，' : '输入已暂存，'
      }重新登录后返回本页继续确认。${requestId ? ` 请求编号：${requestId}` : ''}`
    } else if (error instanceof ApiError && (error.code === 40300 || error.code === 40400)) {
      message.value = `${describe(error)} 保存结果仍待确认，请复制需要保留的内容。`
    } else {
      message.value = `保存结果仍待确认：暂时无法读取服务器内容。当前输入已保留，编辑和保存已暂停；可重新确认结果、复制全部字段或经提醒离开。${requestId ? ` 请求编号：${requestId}` : ''}`
    }
  } finally {
    if (current === sequence) verifying.value = false
  }
}

function rebase(value: ProblemDetail) {
  detail.value = value
  baseline = formSignature(form)
  conflict.value = null
  pending.value = null
  stopped.value = false
}

function recheckPending() {
  if (!pending.value || !detail.value) return
  void verifyPending(detail.value, sequence, auth.identity?.userId)
}

function confirmMerge(merged: DraftFields, server: ProblemDetail) {
  if (!detail.value || !conflict.value) return
  const installed = formFromFields(server)
  const synced = sameFields(merged, server)
  Object.assign(form, formFromFields(merged))
  baseline = synced ? formSignature(form) : formSignature(installed)
  restoredRevision.value = null
  detail.value = server
  conflict.value = null
  stopped.value = false
  saveError.value = null
  saved.value = false
  message.value = synced
    ? `合并结果与服务器修订号 ${server.revision} 完全一致，已与服务器同步，无需再次保存。`
    : `已采用服务器修订号 ${server.revision} 作为基线。请检查表单后手动保存。`
  cacheNow()
}

function discardConflict(server: ProblemDetail) {
  if (!detail.value) return
  Object.assign(form, formFromFields(server))
  baseline = formSignature(form)
  restoredRevision.value = null
  detail.value = server
  conflict.value = null
  stopped.value = false
  saveError.value = null
  saved.value = false
  message.value = `已放弃本地修改并采用服务器修订号 ${server.revision}。`
  const owner = auth.identity?.userId
  if (owner) removeDraftCache(owner, server.id)
  cacheNotice.value = ''
  cacheFailure = ''
}

async function canLeave() {
  cacheNow()
  if (auth.leaving) return true
  if (saving.value) return false
  if (!dirty.value && !conflict.value && !pending.value && !restore.value) return true
  return window.confirm(
    cacheFailure
      ? '修改尚未保存到服务器，且未能暂存到本标签页。离开会丢失当前输入，建议先复制全部字段。确认离开？'
      : '修改、冲突或保存结果可能尚未确认。确认离开后再进入同一题目，将先读取服务器并检查本标签页暂存。',
  )
}

function beforeUnload(event: BeforeUnloadEvent) {
  cacheNow()
  if (dirty.value || saving.value || pending.value || conflict.value || restore.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

watch(
  [
    () => JSON.stringify(form),
    () => auth.leaving,
    () => saving.value,
    () => pending.value,
    () => (restore.value ? 'restore' : conflict.value ? 'conflict' : 'normal'),
  ],
  () => {
    const state = saving.value
      ? 'busy'
      : dirty.value || conflict.value || restore.value || pending.value
        ? 'unsaved'
        : 'saved'
    setEditingState(auth.identity ? state : null)
    if (auth.leaving) return
    if (restore.value) return
    window.clearTimeout(cacheTimer)
    cacheTimer = window.setTimeout(cacheNow, 400)
  },
  { immediate: true },
)
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
watch(() => route.params.id, load, { immediate: true })
watch(
  () => auth.identity?.userId,
  (userId, previous) => {
    if (previous && userId && userId !== previous) {
      clearDraftCaches(previous)
    }
    sequence++
    detail.value = null
    Object.keys(form).forEach((key) => {
      form[key as keyof typeof form] = ''
    })
    saving.value = false
    loading.value = false
    stopped.value = true
    saveError.value = null
    restore.value = null
    conflict.value = null
    pending.value = null
    incompatible.value = null
    window.clearTimeout(cacheTimer)
    cacheNotice.value = ''
    cacheFailure = ''
    authLost.value = !userId
    message.value = '当前登录身份已变化，请重新进入编辑页。'
  },
)
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  window.clearTimeout(cacheTimer)
  sequence++
  setEditingState(null)
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
  <section class="editor">
    <header>
      <div>
        <h1>编辑题目草稿</h1>
        <p>允许先保存不完整草稿，再逐步完善内容。</p>
      </div>
      <Button
        variant="outline"
        :disabled="saving"
        @click="router.push({ name: 'problems', query: route.query })"
        >返回题库</Button
      >
    </header>
    <p v-if="loading" role="status">正在读取草稿…</p>
    <p v-if="message" class="error" role="alert">{{ message }}</p>
    <p v-if="cacheNotice" class="notice" role="status">{{ cacheNotice }}</p>
    <p v-if="copied" class="notice" role="status">{{ copied }}</p>
    <section v-if="incompatible" class="restore" aria-label="不兼容暂存">
      <p>这份暂存仅供复制，不会写入当前表单。放弃后可为当前编辑重新暂存。</p>
      <div class="actions">
        <Button type="button" variant="outline" @click="copyAll">复制不兼容暂存</Button>
        <Button type="button" variant="outline" @click="discardIncompatible">放弃不兼容暂存</Button>
      </div>
    </section>
    <section v-if="restore" class="restore" aria-labelledby="restore-title">
      <h2 id="restore-title">发现本标签页暂存的修改</h2>
      <p>
        暂存基于草稿修订号 {{ restore.draft.revision }}，服务器当前为修订号
        {{ restore.reviewed.revision }}。恢复后内容不会自动保存。
      </p>
      <div class="actions">
        <Button type="button" @click="restoreDraft">恢复暂存内容</Button>
        <Button type="button" variant="outline" @click="discardRestore">放弃暂存</Button>
      </div>
    </section>
    <Button v-if="!loading && !detail" variant="outline" @click="load">重新读取草稿</Button>
    <Button v-if="!loading && !detail && authLost" type="button" @click="relogin"
      >重新登录并返回本页</Button
    >
    <ProblemConflictPanel
      v-if="detail && conflict"
      v-show="conflictVisible"
      :key="`${conflict.server.id}-${conflict.server.revision}`"
      :local="conflict.local"
      :local-revision="conflict.localRevision"
      :server="conflict.server"
      :reviewed="conflict.reviewed"
      @confirm="confirmMerge"
      @discard="discardConflict"
      @cancel="conflictVisible = false"
    />
    <div v-if="conflict" class="actions">
      <Button v-if="!conflictVisible" type="button" @click="conflictVisible = true"
        >继续对照差异</Button
      >
      <Button type="button" variant="outline" @click="copyAll">复制全部本地字段</Button>
      <textarea
        v-if="copyFallback"
        class="copy"
        readonly
        rows="6"
        aria-label="可复制内容"
        :value="copyText"
        @focus="($event.target as HTMLTextAreaElement).select()"
      ></textarea>
    </div>
    <form v-if="detail && !conflict" ref="formElement" novalidate @submit.prevent="save">
      <p class="metadata">
        题号 {{ detail.problemNo }} · 草稿修订号 {{ detail.revision }} · 更新时间
        {{ detail.updatedAt ?? '—' }}
      </p>
      <p role="status" aria-live="polite">{{ status }}</p>
      <fieldset :disabled="frozen">
        <legend>基本信息</legend>
        <div class="field">
          <Label for="draft-title">标题</Label
          ><Input
            id="draft-title"
            v-model="form.title"
            :aria-invalid="!!errors.title"
            aria-describedby="error-title"
          />
          <p id="error-title" class="error">{{ errors.title }}</p>
        </div>
        <div class="field">
          <Label for="draft-difficulty">难度</Label>
          <Select
            :model-value="form.difficulty || 'UNSET'"
            :disabled="frozen"
            @update:model-value="form.difficulty = $event === 'UNSET' ? '' : String($event)"
          >
            <SelectTrigger id="draft-difficulty"><SelectValue /></SelectTrigger>
            <SelectContent
              ><SelectItem value="UNSET">未设置</SelectItem
              ><SelectItem value="EASY">简单</SelectItem><SelectItem value="MEDIUM">中等</SelectItem
              ><SelectItem value="HARD">困难</SelectItem></SelectContent
            >
          </Select>
        </div>
        <div class="field">
          <Label for="draft-tags">标签（每行一个，最多 5 个）</Label
          ><Textarea
            id="draft-tags"
            v-model="form.tags"
            :rows="5"
            :aria-invalid="!!errors.tags"
            aria-describedby="error-tags"
          />
          <p id="error-tags" class="error">{{ errors.tags }}</p>
        </div>
      </fieldset>
      <fieldset :disabled="frozen">
        <legend>题面与说明</legend>
        <div v-for="field in textFields" :key="field.key" class="field">
          <MarkdownEditor
            :id="`draft-${field.key}`"
            v-model="form[field.key]"
            :label="field.label"
            :disabled="frozen"
            :error="errors[field.key]"
            :rows="field.key === 'statement' ? 12 : 5"
          />
        </div>
      </fieldset>
      <fieldset :disabled="frozen">
        <legend>资源限制（可分别留空）</legend>
        <div class="resources">
          <div v-for="field in resourceFields" :key="field.key" class="field">
            <Label :for="`draft-${field.key}`">{{ field.label }}</Label
            ><Input
              :id="`draft-${field.key}`"
              v-model="form[field.key]"
              inputmode="numeric"
              :aria-invalid="!!errors[field.key]"
              :aria-describedby="`error-${field.key}`"
            />
            <p :id="`error-${field.key}`" class="error">{{ errors[field.key] }}</p>
          </div>
        </div>
      </fieldset>
      <section class="artifacts">
        <h2>测试数据清单（只读）</h2>
        <p v-if="!detail.artifacts.length">尚未登记测试数据。</p>
        <ul v-else>
          <li v-for="artifact in detail.artifacts" :key="artifact.artifactId">
            {{ artifact.kind }} · 第 {{ artifact.caseOrder }} 组 · {{ artifact.fileName }} ·
            {{ artifact.byteSize }} 字节 · 展示顺序 {{ artifact.displayOrder }}<br /><small
              >SHA-256：{{ artifact.contentDigest }}</small
            >
          </li>
        </ul>
      </section>
      <footer>
        <p v-if="cacheNotice">{{ cacheNotice }}</p>
        <p v-else>修改会在输入后 1 秒内暂存到当前标签页，仍须手动保存到服务器。</p>
        <div class="actions">
          <Button type="button" variant="outline" @click="copyAll">复制全部字段</Button>
          <Button
            v-if="pending"
            type="button"
            variant="outline"
            :disabled="saving || verifying"
            @click="recheckPending"
            >{{ verifying ? '正在读取服务器…' : '重新确认保存结果' }}</Button
          >
          <Button v-if="authLost" type="button" @click="relogin">重新登录并返回本页</Button>
          <Button type="submit" :disabled="!dirty || frozen || stopped || restore">{{
            saving ? '正在保存…' : '保存草稿'
          }}</Button>
        </div>
        <p v-if="pending" role="alert">
          保存结果待确认。服务器内容读取完成前不能继续编辑或保存，也不会自动重发原保存请求。
        </p>
        <p v-if="authLost" role="alert">重新登录后请确认是否恢复暂存内容；系统不会自动保存。</p>
        <p v-if="csrfExpired" class="error">
          安全校验曾失效。下次保存会先获取最新安全凭据，不会自动重放原请求。
        </p>
      </footer>
      <textarea
        v-if="copyFallback"
        class="copy"
        readonly
        rows="6"
        aria-label="可复制内容"
        :value="copyText"
        @focus="($event.target as HTMLTextAreaElement).select()"
      ></textarea>
    </form>
  </section>
</template>

<style scoped>
.editor {
  max-width: 1000px;
  margin: auto;
}
header,
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
h1 {
  font-size: 24px;
  font-weight: 650;
}
h2,
legend {
  font-size: 16px;
  font-weight: 600;
}
header p,
.metadata,
footer p {
  color: var(--muted-text);
  font-size: 13px;
}
form {
  display: grid;
  gap: 20px;
  margin-top: 24px;
}
fieldset,
.artifacts {
  min-width: 0;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
}
legend {
  padding: 0 8px;
}
.field {
  min-width: 0;
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}
textarea,
select {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 8px 12px;
  background: var(--background);
  font: inherit;
}
textarea {
  resize: vertical;
}
textarea:focus-visible,
select:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.resources {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.error {
  color: var(--destructive, #b42318);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.notice {
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--muted, #f6f6f4);
  font-size: 13px;
}
.restore {
  display: grid;
  gap: 10px;
  margin-top: 20px;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
}
.restore h2 {
  font-size: 16px;
  font-weight: 650;
}
.restore p {
  color: var(--muted-text);
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.copy {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 8px 12px;
  background: var(--background);
  font: inherit;
  font-size: 12px;
}
li {
  margin: 12px 0;
  overflow-wrap: anywhere;
}
@media (max-width: 760px) {
  .resources {
    grid-template-columns: 1fr;
  }
}
</style>
