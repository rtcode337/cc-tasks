import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/client'
import type { Project, ProjectInput } from '@/api/types'

export const useProjectStore = defineStore('projects', () => {
  /** アーカイブ済みも含めた全件。表示側で絞る。 */
  const all = ref<Project[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const active = computed(() => all.value.filter((p) => !p.archived))
  const byId = computed(() => new Map(all.value.map((p) => [p.id, p])))

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      all.value = await api.listProjects(undefined)
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function create(input: ProjectInput) {
    const created = await api.createProject(input)
    all.value = [...all.value, created].sort((a, b) => a.name.localeCompare(b.name))
    return created
  }

  async function update(id: number, input: ProjectInput) {
    const updated = await api.updateProject(id, input)
    all.value = all.value.map((p) => (p.id === id ? updated : p))
    return updated
  }

  function name(id: number): string {
    return byId.value.get(id)?.name ?? `#${id}`
  }

  return { all, active, byId, loading, loaded, load, create, update, name }
})
