<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useTaskStore } from '@/stores/tasks'
import { useProjectStore } from '@/stores/projects'
import { TASK_STATUSES, STATUS_LABELS } from '@/api/types'
import type { TaskStatus } from '@/api/types'
import ErrorBanner from '@/components/ErrorBanner.vue'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const tasks = useTaskStore()
const projects = useProjectStore()

const isEdit = computed(() => props.id !== undefined)
const error = ref<string | null>(null)
const loading = ref(true)
const saving = ref(false)
// タスク内容より下(コンテキスト・受け入れ条件・スコープ外・状態)は
// 「高度な設定」として折りたたみ、保存ボタンをスクロール無しで押せるようにする
const advancedOpen = ref(false)

const form = reactive({
  projectId: null as number | null,
  title: '',
  context: '',
  acceptanceCriteria: '',
  outOfScope: '',
  status: 'todo' as TaskStatus,
})

onMounted(async () => {
  try {
    await projects.load()
    if (isEdit.value) {
      const detail = await tasks.detail(Number(props.id))
      form.projectId = detail.projectId
      form.title = detail.title
      form.context = detail.context ?? ''
      form.acceptanceCriteria = detail.acceptanceCriteria ?? ''
      form.outOfScope = detail.outOfScope ?? ''
      form.status = detail.status
      // 中身が入っているなら畳んで隠さない
      advancedOpen.value =
        form.context !== '' || form.acceptanceCriteria !== '' || form.outOfScope !== ''
    } else {
      form.projectId = projects.active[0]?.id ?? null
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

async function save() {
  if (!form.title.trim() || saving.value) return
  saving.value = true
  error.value = null
  try {
    const payload = {
      // プロジェクトは任意。未選択(null)ならそのまま送る
      projectId: form.projectId ?? undefined,
      title: form.title.trim(),
      // 空文字は「消す」を意味する (サーバー側で null に落とす)
      context: form.context,
      acceptanceCriteria: form.acceptanceCriteria,
      outOfScope: form.outOfScope,
      status: form.status,
    }
    if (isEdit.value) {
      await tasks.update(Number(props.id), payload)
      await router.replace({ name: 'task-detail', params: { id: props.id } })
    } else {
      const created = await tasks.create(payload)
      await router.replace({ name: 'task-detail', params: { id: String(created.id) } })
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!isEdit.value) return
  if (!window.confirm('このタスクとノートを削除します。よろしいですか?')) return
  error.value = null
  try {
    await tasks.remove(Number(props.id))
    await router.replace({ name: 'tasks' })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

<template>
  <section>
    <ErrorBanner v-if="error" :message="error" />
    <div class="head">
      <h1 class="title">{{ isEdit ? 'タスクを編集' : '新しいタスク' }}</h1>
      <!-- 削除は一覧・詳細には置かず、ここからだけ行う -->
      <button v-if="isEdit && !loading" type="button" class="delete" @click="remove">
        タスクを削除
      </button>
    </div>
    <p v-if="loading" class="muted">読み込み中…</p>

    <form v-else class="form" @submit.prevent="save">
      <label class="field">
        <span class="field__label">プロジェクト</span>
        <select v-model="form.projectId">
          <option :value="null">プロジェクトなし</option>
          <option v-for="project in projects.active" :key="project.id" :value="project.id">
            {{ project.name }}
          </option>
        </select>
      </label>

      <label class="field">
        <span class="field__label">タスク内容<span class="field__required">必須</span></span>
        <textarea v-model="form.title" rows="3" required placeholder="やりたいこと" />
      </label>

      <button
        type="button"
        class="fold"
        :aria-expanded="advancedOpen"
        @click="advancedOpen = !advancedOpen"
      >
        <span class="fold__chevron" :class="{ 'fold__chevron--open': advancedOpen }">▸</span>
        <span>高度な設定</span>
        <span class="fold__hint">コンテキスト・受け入れ条件・スコープ外{{ isEdit ? '・状態' : '' }}</span>
      </button>

      <!-- v-show なので畳んでも入力値は保持される -->
      <label v-show="advancedOpen" class="field">
        <span class="field__label">コンテキスト</span>
        <span class="field__hint">背景・現状・なぜやりたいのか。Markdown 可</span>
        <textarea v-model="form.context" rows="5" />
      </label>

      <label v-show="advancedOpen" class="field">
        <span class="field__label">受け入れ条件</span>
        <span class="field__hint">どうなったら完了か</span>
        <textarea v-model="form.acceptanceCriteria" rows="4" />
      </label>

      <label v-show="advancedOpen" class="field">
        <span class="field__label">スコープ外</span>
        <span class="field__hint">今回はやらないこと</span>
        <textarea v-model="form.outOfScope" rows="3" />
      </label>

      <label v-show="advancedOpen && isEdit" class="field">
        <span class="field__label">状態</span>
        <select v-model="form.status">
          <option v-for="status in TASK_STATUSES" :key="status" :value="status">
            {{ STATUS_LABELS[status] }}
          </option>
        </select>
      </label>

      <div class="actions">
        <button type="button" class="button button--ghost" @click="router.back()">キャンセル</button>
        <button type="submit" class="button" :disabled="!form.title.trim() || saving">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 1.25rem 0 1rem;
}

.title {
  font-size: 1.125rem;
  margin: 0;
}

.delete {
  padding: 0.25rem 0.625rem;
  border: 1px solid var(--danger);
  border-radius: 8px;
  background: transparent;
  color: var(--danger);
  font-size: 0.75rem;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 1.125rem;
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

.fold {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin: 0;
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

.fold:hover {
  color: var(--text);
}

.fold__chevron {
  display: inline-block;
  transition: transform 0.15s ease;
  color: var(--muted-dim);
}

.fold__chevron--open {
  transform: rotate(90deg);
}

.fold__hint {
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--muted-dim);
}

.actions {
  display: flex;
  gap: 0.75rem;
  padding-top: 0.5rem;
}

.actions .button {
  flex: 1;
}

.muted {
  color: var(--muted);
}
</style>
