export type User = { id: number; name: string; email: string; avatarUrl?: string | null }
export type Project = {
  id: number
  name: string
  description?: string | null
  status: string
  currentUserRole: string
  ownerName?: string
  memberCount?: number
  taskSummary: { total: number; todo?: number; inProgress?: number; done: number; completionRate: number }
}
export type Member = { id: number; user: User; role: string; joinedAt: string }
export type Task = {
  id: number
  projectId: number
  title: string
  description?: string | null
  status: TaskStatus
  priority: string
  assignee?: User | null
  creator: User
  dueDate?: string | null
  commentCount: number
  canEdit?: boolean
  canDelete?: boolean
}
export type Comment = { id: number; content: string; author: User; createdAt: string; canDelete: boolean }
export type Stats = {
  totalTasks: number
  todoTasks: number
  inProgressTasks: number
  doneTasks: number
  overdueTasks: number
  completionRate: number
  byAssignee: { userId: number | string; userName: string; total: number; done: number }[]
}
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export type ApiResult<T> = { success: boolean; data: T; message: string; code?: string }

const API_BASE = '/api'

let authToken = localStorage.getItem('team-flow-token') ?? ''

export function getToken() {
  return authToken
}

export function setToken(token: string) {
  authToken = token
  localStorage.setItem('team-flow-token', token)
}

export function clearToken() {
  authToken = ''
  localStorage.removeItem('team-flow-token')
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(API_BASE + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      ...options.headers,
    },
  })
  const payload = (await response.json()) as ApiResult<T>
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || payload.code || '请求失败')
  }
  return payload.data
}
