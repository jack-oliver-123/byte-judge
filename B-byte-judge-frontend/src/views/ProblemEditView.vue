<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
const errors = ref<Record<string, string>>({})
const saveError = shallowRef<ApiError | null>(null)
const formElement = ref<HTMLFormElement | null>(null)
const form = reactive({
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
let baseline = ''
let sequence = 0

const textFields = [
  { key: 'statement', label: '题面' },
  { key: 'inputDescription', label: '输入说明' },
  { key: 'outputDescription', label: '输出说明' },
] as const
const resourceFields = [
  { key: 'timeLimitCpuMs', label: 'CPU 时限（ms）', min: 100, max: 10000 },
  { key: 'timeLimitWallMs', label: '墙钟时限（ms）', min: 100, max: 30000 },
  { key: 'memoryLimitMiB', label: '内存限制（MiB）', min: 64, max: 2048 },
] as const
const dirty = computed(() => detail.value !== null && JSON.stringify(form) !== baseline)
const conflictRevision = computed(() => {
  if (saveError.value?.status !== 409 || saveError.value.code !== 40910) return null
  const current = saveError.value.data as Partial<ProblemDetail> | null
  return current?.id === detail.value?.id && Number.isSafeInteger(current?.revision)
    ? current?.revision
    : null
})
const status = computed(() =>
  saving.value
    ? '正在保存到服务器…'
    : stopped.value
      ? '保存已停止，请保留输入'
      : dirty.value
        ? '有尚未保存到服务器的修改'
        : saved.value
          ? '已保存到服务器'
          : '已读取服务器草稿',
)

function strip(value: string) {
  const whitespace = (index: number) => {
    const code = value.charCodeAt(index)
    return (
      (code >= 9 && code <= 13) || (code >= 28 && code <= 32) || /\p{Z}/u.test(value.charAt(index))
    )
  }
  let start = 0
  let end = value.length
  while (start < end && whitespace(start)) start++
  while (end > start && whitespace(end - 1)) end--
  return value.slice(start, end)
}

function install(value: ProblemDetail) {
  detail.value = value
  Object.assign(form, {
    title: value.title,
    statement: value.statement ?? '',
    inputDescription: value.inputDescription ?? '',
    outputDescription: value.outputDescription ?? '',
    difficulty: value.difficulty ?? '',
    tags: (value.tags ?? []).join('\n'),
    timeLimitCpuMs: value.timeLimitCpuMs === null ? '' : String(value.timeLimitCpuMs),
    timeLimitWallMs: value.timeLimitWallMs === null ? '' : String(value.timeLimitWallMs),
    memoryLimitMiB: value.memoryLimitMiB === null ? '' : String(value.memoryLimitMiB),
  })
  baseline = JSON.stringify(form)
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
  loading.value = true
  detail.value = null
  stopped.value = false
  csrfExpired.value = false
  saved.value = false
  message.value = ''
  errors.value = {}
  saveError.value = null
  const owner = auth.identity?.userId
  try {
    const value = await getProblem(String(route.params.id))
    if (current !== sequence || owner !== auth.identity?.userId) return
    if (!validDetail(value)) throw new Error('草稿响应格式异常')
    install(value)
  } catch (error) {
    if (current === sequence)
      message.value = error instanceof ApiError ? describe(error) : '草稿读取失败，请稍后重试。'
  } finally {
    if (current === sequence) loading.value = false
  }
}

function fields(): DraftFields | null {
  const found: Record<string, string> = {}
  const title = strip(form.title)
  if (!title || Array.from(title).length > 100) found.title = '标题需为 1–100 个 Unicode 字符。'
  const tags = form.tags === '' ? [] : form.tags.split('\n').map(strip)
  if (tags.length > 5 || tags.some((tag) => !tag || Array.from(tag).length > 20))
    found.tags = '每行一个标签，最多 5 个，每个 1–20 个字符，不可为空。'
  const bytes = textFields.reduce(
    (total, field) => total + new TextEncoder().encode(form[field.key]).length,
    0,
  )
  if (bytes > 102400) found.statement = '题面与输入、输出说明合计不得超过 102400 UTF-8 字节。'
  const resources = { timeLimitCpuMs: null, timeLimitWallMs: null, memoryLimitMiB: null } as Pick<
    DraftFields,
    'timeLimitCpuMs' | 'timeLimitWallMs' | 'memoryLimitMiB'
  >
  for (const resource of resourceFields) {
    const raw = form[resource.key].trim()
    if (!raw) continue
    const value = Number(raw)
    if (
      !/^\d+$/.test(raw) ||
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
  errors.value = found
  if (Object.keys(found).length) return null
  return {
    title,
    tags,
    statement: form.statement === '' ? null : form.statement,
    inputDescription: form.inputDescription === '' ? null : form.inputDescription,
    outputDescription: form.outputDescription === '' ? null : form.outputDescription,
    difficulty: form.difficulty === '' ? null : (form.difficulty as DraftFields['difficulty']),
    ...resources,
  }
}

function validDetail(value: ProblemDetail) {
  return (
    value &&
    typeof value.id === 'string' &&
    typeof value.draftId === 'string' &&
    Number.isSafeInteger(value.problemNo) &&
    Number.isSafeInteger(value.revision) &&
    value.revision >= 0 &&
    typeof value.title === 'string' &&
    ['statement', 'inputDescription', 'outputDescription'].every((key) => {
      const field = value[key as 'statement']
      return field === null || typeof field === 'string'
    }) &&
    (value.difficulty === null || ['EASY', 'MEDIUM', 'HARD'].includes(value.difficulty)) &&
    (value.tags === null ||
      (Array.isArray(value.tags) && value.tags.every((tag) => typeof tag === 'string'))) &&
    resourceFields.every(
      (resource) => value[resource.key] === null || Number.isSafeInteger(value[resource.key]),
    ) &&
    Array.isArray(value.artifacts)
  )
}

async function save() {
  if (!detail.value || saving.value || stopped.value || !dirty.value) return
  const value = fields()
  if (!value) {
    await nextTick()
    formElement.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    return
  }
  saving.value = true
  message.value = ''
  saveError.value = null
  const previous = detail.value
  const owner = auth.identity?.userId
  const current = sequence
  try {
    const headers = await csrfHeader(csrfExpired.value)
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
    saved.value = true
  } catch (error) {
    if (current !== sequence || owner !== auth.identity?.userId) return
    saveError.value = error instanceof ApiError ? error : null
    const failure = writeFailure(error)
    if (failure.kind === 'csrf') {
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
    } else {
      clearCsrf()
      message.value =
        failure.kind === 'unknown'
          ? `网络或服务异常，无法确认服务器是否已保存。当前输入已保留，请重试确认服务器内容。${saveError.value?.requestId ? ` 请求编号：${saveError.value.requestId}` : ''}`
          : describe(error)
      stopped.value = failure.kind === 'unknown' || failure.error.status !== 400
    }
  } finally {
    if (current === sequence) saving.value = false
  }
}

function canLeave() {
  if (saving.value) return false
  return !dirty.value || window.confirm('修改尚未保存到服务器，离开将丢失当前输入。确认离开？')
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || saving.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
watch(() => route.params.id, load, { immediate: true })
watch(
  () => auth.identity?.userId,
  () => {
    sequence++
    detail.value = null
    Object.keys(form).forEach((key) => {
      form[key as keyof typeof form] = ''
    })
    saving.value = false
    loading.value = false
    stopped.value = true
    saveError.value = null
    message.value = '当前登录身份已变化，请重新进入编辑页。'
  },
)
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  sequence++
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
    <p v-if="conflictRevision !== null" class="error">
      服务器当前草稿修订号：{{ conflictRevision }}。本地输入仍基于修订号 {{ detail?.revision }}。
    </p>
    <Button v-if="!loading && !detail" variant="outline" @click="load">重新读取草稿</Button>
    <form v-if="detail" ref="formElement" novalidate @submit.prevent="save">
      <p class="metadata">
        题号 {{ detail.problemNo }} · 草稿修订号 {{ detail.revision }} · 更新时间
        {{ detail.updatedAt ?? '—' }}
      </p>
      <p role="status" aria-live="polite">{{ status }}</p>
      <fieldset :disabled="saving">
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
            :disabled="saving"
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
      <fieldset :disabled="saving">
        <legend>题面与说明</legend>
        <div v-for="field in textFields" :key="field.key" class="field">
          <Label :for="`draft-${field.key}`">{{ field.label }}</Label
          ><Textarea
            :id="`draft-${field.key}`"
            v-model="form[field.key]"
            :rows="field.key === 'statement' ? 12 : 5"
            :aria-invalid="!!errors[field.key]"
            :aria-describedby="`error-${field.key}`"
          />
          <p :id="`error-${field.key}`" class="error">{{ errors[field.key] }}</p>
        </div>
      </fieldset>
      <fieldset :disabled="saving">
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
        <p>本页尚未提供本地暂存；离开前请保存或复制需要保留的输入。</p>
        <p v-if="csrfExpired" class="error">
          安全校验曾失效。下次保存会先获取最新安全凭据，不会自动重放原请求。
        </p>
        <Button type="submit" :disabled="!dirty || saving || stopped">{{
          saving ? '正在保存…' : '保存草稿'
        }}</Button>
      </footer>
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
