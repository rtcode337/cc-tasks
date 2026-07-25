<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import ErrorBanner from '@/components/ErrorBanner.vue'
import AppHeader from '@/components/AppHeader.vue'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const bootError = ref<string | null>(null)
const offline = ref(!navigator.onLine)

onMounted(async () => {
  window.addEventListener('online', () => (offline.value = false))
  window.addEventListener('offline', () => (offline.value = true))
  try {
    const ok = await session.load()
    if (!ok) {
      await router.replace({ name: 'login' })
    } else if (route.name === 'login') {
      await router.replace({ name: 'home' })
    }
  } catch (error) {
    bootError.value = error instanceof Error ? error.message : String(error)
  }
})
</script>

<template>
  <div class="app">
    <AppHeader v-if="session.me" />
    <!-- オフライン時はバナーのみ。入力の退避は将来検討 (仕様書 §8) -->
    <ErrorBanner v-if="offline" kind="warn" message="オフラインです。通信が回復するまで保存できません。" />
    <ErrorBanner v-if="bootError" :message="bootError" />
    <main class="app__main">
      <RouterView v-if="session.checked" />
      <p v-else class="app__loading">読み込み中…</p>
    </main>
  </div>
</template>

<style scoped>
.app {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

.app__main {
  flex: 1;
  width: 100%;
  max-width: 46rem;
  margin: 0 auto;
  padding: 0 1rem calc(5rem + env(safe-area-inset-bottom));
}

.app__loading {
  padding: 2rem 0;
  color: var(--muted);
}
</style>
