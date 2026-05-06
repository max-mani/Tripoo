import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Box, CircularProgress } from '@mui/material'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ProtectedLayout } from './components/ProtectedLayout'
import { SplashOverlay } from './components/SplashOverlay'
import LoginPage from './pages/LoginPage'
import SignUpPage from './pages/SignUpPage'
import DashboardPage from './pages/DashboardPage'
import ProfilePage from './pages/ProfilePage'
import CreateTripPage from './pages/CreateTripPage'
import JoinTripPage from './pages/JoinTripPage'
import TripLayout from './pages/TripLayout'
import TripHomePage from './pages/TripHomePage'
import ExpensesPage from './pages/ExpensesPage'
import TasksPage from './pages/TasksPage'
import GroupsPage from './pages/GroupsPage'
import { tripooColors } from './theme'

function LoginGate({ children }: { children: ReactNode }) {
  const { firebaseUser, loading } = useAuth()
  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <CircularProgress sx={{ color: tripooColors.orange }} />
      </Box>
    )
  }
  if (firebaseUser) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}

function RootRedirect() {
  const { firebaseUser, loading } = useAuth()
  if (loading) {
    return (
      <Box sx={{ minHeight: '100dvh', bgcolor: tripooColors.bg }} />
    )
  }
  if (firebaseUser) return <Navigate to="/dashboard" replace />
  return <Navigate to="/login" replace />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <SplashOverlay />
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route
            path="/login"
            element={
              <LoginGate>
                <LoginPage />
              </LoginGate>
            }
          />
          <Route
            path="/signup"
            element={
              <LoginGate>
                <SignUpPage />
              </LoginGate>
            }
          />
          <Route element={<ProtectedLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/trips/new" element={<CreateTripPage />} />
            <Route path="/trips/join" element={<JoinTripPage />} />
            <Route path="/trips/:tripId" element={<TripLayout />}>
              <Route index element={<TripHomePage />} />
              <Route path="expenses" element={<ExpensesPage />} />
              <Route path="tasks" element={<TasksPage />} />
              <Route path="groups" element={<GroupsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
