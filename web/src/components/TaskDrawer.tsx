import { useState, useEffect, type FormEvent } from 'react'
import { api } from '../api/client'
import type { Task, Comment, Member } from '../api/client'

const priorityLabel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }

export default function TaskDrawer({
  taskId,
  members,
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
  const [commentText, setCommentText] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [editForm, setEditForm] = useState({
    title: '',
    description: '',
    priority: 'MEDIUM',
    assigneeId: '',
    dueDate: '',
  })

  useEffect(() => {
    loadTask()
  }, [taskId])

  async function loadTask() {
    const [detail, commentPage] = await Promise.all([
      api<Task>(`/tasks/${taskId}`),
      api<{ items: Comment[] }>(`/tasks/${taskId}/comments`),
    ])
    setTask(detail)
    setComments(commentPage.items)
    setEditForm({
      title: detail.title,
      description: detail.description ?? '',
      priority: detail.priority,
      assigneeId: detail.assignee?.id?.toString() ?? '',
      dueDate: detail.dueDate ?? '',
    })
  }

  async function addComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!commentText.trim()) return
    await api(`/tasks/${taskId}/comments`, { method: 'POST', body: JSON.stringify({ content: commentText }) })
    setCommentText('')
    await loadTask()
    onRefresh()
  }

  async function deleteComment(commentId: number) {
    if (!confirm('确定删除这条评论？')) return
    await api(`/comments/${commentId}`, { method: 'DELETE' })
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

  if (!task) return null

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
          <p className="eyebrow">{task.status}</p>
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

      <section className="comments">
        <h3>评论</h3>
        {comments.map((comment) => (
          <article key={comment.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <strong>{comment.author.name}</strong>
              {comment.canDelete && (
                <button
                  style={{ fontSize: 12, background: 'transparent', border: 0, color: '#e03131', cursor: 'pointer' }}
                  onClick={() => deleteComment(comment.id)}
                >
                  删除
                </button>
              )}
            </div>
            <p>{comment.content}</p>
          </article>
        ))}
        <form onSubmit={addComment}>
          <textarea value={commentText} onChange={(e) => setCommentText(e.target.value)} placeholder="写下评论" />
          <button type="submit">发送评论</button>
        </form>
      </section>
    </aside>
  )
}
