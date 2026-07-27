<script setup lang="ts">
import CopyButton from '@/components/CopyButton.vue'
import ClaudeCodeButton from '@/components/ClaudeCodeButton.vue'
import type { Task } from '@/api/types'

/** トップの未完了一覧のカード。未紐づけ・プロジェクトどちらのリストからも使う。 */
defineProps<{ task: Task; repoUrls?: string[] }>()

const emit = defineEmits<{ edit: []; complete: []; reopen: [] }>()
</script>

<template>
  <!-- 本文を押すと編集モーダル。専用の編集ボタンは置かない -->
  <div class="card">
    <button type="button" class="card__memo" @click="emit('edit')">{{ task.title }}</button>

    <!-- 右カラム。メモが複数行なら伸びて、ボタンの下端がメモの下端に揃う -->
    <div class="card__side">
      <!-- data-no-drag: ここの長押しはドラッグにしない。特に ✳ は iOS で
           「長押し → Safari で開く」を選ばせる必要があるので奪ってはいけない -->
      <span class="card__tools" data-no-drag>
        <CopyButton icon :text="task.title" />
        <ClaudeCodeButton :task="task" :repo-urls="repoUrls" />
      </span>
      <span class="card__buttons" data-no-drag>
        <!-- 完了済みの一覧では同じ位置が「戻す」になる -->
        <button v-if="task.status === 'done'" type="button" class="btn" @click="emit('reopen')">
          未完了に戻す
        </button>
        <button v-else type="button" class="btn btn--done" @click="emit('complete')">完了</button>
      </span>
    </div>
  </div>
</template>

<style scoped>
.card {
  display: flex;
  /* 右カラムをメモの高さまで伸ばすため stretch */
  align-items: stretch;
  gap: 0.625rem;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  padding: 0.75rem;
  /* 長押しでドラッグを始めるので、iOS のリンクプレビュー等の長押しメニューは出さない */
  -webkit-touch-callout: none;
}

/* ボタン類は長押しメニューを残す(✳ の「Safari で開く」を選べるように) */
.card [data-no-drag] {
  -webkit-touch-callout: default;
}

.card__memo {
  flex: 1;
  min-width: 0;
  /* 1 行目をアイコンの高さに合わせる */
  padding: 0.1875rem 0 0;
  border: none;
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  cursor: pointer;
}

.card__memo:hover {
  color: var(--accent);
}

/* 上にコピー / ✳、下に完了ボタン */
.card__side {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  gap: 0.625rem;
  flex-shrink: 0;
}

.card__tools {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card__buttons {
  display: flex;
  align-items: center;
  gap: 0.5rem;
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
</style>
