<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import { useTaskStore } from '@/stores/tasks'
import { useProjectStore } from '@/stores/projects'
import StatusBadge from '@/components/StatusBadge.vue'
import CopyButton from '@/components/CopyButton.vue'
import ClaudeCodeButton from '@/components/ClaudeCodeButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import type { Task } from '@/api/types'

const PAGE_SIZE = 10

const tasks = useTaskStore()
const projects = useProjectStore()

const error = ref<string | null>(null)
// 'active' = 未完了 / 'done' = 完了(ページング)
const mode = ref<'active' | 'done'>('active')
// '' = すべて / 'none' = 未紐づけ / 数値 = そのプロジェクト
const filter = ref<string>('')

// 完了タスク(ページング)
const doneItems = ref<Task[]>([])
const doneTotal = ref(0)
const donePage = ref(0)
const doneLoading = ref(false)
const doneTotalPages = computed(() => Math.max(1, Math.ceil(doneTotal.value / PAGE_SIZE)))

onMounted(async () => {
  try {
    await Promise.all([projects.load(), tasks.load()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

const visibleActive = computed<Task[]>(() => {
  if (filter.value === '') return tasks.active
  if (filter.value === 'none') return tasks.active.filter((t) => t.projectId == null)
  const id = Number(filter.value)
  return tasks.active.filter((t) => t.projectId === id)
})

function filterProjectId(): number | undefined {
  return filter.value === '' || filter.value === 'none' ? undefined : Number(filter.value)
}

async function loadDone(page: number) {
  doneLoading.value = true
  error.value = null
  try {
    const result = await api.listDoneTasks({ projectId: filterProjectId(), page, size: PAGE_SIZE })
    doneItems.value = result.items
    doneTotal.value = result.total
    donePage.value = result.page
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    doneLoading.value = false
  }
}

async function showDone() {
  mode.value = 'done'
  await loadDone(0)
}

function showActive() {
  mode.value = 'active'
}

// done 表示中にプロジェクト絞り込みが変わったら先頭ページから取り直す
watch(filter, () => {
  if (mode.value === 'done') loadDone(0)
})

async function associate(task: Task, event: Event) {
  const value = (event.target as HTMLSelectElement).value
  error.value = null
  try {
    if (value !== '') await tasks.update(task.id, { projectId: Number(value) })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function complete(id: number) {
  error.value = null
  try {
    await tasks.complete(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function remove(id: number, fromDone = false) {
  error.value = null
  try {
    await tasks.remove(id)
    if (fromDone) {
      // 削除でそのページが空になったら 1 つ前へ
      const nextPage = doneItems.value.length === 1 && donePage.value > 0 ? donePage.value - 1 : donePage.value
      await loadDone(nextPage)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

<template>
  <section>
    <ErrorBanner v-if="error" :message="error" />

    <div class="head">
      <h1 class="title">タスク一覧</h1>
      <button
        type="button"
        class="switch"
        @click="mode === 'active' ? showDone() : showActive()"
      >
        {{ mode === 'active' ? '完了したタスク' : '未完了のタスク' }}
      </button>
      <select v-model="filter" class="head__filter" aria-label="絞り込み">
        <option value="">すべて</option>
        <option v-if="mode === 'active'" value="none">未紐づけ</option>
        <option v-for="p in projects.active" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
      </select>
    </div>

    <!-- 未完了 -->
    <template v-if="mode === 'active'">
      <p v-if="tasks.loading" class="muted">読み込み中…</p>
      <p v-else-if="visibleActive.length === 0" class="muted">タスクはありません。</p>
      <ul v-else class="cards">
        <li v-for="task in visibleActive" :key="task.id" class="card">
          <div class="card__body">
            <CopyButton icon :text="task.title" class="card__copy" />
            <ClaudeCodeButton
              :task="task"
              :repo-urls="task.projectId ? projects.byId.get(task.projectId)?.repoUrls : undefined"
            />
            <div class="card__main">
              <RouterLink :to="`/tasks/${task.id}`" class="card__memo">{{ task.title }}</RouterLink>
              <StatusBadge :status="task.status" class="card__badge" />
            </div>
          </div>
          <div class="card__foot">
            <select
              class="card__project"
              :value="task.projectId ? String(task.projectId) : ''"
              aria-label="プロジェクトに紐づけ"
              @change="associate(task, $event)"
            >
              <option value="">未紐づけ</option>
              <option v-for="p in projects.active" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
            </select>
            <span class="card__buttons">
              <button type="button" class="btn btn--done" @click="complete(task.id)">完了</button>
              <button type="button" class="btn btn--delete" @click="remove(task.id)">削除</button>
            </span>
          </div>
        </li>
      </ul>
    </template>

    <!-- 完了(ページング) -->
    <template v-else>
      <p v-if="doneLoading" class="muted">読み込み中…</p>
      <p v-else-if="doneItems.length === 0" class="muted">完了したタスクはありません。</p>
      <template v-else>
        <ul class="cards">
          <li v-for="task in doneItems" :key="task.id" class="card">
            <div class="card__body">
              <CopyButton icon :text="task.title" class="card__copy" />
              <ClaudeCodeButton
                :task="task"
                :repo-urls="task.projectId ? projects.byId.get(task.projectId)?.repoUrls : undefined"
              />
              <div class="card__main">
                <RouterLink :to="`/tasks/${task.id}`" class="card__memo">{{ task.title }}</RouterLink>
                <StatusBadge :status="task.status" class="card__badge" />
              </div>
            </div>
            <div class="card__foot">
              <span class="card__projectlabel">
                {{ task.projectId ? projects.name(task.projectId) : '未紐づけ' }}
              </span>
              <button type="button" class="btn btn--delete" @click="remove(task.id, true)">削除</button>
            </div>
          </li>
        </ul>

        <div class="pager">
          <button type="button" class="btn" :disabled="donePage === 0" @click="loadDone(donePage - 1)">
            ← 前へ
          </button>
          <span class="pager__info">{{ donePage + 1 }} / {{ doneTotalPages }}({{ doneTotal }} 件)</span>
          <button
            type="button"
            class="btn"
            :disabled="donePage + 1 >= doneTotalPages"
            @click="loadDone(donePage + 1)"
          >
            次へ →
          </button>
        </div>
      </template>
    </template>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 0 1rem;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  font-size: 1.125rem;
}

.switch {
  border: none;
  background: none;
  padding: 0;
  color: var(--accent);
  font-size: 0.8125rem;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}

.switch:hover {
  text-decoration: underline;
}

.head__filter {
  width: auto;
  max-width: 10rem;
  margin-left: auto;
  padding: 0.375rem 0.5rem;
  font-size: 0.8125rem;
}

.cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.card {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  padding: 0.75rem;
}

.card__body {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
}

.card__main {
  flex: 1;
  min-width: 0;
}

.card__memo,
.card__memo:visited {
  display: block;
  color: var(--text);
  text-decoration: none;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.card__memo:hover {
  color: var(--accent);
}

.card__badge {
  margin-top: 0.375rem;
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.625rem;
}

.card__project {
  width: auto;
  min-width: 8rem;
  max-width: 14rem;
  padding: 0.375rem 0.5rem;
  font-size: 0.8125rem;
}

.card__projectlabel {
  font-size: 0.75rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__buttons {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.btn {
  padding: 0.25rem 0.625rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted);
  font-size: 0.75rem;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.btn--done:hover {
  color: var(--badge-done-text);
  border-color: var(--badge-done-text);
}

.btn--delete:hover:not(:disabled) {
  color: var(--danger);
  border-color: var(--danger);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 1.25rem;
}

.pager__info {
  font-size: 0.8125rem;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--muted);
}
</style>
