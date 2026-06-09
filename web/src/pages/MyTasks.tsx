import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { Task, Project, User } from '../api/client'
import TaskDrawer from '../components/TaskDrawer'
import NotificationBell from '../components/NotificationBell'

const statusMap: Record<string, string> = { TODO: '待处理', IN_PROGRESS: '进行中', DONE: '已完成' }
const priorityLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }
const metricIcons = ['📋', '📌', '🔄', '✅', '⚠️']

function isOverdue(dueDate?: string | null): boolean {
  if (!dueDate) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return new Date(dueDate + 'T00:00:00') < today
}

export default function MyTasks({ user, onLogout, theme, onToggleTheme }: { user: User; onLogout: () => void; theme: 'light' | 'dark'; onToggleTheme: () => void }) {
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<Task[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [filterProjectId, setFilterProjectId] = useState<string>('')
  const [filterStatus, setFilterStatus] = useState<string>('')
  const [keyword, setKeyword] = useState('')
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadProjects()
    loadTasks()
  }, [filterProjectId, filterStatus])

  async function loadProjects() {
    try {
      const data = await api<{ items: Project[] }>('/projects')
      setProjects(data.items)
    } catch (e) {
      // project list failure is non-critical
    }
  }

  async function loadTasks() {
    setLoading(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (filterProjectId) params.set('projectId', filterProjectId)
      if (filterStatus) params.set('status', filterStatus)
      const data = await api<{ items: Task[] }>(`/my/tasks?${params.toString()}`)
      setTasks(data.items)
    } catch (e: any) {
      setError(e?.message || '加载任务失败')
      setTasks([])
    } finally {
      setLoading(false)
    }
  }

  const filteredTasks = useMemo(() => {
    if (!keyword.trim()) return tasks
    const kw = keyword.toLowerCase()
    return tasks.filter((t) => t.title.toLowerCase().includes(kw) || (t.description ?? '').toLowerCase().includes(kw))
  }, [tasks, keyword])

  const stats = useMemo(() => {
    const total = filteredTasks.length
    const todo = filteredTasks.filter((t) => t.status === 'TODO').length
    const inProgress = filteredTasks.filter((t) => t.status === 'IN_PROGRESS').length
    const done = filteredTasks.filter((t) => t.status === 'DONE').length
    const overdue = filteredTasks.filter((t) => isOverdue(t.dueDate) && t.status !== 'DONE').length
    return { total, todo, inProgress, done, overdue }
  }, [filteredTasks])

  const metricData = [
    { label: '全部', value: stats.total, icon: metricIcons[0] },
    { label: '待处理', value: stats.todo, icon: metricIcons[1] },
    { label: '进行中', value: stats.inProgress, icon: metricIcons[2] },
    { label: '已完成', value: stats.done, icon: metricIcons[3] },
    { label: '逾期', value: stats.overdue, icon: metricIcons[4] },
  ]

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
        <nav className="nav-menu">
          <button onClick={() => navigate('/')}>项目看板</button>
          <button className="active">我的任务</button>
        </nav>
        <NotificationBell onOpenTask={(taskId) => { setSelectedTaskId(taskId) }} />
        <button className="theme-toggle" onClick={onToggleTheme}>{theme === 'dark' ? '☀️ 浅色模式' : '🌙 深色模式'}</button>
        <button className="ghost" onClick={onLogout}>退出登录</button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Team Flow</p>
            <h1>我的任务</h1>
            <p className="muted">查看所有项目中分配给你的任务。</p>
          </div>
          <div className="metrics">
            {metricData.map((m) => (
              <div key={m.label}>
                <strong>{m.value}</strong>
                <span>{m.label}</span>
              </div>
            ))}
          </div>
        </header>

        <div className="board-toolbar" style={{ marginBottom: 16 }}>
          <select value={filterProjectId} onChange={(e) => setFilterProjectId(e.target.value)}>
            <option value="">所有项目</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
            <option value="">所有状态</option>
            <option value="TODO">待处理</option>
            <option value="IN_PROGRESS">进行中</option>
            <option value="DONE">已完成</option>
          </select>
          <input
            className="search-input"
            placeholder="搜索任务标题或描述..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ maxWidth: 280 }}
          />
          {keyword && (
            <small style={{ color: 'var(--text-muted)' }}>找到 {filteredTasks.length} 个任务</small>
          )}
        </div>

        {error && (
          <div className="task-table-wrapper" style={{ padding: 24, color: 'var(--danger)' }}>
            <strong>加载失败：</strong>{error}
          </div>
        )}
        {loading && (
          <div className="task-table-wrapper" style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
            <div style={{ animation: 'pulse 1.5s ease infinite' }}>加载中...</div>
          </div>
        )}
        {!loading && !error && (
          <div className="task-table-wrapper" style={{ overflowX: 'auto' }}>
            <table className="task-table">
              <thead>
                <tr>
                  <th>任务</th>
                  <th>项目</th>
                  <th>状态</th>
                  <th>优先级</th>
                  <th>截止日</th>
                </tr>
              </thead>
              <tbody>
                {filteredTasks.length === 0 && (
                  <tr>
                    <td colSpan={5} className="empty-cell">
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: 32 }}>📭</span>
                        <span>暂无任务</span>
                      </div>
                    </td>
                  </tr>
                )}
                {filteredTasks.map((task) => (
                  <tr key={task.id} onClick={() => setSelectedTaskId(task.id)} className="clickable-row">
                    <td>
                      <strong style={{ fontSize: 14 }}>{task.title}</strong>
                      <p className="muted" style={{ margin: '4px 0 0', fontSize: 13, lineHeight: 1.5 }}>{task.description || '暂无描述'}</p>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>{task.projectName ?? '-'}</td>
                    <td>
                      <span className={`badge ${task.status.toLowerCase()}`}>{statusMap[task.status] ?? task.status}</span>
                    </td>
                    <td>
                      <span className={`priority ${task.priority.toLowerCase()}`}>{priorityLabel[task.priority] ?? task.priority}</span>
                    </td>
                    <td className={isOverdue(task.dueDate) && task.status !== 'DONE' ? 'overdue-text' : ''} style={{ whiteSpace: 'nowrap' }}>
                      {task.dueDate ?? '-'}
                      {isOverdue(task.dueDate) && task.status !== 'DONE' ? ' (逾期)' : ''}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {selectedTaskId && (
          <TaskDrawer
            taskId={selectedTaskId}
            members={[]}
            onClose={() => setSelectedTaskId(null)}
            onRefresh={() => loadTasks()}
          />
        )}
      </section>
    </main>
  )
}
