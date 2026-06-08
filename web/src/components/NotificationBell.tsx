import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'
import type { Notification } from '../api/client'

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

export default function NotificationBell({
  onOpenTask,
}: {
  onOpenTask: (taskId: number, projectId: number) => void
}) {
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [open, setOpen] = useState(false)
  const bellRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    loadUnreadCount()
    const interval = setInterval(loadUnreadCount, 30000)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    if (open) loadNotifications()
  }, [open])

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (bellRef.current && !bellRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  async function loadUnreadCount() {
    try {
      const data = await api<{ count: number }>('/notifications/unread-count')
      setUnreadCount(data.count)
    } catch {
      // ignore
    }
  }

  async function loadNotifications() {
    try {
      const data = await api<{ items: Notification[] }>('/notifications?page=1&pageSize=20')
      setNotifications(data.items)
    } catch {
      // ignore
    }
  }

  async function markRead(id: number) {
    try {
      await api(`/notifications/${id}/read`, { method: 'PATCH' })
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n))
      )
      setUnreadCount((c) => Math.max(0, c - 1))
    } catch {
      // ignore
    }
  }

  async function markAllRead() {
    const unread = notifications.filter((n) => !n.readAt)
    await Promise.all(unread.map((n) => api(`/notifications/${n.id}/read`, { method: 'PATCH' })))
    setNotifications((prev) => prev.map((n) => ({ ...n, readAt: new Date().toISOString() })))
    setUnreadCount(0)
  }

  function handleClick(n: Notification) {
    if (!n.readAt) markRead(n.id)
    onOpenTask(n.taskId, n.projectId)
    setOpen(false)
  }

  return (
    <div ref={bellRef} className="notification-bell">
      <button className="bell-btn" onClick={() => setOpen(!open)} title="通知">
        🔔
        {unreadCount > 0 && <span className="bell-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>
      {open && (
        <div className="notification-dropdown">
          <div className="notification-header">
            <strong>通知</strong>
            {unreadCount > 0 && (
              <button className="ghost-link" onClick={markAllRead}>
                全部已读
              </button>
            )}
          </div>
          <div className="notification-list">
            {notifications.length === 0 && (
              <div className="notification-empty">暂无通知</div>
            )}
            {notifications.map((n) => (
              <div
                key={n.id}
                className={`notification-item ${n.readAt ? 'read' : 'unread'}`}
                onClick={() => handleClick(n)}
              >
                <div className="notification-meta">
                  <strong>{n.mentionedBy.name}</strong>
                  <span>在 {n.projectName} 提及了你</span>
                </div>
                <div className="notification-task">「{n.taskTitle}」</div>
                <div className="notification-preview">{n.content}</div>
                <small>{formatTime(n.createdAt)}</small>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
