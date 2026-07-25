import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/client'
import type { Task, TaskDetail, TaskInput } from '@/api/types'

/**
 * 未完了(done 以外)のタスクをここに保持し、画面側でフィルタする。
 * 完了タスクは件数が増えるためストアには持たず、一覧画面がページングで直接取得する。
 * 並びは作成日時降順で固定。
 */
export const useTaskStore = defineStore('tasks', () => {
  const active = ref<Task[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const todo = computed(() => active.value.filter((t) => t.status === 'todo'))

  function sort(list: Task[]): Task[] {
    return [...list].sort((a, b) => {
      if (a.createdAt !== b.createdAt) return a.createdAt < b.createdAt ? 1 : -1
      return b.id - a.id
    })
  }

  /** 更新結果を active に反映(done になったら active から外す)。 */
  function apply(updated: Task) {
    if (updated.status === 'done') {
      active.value = active.value.filter((t) => t.id !== updated.id)
      return
    }
    const exists = active.value.some((t) => t.id === updated.id)
    active.value = sort(
      exists ? active.value.map((t) => (t.id === updated.id ? updated : t)) : [updated, ...active.value],
    )
  }

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      active.value = sort(await api.listTasks({ done: false }))
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function create(input: TaskInput) {
    const created = await api.createTask(input)
    if (created.status !== 'done') active.value = sort([created, ...active.value])
    return created
  }

  async function update(id: number, input: TaskInput) {
    const updated = await api.updateTask(id, input)
    apply(updated)
    return updated
  }

  /** 完了にする(削除ではなく status=done)。 */
  async function complete(id: number) {
    return update(id, { status: 'done' })
  }

  async function remove(id: number) {
    await api.deleteTask(id)
    active.value = active.value.filter((t) => t.id !== id)
  }

  function detail(id: number): Promise<TaskDetail> {
    return api.getTask(id)
  }

  return { active, todo, loading, loaded, load, create, update, complete, remove, detail }
})
