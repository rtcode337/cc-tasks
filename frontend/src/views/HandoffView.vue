<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'

/**
 * Claude Code へのハンドオフ用リダイレクトページ。
 * claude.ai へのリンクを直接タップするとスマホではユニバーサルリンクで
 * Claude アプリが起動し、prompt / repositories のクエリが失われる。
 * 同一オリジンのこのページを経由し、タップのユーザー操作が消えてから
 * JS で遷移することで、アプリ起動の判定を避けてブラウザ版を開く。
 */
const route = useRoute()

const raw = typeof route.query.to === 'string' ? route.query.to : ''
// open redirect 防止: 遷移先は Claude Code の URL のみ許可
const target = raw.startsWith('https://claude.ai/') ? raw : null

onMounted(() => {
  if (!target) return
  // マウント直後でもタップの transient activation が残っている可能性があるため一拍置く
  setTimeout(() => window.location.replace(target), 100)
})
</script>

<template>
  <section class="handoff">
    <template v-if="target">
      <p class="handoff__main">Claude Code を開いています…</p>
      <p class="handoff__sub">
        自動で開かない場合は <a :href="target">こちらをタップ</a>
      </p>
    </template>
    <p v-else class="handoff__main">リンクが不正です。</p>
  </section>
</template>

<style scoped>
.handoff {
  padding: 3rem 0;
  text-align: center;
}

.handoff__main {
  color: var(--text);
}

.handoff__sub {
  font-size: 0.8125rem;
  color: var(--muted);
}

.handoff__sub a {
  color: var(--accent);
}
</style>
