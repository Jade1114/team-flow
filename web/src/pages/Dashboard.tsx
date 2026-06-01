import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api/client'
import type { User, Project, Member, Task, Stats, TaskStatus } from '../api/client'
import TaskDrawer from '../components/TaskDrawer'

const statusColumns: { status: TaskStatus; title: string }[] = [
  { status: 'TODO', title: '待处理' },
  { status: 'IN_PROGRESS', title: '进行中' },
  { status: 'DONE', title: '已完成' },
]
const priorityLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }

export default function Dashboard({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [activeProjectId, setActiveProjectId] = useState<number | null>(null)
  const [project, setProject] = useState<Project | null>(null)
  const [members, setMembers] = useState<Member[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [stats, setStats] = useState<Stats | null>(null)
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [projectForm, setProjectForm] = useState({ name: '', description: '' })
  const [inviteEmail, setInviteEmail] = useState('')
  const [taskForm, setTaskForm] = useState({ title: '', description: '', priority: 'MEDIUM', assigneeId: '', dueDate: '' })
  const [draggingTaskId, setDraggingTaskId] = useState<number | null>(null)
  const [message, setMessage] = useState('')

  const activeProject = useMemo(
    () => projects.find((item) => item.id === activeProjectId) ?? project,
    [activeProjectId, project, projects],
  )

  useEffect(() => {
    loadProjects()
  }, [])

  useEffect(() => {
    if (activeProjectId) {
      loadProject(activeProjectId)
      loadMembers(activeProjectId)
      loadBoard(activeProjectId)
      loadStats(activeProjectId)
    }
  }, [activeProjectId])

  async function loadProjects() {
    const data = await api<{ items: Project[] }>('/projects')
    setProjects(data.items)
    if (data.items.length > 0 && !activeProjectId) {
      setActiveProjectId(data.items[0].id)
    }
  }

  async function loadProject(projectId: number) {
    setProject(await api<Project>(`/projects/${projectId}`))
  }

  async function loadMembers(projectId: number) {
    const data = await api<{ items: Member[] }>(`/projects/${projectId}/members`)
    setMembers(data.items)
  }

  async function loadBoard(projectId: number) {
    const data = await api<{ columns: { status: TaskStatus; tasks: Task[] }[] }>(`/projects/${projectId}/board`)
    setTasks(data.columns.flatMap((column) => column.tasks))
  }

  async function loadStats(projectId: number) {
    setStats(await api<Stats>(`/projects/${projectId}/stats`))
  }

  async function refreshProject(projectId = activeProjectId) {
    if (!projectId) return
    await Promise.all([loadProjects(), loadProject(projectId), loadMembers(projectId), loadBoard(projectId), loadStats(projectId)])
  }

  async function createProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const created = await api<Project>('/projects', { method: 'POST', body: JSON.stringify(projectForm) })
    setProjectForm({ name: '', description: '' })
    setActiveProjectId(created.id)
    await loadProjects()
  }

  async function inviteMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!activeProjectId) return
    await api(`/projects/${activeProjectId}/invites`, { method: 'POST', body: JSON.stringify({ email: inviteEmail, role: 'MEMBER' }) })
    setInviteEmail('')
    setMessage('成员已加入项目')
    await refreshProject()
    setTimeout(() => setMessage(''), 2000)
  }

  async function removeMember(memberId: number) {
    if (!confirm('确定移除该成员？')) return
    if (!activeProjectId) return
    await api(`/projects/${activeProjectId}/members/${memberId}`, { method: 'DELETE' })
    await refreshProject()
  }

  async function createTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!activeProjectId) return
    await api(`/projects/${activeProjectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify({
        title: taskForm.title,
        description: taskForm.description || null,
        priority: taskForm.priority,
        assigneeId: taskForm.assigneeId ? Number(taskForm.assigneeId) : null,
        dueDate: taskForm.dueDate || null,
      }),
    })
    setTaskForm({ title: '', description: '', priority: 'MEDIUM', assigneeId: '', dueDate: '' })
    await refreshProject()
  }

  async function moveTask(task: Task, status: TaskStatus) {
    await api(`/tasks/${task.id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })
    await refreshProject()
  }

  async function dropTask(status: TaskStatus) {
    const task = tasks.find((item) => item.id === draggingTaskId)
    setDraggingTaskId(null)
    if (!task || task.status === status) return
    await moveTask(task, status)
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
          <input placeholder="新项目名称" value={projectForm.name} onChange={(e) => setProjectForm({ ...projectForm, name: e.target.value })} />
          <textarea placeholder="项目描述" value={projectForm.description} onChange={(e) => setProjectForm({ ...projectForm, description: e.target.value })} />
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
        <button className="ghost" onClick={onLogout}>退出登录</button>
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
                <input placeholder="任务标题" value={taskForm.title} onChange={(e) => setTaskForm({ ...taskForm, title: e.target.value })} />
                <textarea placeholder="任务描述" value={taskForm.description} onChange={(e) => setTaskForm({ ...taskForm, description: e.target.value })} />
                <div className="form-row">
                  <select value={taskForm.priority} onChange={(e) => setTaskForm({ ...taskForm, priority: e.target.value })}>
                    <option value="LOW">低优先级</option>
                    <option value="MEDIUM">中优先级</option>
                    <option value="HIGH">高优先级</option>
                  </select>
                  <select value={taskForm.assigneeId} onChange={(e) => setTaskForm({ ...taskForm, assigneeId: e.target.value })}>
                    <option value="">未分配</option>
                    {members.map((member) => (
                      <option key={member.user.id} value={member.user.id}>{member.user.name}</option>
                    ))}
                  </select>
                  <input type="date" value={taskForm.dueDate} onChange={(e) => setTaskForm({ ...taskForm, dueDate: e.target.value })} />
                  <button type="submit">创建任务</button>
                </div>
              </form>

              <div className="board">
                {statusColumns.map((column) => (
                  <section
                    key={column.status}
                    className="column"
                    onDragOver={(e) => e.preventDefault()}
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
                        onClick={() => setSelectedTaskId(task.id)}
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
                        <div className="task-actions" onClick={(e) => e.stopPropagation()}>
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
                  <input placeholder="成员邮箱" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} />
                  <button type="submit" disabled={project?.currentUserRole !== 'OWNER'}>添加</button>
                </form>
                {message && <p style={{ color: '#2f9e44', fontSize: 14, margin: '0 0 8px' }}>{message}</p>}
                <div className="member-list">
                  {members.map((member) => (
                    <div key={member.id}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>{member.user.name}</span>
                        {project?.currentUserRole === 'OWNER' && member.role !== 'OWNER' && (
                          <button
                            style={{ fontSize: 12, background: 'transparent', border: 0, color: '#e03131', cursor: 'pointer' }}
                            onClick={() => removeMember(member.id)}
                          >
                            移除
                          </button>
                        )}
                      </div>
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
                        <progress max={item.total || 1} value={item.done}></progress>
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

      {selectedTaskId && (
        <TaskDrawer
          taskId={selectedTaskId}
          members={members}
          onClose={() => setSelectedTaskId(null)}
          onRefresh={() => refreshProject()}
        />
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
