<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { DraftFields } from '@/api/problems'
import {
  describeArtifact,
  differingFields,
  displayFieldValue,
  fieldLabels,
  fieldUnit,
  fieldsFromForm,
  formFromFields,
  manifestsChange,
  metadataSignature,
  normalizeGiven,
  sameFields,
  textFields,
  type FieldKey,
} from '@/lib/problem-fields'
import type { ProblemDetail } from '@/types/api'

const props = defineProps<{
  local: DraftFields
  localRevision: number
  server: ProblemDetail
  reviewed: ProblemDetail | null
}>()

const emit = defineEmits<{
  confirm: [fields: DraftFields, server: ProblemDetail]
  discard: [server: ProblemDetail]
  cancel: []
}>()

type Choice = 'local' | 'server'
const serverFields = computed(() => normalizeGiven(props.server))
const differing = computed(() => differingFields(props.local, serverFields.value))
const metadataChanged = computed(() =>
  props.reviewed ? metadataSignature(props.reviewed) !== metadataSignature(props.server) : true,
)
const artifactChanged = computed(() =>
  props.reviewed ? manifestsChange(props.reviewed, props.server) : true,
)
const choices = reactive<Partial<Record<FieldKey, Choice>>>({})
const acknowledged = ref(false)
const discarding = ref(false)

function isLongText(key: FieldKey) {
  return textFields.some((field) => field.key === key)
}

function chosen(key: FieldKey): DraftFields[FieldKey] {
  if (choices[key] === 'server') return serverFields.value[key]
  return props.local[key]
}

const pending = computed(() => differing.value.filter((key) => !choices[key]))
const merged = computed<DraftFields>(() => {
  const result = { ...serverFields.value }
  for (const key of differing.value) result[key] = chosen(key) as never
  return result
})
const resourceError = computed(() => {
  const cpu = merged.value.timeLimitCpuMs
  const wall = merged.value.timeLimitWallMs
  return cpu !== null && wall !== null && wall < cpu ? '合并后墙钟时限不能小于 CPU 时限。' : ''
})
const invalid = computed(() => {
  if (resourceError.value) return resourceError.value
  const errors = fieldsFromForm(formFromFields(merged.value)).errors
  return Object.values(errors)[0] ?? ''
})
const canConfirm = computed(
  () =>
    acknowledged.value &&
    pending.value.length === 0 &&
    !invalid.value &&
    (metadataChanged.value || differing.value.length > 0),
)

function confirm() {
  if (!canConfirm.value) return
  emit(
    'confirm',
    sameFields(merged.value, serverFields.value) ? serverFields.value : merged.value,
    props.server,
  )
}
</script>

