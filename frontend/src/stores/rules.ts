import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '@/api/client'
import type { Rule, RuleInput } from '@/api/types'

/**
 * すべての Claude Code 環境に効かせたい共通ルール。
 * 並び順がそのまま連結順になるので、表示順は sortOrder 昇順で固定する。
 */
export const useRuleStore = defineStore('rules', () => {
  const all = ref<Rule[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const enabledCount = computed(() => all.value.filter((r) => r.enabled).length)

  function sort(list: Rule[]): Rule[] {
    return [...list].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  }

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    try {
      all.value = sort(await api.listRules())
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function create(input: RuleInput) {
    // 新規は並びの末尾に付く(sortOrder はサーバーが採番)
    const created = await api.createRule(input)
    all.value = sort([...all.value, created])
    return created
  }

  async function update(id: number, input: RuleInput) {
    const updated = await api.updateRule(id, input)
    all.value = sort(all.value.map((r) => (r.id === id ? updated : r)))
    return updated
  }

  async function remove(id: number) {
    await api.deleteRule(id)
    all.value = all.value.filter((r) => r.id !== id)
  }

  /** 並び替え。全ルールの id を望む順で渡す。 */
  async function reorder(ids: number[]) {
    all.value = sort(await api.reorderRules(ids))
  }

  /** 有効なルールを連結した Markdown をサーバーから取り直す。 */
  async function combined(): Promise<string> {
    return (await api.combinedRules()).markdown
  }

  return { all, enabledCount, loading, loaded, load, create, update, remove, reorder, combined }
})
