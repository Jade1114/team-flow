import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { api, getToken, clearToken } from './api/client'
import type { User } from './api/client'
import AuthPage from './pages/AuthPage'
import Dashboard from './pages/Dashboard'
import MyTasks from './pages/MyTasks'
import './App.css'

const THEME_KEY = 'team-flow-theme'

function getStoredTheme(): 'light' | 'dark' {
  const stored = localStorage.getItem(THEME_KEY)
  if (stored === 'dark' || stored === 'light') return stored
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function App() {
  const [token, setTokenState] = useState(getToken)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [theme, setTheme] = useState<'light' | 'dark'>(getStoredTheme)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem(THEME_KEY, theme)
  }, [theme])

  useEffect(() => {
    if (token) {
      api<User>('/auth/me')
        .then(setUser)
        .catch(() => {
          clearToken()
          setTokenState('')
        })
        .finally(() => setLoading(false))
    } else {
      setLoading(false)
    }
  }, [token])

  function handleLogin(user: User) {
    setUser(user)
    setTokenState(getToken())
  }

  function handleLogout() {
    clearToken()
    setTokenState('')
    setUser(null)
  }

  function toggleTheme() {
    setTheme((t) => (t === 'light' ? 'dark' : 'light'))
  }

  if (loading) return <div style={{ display: 'grid', placeItems: 'center', height: '100vh' }}>加载中...</div>

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/"
          element={
            token && user ? (
              <Dashboard user={user} onLogout={handleLogout} theme={theme} onToggleTheme={toggleTheme} />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/my-tasks"
          element={
            token && user ? (
              <MyTasks user={user} onLogout={handleLogout} theme={theme} onToggleTheme={toggleTheme} />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/login"
          element={
            token && user ? (
              <Navigate to="/" replace />
            ) : (
              <AuthPage onLogin={handleLogin} />
            )
          }
        />
      </Routes>
    </BrowserRouter>
  )
}

export default App
