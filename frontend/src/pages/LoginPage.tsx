import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Card,
  CardContent,
  Link,
  Stack,
  TextField,
  Typography,
  Alert,
  InputAdornment,
} from '@mui/material'
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { useAuth } from '../context/AuthContext'
import { tripooColors } from '../theme'

export default function LoginPage() {
  const { signIn, resetPassword } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [resetMsg, setResetMsg] = useState<string | null>(null)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErr(null)
    setResetMsg(null)
    if (!email.trim() || !password) {
      setErr('Please fill in all fields')
      return
    }
    setLoading(true)
    try {
      await signIn(email, password)
      navigate('/dashboard', { replace: true })
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Sign in failed')
    } finally {
      setLoading(false)
    }
  }

  async function onForgot() {
    setErr(null)
    setResetMsg(null)
    if (!email.trim()) {
      setErr('Enter your email first')
      return
    }
    try {
      await resetPassword(email)
      setResetMsg('Password reset email sent.')
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Could not send reset email')
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        px: 2,
        py: 3,
        bgcolor: tripooColors.bg,
      }}
    >
      <Card
        elevation={4}
        sx={{
          maxWidth: 440,
          width: 1,
          borderRadius: 2,
          boxShadow: '0 4px 24px rgba(24,20,17,0.08)',
        }}
      >
        <CardContent sx={{ p: 3 }}>
          <Typography
            sx={{
              textAlign: 'center',
              fontSize: 24,
              fontWeight: 700,
              color: tripooColors.textPrimary,
              mb: 4,
            }}
          >
            Login
          </Typography>
          {err && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErr(null)}>
              {err}
            </Alert>
          )}
          {resetMsg && (
            <Alert severity="success" sx={{ mb: 2 }}>
              {resetMsg}
            </Alert>
          )}
          <form onSubmit={onSubmit}>
            <Stack spacing={2}>
              <TextField
                label="Email"
                type="email"
                autoComplete="email"
                fullWidth
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <EmailOutlinedIcon sx={{ color: tripooColors.textSecondary, fontSize: 22 }} />
                    </InputAdornment>
                  ),
                }}
              />
              <TextField
                label="Password"
                type="password"
                autoComplete="current-password"
                fullWidth
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <LockOutlinedIcon sx={{ color: tripooColors.textSecondary, fontSize: 22 }} />
                    </InputAdornment>
                  ),
                }}
              />
              <Button
                type="submit"
                variant="contained"
                size="large"
                disabled={loading}
                sx={{ py: 1.25, fontWeight: 700 }}
              >
                Login
              </Button>
              <Button type="button" variant="text" onClick={onForgot} sx={{ textTransform: 'none' }}>
                Forgot password?
              </Button>
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
                No account?{' '}
                <Link component={RouterLink} to="/signup" fontWeight={700} underline="hover">
                  Sign up
                </Link>
              </Typography>
            </Stack>
          </form>
        </CardContent>
      </Card>
    </Box>
  )
}
