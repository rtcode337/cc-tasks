<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import { useTaskStore } from '@/stores/tasks'
import { useProjectStore } from '@/stores/projects'
import { usePullToRefresh } from '@/lib/pullToRefresh'
import { STATUS_LABELS, TASK_STATUSES } from '@/api/types'
import type { TaskDetail, TaskStatus } from '@/api/types'
import StatusBadge from '@/components/StatusBadge.vue'
import MarkdownText from '@/components/MarkdownText.vue'
import CopyButton from '@/components/CopyButton.vue'
import ClaudeCodeButton from '@/components/ClaudeCodeButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'

const props = defineProps<{ id: string }>()
const tasks = useTaskStore()
const projects = useProjectStore()

const task = ref<TaskDetail | null>(null)
const error = ref<string | null>(null)
const loading = ref(true)
const noteBody = ref('')
const posting = ref(false)

const AUTHOR_LABELS: Record<string, string> = { human: '自分', claude_code: 'Claude Code' }

onMounted(async () => {
  projects.load().catch(() => undefined)
  await reload()
})

// 下に引っ張ったらタスクを取り直す(Claude Code が書き戻したノートの確認に効く)
usePullToRefresh(reload)

async function reload() {
  loading.value = true
  try {
    task.value = await api.getTask(Number(props.id))
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function changeStatus(status: TaskStatus) {
  if (!task.value || task.value.status === status) return
  error.value = null
  try {
    await tasks.update(task.value.id, { status })
    task.value = { ...task.value, status }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function associate(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (!task.value || value === '') return
  error.value = null
  try {
    const projectId = Number(value)
    await tasks.update(task.value.id, { projectId })
    task.value = { ...task.value, projectId, projectName: projects.name(projectId) }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function addNote() {
  const body = noteBody.value.trim()
  if (!body || !task.value || posting.value) return
  posting.value = true
  error.value = null
  try {
    const note = await api.addNote(task.value.id, body)
    task.value = { ...task.value, notes: [note, ...task.value.notes] }
    noteBody.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    posting.value = false
  }
}

function formatDate(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <section>
    <ErrorBanner v-if="error" :message="error" />
    <p v-if="loading" class="muted">読み込み中…</p>

    <template v-else-if="task">
      <div class="head">
        <RouterLink to="/tasks" class="head__back">← 一覧</RouterLink>
        <RouterLink :to="`/tasks/${task.id}/edit`" class="head__edit">編集</RouterLink>
      </div>

      <div class="titlebar">
        <CopyButton icon :text="task.title" label="タスクをコピー" class="titlebar__copy" />
        <ClaudeCodeButton
          :task="task"
          :repo-urls="task.projectId ? projects.byId.get(task.projectId)?.repoUrls : undefined"
          class="titlebar__copy"
        />
        <h1 class="title">{{ task.title }}</h1>
      </div>
      <p class="meta">
        <span>#{{ task.id }}</span>
        <StatusBadge :status="task.status" />
      </p>

      <!-- プロジェクトはあとから紐づけられる -->
      <label class="project">
        <span class="project__label">プロジェクト</span>
        <select :value="task.projectId ? String(task.projectId) : ''" @change="associate">
          <option value="">未紐づけ</option>
          <option v-for="p in projects.active" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
        </select>
      </label>

      <div class="statuses">
        <button
          v-for="status in TASK_STATUSES"
          :key="status"
          type="button"
          class="statuses__button"
          :class="{ 'statuses__button--on': task.status === status }"
          @click="changeStatus(status)"
        >
          {{ STATUS_LABELS[status] }}
        </button>
      </div>

      <section v-if="task.context" class="block">
        <h2 class="block__title">コンテキスト</h2>
        <MarkdownText :source="task.context" />
      </section>

      <section v-if="task.acceptanceCriteria" class="block">
        <h2 class="block__title">受け入れ条件</h2>
        <MarkdownText :source="task.acceptanceCriteria" />
      </section>

      <section v-if="task.outOfScope" class="block">
        <h2 class="block__title">スコープ外</h2>
        <MarkdownText :source="task.outOfScope" />
      </section>

      <section class="block">
        <h2 class="block__title">経緯 ({{ task.notes.length }})</h2>

        <form class="note-form" @submit.prevent="addNote">
          <textarea
            v-model="noteBody"
            rows="3"
            placeholder="追記する (Markdown 可)。ノートは後から編集できません"
          />
          <button type="submit" class="button" :disabled="!noteBody.trim() || posting">
            {{ posting ? '追記中…' : '追記' }}
          </button>
        </form>

        <p v-if="task.notes.length === 0" class="muted">まだノートはありません。</p>
        <ol v-else class="timeline">
          <li v-for="note in task.notes" :key="note.id" class="timeline__item">
            <p class="timeline__meta">
              <span class="timeline__author" :class="`timeline__author--${note.author}`">
                {{ AUTHOR_LABELS[note.author] ?? note.author }}
              </span>
              <time :datetime="note.createdAt">{{ formatDate(note.createdAt) }}</time>
            </p>
            <MarkdownText :source="note.body" />
          </li>
        </ol>
      </section>

    </template>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 0 0.5rem;
  font-size: 0.8125rem;
}

.head__back,
.head__edit {
  color: var(--muted);
}

.titlebar {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  margin: 0.25rem 0 0.5rem;
}

.titlebar__copy {
  margin-top: 0.25rem;
}

.title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 1.25rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.project {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  margin-bottom: 1.25rem;
}

.project__label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--muted);
  white-space: nowrap;
}

.meta {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  margin: 0 0 1rem;
  font-size: 0.75rem;
  color: var(--muted);
}

.statuses {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.statuses__button {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted);
  font-size: 0.8125rem;
  cursor: pointer;
}

.statuses__button--on {
  border-color: var(--accent);
  color: var(--text);
  background: var(--surface-raised);
}

.block {
  margin-bottom: 1.75rem;
}

.block__title {
  margin: 0 0 0.5rem;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--muted);
}

.note-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.25rem;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.timeline__item {
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
}

.timeline__meta {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  margin: 0 0 0.375rem;
  font-size: 0.6875rem;
  color: var(--muted-dim);
}

.timeline__author {
  font-weight: 600;
}

.timeline__author--claude_code {
  color: var(--accent);
}

.timeline__author--human {
  color: var(--muted);
}

.muted {
  color: var(--muted);
}
</style>
