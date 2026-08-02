<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useTaskStore } from '@/stores/tasks'
import { useProjectStore } from '@/stores/projects'
import { backdropClose } from '@/lib/backdropClose'
import type { TaskExport, TaskImportResult } from '@/api/types'
import ErrorBanner from '@/components/ErrorBanner.vue'
import CopyButton from '@/components/CopyButton.vue'

/**
 * 未完了タスクの書き出し / 読み込み。DB を失っても打ち直さずに戻せるようにするための機能。
 * **書き出したものをそのまま読み込める**ので、テキストを手元に置けばバックアップになる。
 *
 * 何を作り何を飛ばすかの判断はサーバー側(`TaskTransferService`)に任せ、
 * 画面は貼られた文字列を JSON として解釈するところまでを持つ。
 */
const props = defineProps<{ mode: 'export' | 'import' }>()

const emit = defineEmits<{ close: [] }>()

const backdrop = backdropClose(() => emit('close'))

const tasks = useTaskStore()
const projects = useProjectStore()
const error = ref<string | null>(null)
const busy = ref(false)

// --- 書き出し ---
const exported = ref('')

// --- 読み込み ---
const input = ref('')
/** dryRun の結果。null = まだ確認していない */
const preview = ref<TaskImportResult | null>(null)

onMounted(async () => {
  if (props.mode !== 'export') return
  busy.value = true
  try {
    // 貼り付けやすいよう整形する。この文字列をそのまま読み込みに渡せる
    exported.value = JSON.stringify(await tasks.exportAll(), null, 2)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
})

/** 貼られた文字列を JSON として読む。壊れていれば読み込みに行かず手元で弾く */
function parse(): TaskExport | null {
  try {
    return JSON.parse(input.value) as TaskExport
  } catch {
    error.value = 'JSON として読めません。書き出した内容をそのまま貼り付けてください'
    return null
  }
}

/** 本文を変えたら、確認済みの内容は古くなるので破棄する */
function invalidatePreview() {
  preview.value = null
}

async function confirm() {
  if (busy.value) return
  error.value = null
  const data = parse()
  if (!data) return
  busy.value = true
  try {
    preview.value = await tasks.importAll(data, true)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function run() {
  if (busy.value || !preview.value) return
  error.value = null
  const data = parse()
  if (!data) return
  busy.value = true
  try {
    await tasks.importAll(data)
    // プロジェクトが増えていることがあるので一覧を取り直す
    await projects.load(true)
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <!-- 背景クリックで閉じる。本文を選択して背景で指を離したときは閉じない -->
  <div class="modal" v-on="backdrop">
    <div class="modal__panel">
      <div class="modal__head">
        <h2 class="modal__title">{{ mode === 'export' ? 'タスクの書き出し' : 'タスクの読み込み' }}</h2>
        <CopyButton v-if="mode === 'export' && exported" icon :text="exported" label="書き出した内容をコピー" />
      </div>

      <ErrorBanner v-if="error" :message="error" />

      <!-- 書き出し -->
      <template v-if="mode === 'export'">
        <p class="hint">
          未完了のタスクを、所属プロジェクトの名前とリポジトリ付きで書き出す。
          コピーして手元に保存しておけば、この内容をそのまま「読み込み」に貼って戻せる。
          完了タスクとプロジェクトの説明は含まない。
        </p>
        <p v-if="busy" class="muted">書き出し中…</p>
        <pre v-else class="dump">{{ exported }}</pre>
      </template>

      <!-- 読み込み -->
      <template v-else>
        <label class="field">
          <span class="field__label">書き出した内容<span class="field__required">必須</span></span>
          <span class="field__hint">
            プロジェクトは名前で照合し、無ければリポジトリごと作る。既にあるプロジェクトは触らない。
            同じタイトルの未完了タスクが既にあれば飛ばすので、二度読み込んでも増えない。
          </span>
          <textarea
            v-model="input"
            rows="10"
            required
            placeholder='{ "version": 1, "projects": [ … ] }'
            @input="invalidatePreview"
          />
        </label>

        <!-- 何が入るかを先に見せる -->
        <div v-if="preview" class="preview">
          <p v-if="preview.createdProjects.length" class="preview__head">
            作るプロジェクト ({{ preview.createdProjects.length }})
          </p>
          <ul v-if="preview.createdProjects.length" class="preview__list">
            <li v-for="name in preview.createdProjects" :key="name">{{ name }}</li>
          </ul>

          <p class="preview__head">作るタスク ({{ preview.createdTasks.length }})</p>
          <ul v-if="preview.createdTasks.length" class="preview__list">
            <li v-for="label in preview.createdTasks" :key="label">{{ label }}</li>
          </ul>
          <p v-else class="muted">新しく作るタスクはありません。</p>

          <template v-if="preview.skippedTasks.length">
            <p class="preview__head">既にあるので飛ばす ({{ preview.skippedTasks.length }})</p>
            <ul class="preview__list preview__list--muted">
              <li v-for="label in preview.skippedTasks" :key="label">{{ label }}</li>
            </ul>
          </template>
        </div>
      </template>

      <div class="actions">
        <button type="button" class="button button--ghost" @click="emit('close')">閉じる</button>
        <template v-if="mode === 'import'">
          <button
            v-if="!preview"
            type="button"
            class="button"
            :disabled="!input.trim() || busy"
            @click="confirm"
          >
            {{ busy ? '確認中…' : '確認' }}
          </button>
          <button
            v-else
            type="button"
            class="button"
            :disabled="busy || preview.createdTasks.length === 0"
            @click="run"
          >
            {{ busy ? '読み込み中…' : '読み込む' }}
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
  max-width: 40rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface-raised);
  margin-bottom: env(safe-area-inset-bottom);
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.modal__title {
  margin: 0;
  font-size: 1rem;
}

.hint {
  margin: 0;
  font-size: 0.6875rem;
  line-height: 1.6;
  color: var(--muted-dim);
}

/* 書き出した素の JSON。整形せずそのまま出す(コピーするのはこれ自体だから) */
.dump {
  margin: 0;
  max-height: 55vh;
  overflow: auto;
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  font-size: 0.75rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
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
  line-height: 1.6;
  color: var(--muted-dim);
}

.field textarea {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.75rem;
}

.preview {
  max-height: 40vh;
  overflow: auto;
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
}

.preview__head {
  margin: 0.75rem 0 0.375rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
}

.preview__head:first-child {
  margin-top: 0;
}

.preview__list {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8125rem;
  line-height: 1.7;
}

.preview__list--muted {
  color: var(--muted-dim);
}

.actions {
  display: flex;
  gap: 0.75rem;
}

.actions .button {
  flex: 1;
}

.muted {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--muted);
}

@media (min-width: 40rem) {
  .modal {
    align-items: center;
  }
}
</style>
