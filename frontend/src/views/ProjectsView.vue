<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useProjectStore } from '@/stores/projects'
import { usePullToRefresh } from '@/lib/pullToRefresh'
import type { Project } from '@/api/types'
import ErrorBanner from '@/components/ErrorBanner.vue'

const projects = useProjectStore()

// 下に引っ張ったら一覧を取り直す
usePullToRefresh(() => projects.load(true))
const error = ref<string | null>(null)
const saving = ref(false)

/** null = 新規作成 */
const editing = ref<Project | null>(null)
const modalOpen = ref(false)
const form = reactive({ name: '', repoUrls: [''], description: '', archived: false })

onMounted(async () => {
  try {
    await projects.load(true)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', repoUrls: [''], description: '', archived: false })
  modalOpen.value = true
}

function openEdit(project: Project) {
  editing.value = project
  Object.assign(form, {
    name: project.name,
    repoUrls: project.repoUrls.length > 0 ? [...project.repoUrls] : [''],
    description: project.description ?? '',
    archived: project.archived,
  })
  modalOpen.value = true
}

function addRepoUrl() {
  form.repoUrls.push('')
}

function removeRepoUrl(index: number) {
  form.repoUrls.splice(index, 1)
  if (form.repoUrls.length === 0) form.repoUrls.push('')
}

/** URL の末尾 user/repo 形からリポジトリ名を取り出す。取れなければ空文字。 */
function repoNameFromUrl(url: string): string {
  const trimmed = url
    .trim()
    .replace(/\/+$/, '')
    .replace(/\.git$/, '')
  const match = trimmed.match(/[/:]([^/:]+)\/([^/:]+)$/)
  return match ? match[2] : ''
}

/** 最初の URL を入れ終えたとき、名前が空ならリポジトリ名を自動セットする */
function onFirstRepoUrlChange() {
  if (form.name.trim()) return
  const name = repoNameFromUrl(form.repoUrls[0] ?? '')
  if (name) form.name = name
}

async function save() {
  if (!form.name.trim() || saving.value) return
  saving.value = true
  error.value = null
  try {
    const payload = {
      name: form.name.trim(),
      repoUrls: form.repoUrls.map((u) => u.trim()).filter((u) => u !== ''),
      description: form.description,
      archived: form.archived,
    }
    if (editing.value) {
      await projects.update(editing.value.id, payload)
    } else {
      await projects.create(payload)
    }
    modalOpen.value = false
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section>
    <ErrorBanner v-if="error" :message="error" />

    <div class="head">
      <RouterLink to="/" class="head__back">← 一覧</RouterLink>
      <button type="button" class="head__add" @click="openCreate">＋ 新規</button>
    </div>

    <h1 class="title">プロジェクト</h1>

    <p v-if="projects.loading" class="muted">読み込み中…</p>
    <p v-else-if="projects.all.length === 0" class="muted">まだありません。</p>

    <ul v-else class="list">
      <li v-for="project in projects.all" :key="project.id">
        <button type="button" class="row" @click="openEdit(project)">
          <span class="row__main">
            <span class="row__name">{{ project.name }}</span>
            <span v-if="project.description" class="row__desc">{{ project.description }}</span>
            <span v-for="url in project.repoUrls" :key="url" class="row__repo">{{ url }}</span>
          </span>
          <span v-if="project.archived" class="row__archived">アーカイブ</span>
        </button>
      </li>
    </ul>

    <div v-if="modalOpen" class="modal" @click.self="modalOpen = false">
      <form class="modal__panel" @submit.prevent="save">
        <h2 class="modal__title">{{ editing ? 'プロジェクトを編集' : '新しいプロジェクト' }}</h2>

        <div class="field">
          <span class="field__label">リポジトリ URL</span>
          <span class="field__hint">複数登録できる。最初の URL から名前を自動入力する</span>
          <div v-for="(_, i) in form.repoUrls" :key="i" class="repo-row">
            <input
              v-model="form.repoUrls[i]"
              type="url"
              placeholder="https://github.com/..."
              @change="i === 0 && onFirstRepoUrlChange()"
            />
            <button
              v-if="form.repoUrls.length > 1 || form.repoUrls[0] !== ''"
              type="button"
              class="repo-row__remove"
              aria-label="この URL を削除"
              @click="removeRepoUrl(i)"
            >
              ×
            </button>
          </div>
          <button type="button" class="repo-add" @click="addRepoUrl">＋ URL を追加</button>
        </div>

        <label class="field">
          <span class="field__label">名前<span class="field__required">必須</span></span>
          <span class="field__hint">MCP の list_tasks で指定する名前。リポジトリ名に揃えると迷わない</span>
          <input v-model="form.name" type="text" required placeholder="sample-project" />
        </label>

        <label class="field">
          <span class="field__label">説明</span>
          <textarea v-model="form.description" rows="2" />
        </label>

        <label v-if="editing" class="checkbox">
          <input v-model="form.archived" type="checkbox" />
          <span>アーカイブする</span>
        </label>

        <div class="actions">
          <button type="button" class="button button--ghost" @click="modalOpen = false">
            キャンセル
          </button>
          <button type="submit" class="button" :disabled="!form.name.trim() || saving">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0 0.5rem;
  font-size: 0.8125rem;
}

.head__back {
  color: var(--muted);
}

.head__add {
  background: none;
  border: none;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
}

.title {
  margin: 0.25rem 0 1rem;
  font-size: 1.125rem;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  color: var(--text);
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.row:hover {
  border-color: var(--accent);
}

.row__main {
  display: flex;
  flex-direction: column;
  gap: 0.1875rem;
  min-width: 0;
}

.row__name {
  font-weight: 600;
}

.row__desc,
.row__repo {
  font-size: 0.75rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__archived {
  font-size: 0.6875rem;
  color: var(--muted-dim);
  white-space: nowrap;
}

.modal {
  position: fixed;
  inset: 0;
  background: var(--overlay);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 1rem;
  z-index: 20;
}

.modal__panel {
  width: 100%;
  max-width: 30rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-raised);
  margin-bottom: env(safe-area-inset-bottom);
}

.modal__title {
  margin: 0;
  font-size: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.field__label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--muted);
}

.field__required {
  margin-left: 0.5rem;
  font-weight: 400;
  color: var(--danger);
}

.field__hint {
  font-size: 0.75rem;
  color: var(--muted-dim);
}

.repo-row {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.repo-row input {
  flex: 1;
  min-width: 0;
}

.repo-row__remove {
  background: none;
  border: none;
  color: var(--muted-dim);
  font-size: 1rem;
  line-height: 1;
  padding: 0.25rem;
  cursor: pointer;
}

.repo-row__remove:hover {
  color: var(--danger);
}

.repo-add {
  align-self: flex-start;
  background: none;
  border: none;
  padding: 0;
  color: var(--accent);
  font: inherit;
  font-size: 0.8125rem;
  cursor: pointer;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--muted);
}

.actions {
  display: flex;
  gap: 0.75rem;
}

.actions .button {
  flex: 1;
}

.muted {
  color: var(--muted);
}

@media (min-width: 40rem) {
  .modal {
    align-items: center;
  }
}
</style>
