<script setup lang="ts">
import { STATUS_LABELS } from '@/api/types'
import type { TaskStatus } from '@/api/types'

defineProps<{ status: TaskStatus }>()
</script>

<template>
  <span class="badge" :class="`badge--${status}`">{{ STATUS_LABELS[status] }}</span>
</template>

<style scoped>
.badge {
  display: inline-block;
  padding: 0.125rem 0.5rem;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 600;
  white-space: nowrap;
}

.badge--todo {
  background: var(--badge-todo-bg);
  color: var(--badge-todo-text);
}

.badge--in_progress {
  background: var(--badge-inprogress-bg);
  color: var(--badge-inprogress-text);
}

.badge--done {
  background: var(--badge-done-bg);
  color: var(--badge-done-text);
}
</style>
