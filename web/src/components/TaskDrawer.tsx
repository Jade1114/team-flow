import { useState, useEffect, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Task, Comment, Member, Activity, Subtask } from '../api/client'

const priorityLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }
const statusMap: Record<string, string> = { TODO: '待处理', IN_PROGRESS: '进行中', DONE: '已完成' }

function activityText(a: Activity): string {
  switch (a.type) {
    case 'TASK_CREATED': return `创建了此任务`
    case 'TASK_UPDATED': return `更新了 ${a.newValue}`
    case 'TASK_STATUS_CHANGED': return `状态从 ${statusMap[a.oldValue || ''] || a.oldValue} 移动到 ${statusMap[a.newValue || ''] || a.newValue}`
    case 'TASK_ASSIGNED': return `负责人从 ${a.oldValue} 改为 ${a.newValue}`
    case 'TASK_DELETED': return `删除了此任务`
    case 'COMMENT_ADDED': return `评论：${a.newValue}`
    case 'COMMENT_DELETED': return `删除了评论：${a.oldValue}`
    default: return a.type
  }
}

function formatTime(ts: string): string {
  const date = new Date(ts)
  const now = new Date()
  const diff = Math.floor((now.getTime() - date.getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`
  return date.toLocaleDateString('zh-CN')
}

function renderCommentContent(content: string) {
  const parts = content.split(/(@[^\s@]+)/g)
  return parts.map((part, i) => {
    if (part.startsWith('@')) {
      return <span key={i} className="mention-highlight">{part}</span>
    }
    return <span key={i}>{part}</span>
  })
}

export default function TaskDrawer({
  taskId,
  members: propMembers,
  onClose,
  onRefresh,
}: {
  taskId: number
  members: Member[]
  onClose: () => void
  onRefresh: () => void
}) {
  const [task, setTask] = useState<Task | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [activities, setActivities] = useState<Activity[]>([])
  const [subtasks, setSubtasks] = useState<Subtask[]>([])
  const [commentText, setCommentText] = useState('')
  const [subtaskText, setSubtaskText] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [editForm, setEditForm] = useState({
    title: '',
    description: '',
    priority: 'MEDIUM',
    assigneeId: '',
    dueDate: '',
  })

  // @mention states
  const [showMentions, setShowMentions] = useState(false)
  const [mentionQuery, setMentionQuery] = useState('')
  const [mentionIndex, setMentionIndex] = useState(0)
  const [mentionUserIds, setMentionUserIds] = useState<number[]>([])
  const [localMembers, setLocalMembers] = useState<Member[]>([])

  const members = propMembers.length > 0 ? propMembers : localMembers

  useEffect(() => {
    loadTask()
  }, [taskId])

  async function loadTask() {
    const [detail, commentPage, activityPage, subtaskPage] = await Promise.all([
      api<Task>(`/tasks/${taskId}`),
      api<{ items: Comment[] }>(`/tasks/${taskId}/comments`),
      api<{ items: Activity[] }>(`/tasks/${taskId}/activities`),
      api<{ items: Subtask[] }>(`/tasks/${taskId}/subtasks`),
    ])
    setTask(detail)
    setComments(commentPage.items)
    setActivities(activityPage.items)
    setSubtasks(subtaskPage.items)
    setEditForm({
      title: detail.title,
      description: detail.description ?? '',
      priority: detail.priority,
      assigneeId: detail.assignee?.id?.toString() ?? '',
      dueDate: detail.dueDate ?? '',
    })
    if (propMembers.length === 0) {
      try {
        const data = await api<{ items: Member[] }>(`/projects/${detail.projectId}/members`)
        setLocalMembers(data.items)
      } catch {
        // ignore
      }
    }
  }

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!commentText.trim()) return
    await api(`/tasks/${taskId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: commentText, mentions: mentionUserIds }),
    })
    setCommentText('')
    setMentionUserIds([])
    setShowMentions(false)
    await loadTask()
    onRefresh()
  }

  async function deleteComment(commentId: number) {
    if (!confirm('确定删除这条评论？')) return
    await api(`/comments/${commentId}`, { method: 'DELETE' })
    await loadTask()
    onRefresh()
  }

  async function addSubtask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!subtaskText.trim()) return
    await api(`/tasks/${taskId}/subtasks`, { method: 'POST', body: JSON.stringify({ title: subtaskText }) })
    setSubtaskText('')
    await loadTask()
    onRefresh()
  }

  async function toggleSubtask(subtask: Subtask) {
    await api(`/subtasks/${subtask.id}`, { method: 'PATCH', body: JSON.stringify({ completed: !subtask.completed }) })
    await loadTask()
    onRefresh()
  }

  async function deleteSubtask(subtaskId: number) {
    await api(`/subtasks/${subtaskId}`, { method: 'DELETE' })
    await loadTask()
    onRefresh()
  }

  async function saveEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await api(`/tasks/${taskId}`, {
      method: 'PUT',
      body: JSON.stringify({
        title: editForm.title,
        description: editForm.description || null,
        priority: editForm.priority,
        assigneeId: editForm.assigneeId ? Number(editForm.assigneeId) : null,
        dueDate: editForm.dueDate || null,
      }),
    })
    setIsEditing(false)
    await loadTask()
    onRefresh()
  }

  async function handleDeleteTask() {
    if (!confirm('确定删除这个任务？')) return
    await api(`/tasks/${taskId}`, { method: 'DELETE' })
    onClose()
    onRefresh()
  }

  // @mention handlers
  function handleCommentKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (!showMentions) return
    const filtered = getFilteredMembers()
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setMentionIndex((i) => (i + 1) % filtered.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setMentionIndex((i) => (i - 1 + filtered.length) % filtered.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (filtered.length > 0) {
        selectMention(filtered[mentionIndex])
      }
    } else if (e.key === 'Escape') {
      setShowMentions(false)
    }
  }

  function handleCommentChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const value = e.target.value
    const cursor = e.target.selectionStart
    setCommentText(value)

    // Check if we are typing an @mention
    const beforeCursor = value.slice(0, cursor)
    const lastAtIndex = beforeCursor.lastIndexOf('@')

    if (lastAtIndex >= 0) {
      const afterAt = beforeCursor.slice(lastAtIndex + 1)
      // If there is no space/newline between @ and cursor, we are in mention mode
      if (!afterAt.includes(' ') && !afterAt.includes('\n') && !afterAt.includes('@')) {
        setMentionQuery(afterAt)
        setShowMentions(true)
        setMentionIndex(0)
        return
      }
    }

    setShowMentions(false)
  }

  function getFilteredMembers() {
    if (!mentionQuery) return members
    const q = mentionQuery.toLowerCase()
    return members.filter((m) => m.user.name.toLowerCase().includes(q))
  }

  function selectMention(member: Member) {
    const textarea = document.getElementById('comment-textarea') as HTMLTextAreaElement
    const cursor = textarea?.selectionStart ?? commentText.length
    const beforeCursor = commentText.slice(0, cursor)
    const lastAtIndex = beforeCursor.lastIndexOf('@')
    if (lastAtIndex < 0) return

    const beforeAt = commentText.slice(0, lastAtIndex)
    const afterCursor = commentText.slice(cursor)
    const newText = beforeAt + `@${member.user.name} ` + afterCursor
    setCommentText(newText)
    if (!mentionUserIds.includes(member.user.id)) {
      setMentionUserIds((prev) => [...prev, member.user.id])
    }
    setShowMentions(false)
    setTimeout(() => {
      textarea?.focus()
      const pos = beforeAt.length + member.user.name.length + 2 // +2 for '@' and ' '
      textarea?.setSelectionRange(pos, pos)
    }, 0)
  }

  const subtaskProgress = task && task.subtaskCount && task.subtaskCount > 0
    ? `${task.completedSubtaskCount}/${task.subtaskCount}`
    : null

  if (!task) return null

  const filteredMembers = getFilteredMembers()

  return (
    <aside className="drawer">
      <button className="close" onClick={onClose}>×</button>

      {isEditing ? (
        <form onSubmit={saveEdit}>
          <label>标题</label>
          <input value={editForm.title} onChange={(e) => setEditForm({ ...editForm, title: e.target.value })} />
          <label>描述</label>
          <textarea value={editForm.description} onChange={(e) => setEditForm({ ...editForm, description: e.target.value })} />
          <label>优先级</label>
          <select value={editForm.priority} onChange={(e) => setEditForm({ ...editForm, priority: e.target.value })}>
            <option value="LOW">低优先级</option>
            <option value="MEDIUM">中优先级</option>
            <option value="HIGH">高优先级</option>
          </select>
          <label>负责人</label>
          <select value={editForm.assigneeId} onChange={(e) => setEditForm({ ...editForm, assigneeId: e.target.value })}>
            <option value="">未分配</option>
            {members.map((member) => (
              <option key={member.user.id} value={member.user.id}>{member.user.name}</option>
            ))}
          </select>
          <label>截止日期</label>
          <input type="date" value={editForm.dueDate} onChange={(e) => setEditForm({ ...editForm, dueDate: e.target.value })} />
          <div style={{ display: 'flex', gap: 10, marginTop: 12 }}>
            <button type="submit" className="primary">保存</button>
            <button type="button" onClick={() => setIsEditing(false)}>取消</button>
          </div>
        </form>
      ) : (
        <>
          <p className="eyebrow">{task.status}{subtaskProgress ? ` · 子任务 ${subtaskProgress}` : ''}</p>
          <h2>{task.title}</h2>
          <p className="muted">{task.description || '暂无描述'}</p>
          <div className="detail-grid">
            <span>负责人</span><strong>{task.assignee?.name ?? '未分配'}</strong>
            <span>创建人</span><strong>{task.creator.name}</strong>
            <span>截止日</span><strong>{task.dueDate ?? '无'}</strong>
            <span>优先级</span><strong>{priorityLabel[task.priority] ?? task.priority}</strong>
          </div>
          {task.canEdit && (
            <button className="primary" onClick={() => setIsEditing(true)} style={{ marginRight: 10 }}>
              编辑任务
            </button>
          )}
          {task.canDelete && <button className="danger" onClick={handleDeleteTask}>删除任务</button>}
        </>
      )}

      <section className="subtasks">
        <h3>子任务 {subtaskProgress ? `(${subtaskProgress})` : ''}</h3>
        {subtasks.length === 0 && <p className="muted" style={{ fontSize: 14 }}>暂无子任务</p>}
        {subtasks.map((subtask) => (
          <div key={subtask.id} className="subtask-row">
            <input
              type="checkbox"
              checked={subtask.completed}
              onChange={() => toggleSubtask(subtask)}
            />
            <span style={{ flex: 1, textDecoration: subtask.completed ? 'line-through' : 'none', color: subtask.completed ? 'var(--text-muted)' : 'var(--text-main)' }}>
              {subtask.title}
            </span>
            <button
              style={{ fontSize: 12, background: 'transparent', border: 0, color: 'var(--danger)', cursor: 'pointer' }}
              onClick={() => deleteSubtask(subtask.id)}
            >
              删除
            </button>
          </div>
        ))}
        <form onSubmit={addSubtask} style={{ display: 'flex', gap: 8, marginTop: 10 }}>
          <input
            style={{ flex: 1 }}
            placeholder="添加子任务..."
            value={subtaskText}
            onChange={(e) => setSubtaskText(e.target.value)}
          />
          <button type="submit">添加</button>
        </form>
      </section>

      <section className="activities">
        <h3>任务动态</h3>
        {activities.length === 0 && <p className="muted" style={{ fontSize: 14 }}>暂无动态</p>}
        {activities.map((a) => (
          <div key={a.id} className="activity-row">
            <strong>{a.user.name}</strong>
            <span>{activityText(a)}</span>
            <small>{formatTime(a.createdAt)}</small>
          </div>
        ))}
      </section>

      <section className="comments">
        <h3>评论</h3>
        {comments.map((comment) => (
          <article key={comment.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ width: 28, height: 28, borderRadius: 999, background: 'var(--accent)', color: '#fff', display: 'grid', placeItems: 'center', fontSize: 12, fontWeight: 700, flexShrink: 0 }}>
                  {comment.author.name.charAt(0)}
                </div>
                <div>
                  <strong style={{ fontSize: 13 }}>{comment.author.name}</strong>
                  <div style={{ fontSize: 11, color: 'var(--text-activity-time)', marginTop: 1 }}>{formatTime(comment.createdAt)}</div>
                </div>
              </div>
              {comment.canDelete && (
                <button
                  style={{ fontSize: 12, background: 'transparent', border: 0, color: 'var(--danger)', cursor: 'pointer', fontWeight: 500 }}
                  onClick={() => deleteComment(comment.id)}
                >
                  删除
                </button>
              )}
            </div>
            <p style={{ marginLeft: 38 }}>{renderCommentContent(comment.content)}</p>
          </article>
        ))}
        <form onSubmit={addComment} style={{ position: 'relative' }}>
          {showMentions && filteredMembers.length > 0 && (
            <div className="mention-dropdown">
              {filteredMembers.map((member, idx) => (
                <div
                  key={member.user.id}
                  className={`mention-option ${idx === mentionIndex ? 'active' : ''}`}
                  onClick={() => selectMention(member)}
                >
                  {member.user.name}
                </div>
              ))}
            </div>
          )}
          <textarea
            id="comment-textarea"
            value={commentText}
            onChange={handleCommentChange}
            onKeyDown={handleCommentKeyDown}
            placeholder="写下评论，输入 @ 提及成员"
          />
          <button type="submit">发送评论</button>
        </form>
      </section>
    </aside>
  )
}
