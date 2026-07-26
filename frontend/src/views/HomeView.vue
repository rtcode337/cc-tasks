<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useTaskStore } from '@/stores/tasks'
import { useProjectStore } from '@/stores/projects'
import { usePullToRefresh } from '@/lib/pullToRefresh'
import CopyButton from '@/components/CopyButton.vue'
import ClaudeCodeButton from '@/components/ClaudeCodeButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import type { Task } from '@/api/types'

const tasks = useTaskStore()
const projects = useProjectStore()

// 下に引っ張ったら一覧を取り直す
usePullToRefresh(() => Promise.all([projects.load(true), tasks.load(true)]))

// 未着手一覧のプロジェクトごとの折りたたみ。デフォルトは閉じた状態で、
// 開いたグループだけをブラウザに永続化する
const EXPANDED_KEY = 'cc-tasks-home-expanded'

interface TaskGroup {
  key: string
  name: string
  tasks: Task[]
  /** false = 常に展開(未紐づけ用)。ヘッダも開閉ボタンにしない */
  collapsible: boolean
}

function loadExpanded(): Set<string> {
  try {
    const raw = localStorage.getItem(EXPANDED_KEY)
    return new Set(raw ? (JSON.parse(raw) as string[]) : [])
  } catch {
    return new Set()
  }
}

const expanded = ref(loadExpanded())

function toggleGroup(key: string) {
  const next = new Set(expanded.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expanded.value = next
  try {
    localStorage.setItem(EXPANDED_KEY, JSON.stringify([...next]))
  } catch {
    // 保存できなくてもその場の開閉は効くので握りつぶす
  }
}

/**
 * 未着手をプロジェクトごとにまとめる。先頭は未紐づけ(常に展開)、
 * 続いてプロジェクトの並び順(プロジェクト画面で並び替えた順)。
 * グループ内は todo の並びのまま作成日時降順。
 */
const groups = computed<TaskGroup[]>(() => {
  const byProject = new Map<number, Task[]>()
  const unlinked: Task[] = []
  for (const task of tasks.todo) {
    if (task.projectId == null) {
      unlinked.push(task)
    } else {
      const list = byProject.get(task.projectId)
      if (list) list.push(task)
      else byProject.set(task.projectId, [task])
    }
  }

  const result: TaskGroup[] = []
  if (unlinked.length > 0) {
    result.push({ key: 'none', name: '未紐づけ', tasks: unlinked, collapsible: false })
  }
  // アーカイブ済みプロジェクトのタスクも表示するため all(並び順どおり)を使う
  for (const p of projects.all) {
    const list = byProject.get(p.id)
    if (list) {
      result.push({ key: `p${p.id}`, name: p.name, tasks: list, collapsible: true })
      byProject.delete(p.id)
    }
  }
  // プロジェクト一覧の読み込み前などの取りこぼし
  for (const [projectId, list] of byProject) {
    result.push({ key: `p${projectId}`, name: `#${projectId}`, tasks: list, collapsible: true })
  }
  return result
})

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

      <div v-else class="groups">
        <section v-for="group in groups" :key="group.key" class="group">
          <!-- 未紐づけ (collapsible=false) は見出しを出さず、常に展開してそのまま並べる -->
          <button
            v-if="group.collapsible"
            type="button"
            class="group__header"
            :aria-expanded="expanded.has(group.key)"
            @click="toggleGroup(group.key)"
          >
            <span class="group__chevron" :class="{ 'group__chevron--open': expanded.has(group.key) }">▸</span>
            <span class="group__name">{{ group.name }}</span>
            <span class="group__count">{{ group.tasks.length }}</span>
          </button>

          <ul v-show="!group.collapsible || expanded.has(group.key)" class="cards">
            <li v-for="task in group.tasks" :key="task.id" class="card">
              <div class="card__body">
                <RouterLink :to="`/tasks/${task.id}`" class="card__memo">{{ task.title }}</RouterLink>
                <span class="card__tools">
                  <CopyButton icon :text="task.title" />
                  <ClaudeCodeButton
                    :task="task"
                    :repo-urls="task.projectId ? projects.byId.get(task.projectId)?.repoUrls : undefined"
                  />
                </span>
              </div>
              <div class="card__foot">
                <span class="card__buttons">
                  <!-- 削除はタスク編集画面から。誤タップしやすい一覧には置かない -->
                  <RouterLink :to="`/tasks/${task.id}/edit`" class="btn btn--edit">編集</RouterLink>
                  <button type="button" class="btn btn--done" @click="complete(task.id)">完了</button>
                </span>
              </div>
            </li>
          </ul>
        </section>
      </div>
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

.groups {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.group__header {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  width: 100%;
  margin: 0 0 0.375rem;
  padding: 0.25rem 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  text-align: left;
}

.group__header:hover .group__name {
  color: var(--text);
}

.group__chevron {
  display: inline-block;
  transition: transform 0.15s ease;
  color: var(--muted-dim);
}

.group__chevron--open {
  transform: rotate(90deg);
}

.group__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group__count {
  font-variant-numeric: tabular-nums;
  font-weight: 400;
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

.card__tools {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.card__memo:hover {
  color: var(--accent);
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.625rem;
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

.btn--edit,
.btn--edit:visited {
  color: var(--muted);
  text-decoration: none;
}

.btn--edit:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.muted {
  color: var(--muted);
}
</style>
