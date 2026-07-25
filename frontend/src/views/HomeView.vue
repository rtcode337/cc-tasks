<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useTaskStore } from '@/stores/tasks'
import { useProjectStore } from '@/stores/projects'
import CopyButton from '@/components/CopyButton.vue'
import ClaudeCodeButton from '@/components/ClaudeCodeButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'

const tasks = useTaskStore()
const projects = useProjectStore()

const error = ref<string | null>(null)
const memo = ref('')
// null = プロジェクトに紐づけない
const projectId = ref<number | null>(null)
const saving = ref(false)

onMounted(async () => {
  try {
    await Promise.all([projects.load(), tasks.load()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

async function save() {
  const title = memo.value.trim()
  if (!title || saving.value) return
  saving.value = true
  error.value = null
  try {
    await tasks.create({ title, projectId: projectId.value ?? undefined })
    memo.value = ''
    // プロジェクト選択は次のタスクでも使い回せるよう残す
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
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

async function remove(id: number) {
  error.value = null
  try {
    await tasks.remove(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

<template>
  <section>
    <ErrorBanner v-if="error" :message="error" />

    <!-- やりたいことをさっと書いて放り込む -->
    <form class="entry" @submit.prevent="save">
      <textarea
        v-model="memo"
        class="entry__input"
        rows="3"
        placeholder="やりたいことを入力 (Enter で改行、ボタンで保存)"
      />
      <div class="entry__actions">
        <select v-model="projectId" class="entry__project" aria-label="プロジェクト">
          <option :value="null">プロジェクトなし</option>
          <option v-for="p in projects.active" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
        <button type="submit" class="button entry__save" :disabled="!memo.trim() || saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
      <RouterLink to="/tasks/new" class="entry__detail">受け入れ条件などを詳しく書く →</RouterLink>
    </form>

    <!-- 未着手のタスク(作成日時降順) -->
    <section class="list">
      <h2 class="list__title">
        未着手 <span class="list__count">{{ tasks.todo.length }}</span>
      </h2>

      <p v-if="tasks.loading" class="muted">読み込み中…</p>
      <p v-else-if="tasks.todo.length === 0" class="muted">タスクはまだありません。</p>

      <ul v-else class="cards">
        <li v-for="task in tasks.todo" :key="task.id" class="card">
          <div class="card__body">
            <CopyButton icon :text="task.title" class="card__copy" />
            <ClaudeCodeButton
              :task="task"
              :repo-urls="task.projectId ? projects.byId.get(task.projectId)?.repoUrls : undefined"
            />
            <RouterLink :to="`/tasks/${task.id}`" class="card__memo">{{ task.title }}</RouterLink>
          </div>
          <div class="card__foot">
            <span class="card__project">
              {{ task.projectId ? projects.name(task.projectId) : '未紐づけ' }}
            </span>
            <span class="card__buttons">
              <button type="button" class="btn btn--done" @click="complete(task.id)">完了</button>
              <button type="button" class="btn btn--delete" @click="remove(task.id)">削除</button>
            </span>
          </div>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.entry {
  padding: 1rem 0 1.5rem;
}

.entry__input {
  margin-bottom: 0.5rem;
}

.entry__actions {
  display: flex;
  gap: 0.5rem;
}

.entry__project {
  flex: 1;
}

.entry__save {
  width: 6rem;
  flex-shrink: 0;
}

.entry__detail {
  display: inline-block;
  margin-top: 0.625rem;
  font-size: 0.75rem;
  color: var(--muted);
}

.list__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.75rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--muted);
}

.list__count {
  font-variant-numeric: tabular-nums;
  color: var(--muted-dim);
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

.card__memo,
.card__memo:visited {
  display: block;
  flex: 1;
  min-width: 0;
  color: var(--text);
  text-decoration: none;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  padding-top: 0.1875rem;
}

.card__memo:hover {
  color: var(--accent);
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.625rem;
}

.card__project {
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

.btn--done:hover {
  color: var(--badge-done-text);
  border-color: var(--badge-done-text);
}

.btn--delete:hover {
  color: var(--danger);
  border-color: var(--danger);
}

.muted {
  color: var(--muted);
}
</style>