<template>
  <section class="conflict" aria-labelledby="conflict-heading">
    <header>
      <div>
        <h2 id="conflict-heading">草稿已被他人修改</h2>
        <p>
          你的输入基于修订号 {{ localRevision }}，服务器当前为修订号
          {{ server.revision }}。请逐项选择采用哪份内容，确认后再手动保存。
        </p>
      </div>
      <Badge>修订冲突</Badge>
    </header>
    <Alert v-if="artifactChanged">
      <AlertDescription
        >测试数据清单或题目元信息已随最新修订变化，请核对下方最新清单后再确认采用修订号
        {{ server.revision }}。</AlertDescription
      >
    </Alert>
    <ol v-if="differing.length" class="fields">
      <li v-for="key in differing" :key="key">
        <fieldset>
          <legend>{{ fieldLabels[key] }}</legend>
          <div class="pairs">
            <label class="side">
              <span
                ><input
                  type="radio"
                  :name="`conflict-${key}`"
                  :checked="choices[key] === 'local'"
                  @change="choices[key] = 'local'"
                />
                采用你的内容</span
              >
              <pre v-if="isLongText(key)" class="value">{{
                displayFieldValue(key, local[key])
              }}</pre>
              <p v-else class="value">{{ displayFieldValue(key, local[key]) }}</p>
            </label>
            <label class="side">
              <span
                ><input
                  type="radio"
                  :name="`conflict-${key}`"
                  :checked="choices[key] === 'server'"
                  @change="choices[key] = 'server'"
                />
                采用服务器内容</span
              >
              <pre v-if="isLongText(key)" class="value">{{
                displayFieldValue(key, serverFields[key])
              }}</pre>
              <p v-else class="value">
                {{ displayFieldValue(key, serverFields[key])
                }}{{
                  serverFields[key] === null || key === 'tags' || key === 'difficulty'
                    ? ''
                    : fieldUnit(key)
                }}
              </p>
            </label>
          </div>
        </fieldset>
      </li>
    </ol>
    <p v-else>可编辑字段与服务器一致，仅题目元信息发生变化。</p>
    <section v-if="metadataChanged" class="metadata">
      <h3>服务器最新信息</h3>
      <p>
        草稿修订号 {{ server.revision }} · 更新时间 {{ server.updatedAt ?? '—' }} · 状态
        {{ server.status }}
      </p>
      <h3>服务器最新测试数据清单</h3>
      <p v-if="!server.artifacts.length">尚未登记测试数据。</p>
      <ul v-else>
        <li v-for="artifact in server.artifacts" :key="artifact.artifactId">
          {{ describeArtifact(artifact) }}
        </li>
      </ul>
    </section>
    <p v-if="pending.length" class="error" role="status">
      还有 {{ pending.length }} 项差异未选择处理方式，全部选择后才能确认。
    </p>
    <p v-if="invalid" class="error" role="alert">{{ invalid }}</p>
    <label class="acknowledge">
      <input v-model="acknowledged" type="checkbox" />
      我已核对以上差异和最新测试数据清单，确认以修订号 {{ server.revision }} 继续编辑。
    </label>
    <footer>
      <Button type="button" variant="outline" @click="emit('cancel')">取消对照</Button>
      <Button type="button" variant="outline" @click="discarding = true">放弃我的修改</Button>
      <Button type="button" :disabled="!canConfirm" @click="confirm">确认合并并返回表单</Button>
    </footer>
    <div v-if="discarding" class="discard" role="group" aria-labelledby="discard-title">
      <h3 id="discard-title">放弃本地修改？</h3>
      <p>
        将采用服务器修订号
        {{ server.revision }} 的全部内容，你的输入不会保留。建议先复制需要保留的内容。
      </p>
      <div class="actions">
        <Button type="button" variant="outline" @click="discarding = false">取消</Button>
        <Button type="button" variant="destructive" @click="emit('discard', server)"
          >确认放弃并采用服务器内容</Button
        >
      </div>
    </div>
  </section>
</template>

<style scoped>
.conflict {
  display: grid;
  gap: 18px;
  margin-top: 24px;
  border: 1px solid var(--destructive, #b42318);
  border-radius: 12px;
  padding: 20px;
}
header,
footer,
.actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
h2 {
  font-size: 18px;
  font-weight: 650;
}
h3,
legend {
  font-size: 14px;
  font-weight: 600;
}
header p,
.metadata p,
.discard p {
  color: var(--muted-text);
  font-size: 13px;
}
.fields {
  display: grid;
  gap: 16px;
  padding-left: 20px;
}
fieldset {
  min-width: 0;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px;
}
legend {
  padding: 0 6px;
}
.pairs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.side {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 8px;
  border-radius: 8px;
  padding: 10px;
  background: var(--muted, #f6f6f4);
}
.side span {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.value {
  min-height: 1.5em;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
}
.side .value {
  max-height: 220px;
  overflow: auto;
  padding: 8px;
  border-radius: 6px;
  background: var(--background, #fff);
}
.metadata {
  display: grid;
  gap: 8px;
}
.acknowledge {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
}
.acknowledge input {
  margin-top: 3px;
}
.error {
  color: var(--destructive, #b42318);
  font-size: 13px;
}
.discard {
  display: grid;
  gap: 10px;
  border-radius: 10px;
  padding: 16px;
  background: var(--muted, #f6f6f4);
}
@media (max-width: 760px) {
  .pairs {
    grid-template-columns: 1fr;
  }
}
</style>
