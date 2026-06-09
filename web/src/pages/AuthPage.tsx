import { useState, type FormEvent } from 'react'
import { api, setToken } from '../api/client'
import type { User } from '../api/client'

const features = [
  { icon: '📋', text: '看板式任务管理，拖拽切换状态' },
  { icon: '👥', text: '邀请成员协作，实时追踪进度' },
  { icon: '🔔', text: '@提及通知，评论互动不遗漏' },
  { icon: '📊', text: '项目统计与成员任务分布' },
]

export default function AuthPage({ onLogin }: { onLogin: (user: User) => void }) {
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [authForm, setAuthForm] = useState({ name: '', email: '', password: '' })
  const [message, setMessage] = useState('')

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const path = authMode === 'login' ? '/auth/login' : '/auth/register'
    try {
      const data = await api<{ token: string; user: User }>(path, { method: 'POST', body: JSON.stringify(authForm) })
      setToken(data.token)
      onLogin(data.user)
    } catch (error) {
      setMessage((error as Error).message)
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <div>
          <p className="eyebrow">Team Flow</p>
          <h1>小团队项目协作工作台</h1>
          <p className="muted">用项目、成员、任务看板、评论和进度统计跑通一个清爽的 MVP。</p>
          <div style={{ marginTop: 32, display: 'grid', gap: 14 }}>
            {features.map((f, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 14, color: 'var(--text-muted)' }}>
                <span style={{ width: 32, height: 32, borderRadius: 10, background: 'var(--accent-light)', display: 'grid', placeItems: 'center', fontSize: 16, flexShrink: 0 }}>
                  {f.icon}
                </span>
                {f.text}
              </div>
            ))}
          </div>
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
              <input value={authForm.name} onChange={(e) => setAuthForm({ ...authForm, name: e.target.value })} placeholder="请输入姓名" />
            </label>
          )}
          <label>
            邮箱
            <input type="email" value={authForm.email} onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })} placeholder="name@example.com" />
          </label>
          <label>
            密码
            <input type="password" value={authForm.password} onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })} placeholder="••••••••" />
          </label>
          <button className="primary" type="submit" style={{ marginTop: 4 }}>{authMode === 'login' ? '进入工作台' : '创建账号'}</button>
          {message && <p className="form-message">{message}</p>}
        </form>
      </section>
    </main>
  )
}
