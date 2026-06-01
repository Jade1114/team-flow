import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { api, getToken, clearToken } from './api/client'
import type { User } from './api/client'
import AuthPage from './pages/AuthPage'
import Dashboard from './pages/Dashboard'
import './App.css'

function App() {
  const [token, setTokenState] = useState(getToken)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

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

  if (loading) return <div style={{ display: 'grid', placeItems: 'center', height: '100vh' }}>加载中...</div>

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/"
          element={
            token && user ? (
              <Dashboard user={user} onLogout={handleLogout} />
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
