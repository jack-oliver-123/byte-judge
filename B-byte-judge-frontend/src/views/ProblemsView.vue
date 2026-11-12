<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowUpRight,
  FileCode2,
  FilePlus2,
  Inbox,
  LoaderCircle,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  Upload,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'
import {
  createProblem,
  deleteProblem,
  getProblem,
  listProblems,
  type ProblemQuery,
} from '@/api/problems'
import { ApiError } from '@/api/http'
import { csrfHeader } from '@/api/http'
import type { ProblemDetail, ProblemSummary } from '@/types/api'

const route = useRoute()
const router = useRouter()
const initialTitle = typeof route.query.title === 'string' ? route.query.title : ''
const initialDifficulty = ['EASY', 'MEDIUM', 'HARD'].includes(String(route.query.difficulty))
  ? String(route.query.difficulty)
  : ''
const initialPage = Number(route.query.pageNum)
const initialSize = Number(route.query.pageSize)
const filters = reactive({ title: initialTitle, difficulty: initialDifficulty || 'ALL' })
const applied = reactive<ProblemQuery>({
  title: initialTitle,
  difficulty: initialDifficulty,
  pageNum: Number.isSafeInteger(initialPage) && initialPage > 0 ? initialPage : 1,
  pageSize: [10, 20, 50].includes(initialSize) ? initialSize : 20,
})
const rows = ref<ProblemSummary[]>([])
const total = ref(0)
const loading = ref(true)
const loaded = ref(false)
const loadError = ref('')
const status = ref(route.query.includeDeleted === 'true' ? 'INCLUDE_DELETED' : 'ALL')
const createVisible = ref(false)
const creating = ref(false)
const newTitle = ref('')
const titleError = ref('')
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProblemDetail | null>(null)
const detailError = ref('')
const deleting = ref(false)
const deleteOpen = ref(false)
const syncedAt = ref('')
let loadSequence = 0
let detailSequence = 0

const pageDrafts = computed(() => rows.value.filter((row) => row.status === 'DRAFT').length)
const pageDeleted = computed(() => rows.value.filter((row) => row.status === 'DELETED').length)
const filtered = computed(() => Boolean(applied.title || applied.difficulty))
const difficultyText: Record<string, string> = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const difficultyClass: Record<string, string> = { EASY: 'easy', MEDIUM: 'medium', HARD: 'hard' }

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.code === 40100) return '登录已失效，请重新登录。'
    if (error.code === 40300) return '当前账号没有管理员权限。'
    if (error.code === 50300) return '题库服务暂时不可用，请稍后重试。'
  }
  return error instanceof Error ? error.message : fallback
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function artifactCounts(value: ProblemDetail | null) {
  const kinds = value?.artifacts ?? []
  return {
    samples: new Set(
      kinds.filter((item) => item.kind.startsWith('SAMPLE_')).map((item) => item.caseOrder),
    ).size,
    hidden: new Set(
      kinds.filter((item) => item.kind.startsWith('HIDDEN_')).map((item) => item.caseOrder),
    ).size,
  }
}

