<script setup lang="ts">
import { computed } from 'vue'
import type { Task } from '@/api/types'
import { claudeCodeUrl } from '@/lib/claudeCode'

const props = defineProps<{ task: Task; repoUrls?: string[] }>()

const href = computed(() => claudeCodeUrl(props.task, props.repoUrls))

/**
 * リンクの直接タップだとスマホでユニバーサルリンクが発火して Claude アプリに
 * 横取りされ、prompt / repositories のプリフィルが失われる(アプリはクエリを
 * 引き継がない)。空タブを開いてから JS で遷移させるとアプリ判定を通らず、
 * モバイルでもブラウザ版 claude.ai/code が開いてプリフィルが効く。
 */
function open() {
  const w = window.open('about:blank', '_blank')
  if (!w) return
  // about:blank の時点では同一オリジンなので opener を切ってから遷移する
  w.opener = null
  w.location.href = href.value
}
</script>

<template>
  <!-- タスク内容をプリフィルして Claude Code を開くハンドオフボタン -->
  <a
    class="icon"
    :href="href"
    target="_blank"
    rel="noopener noreferrer"
    aria-label="Claude Code で開く"
    title="Claude Code で開く(内容をプリフィル)"
    @click.stop.prevent="open"
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
