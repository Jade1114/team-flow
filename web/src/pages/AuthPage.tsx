import { useState, type FormEvent } from 'react'
import { api, setToken } from '../api/client'
import type { User } from '../api/client'

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
              <input value={authForm.name} onChange={(e) => setAuthForm({ ...authForm, name: e.target.value })} />
            </label>
          )}
          <label>
            邮箱
            <input value={authForm.email} onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })} />
          </label>
          <label>
            密码
            <input type="password" value={authForm.password} onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })} />
          </label>
          <button className="primary" type="submit">{authMode === 'login' ? '进入工作台' : '创建账号'}</button>
          {message && <p className="form-message">{message}</p>}
        </form>
      </section>
    </main>
  )
}