async function load() {
  void router.replace({
    query: {
      title: applied.title || undefined,
      difficulty: applied.difficulty || undefined,
      pageNum: String(applied.pageNum),
      pageSize: String(applied.pageSize),
      includeDeleted: status.value === 'INCLUDE_DELETED' ? 'true' : undefined,
    },
  })
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const page = await listProblems({
      ...applied,
      includeDeleted: status.value === 'INCLUDE_DELETED',
    })
    if (sequence !== loadSequence) return
    rows.value = page.items
    total.value = page.total
    loaded.value = true
    syncedAt.value = new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date())
  } catch (error) {
    if (sequence !== loadSequence) return
    rows.value = []
    total.value = 0
    loadError.value = errorMessage(error, '题目列表加载失败，请稍后重试。')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function applyFilters() {
  applied.title = filters.title.trim()
  applied.difficulty = filters.difficulty === 'ALL' ? '' : filters.difficulty
  applied.pageNum = 1
  void load()
}

function editProblem(id: string) {
  void router.push({ name: 'problem-edit', params: { id }, query: route.query })
}

function resetFilters() {
  filters.title = ''
  filters.difficulty = 'ALL'
  status.value = 'ALL'
  applyFilters()
}

function changeStatus() {
  applied.pageNum = 1
  void load()
}

function changePageSize(value: unknown) {
  applied.pageSize = Number(value)
  applied.pageNum = 1
  void load()
}

function openCreate() {
  newTitle.value = ''
  titleError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  const title = newTitle.value.trim()
  titleError.value = !title
    ? '请输入题目标题'
    : Array.from(title).length > 100
      ? '标题最多 100 个字符'
      : ''
  if (titleError.value || creating.value) return
  creating.value = true
  try {
    await createProblem(title, await csrfHeader())
    createVisible.value = false
    toast.success('草稿已创建')
    resetFilters()
  } catch (error) {
    titleError.value = errorMessage(error, '创建草稿失败，请稍后重试。')
  } finally {
    creating.value = false
  }
}

async function openDetail(row: ProblemSummary) {
  const sequence = ++detailSequence
  drawerVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const result = await getProblem(row.id)
    if (sequence === detailSequence) detail.value = result
  } catch (error) {
    if (sequence === detailSequence)
      detailError.value = errorMessage(error, '题目详情加载失败，请稍后重试。')
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}

async function removeDraft() {
  if (!detail.value || deleting.value) return
  const current = detail.value
  deleting.value = true
  try {
    await deleteProblem(current.id, current.revision, await csrfHeader())
    toast.success('草稿已删除')
    drawerVisible.value = false
    if (rows.value.length === 1 && applied.pageNum > 1) applied.pageNum--
    await load()
  } catch (error) {
    toast.error(
      error instanceof ApiError && error.status === 409
        ? '草稿已被更新，请刷新后重试。'
        : errorMessage(error, '删除草稿失败，请稍后重试。'),
    )
  } finally {
    deleting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="problems-page page-enter">
    <div class="page-head">
      <div>
        <h1>题库管理</h1>
        <p>统一管理题目、难度与发布状态。</p>
      </div>
      <div class="page-actions">
        <TooltipProvider
          ><Tooltip
            ><TooltipTrigger as-child
              ><span tabindex="0" class="import-wrapper"
                ><Button variant="outline" disabled><Upload />批量导入</Button></span
              ></TooltipTrigger
            ><TooltipContent>批量导入暂未开放</TooltipContent></Tooltip
          ></TooltipProvider
        >
        <Button class="primary-action" @click="openCreate"><Plus />新建题目</Button>
      </div>
    </div>

    <section class="summary-strip" aria-label="列表统计" aria-live="polite">
      <div>
        <span>{{ filtered ? '筛选结果' : '当前范围' }}</span
        ><strong>{{ loading || loadError ? '—' : total }}</strong
        ><small>道题目</small>
      </div>
      <div>
        <span>本页草稿</span><strong>{{ loading || loadError ? '—' : pageDrafts }}</strong>
      </div>
      <div>
        <span>本页已删除</span><strong>{{ loading || loadError ? '—' : pageDeleted }}</strong>
      </div>
      <span class="sync-status" :class="{ failed: loadError }"
        ><LoaderCircle v-if="loading" :size="13" class="spin" /><i v-else aria-hidden="true"></i
        >{{ loading ? '正在更新' : loadError ? '更新失败' : `已更新 · ${syncedAt}` }}</span
      >
    </section>

    <section class="panel" aria-label="题目列表">
      <Tabs v-model="status" @update:model-value="changeStatus">
        <div class="panel-top">
          <TabsList class="list-tabs"
            ><TabsTrigger value="ALL">全部题目</TabsTrigger
            ><TabsTrigger value="INCLUDE_DELETED">包含已删除</TabsTrigger></TabsList
          ><span class="panel-note">按创建时间倒序</span>
        </div>
        <TabsContent :value="status" class="tab-content">
          <form class="filters" @submit.prevent="applyFilters">
            <div class="search-input">
              <Search aria-hidden="true" /><Input
                id="problem-search"
                v-model="filters.title"
                placeholder="搜索题目标题"
                aria-label="搜索题目标题"
              />
            </div>
            <Select v-model="filters.difficulty"
              ><SelectTrigger class="difficulty-select" aria-label="题目难度"
                ><SelectValue placeholder="全部难度" /></SelectTrigger
              ><SelectContent
                ><SelectItem value="ALL">全部难度</SelectItem
                ><SelectItem value="EASY">简单</SelectItem
                ><SelectItem value="MEDIUM">中等</SelectItem
                ><SelectItem value="HARD">困难</SelectItem></SelectContent
              ></Select
            >
            <Button type="submit" variant="outline" class="query-button" :disabled="loading"
              ><Search />查询</Button
            >
            <Button
              type="button"
              variant="outline"
              size="icon"
              class="reset-button"
              aria-label="重置筛选"
              title="重置筛选"
              @click="resetFilters"
              ><RefreshCw
            /></Button>
          </form>
          <div class="table-region" :aria-busy="loading">
            <Table class="problem-table">
              <TableHeader
                ><TableRow
                  ><TableHead class="number-col">题目编号</TableHead
                  ><TableHead class="title-col">题目名称</TableHead
                  ><TableHead class="difficulty-col">难度</TableHead
                  ><TableHead class="status-col">状态</TableHead
                  ><TableHead class="date-col">更新时间</TableHead
                  ><TableHead class="action-col">操作</TableHead></TableRow
                ></TableHeader
              >
              <TableBody>
                <template v-if="loading"
                  ><TableRow
                    ><TableCell :colspan="6"
                      ><div class="loading-state" role="status" aria-live="polite">
                        <span class="loading-beam" aria-hidden="true"></span>
                        <div class="loading-capsule">
                          <LoaderCircle class="spin" :size="17" /><span>正在同步题库</span
                          ><span class="loading-dots" aria-hidden="true"
                            ><i></i><i></i><i></i
                          ></span>
                        </div>
                        <p>好题即将呈现</p>
                      </div></TableCell
                    ></TableRow
                  ></template
                >
                <template v-else-if="rows.length"
                  ><TableRow
                    v-for="(row, index) in rows"
                    :key="row.id"
                    class="data-row"
                    :style="{ '--row-index': Math.min(index, 8) }"
                    :data-deleted="row.status === 'DELETED'"
                  >
                    <TableCell class="problem-number">{{ row.problemNo }}</TableCell>
                    <TableCell
                      ><Button
                        variant="link"
                        class="title-button"
                        :disabled="row.status === 'DELETED'"
                        @click="openDetail(row)"
                        >{{ row.title }}<ArrowUpRight class="title-arrow" /></Button
                    ></TableCell>
                    <TableCell
                      ><Badge
                        variant="secondary"
                        :class="[
                          'difficulty',
                          row.difficulty ? difficultyClass[row.difficulty] : 'unset',
                        ]"
                        >{{ row.difficulty ? difficultyText[row.difficulty] : '未设置' }}</Badge
                      ></TableCell
                    >
                    <TableCell
                      ><span class="status-pill" :class="{ deleted: row.status === 'DELETED' }"
                        ><i aria-hidden="true"></i
                        >{{ row.status === 'DRAFT' ? '草稿' : '已删除' }}</span
                      ></TableCell
                    >
                    <TableCell class="updated">{{ formatTime(row.updatedAt) }}</TableCell>
                    <TableCell
                      ><div class="row-actions">
                        <Button
                          variant="link"
                          size="sm"
                          :disabled="row.status === 'DELETED'"
                          @click="openDetail(row)"
                          >管理</Button
                        ><DropdownMenu
                          ><DropdownMenuTrigger as-child
                            ><Button
                              variant="ghost"
                              size="icon-sm"
                              :disabled="row.status === 'DELETED'"
                              :aria-label="`${row.title}的更多操作`"
                              ><MoreHorizontal /></Button></DropdownMenuTrigger
                          ><DropdownMenuContent align="end"
                            ><DropdownMenuItem @select="editProblem(row.id)"
                              >编辑草稿</DropdownMenuItem
                            >
                            <DropdownMenuItem @select="openDetail(row)"
                              ><FileCode2 />查看详情</DropdownMenuItem
                            ></DropdownMenuContent
                          ></DropdownMenu
                        >
                      </div></TableCell
                    >
                  </TableRow></template
                >
                <TableRow v-else
                  ><TableCell :colspan="6"
                    ><div class="empty-state">
                      <div class="empty-icon"><Inbox /></div>
                      <h2>
                        {{
                          loadError
                            ? '暂时无法加载题库'
                            : filtered
                              ? '没有找到匹配的题目'
                              : '好题，从这里开始'
                        }}
                      </h2>
                      <p>
                        {{
                          loadError ||
                          (filtered
                            ? '试试其他关键词，或清除筛选条件。'
                            : '新建第一道题目草稿，开启你的题库。')
                        }}
                      </p>
                      <Button v-if="loadError" variant="outline" @click="load"
                        ><RefreshCw />重新加载</Button
                      ><Button v-else-if="filtered" variant="outline" @click="resetFilters"
                        >清除筛选</Button
                      ><Button v-else @click="openCreate"><Plus />新建题目</Button>
                    </div></TableCell
                  ></TableRow
                >
              </TableBody>
            </Table>
          </div>
        </TabsContent>
      </Tabs>
      <div class="list-footer">
        <span aria-live="polite">{{
          loadError ? '列表加载失败' : loading && !loaded ? '正在加载题库…' : `共 ${total} 道题目`
        }}</span>
        <div class="pagination-group">
          <Select :model-value="String(applied.pageSize)" @update:model-value="changePageSize"
            ><SelectTrigger class="page-size" aria-label="每页题数"><SelectValue /></SelectTrigger
            ><SelectContent
              ><SelectItem value="10">10 条 / 页</SelectItem
              ><SelectItem value="20">20 条 / 页</SelectItem
              ><SelectItem value="50">50 条 / 页</SelectItem></SelectContent
            ></Select
          >
          <Pagination
            v-slot="{ page }"
            v-model:page="applied.pageNum"
            :items-per-page="applied.pageSize"
            :total="total"
            :sibling-count="1"
            :disabled="loading"
            show-edges
            @update:page="load"
            ><PaginationContent v-slot="{ items }"
              ><PaginationPrevious aria-label="上一页" /><template
                v-for="(item, index) in items"
                :key="index"
                ><PaginationItem
                  v-if="item.type === 'page'"
                  :value="item.value"
                  :is-active="item.value === page"
                  :class="{ 'current-page': item.value === page }"
                  >{{ item.value }}</PaginationItem
                ><PaginationEllipsis v-else /></template
              ><PaginationNext aria-label="下一页" /></PaginationContent
          ></Pagination>
        </div>
      </div>
    </section>
    <div class="below-note">
      <span><FileCode2 :size="13" />当前支持草稿管理，发布与批量导入尚未开放。</span
      ><span>时间按北京时间显示</span>
    </div>

    <Dialog v-model:open="createVisible"
      ><DialogContent class="create-dialog"
        ><DialogHeader
          ><div class="dialog-icon"><FilePlus2 /></div>
          <DialogTitle>新建题目草稿</DialogTitle
          ><DialogDescription>从一个好标题开始，逐步完善你的题目。</DialogDescription></DialogHeader
        >
        <form @submit.prevent="submitCreate">
          <Label for="new-title" class="field-label">题目标题</Label
          ><Input
            id="new-title"
            v-model="newTitle"
            placeholder="例如：两数之和"
            :aria-invalid="Boolean(titleError)"
            :aria-describedby="titleError ? 'title-error' : 'title-hint'"
            @input="titleError = ''"
          />
          <p v-if="titleError" id="title-error" class="field-error" role="alert">
            {{ titleError }}
          </p>
          <p id="title-hint" class="dialog-hint">1–100 个字符。创建后自动分配永久题号。</p>
          <DialogFooter class="mt-7"
            ><Button
              type="button"
              variant="outline"
              :disabled="creating"
              @click="createVisible = false"
              >取消</Button
            ><Button type="submit" class="primary-action" :disabled="creating"
              ><LoaderCircle v-if="creating" class="spin" /><Plus v-else />{{
                creating ? '正在创建…' : '创建草稿'
              }}</Button
            ></DialogFooter
          >
        </form></DialogContent
      ></Dialog
    >

    <Sheet v-model:open="drawerVisible"
      ><SheetContent class="detail-sheet"
        ><SheetHeader
          ><SheetTitle>题目管理</SheetTitle
          ><SheetDescription>查看草稿内容、资源限制与测试数据概况。</SheetDescription></SheetHeader
        >
        <div class="sheet-body">
          <div v-if="detailLoading" class="detail-loading" role="status" aria-live="polite">
            <span class="loading-beam" aria-hidden="true"></span>
            <div class="loading-capsule">
              <LoaderCircle class="spin" :size="17" /><span>正在读取草稿</span
              ><span class="loading-dots" aria-hidden="true"><i></i><i></i><i></i></span>
            </div>
            <p>题面与测试数据正在准备</p>
          </div>
          <Alert v-else-if="detailError" variant="destructive"
            ><AlertDescription>{{ detailError }}</AlertDescription></Alert
          >
          <template v-else-if="detail"
            ><p class="detail-number">
              PROBLEM {{ detail.problemNo
              }}<Badge variant="secondary">草稿 r{{ detail.revision }}</Badge>
            </p>
            <h2 class="detail-title">{{ detail.title }}</h2>
            <div class="detail-tags">
              <Badge v-for="tag in detail.tags" :key="tag" variant="outline">{{ tag }}</Badge>
            </div>
            <dl class="detail-meta">
              <div>
                <dt>难度</dt>
                <dd>{{ detail.difficulty ? difficultyText[detail.difficulty] : '未设置' }}</dd>
              </div>
              <div>
                <dt>启用语言</dt>
                <dd>Java 21</dd>
              </div>
              <div>
                <dt>样例数据</dt>
                <dd>{{ artifactCounts(detail).samples }} 组</dd>
              </div>
              <div>
                <dt>隐藏数据</dt>
                <dd>{{ artifactCounts(detail).hidden }} 组</dd>
              </div>
            </dl>
            <section class="detail-section">
              <h3>题面</h3>
              <p>{{ detail.statement || '尚未填写题面。' }}</p>
            </section>
            <section class="detail-section">
              <h3>输入 / 输出说明</h3>
              <p>{{ detail.inputDescription || '尚未填写输入说明。' }}</p>
              <p>{{ detail.outputDescription || '尚未填写输出说明。' }}</p>
            </section>
            <section class="detail-section">
              <h3>资源限制</h3>
              <p>
                CPU {{ detail.timeLimitCpuMs ?? '—' }} ms · 墙钟
                {{ detail.timeLimitWallMs ?? '—' }} ms<br />内存
                {{ detail.memoryLimitMiB ?? '—' }} MiB
              </p>
            </section>
            <Alert class="capability-note"
              ><AlertDescription
                >可编辑草稿并手动保存。测试数据上传、校验与发布入口尚未开放。</AlertDescription
              ></Alert
            >
            <div class="drawer-actions">
              <Button @click="editProblem(detail.id)">编辑草稿</Button
              ><Button
                variant="outline"
                :disabled="deleting || detail.status !== 'DRAFT' || detail.everPublished"
                class="delete-button"
                @click="deleteOpen = true"
                ><LoaderCircle v-if="deleting" class="spin" /><Trash2 v-else />{{
                  deleting ? '正在删除…' : '删除草稿'
                }}</Button
              >
            </div></template
          >
        </div></SheetContent
      ></Sheet
    >

    <AlertDialog v-model:open="deleteOpen"
      ><AlertDialogContent
        ><AlertDialogHeader
          ><AlertDialogTitle>删除草稿「{{ detail?.title }}」？</AlertDialogTitle
          ><AlertDialogDescription
            >删除后题号
            {{ detail?.problemNo }} 将保留且不再复用，此操作不可撤销。</AlertDialogDescription
          ></AlertDialogHeader
        ><AlertDialogFooter
          ><AlertDialogCancel>取消</AlertDialogCancel
          ><AlertDialogAction
            class="bg-destructive hover:bg-destructive/90"
            :disabled="deleting"
            @click="removeDraft"
            >删除草稿</AlertDialogAction
          ></AlertDialogFooter
        ></AlertDialogContent
      ></AlertDialog
    >
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}
h1 {
  margin: 0 0 6px;
  font-size: 28px;
  line-height: 1.3;
  font-weight: 750;
  letter-spacing: -1px;
}
.page-head p {
  margin: 0;
  color: var(--muted-text);
  font-size: 12px;
}
.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-actions button {
  height: 39px;
  border-radius: 9px;
  font-size: 12px;
  padding-inline: 16px;
}
.import-wrapper {
  display: inline-flex;
  border-radius: 9px;
}
.summary-strip {
  display: flex;
  align-items: center;
  gap: 0;
  min-height: 58px;
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 3px #252c1002;
}
.summary-strip > div {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  padding: 0 24px;
  border-right: 1px solid var(--border);
  color: #68717e;
  font-size: 12px;
}
.summary-strip strong {
  color: #15191e;
  font-size: 22px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.6px;
}
.summary-strip small {
  font-size: 10px;
}
.sync-status {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  min-width: 130px;
  padding: 0 20px;
  color: #68717e;
  font-size: 10px;
}
.sync-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #12b886;
  box-shadow: 0 0 0 3px #12b8860c;
}
.sync-status.failed i {
  background: #c53c4b;
}
.panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 30px -24px #35392b30;
}
.panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
}
.list-tabs {
  height: 51px;
  padding: 0;
  gap: 26px;
  border-radius: 0;
  background: none;
}
.list-tabs :deep([data-slot='tabs-trigger']) {
  position: relative;
  flex: none;
  height: 100%;
  padding: 0 3px;
  border: 0;
  border-radius: 0;
  background: none;
  color: #707887;
  box-shadow: none;
  font-size: 13px;
}
.list-tabs :deep([data-state='active']) {
  color: #15191e;
  font-weight: 650;
}
.list-tabs :deep([data-state='active']::after) {
  position: absolute;
  inset: auto 0 0;
  height: 3px;
  border-radius: 0;
  background: var(--lime);
  content: '';
  animation: tab-in 250ms var(--ease-out);
}
.panel-note {
  font-size: 10px;
  color: #989b94;
}
.tab-content {
  margin: 0;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 17px 20px;
}
.search-input {
  position: relative;
  flex: 1;
  max-width: 420px;
}
.search-input > svg {
  position: absolute;
  z-index: 1;
  top: 10px;
  left: 12px;
  width: 16px;
  height: 16px;
  color: #79808b;
}
.search-input :deep(input) {
  height: 36px;
  padding-left: 36px;
  font-size: 12px;
  box-shadow: none;
}
.difficulty-select {
  width: 160px;
  height: 36px;
  font-size: 12px;
}
.query-button {
  height: 36px;
  font-size: 12px;
  background: #fff;
}
.reset-button {
  margin-left: auto;
  height: 36px;
  width: 36px;
  background: #fff;
}
.reset-button:hover svg {
  transform: rotate(60deg);
}
.reset-button svg {
  transition: transform 250ms var(--ease-out);
}
.table-region {
  padding: 0 14px;
  min-height: 305px;
}
.problem-table {
  min-width: 700px;
  font-size: 12px;
}
.problem-table :deep(th) {
  height: 38px;
  background: #f8f8f5;
  color: #697180;
  font-size: 11px;
  font-weight: 550;
}
.problem-table :deep(th:first-child) {
  border-radius: 7px 0 0 7px;
}
.problem-table :deep(th:last-child) {
  border-radius: 0 7px 7px 0;
}
.problem-table :deep(td) {
  height: 48px;
  padding: 6px 12px;
  border-color: #eeede7;
}
.problem-table :deep(th) {
  padding-inline: 12px;
}
.problem-table :deep(tr) {
  border-color: #eeede7;
}
.number-col {
  width: 13%;
}
.title-col {
  width: 33%;
}
.difficulty-col {
  width: 12%;
}
.status-col {
  width: 12%;
}
.date-col {
  width: 18%;
}
.action-col {
  width: 12%;
  text-align: right;
  padding-right: 18px !important;
}
.data-row {
  animation: row-in 350ms var(--ease-out) both;
  animation-delay: calc(var(--row-index) * 25ms);
}
.data-row:hover {
  background: #f3f6ff;
  box-shadow: inset 3px 0 var(--primary);
}
.data-row[data-deleted='true'] {
  color: #92978e;
}
.problem-number {
  color: #747b86;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.title-button {
  height: auto;
  min-height: 28px;
  max-width: 100%;
  gap: 7px;
  padding: 0;
  color: #232933;
  font-size: 12px;
  font-weight: 550;
  text-align: left;
  white-space: normal;
  justify-content: flex-start;
}
.title-button:hover {
  text-decoration: none;
  color: var(--primary);
}
.title-arrow {
  flex-shrink: 0;
  width: 13px;
  opacity: 0;
  transform: translate(-3px, 3px);
  transition:
    opacity 180ms,
    transform 180ms;
}
.data-row:hover .title-arrow,
.title-button:focus-visible .title-arrow {
  opacity: 1;
  transform: translate(0, 0);
}
.difficulty {
  padding: 2px 7px;
  border: 0;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 500;
}
.difficulty.easy {
  background: #eaf8cd;
  color: #476616;
}
.difficulty.medium {
  background: #fff1cd;
  color: #916012;
}
.difficulty.hard {
  background: #ffe6e7;
  color: #bb3946;
}
.difficulty.unset {
  background: #f3f3ef;
  color: #8d928c;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: #767d87;
}
.status-pill i {
  width: 6px;
  height: 6px;
  background: #a3a8a2;
  border-radius: 50%;
}
.status-pill.deleted {
  color: #b15a63;
}
.status-pill.deleted i {
  background: #c98a91;
}
.updated {
  color: #737b87;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
}
.row-actions button {
  height: 30px;
  font-size: 11px;
}
.row-actions button:last-child {
  width: 26px;
  color: #838985;
}
.list-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  padding: 17px 20px;
  color: #737b87;
  font-size: 11px;
}
.pagination-group {
  display: flex;
  align-items: center;
  gap: 15px;
}
.page-size {
  width: 104px;
  height: 32px;
  font-size: 11px;
}
.pagination-group :deep([data-slot='pagination']) {
  width: auto;
}
.pagination-group :deep([data-slot='pagination-item']),
.pagination-group :deep([data-slot='pagination-next']),
.pagination-group :deep([data-slot='pagination-previous']) {
  min-width: 31px;
  width: 31px;
  height: 31px;
  border-radius: 7px;
  font-size: 11px;
}
.pagination-group :deep(.current-page) {
  border-color: var(--primary);
  background: var(--primary);
  color: #fff;
  box-shadow: 0 3px 7px #1f4bff20;
}
.pagination-group :deep([data-slot='pagination-previous'] span),
.pagination-group :deep([data-slot='pagination-next'] span) {
  display: none;
}
.below-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  margin: 17px 1px;
  color: #92978e;
  font-size: 10px;
}
.below-note > span:first-child {
  display: flex;
  align-items: center;
  gap: 7px;
}
.empty-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  min-height: 280px;
  padding: 30px 20px;
  text-align: center;
}
.empty-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 15px;
  background: #f0f6e4;
  color: #78934e;
  border: 1px solid #e5edda;
  border-radius: 17px;
  transform: rotate(-5deg);
}
.empty-state h2 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
}
.empty-state p {
  margin: 0 0 20px;
  max-width: 360px;
  color: #8a9088;
  font-size: 12px;
  line-height: 1.8;
}
.empty-state button {
  height: 36px;
  font-size: 12px;
}
.dialog-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: 8px;
  border: 1px solid #dcecc1;
  border-radius: 13px;
  background: #eff8d9;
  color: #537522;
}
.dialog-hint {
  margin-top: 10px;
  color: #8a9088;
  font-size: 11px;
}
.sheet-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 28px 28px;
}
.loading-state,
.detail-loading {
  position: relative;
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  gap: 13px;
  min-height: 280px;
  overflow: hidden;
  border-radius: 12px;
  background: radial-gradient(circle at 50% 42%, #fff 0%, #fbfaf7 72%);
}
.detail-loading {
  min-height: 360px;
  margin-top: 10px;
}
.loading-beam {
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  overflow: hidden;
  background: #eef0e9;
}
.loading-beam::after {
  position: absolute;
  inset: 0 auto 0 0;
  width: 34%;
  background: linear-gradient(90deg, transparent, var(--primary), var(--lime), transparent);
  content: '';
  animation: beam-sweep 1.6s var(--ease-out) infinite;
}
.loading-capsule {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 11px 17px;
  border: 1px solid #e6e3db;
  border-radius: 999px;
  background: #fff;
  box-shadow:
    0 14px 32px -22px #1f4bff70,
    inset 0 1px 0 #fff;
  color: #4a5361;
  font-size: 12px;
  animation: capsule-float 2.4s ease-in-out infinite;
}
.loading-capsule > svg {
  color: var(--primary);
}
.loading-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.loading-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--primary);
  opacity: 0.55;
  animation: dot-bounce 1s ease-in-out infinite;
}
.loading-dots i:nth-child(2) {
  animation-delay: 120ms;
}
.loading-dots i:nth-child(3) {
  animation-delay: 240ms;
}
.loading-state p,
.detail-loading p {
  margin: 0;
  color: #92978e;
  font-size: 11px;
  letter-spacing: 0.3px;
}
.detail-number {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #8a9088;
  font-size: 10px;
  letter-spacing: 1px;
}
.detail-number :deep([data-slot='badge']) {
  font-size: 10px;
  letter-spacing: 0;
}
.detail-title {
  margin: 14px 0;
  font-size: 27px;
  line-height: 1.5;
  font-weight: 650;
  letter-spacing: -0.7px;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.detail-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-top: 26px;
  padding: 25px 0;
  border-block: 1px solid var(--border);
}
.detail-meta dt {
  margin-bottom: 7px;
  color: #8a9088;
  font-size: 11px;
}
.detail-meta dd {
  font-size: 13px;
}
.detail-section {
  margin-top: 25px;
}
.detail-section h3 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 650;
}
.detail-section p {
  margin: 0 0 8px;
  color: #737b87;
  font-size: 12px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.capability-note {
  margin-top: 26px;
  background: #f6f7f1;
  border-color: #e8ebdf;
}
.capability-note :deep([data-slot='alert-description']) {
  font-size: 11px;
  line-height: 1.8;
}
.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}
.drawer-actions button {
  font-size: 12px;
}
.delete-button {
  color: #b64957;
}
:global([data-slot='sheet-content'].detail-sheet) {
  width: min(500px, 100vw);
  max-width: 500px;
  gap: 16px;
  background: #fff;
}
:global(.detail-sheet [data-slot='sheet-header']) {
  padding: 27px 28px 20px;
  border-bottom: 1px solid var(--border);
}
:global(.detail-sheet [data-slot='sheet-description']) {
  font-size: 11px;
}
:global(.create-dialog) {
  border-radius: 20px;
  background: #fff;
  padding: 28px;
}
@keyframes tab-in {
  from {
    transform: scaleX(0.3);
  }
  to {
    transform: scaleX(1);
  }
}
@keyframes row-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
@keyframes beam-sweep {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(390%);
  }
}
@keyframes capsule-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}
@keyframes dot-bounce {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}
@media (max-width: 1100px) {
  .summary-strip > div {
    padding-inline: 17px;
    gap: 8px;
  }
  .summary-strip small {
    display: none;
  }
  .sync-status {
    min-width: 115px;
    padding-inline: 14px;
  }
}
@media (max-width: 760px) {
  .page-head {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 18px;
    margin-bottom: 22px;
  }
  h1 {
    font-size: 26px;
  }
  .page-actions {
    width: 100%;
  }
  .page-actions .primary-action {
    margin-left: auto;
  }
  .summary-strip {
    flex-wrap: wrap;
    gap: 13px 0;
    padding: 15px 0;
  }
  .summary-strip > div {
    gap: 8px;
    flex: auto;
    padding-inline: 14px;
    font-size: 10px;
  }
  .summary-strip > div:nth-child(3) {
    border-right: 0;
  }
  .summary-strip strong {
    font-size: 20px;
  }
  .sync-status {
    width: 100%;
    justify-content: flex-start;
    margin-left: 14px;
    padding: 0;
  }
  .panel-top {
    padding-inline: 14px;
  }
  .list-tabs {
    gap: 18px;
  }
  .panel-note {
    display: none;
  }
  .filters {
    flex-wrap: wrap;
    padding: 15px 14px;
    gap: 10px;
  }
  .search-input {
    flex-basis: 100%;
    max-width: none;
  }
  .difficulty-select {
    flex: 1;
  }
  .reset-button {
    margin-left: 0;
  }
  .table-region {
    padding-inline: 8px;
  }
  .list-footer {
    padding: 15px 14px;
    flex-wrap: wrap;
  }
  .pagination-group {
    width: 100%;
    gap: 10px;
    justify-content: space-between;
  }
  .below-note {
    align-items: flex-start;
    flex-direction: column;
    gap: 9px;
    line-height: 1.7;
  }
}
</style>
