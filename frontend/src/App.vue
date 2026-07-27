<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { currentRefreshHandler } from '@/lib/pullToRefresh'
import { isDragActive } from '@/lib/dragSort'
import ErrorBanner from '@/components/ErrorBanner.vue'
import AppHeader from '@/components/AppHeader.vue'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const bootError = ref<string | null>(null)
const offline = ref(!navigator.onLine)

// ---- 下に引っ張って更新(PWA にはリロード手段が無いため) ----
// 指の移動量には減衰(DAMP)を掛けてから pull に入れる。TRIGGER は減衰後の値
const DAMP = 0.4
const TRIGGER = 32 // 指の移動 80px 相当
const PULL_MAX = 80

const pull = ref(0)
const refreshing = ref(false)
const armed = computed(() => pull.value >= TRIGGER)
let startY = 0
let tracking = false

function onTouchStart(e: TouchEvent) {
  // ページ最上部で触れたときだけ追跡を始める(スクロール中は何もしない)
  tracking = window.scrollY <= 0 && !refreshing.value
  startY = e.touches[0].clientY
}

function onTouchMove(e: TouchEvent) {
  if (!tracking) return
  // 長押しからの並び替え中は指を下に動かすので、引っ張り更新と食い合う。並び替えを優先する
  if (isDragActive()) {
    tracking = false
    pull.value = 0
    return
  }
  if (window.scrollY > 0) {
    tracking = false
    pull.value = 0
    return
  }
  const dy = e.touches[0].clientY - startY
  pull.value = dy > 0 ? Math.min(dy * DAMP, PULL_MAX) : 0
}

async function onTouchEnd() {
  if (!tracking) return
  tracking = false
  if (!armed.value) {
    pull.value = 0
    return
  }
  refreshing.value = true
  try {
    const handler = currentRefreshHandler()
    if (handler) {
      await handler()
    } else {
      // 再読込処理を持たない画面はページ全体をリロード
      window.location.reload()
      return
    }
  } finally {
    refreshing.value = false
    pull.value = 0
  }
}

function onTouchCancel() {
  tracking = false
  pull.value = 0
}

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
  <div
    class="app"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend.passive="onTouchEnd"
    @touchcancel.passive="onTouchCancel"
  >
    <!-- 引っ張り量に応じて出るインジケータ -->
    <div
      v-if="pull > 0 || refreshing"
      class="ptr"
      :style="refreshing ? undefined : { opacity: String(Math.min(pull / TRIGGER, 1)) }"
      aria-hidden="true"
    >
      <span v-if="refreshing" class="ptr__spinner" />
      <span v-else class="ptr__arrow" :class="{ 'ptr__arrow--armed': armed }">↓</span>
    </div>

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

.ptr {
  position: fixed;
  top: calc(3.25rem + env(safe-area-inset-top));
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--surface-raised);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  pointer-events: none;
}

.ptr__arrow {
  font-size: 1rem;
  color: var(--muted);
  transition: transform 0.15s ease;
}

/* しきい値を超えたら矢印を反転して「離すと更新」を示す */
.ptr__arrow--armed {
  transform: rotate(180deg);
  color: var(--accent);
}

.ptr__spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: ptr-spin 0.7s linear infinite;
}

@keyframes ptr-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
