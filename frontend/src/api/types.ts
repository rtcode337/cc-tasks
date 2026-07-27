/** 未完了(todo)と完了(done)の 2 つだけ。wire 値の 'todo' は「未完了」の意味 */
export type TaskStatus = 'todo' | 'done'

export interface Project {
  id: number
  name: string
  repoUrls: string[]
  description?: string | null
  archived: boolean
  /** 手動並び替えの表示順(昇順) */
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** 更新時に projectId へこれを送ると紐づけを外す(未分類に戻す)。null は「変更しない」 */
export const UNLINK_PROJECT_ID = 0

export interface Task {
  id: number
  /** 未分類(どのプロジェクトにも紐づいていない)なら欠落する(JSON は null を落とす設定) */
  projectId?: number | null
  title: string
  status: TaskStatus
  /** プロジェクト内の手動並び順(昇順)。0 = 未並び替えでグループの先頭 */
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface TaskDetail extends Task {
  /** 未分類なら欠落する */
  projectName?: string
}

/** すべての Claude Code 環境に効かせたい共通ルールの 1 本。本文は Markdown。 */
export interface Rule {
  id: number
  title: string
  body: string
  /** false のルールは連結に含めない */
  enabled: boolean
  /** 手動並び替えの表示順(昇順)。連結の順にもなる */
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface RuleInput {
  title?: string
  body?: string
  enabled?: boolean
}

export interface Me {
  email: string
  name?: string | null
  pictureUrl?: string | null
}

export interface Paged<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface TaskInput {
  /** 更新では undefined = 変更しない、{@link UNLINK_PROJECT_ID}(0) = 紐づけを外す */
  projectId?: number
  title?: string
  status?: TaskStatus
}

export interface ProjectInput {
  name?: string
  /** undefined = 変更しない、空配列 = 全部消す */
  repoUrls?: string[]
  description?: string | null
  archived?: boolean
}
