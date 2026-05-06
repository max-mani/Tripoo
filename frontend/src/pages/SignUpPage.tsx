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

export default function SignUpPage() {
  const { signUp } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErr(null)
    if (!name.trim() || !email.trim() || !password) {
      setErr('Please fill in all fields')
      return
    }
    if (password.length < 6) {
      setErr('Password must be at least 6 characters')
      return
    }
    setLoading(true)
    try {
      await signUp(name, email, password)
      navigate('/dashboard', { replace: true })
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Sign up failed')
    } finally {
      setLoading(false)
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
            Create account
          </Typography>
          {err && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErr(null)}>
              {err}
            </Alert>
          )}
          <form onSubmit={onSubmit}>
            <Stack spacing={2}>
              <TextField
                label="Display name"
                autoComplete="name"
                fullWidth
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
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
                autoComplete="new-password"
                fullWidth
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <Button type="submit" variant="contained" size="large" disabled={loading}>
                Sign up
              </Button>
              <Typography variant="body2" color="text.secondary">
                Already have an account?{' '}
                <Link component={RouterLink} to="/login" fontWeight={700}>
                  Sign in
                </Link>
              </Typography>
            </Stack>
          </form>
        </CardContent>
      </Card>
    </Box>
  )
}
