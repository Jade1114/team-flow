import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type User = { id: number; name: string; email: string; avatarUrl?: string | null }
type Project = {
  id: number
  name: string
  description?: string | null
  status: string
  currentUserRole: string
  ownerName?: string
  memberCount?: number
  taskSummary: { total: number; todo?: number; inProgress?: number; done: number; completionRate: number }
}
type Member = { id: number; user: User; role: string; joinedAt: string }
type Task = {
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
type Comment = { id: number; content: string; author: User; createdAt: string; canDelete: boolean }
type Stats = {
  totalTasks: number
  todoTasks: number
  inProgressTasks: number
  doneTasks: number
  overdueTasks: number
  completionRate: number
  byAssignee: { userId: number | string; userName: string; total: number; done: number }[]
}
type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
type ApiResult<T> = { success: boolean; data: T; message: string; code?: string }

const statusColumns: { status: TaskStatus; title: string }[] = [
  { status: 'TODO', title: '待处理' },
  { status: 'IN_PROGRESS', title: '进行中' },
  { status: 'DONE', title: '已完成' },
]
const priorityLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('team-flow-token') ?? '')
  const [user, setUser] = useState<User | null>(null)
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [authForm, setAuthForm] = useState({ name: '张三', email: 'zhangsan@example.com', password: '123456' })
  const [projects, setProjects] = useState<Project[]>([])
  const [activeProjectId, setActiveProjectId] = useState<number | null>(null)
  const [project, setProject] = useState<Project | null>(null)
  const [members, setMembers] = useState<Member[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [stats, setStats] = useState<Stats | null>(null)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [message, setMessage] = useState('')
  const [projectForm, setProjectForm] = useState({ name: '', description: '' })
  const [inviteEmail, setInviteEmail] = useState('lisi@example.com')
  const [taskForm, setTaskForm] = useState({ title: '', description: '', priority: 'MEDIUM', assigneeId: '', dueDate: '' })
  const [commentText, setCommentText] = useState('')
  const [draggingTaskId, setDraggingTaskId] = useState<number | null>(null)

  const activeProject = useMemo(
    () => projects.find((item) => item.id === activeProjectId) ?? project,
    [activeProjectId, project, projects],
  )

  useEffect(() => {
    if (!token) return
    localStorage.setItem('team-flow-token', token)
    loadMe()
    loadProjects()
  }, [token])

  useEffect(() => {
    if (activeProjectId) {
      loadProject(activeProjectId)
      loadMembers(activeProjectId)
      loadBoard(activeProjectId)
      loadStats(activeProjectId)
    }
  }, [activeProjectId])

  async function api<T>(path: string, options: RequestInit = {}) {
    const response = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    })
    const payload = (await response.json()) as ApiResult<T>
    if (!response.ok || !payload.success) {
      throw new Error(payload.message || payload.code || '请求失败')
    }
    return payload.data
  }

  async function loadMe() {
    try {
      setUser(await api<User>('/api/auth/me'))
    } catch {
      logout()
    }
  }

  async function loadProjects() {
    const data = await api<{ items: Project[] }>('/api/projects')
    setProjects(data.items)
    setActiveProjectId((current) => current ?? data.items[0]?.id ?? null)
  }

  async function loadProject(projectId: number) {
    setProject(await api<Project>(`/api/projects/${projectId}`))
  }

  async function loadMembers(projectId: number) {
    const data = await api<{ items: Member[] }>(`/api/projects/${projectId}/members`)
    setMembers(data.items)
  }

  async function loadBoard(projectId: number) {
    const data = await api<{ columns: { status: TaskStatus; tasks: Task[] }[] }>(`/api/projects/${projectId}/board`)
    setTasks(data.columns.flatMap((column) => column.tasks))
  }

  async function loadStats(projectId: number) {
    setStats(await api<Stats>(`/api/projects/${projectId}/stats`))
  }

  async function refreshProject(projectId = activeProjectId) {
    if (!projectId) return
    await Promise.all([loadProjects(), loadProject(projectId), loadMembers(projectId), loadBoard(projectId), loadStats(projectId)])
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const path = authMode === 'login' ? '/api/auth/login' : '/api/auth/register'
    try {
      const data = await api<{ token: string; user: User }>(path, { method: 'POST', body: JSON.stringify(authForm) })
      setToken(data.token)
      setUser(data.user)
      setMessage(authMode === 'login' ? '登录成功' : '注册成功')
    } catch (error) {
      setMessage((error as Error).message)
    }
  }

  async function createProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const created = await api<Project>('/api/projects', { method: 'POST', body: JSON.stringify(projectForm) })
    setProjectForm({ name: '', description: '' })
    setActiveProjectId(created.id)
    await loadProjects()
  }

  async function inviteMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!activeProjectId) return
    await api(`/api/projects/${activeProjectId}/invites`, { method: 'POST', body: JSON.stringify({ email: inviteEmail, role: 'MEMBER' }) })
    setMessage('成员已加入项目')
    await refreshProject()
  }

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!activeProjectId) return
    await api(`/api/projects/${activeProjectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify({
        ...taskForm,
        assigneeId: taskForm.assigneeId ? Number(taskForm.assigneeId) : null,
        dueDate: taskForm.dueDate || null,
      }),
    })
    setTaskForm({ title: '', description: '', priority: 'MEDIUM', assigneeId: '', dueDate: '' })
    await refreshProject()
  }

  async function moveTask(task: Task, status: TaskStatus) {
    await api(`/api/tasks/${task.id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
    await refreshProject()
    if (selectedTask?.id === task.id) openTask(task.id)
  }

  async function openTask(taskId: number) {
    const [detail, commentPage] = await Promise.all([
      api<Task>(`/api/tasks/${taskId}`),
      api<{ items: Comment[] }>(`/api/tasks/${taskId}/comments`),
    ])
    setSelectedTask(detail)
    setComments(commentPage.items)
  }

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedTask) return
    await api(`/api/tasks/${selectedTask.id}/comments`, { method: 'POST', body: JSON.stringify({ content: commentText }) })
    setCommentText('')
    await openTask(selectedTask.id)
    await refreshProject()
  }

  async function dropTask(status: TaskStatus) {
    const task = tasks.find((item) => item.id === draggingTaskId)
    setDraggingTaskId(null)
    if (!task || task.status === status) return
    await moveTask(task, status)
  }

  async function deleteTask(taskId: number) {
    await api(`/api/tasks/${taskId}`, { method: 'DELETE' })
    setSelectedTask(null)
    await refreshProject()
  }

  function logout() {
    localStorage.removeItem('team-flow-token')
    setToken('')
    setUser(null)
    setProjects([])
    setActiveProjectId(null)
  }

  if (!token || !user) {
    return (
      <main className="auth-shell">
        <section className="auth-panel">
          <div>
            <p className="eyebrow">Team Flow</p>
            <h1>小团队项目协作工作台</h1>
            <p className="muted">用项目、成员、任务看板、评论和进度统计跑通一个清爽的 MVP。</p>
          </div>
          <form onSubmit={submitAuth} className="auth-form">
            <div className="segmented">
              <button type="button" className={authMode === 'login' ? 'active' : ''} onClick={() => setAuthMode('login')}>
                登录
              </button>
              <button type="button" className={authMode === 'register' ? 'active' : ''} onClick={() => setAuthMode('register')}>
                注册
              </button>
            </div>
            {authMode === 'register' && (
              <label>
                姓名
                <input value={authForm.name} onChange={(event) => setAuthForm({ ...authForm, name: event.target.value })} />
              </label>
            )}
            <label>
              邮箱
              <input value={authForm.email} onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })} />
            </label>
            <label>
              密码
              <input type="password" value={authForm.password} onChange={(event) => setAuthForm({ ...authForm, password: event.target.value })} />
            </label>
            <button className="primary" type="submit">{authMode === 'login' ? '进入工作台' : '创建账号'}</button>
            {message && <p className="form-message">{message}</p>}
          </form>
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span>TF</span>
          <div>
            <strong>Team Flow</strong>
            <small>{user.name}</small>
          </div>
        </div>
        <form onSubmit={createProject} className="compact-form">
          <input placeholder="新项目名称" value={projectForm.name} onChange={(event) => setProjectForm({ ...projectForm, name: event.target.value })} />
          <textarea placeholder="项目描述" value={projectForm.description} onChange={(event) => setProjectForm({ ...projectForm, description: event.target.value })} />
          <button type="submit">新建项目</button>
        </form>
        <nav className="project-list">
          {projects.map((item) => (
            <button key={item.id} className={item.id === activeProjectId ? 'active' : ''} onClick={() => setActiveProjectId(item.id)}>
              <span>{item.name}</span>
              <small>{item.taskSummary.done}/{item.taskSummary.total} 完成</small>
            </button>
          ))}
        </nav>
        <button className="ghost" onClick={logout}>退出登录</button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{activeProject?.status ?? 'ACTIVE'}</p>
            <h1>{activeProject?.name ?? '选择或创建项目'}</h1>
            <p className="muted">{activeProject?.description ?? '左侧创建项目后即可开始协作。'}</p>
          </div>
          {stats && (
            <div className="metrics">
              <Metric label="完成率" value={`${stats.completionRate}%`} />
              <Metric label="任务" value={stats.totalTasks} />
              <Metric label="逾期" value={stats.overdueTasks} />
              <Metric label="成员" value={project?.memberCount ?? members.length} />
            </div>
          )}
        </header>

        {activeProjectId && (
          <div className="content-grid">
            <section className="main-column">
              <form onSubmit={createTask} className="task-composer">
                <input placeholder="任务标题" value={taskForm.title} onChange={(event) => setTaskForm({ ...taskForm, title: event.target.value })} />
                <textarea placeholder="任务描述" value={taskForm.description} onChange={(event) => setTaskForm({ ...taskForm, description: event.target.value })} />
                <div className="form-row">
                  <select value={taskForm.priority} onChange={(event) => setTaskForm({ ...taskForm, priority: event.target.value })}>
                    <option value="LOW">低优先级</option>
                    <option value="MEDIUM">中优先级</option>
                    <option value="HIGH">高优先级</option>
                  </select>
                  <select value={taskForm.assigneeId} onChange={(event) => setTaskForm({ ...taskForm, assigneeId: event.target.value })}>
                    <option value="">未分配</option>
                    {members.map((member) => (
                      <option key={member.user.id} value={member.user.id}>{member.user.name}</option>
                    ))}
                  </select>
                  <input type="date" value={taskForm.dueDate} onChange={(event) => setTaskForm({ ...taskForm, dueDate: event.target.value })} />
                  <button type="submit">创建任务</button>
                </div>
              </form>

              <div className="board">
                {statusColumns.map((column) => (
                  <section
                    key={column.status}
                    className="column"
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={() => dropTask(column.status)}
                  >
                    <div className="column-head">
                      <h2>{column.title}</h2>
                      <span>{tasks.filter((task) => task.status === column.status).length}</span>
                    </div>
                    {tasks.filter((task) => task.status === column.status).map((task) => (
                      <article
                        key={task.id}
                        className="task-card"
                        draggable
                        onDragStart={() => setDraggingTaskId(task.id)}
                        onClick={() => openTask(task.id)}
                      >
                        <div className="task-title">
                          <strong>{task.title}</strong>
                          <span className={`priority ${task.priority.toLowerCase()}`}>{priorityLabel[task.priority] ?? task.priority}</span>
                        </div>
                        <p>{task.description || '暂无描述'}</p>
                        <div className="task-meta">
                          <span>{task.assignee?.name ?? '未分配'}</span>
                          <span>{task.dueDate ?? '无截止日'}</span>
                          <span>{task.commentCount} 评论</span>
                        </div>
                        <div className="task-actions" onClick={(event) => event.stopPropagation()}>
                          {statusColumns.map((target) => (
                            <button key={target.status} disabled={target.status === task.status} onClick={() => moveTask(task, target.status)}>
                              {target.title}
                            </button>
                          ))}
                        </div>
                      </article>
                    ))}
                  </section>
                ))}
              </div>
            </section>

            <aside className="side-panel">
              <section>
                <h2>成员</h2>
                <form onSubmit={inviteMember} className="inline-form">
                  <input value={inviteEmail} onChange={(event) => setInviteEmail(event.target.value)} />
                  <button type="submit" disabled={project?.currentUserRole !== 'OWNER'}>添加</button>
                </form>
                <div className="member-list">
                  {members.map((member) => (
                    <div key={member.id}>
                      <span>{member.user.name}</span>
                      <small>{member.role}</small>
                    </div>
                  ))}
                </div>
              </section>
              {stats && (
                <section>
                  <h2>成员任务</h2>
                  <div className="assignee-list">
                    {stats.byAssignee.map((item) => (
                      <div key={`${item.userId}-${item.userName}`}>
                        <span>{item.userName}</span>
                        <progress max={item.total} value={item.done}></progress>
                        <small>{item.done}/{item.total}</small>
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </aside>
          </div>
        )}
      </section>

      {selectedTask && (
        <aside className="drawer">
          <button className="close" onClick={() => setSelectedTask(null)}>×</button>
          <p className="eyebrow">{selectedTask.status}</p>
          <h2>{selectedTask.title}</h2>
          <p className="muted">{selectedTask.description || '暂无描述'}</p>
          <div className="detail-grid">
            <span>负责人</span><strong>{selectedTask.assignee?.name ?? '未分配'}</strong>
            <span>创建人</span><strong>{selectedTask.creator.name}</strong>
            <span>截止日</span><strong>{selectedTask.dueDate ?? '无'}</strong>
            <span>优先级</span><strong>{priorityLabel[selectedTask.priority] ?? selectedTask.priority}</strong>
          </div>
          {selectedTask.canDelete && <button className="danger" onClick={() => deleteTask(selectedTask.id)}>删除任务</button>}
          <section className="comments">
            <h3>评论</h3>
            {comments.map((comment) => (
              <article key={comment.id}>
                <strong>{comment.author.name}</strong>
                <p>{comment.content}</p>
              </article>
            ))}
            <form onSubmit={addComment}>
              <textarea value={commentText} onChange={(event) => setCommentText(event.target.value)} placeholder="写下评论" />
              <button type="submit">发送评论</button>
            </form>
          </section>
        </aside>
      )}
    </main>
  )
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  )
}

export default App
