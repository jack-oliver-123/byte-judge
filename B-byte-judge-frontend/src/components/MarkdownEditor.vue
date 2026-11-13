<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

const props = defineProps<{
  id: string
  label: string
  error?: string
  disabled: boolean
  rows: number
}>()
const model = defineModel<string>({ required: true })
const previewing = ref(false)
const parser = new MarkdownIt({ html: false, linkify: false, typographer: false })
parser.validateLink = (url) => /^https?:\/\//i.test(url)
parser.renderer.rules.image = () => '<span>（图片不加载）</span>'
parser.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
  tokens[index]!.attrSet('target', '_blank')
  tokens[index]!.attrSet('rel', 'noopener noreferrer')
  return renderer.renderToken(tokens, index, options)
}
const html = computed(() =>
  DOMPurify.sanitize(parser.render(model.value), {
    ALLOWED_TAGS: [
      'h1',
      'h2',
      'h3',
      'h4',
      'h5',
      'h6',
      'p',
      'br',
      'hr',
      'ul',
      'ol',
      'li',
      'blockquote',
      'pre',
      'code',
      'table',
      'thead',
      'tbody',
      'tr',
      'th',
      'td',
      'strong',
      'em',
      's',
      'a',
      'span',
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'start'],
    ADD_URI_SAFE_ATTR: ['target', 'rel'],
    ALLOWED_URI_REGEXP: /^https?:\/\//i,
  }),
)
</script>

<template>
  <div class="markdown-editor">
    <div class="toolbar">
      <Label :for="id">{{ label }}</Label>
      <Button
        type="button"
        size="sm"
        variant="outline"
        :aria-pressed="previewing"
        :aria-controls="`${id}-preview`"
        @click="previewing = !previewing"
      >
        {{ previewing ? `编辑${label}` : `预览${label}` }}
      </Button>
    </div>
    <Textarea
      v-show="!previewing"
      :id="id"
      v-model="model"
      :disabled="disabled"
      :rows="rows"
      :aria-invalid="!!error"
      :aria-describedby="`${id}-error`"
    />
    <div
      v-show="previewing"
      :id="`${id}-preview`"
      class="preview"
      role="region"
      :aria-label="`${label}预览`"
      :aria-invalid="!!error"
      :aria-describedby="`${id}-error`"
      tabindex="0"
    >
      <p v-if="!model">暂无内容可预览。</p>
      <div v-else v-html="html"></div>
    </div>
    <p :id="`${id}-error`" class="error">{{ props.error }}</p>
  </div>
</template>

<style scoped>
.markdown-editor {
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.preview {
  min-width: 0;
  min-height: 130px;
  max-height: 650px;
  overflow: auto;
  overflow-wrap: anywhere;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 16px;
}
.preview:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
.preview :deep(p),
.preview :deep(ul),
.preview :deep(ol),
.preview :deep(pre),
.preview :deep(blockquote),
.preview :deep(table) {
  margin: 12px 0;
}
.preview :deep(h1),
.preview :deep(h2),
.preview :deep(h3),
.preview :deep(h4),
.preview :deep(h5),
.preview :deep(h6) {
  font-weight: 650;
  margin: 16px 0 8px;
}
.preview :deep(h1) {
  font-size: 24px;
}
.preview :deep(h2) {
  font-size: 20px;
}
.preview :deep(ul) {
  list-style: disc;
  padding-left: 24px;
}
.preview :deep(ol) {
  list-style: decimal;
  padding-left: 24px;
}
.preview :deep(pre) {
  white-space: pre;
  overflow: auto;
  background: var(--muted);
  padding: 12px;
  border-radius: 6px;
}
.preview :deep(blockquote) {
  border-left: 3px solid var(--border);
  padding-left: 12px;
}
.preview :deep(table) {
  border-collapse: collapse;
}
.preview :deep(th),
.preview :deep(td) {
  border: 1px solid var(--border);
  padding: 8px 12px;
}
.preview :deep(a) {
  color: var(--primary);
  text-decoration: underline;
}
.error {
  color: var(--destructive, #b42318);
  font-size: 13px;
  overflow-wrap: anywhere;
}
</style>
