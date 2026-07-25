export type TaskStatus = 'todo' | 'in_progress' | 'done'

export const TASK_STATUSES: TaskStatus[] = ['todo', 'in_progress', 'done']

export const STATUS_LABELS: Record<TaskStatus, string> = {
  todo: '未着手',
  in_progress: '着手中',
  done: '完了',
}

export interface Project {
  id: number
  name: string
  repoUrls: string[]
  description?: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface Task {
  id: number
  projectId: number
  title: string
  context?: string | null
  acceptanceCriteria?: string | null
  outOfScope?: string | null
  status: TaskStatus
  createdAt: string
  updatedAt: string
}

export interface Note {
  id: number
  taskId: number
  author: 'human' | 'claude_code'
  body: string
  createdAt: string
}

export interface TaskDetail extends Task {
  projectName: string
  notes: Note[]
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
  projectId?: number
  title?: string
  context?: string | null
  acceptanceCriteria?: string | null
  outOfScope?: string | null
  status?: TaskStatus
}

export interface ProjectInput {
  name?: string
  /** undefined = 変更しない、空配列 = 全部消す */
  repoUrls?: string[]
  description?: string | null
  archived?: boolean
}
