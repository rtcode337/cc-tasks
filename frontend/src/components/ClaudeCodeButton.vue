<script setup lang="ts">
import { computed } from 'vue'
import type { Task } from '@/api/types'
import { claudeCodeUrl } from '@/lib/claudeCode'

const props = defineProps<{ task: Task; repoUrls?: string[] }>()

const href = computed(() => claudeCodeUrl(props.task, props.repoUrls))
</script>

<template>
  <!-- タスク内容をプリフィルして Claude Code を開くハンドオフボタン。
       スマホではユニバーサルリンクで Claude アプリが開きプリフィルは失われる
       (空タブ経由・中継ページ /handoff 経由の JS 遷移でも回避できなかった)。
       モバイルは 📋 コピーで貼り付ける運用とし、ここは素直な直リンクにする -->
  <a
    class="icon"
    :href="href"
    target="_blank"
    rel="noopener noreferrer"
    aria-label="Claude Code で開く"
    title="Claude Code で開く(内容をプリフィル)"
    @click.stop
  >
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        d="M12 3.5v17M3.5 12h17M6 6l12 12M18 6L6 18"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
      />
    </svg>
  </a>
</template>

<style scoped>
.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.875rem;
  height: 1.875rem;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--muted);
  cursor: pointer;
  flex-shrink: 0;
}

.icon:hover {
  color: var(--accent);
  border-color: var(--accent);
}
</style>
