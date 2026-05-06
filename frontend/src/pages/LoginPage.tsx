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
} from '@mui/material'
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
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        px: 2,
        bgcolor: 'background.default',
      }}
    >
      <Card sx={{ maxWidth: 420, width: 1, borderRadius: 3 }}>
        <CardContent sx={{ p: 3 }}>
          <Typography variant="h5" gutterBottom sx={{ color: tripooColors.orange, fontWeight: 800 }}>
            Tripoo
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Sign in to your trips
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
              />
              <TextField
                label="Password"
                type="password"
                autoComplete="current-password"
                fullWidth
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <Button type="submit" variant="contained" size="large" disabled={loading}>
                Sign in
              </Button>
              <Button type="button" variant="text" onClick={onForgot}>
                Forgot password?
              </Button>
              <Typography variant="body2" color="text.secondary">
                No account?{' '}
                <Link component={RouterLink} to="/signup" fontWeight={700}>
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
