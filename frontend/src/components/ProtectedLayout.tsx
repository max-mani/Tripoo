import { Navigate, Outlet } from 'react-router-dom'
import { Box, CircularProgress } from '@mui/material'
import { useAuth } from '../context/AuthContext'
import { tripooColors } from '../theme'

export function ProtectedLayout() {
  const { firebaseUser, loading } = useAuth()

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <CircularProgress sx={{ color: tripooColors.orange }} />
      </Box>
    )
  }
  if (!firebaseUser) {
    return <Navigate to="/login" replace />
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <Outlet />
    </Box>
  )
}
