import { useTaskStore } from '@/stores/tasks'
import type { DropTarget } from '@/lib/dragSort'
import { UNLINKED_KEY } from '@/lib/groups'
import type { TaskGroup } from '@/lib/groups'
import type { Task } from '@/api/types'

/**
 * 移動先のカードリストで、ポインタ Y がどの位置に入るかを返す。
 * タスクが 1 件も無いグループには `.cards` が無く、折りたたまれていれば測れないので、
 * どちらも末尾(= 空なら 0)に入れる。
 */
function insertIndexAt(zone: HTMLElement, pointerY: number): number {
  const cards = zone.querySelector('.cards')
  if (!(cards instanceof HTMLElement)) return 0
  const rows = Array.from(cards.children) as HTMLElement[]
  if (cards.offsetParent === null) return rows.length
  for (let i = 0; i < rows.length; i++) {
    const rect = rows[i].getBoundingClientRect()
    if (pointerY < rect.top + rect.height / 2) return i
  }
  return rows.length
}

/**
 * 別のプロジェクトのグループまで運ばれたタスクを、そのプロジェクトへ移す。
 * 落とした位置にそのまま入るよう、移動先のグループを並び替え直す。
 *
 * 移動したら true。移動先が見つからない・未分類へ戻そうとした場合は false を返すので、
 * 呼び出し側は元のグループ内の並び替えとして続ければよい。
 */
export async function moveTaskToProject(
  drop: DropTarget<Task>,
  groups: TaskGroup[],
): Promise<boolean> {
  // 未分類へ戻す口はまだ無い(PATCH の projectId は null = 「変更しない」のため)
  if (drop.id === UNLINKED_KEY) return false

  const target = groups.find((g) => g.key === drop.id)
  if (!target?.project) return false

  const task = drop.item
  const insertAt = insertIndexAt(drop.el, drop.pointerY)
  const ids = target.tasks.filter((t) => t.id !== task.id).map((t) => t.id)
  ids.splice(insertAt, 0, task.id)

  const tasks = useTaskStore()
  await tasks.update(task.id, { projectId: target.project.id })
  await tasks.reorder(target.project.id, ids)
  return true
}
