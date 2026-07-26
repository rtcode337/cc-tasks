import type {
  Me,
  Note,
  Paged,
  Project,
  ProjectInput,
  Task,
  TaskDetail,
  TaskInput,
  TaskStatus,
} from './types'

/** 未認証。呼び出し側はログイン画面へ誘導する。 */
export class UnauthorizedError extends Error {
  constructor() {
    super('ログインが必要です')
    this.name = 'UnauthorizedError'
  }
}

/** サーバーが返した {"error":{"code","message"}} を運ぶ。 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** オフライン・回線断。オフライン対応はスコープ外なのでバナー表示のみ。 */
export class OfflineError extends Error {
  constructor() {
    super('ネットワークに接続できません')
    this.name = 'OfflineError'
  }
}

function readCookie(name: string): string | null {
  const hit = document.cookie.split('; ').find((row) => row.startsWith(`${name}=`))
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : null
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  // Spring Security の CookieCsrfTokenRepository と対になる
  const csrf = readCookie('XSRF-TOKEN')
  if (csrf && init.method && init.method !== 'GET') {
    headers.set('X-XSRF-TOKEN', csrf)
  }

  let response: Response
  try {
    response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  } catch {
    throw new OfflineError()
  }

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.status === 204) {
    return undefined as T
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const error = body?.error
    throw new ApiError(
      response.status,
      error?.code ?? 'unknown',
      error?.message ?? `リクエストが失敗しました (${response.status})`,
    )
  }
  return (await response.json()) as T
}

export const api = {
  me: () => request<Me>('/api/me'),

  logout: () => request<void>('/api/logout', { method: 'POST' }),

  listProjects: (archived?: boolean) => {
    const query = archived === undefined ? '?archived=' : `?archived=${archived}`
    return request<Project[]>(`/api/projects${query}`)
  },

  createProject: (input: ProjectInput) =>
    request<Project>('/api/projects', { method: 'POST', body: JSON.stringify(input) }),

  updateProject: (id: number, input: ProjectInput) =>
    request<Project>(`/api/projects/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),

  /** 並び替え。全プロジェクトの id を望む順で送ると、並び替え後の全件を返す。 */
  reorderProjects: (ids: number[]) =>
    request<Project[]>('/api/projects/order', { method: 'PUT', body: JSON.stringify({ ids }) }),

  listTasks: (params: { projectId?: number; status?: TaskStatus; done?: boolean } = {}) => {
    const query = new URLSearchParams()
    if (params.projectId !== undefined) query.set('projectId', String(params.projectId))
    if (params.status !== undefined) query.set('status', params.status)
    if (params.done !== undefined) query.set('done', String(params.done))
    const suffix = query.toString() ? `?${query}` : ''
    return request<Task[]>(`/api/tasks${suffix}`)
  },

  /** 完了タスクをページングで取得 (10 件/頁 など)。 */
  listDoneTasks: (params: { projectId?: number; page: number; size: number }) => {
    const query = new URLSearchParams({ done: 'true' })
    if (params.projectId !== undefined) query.set('projectId', String(params.projectId))
    query.set('page', String(params.page))
    query.set('size', String(params.size))
    return request<Paged<Task>>(`/api/tasks?${query}`)
  },

  getTask: (id: number) => request<TaskDetail>(`/api/tasks/${id}`),

  createTask: (input: TaskInput) =>
    request<Task>('/api/tasks', { method: 'POST', body: JSON.stringify(input) }),

  updateTask: (id: number, input: TaskInput) =>
    request<Task>(`/api/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),

  deleteTask: (id: number) => request<void>(`/api/tasks/${id}`, { method: 'DELETE' }),

  addNote: (taskId: number, body: string) =>
    request<Note>(`/api/tasks/${taskId}/notes`, {
      method: 'POST',
      body: JSON.stringify({ body }),
    }),
}
