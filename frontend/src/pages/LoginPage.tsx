import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Card,
  Link,
  Stack,
  TextField,
  Typography,
  Alert,
  InputAdornment,
} from '@mui/material'
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import LoginIcon from '@mui/icons-material/Login'
import { useAuth } from '../context/AuthContext'
import { TripooRocketLogo } from '../components/TripooRocketLogo'
import { tripooColors } from '../theme'

/** Matches `fragment_auth.xml`: hero gradient, logo row, tagline, elevated sign-in card, outline inputs. */
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

  const inputRowSx = {
    '& .MuiOutlinedInput-root': {
      bgcolor: tripooColors.surface,
      borderRadius: 1.25,
      pl: 0,
      minHeight: 50,
      '& fieldset': { borderColor: tripooColors.border },
      '&:hover fieldset': { borderColor: '#BBA898' },
    },
  }

  return (
    <Box
      sx={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: 'column',
        bgcolor: tripooColors.bg,
      }}
    >
      <Box
        sx={{
          height: 260,
          flexShrink: 0,
          background: `linear-gradient(165deg, ${tripooColors.orange} 0%, ${tripooColors.orangeDark} 55%, #9A4A12 100%)`,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            left: '50%',
            top: '40%',
            transform: 'translate(-50%, -50%)',
            width: 260,
            height: 260,
            borderRadius: '50%',
            bgcolor: 'rgba(255,255,255,0.16)',
          }}
        />
        <Stack
          sx={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            px: 3,
            pb: 3.5,
            alignItems: 'center',
          }}
          spacing={1.25}
        >
          <Stack direction="row" alignItems="center" spacing={1.25}>
            <Box
              sx={{
                width: 52,
                height: 52,
                borderRadius: '50%',
                bgcolor: 'rgba(255,255,255,0.2)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <TripooRocketLogo size={28} color={tripooColors.surface} />
            </Box>
            <Typography sx={{ fontWeight: 900, fontSize: 32, color: tripooColors.surface }}>Tripoo</Typography>
          </Stack>
          <Typography
            sx={{
              textAlign: 'center',
              fontSize: 14,
              color: tripooColors.surface,
              opacity: 0.88,
              lineHeight: 1.45,
              maxWidth: 320,
            }}
          >
            Plan trips together. Split expenses. Never lose track.
          </Typography>
        </Stack>
      </Box>

      <Box
        component="main"
        sx={{
          flex: 1,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
        }}
      >
        <Box sx={{ px: 2.5, pt: 2.25, pb: 4 }}>
          <Card
            elevation={6}
            sx={{
              borderRadius: '18px',
              border: `1px solid ${tripooColors.border}`,
              boxShadow: '0 8px 28px rgba(24,20,17,0.08)',
            }}
          >
            <Box sx={{ px: 2.5, pt: 2.75, pb: 2.5 }}>
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

              <Box sx={{ mb: 1.75 }}>
                <Typography sx={{ fontWeight: 800, fontSize: 16, color: tripooColors.textPrimary }}>
                  Welcome back 👋
                </Typography>
                <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary, mt: 0.5 }}>
                  Sign in to continue your adventures
                </Typography>
              </Box>

              <form onSubmit={onSubmit}>
                <Typography sx={{ fontSize: 12, fontWeight: 700, color: tripooColors.textSecondary, mb: 0.75 }}>
                  Email Address
                </Typography>
                <TextField
                  fullWidth
                  placeholder="Enter your email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  sx={{ ...inputRowSx, mb: 1.75 }}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start" sx={{ ml: 1 }}>
                        <EmailOutlinedIcon sx={{ color: tripooColors.textSecondary, fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  }}
                  inputProps={{ style: { fontSize: 15 } }}
                />

                <Typography sx={{ fontSize: 12, fontWeight: 700, color: tripooColors.textSecondary, mb: 0.75 }}>
                  Password
                </Typography>
                <TextField
                  fullWidth
                  placeholder="Enter your password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  sx={inputRowSx}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start" sx={{ ml: 1 }}>
                        <LockOutlinedIcon sx={{ color: tripooColors.textSecondary, fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  }}
                  inputProps={{ style: { fontSize: 15 } }}
                />

                <Typography
                  component="button"
                  type="button"
                  onClick={onForgot}
                  sx={{
                    display: 'block',
                    ml: 'auto',
                    mt: 1,
                    border: 'none',
                    bgcolor: 'transparent',
                    cursor: 'pointer',
                    fontSize: 12,
                    fontWeight: 700,
                    color: tripooColors.orange,
                    textAlign: 'right',
                  }}
                >
                  Forgot password?
                </Typography>

                <Button
                  type="submit"
                  variant="contained"
                  fullWidth
                  disabled={loading}
                  startIcon={<LoginIcon sx={{ color: tripooColors.surface }} />}
                  sx={{
                    mt: 1.5,
                    py: 1.15,
                    fontWeight: 700,
                    borderRadius: 1.5,
                    bgcolor: tripooColors.orange,
                    color: tripooColors.surface,
                    textTransform: 'none',
                    fontSize: 16,
                    '&:hover': { bgcolor: tripooColors.orangeDark },
                  }}
                >
                  Log In
                </Button>

                <Stack direction="row" justifyContent="center" alignItems="center" sx={{ mt: 1.5 }}>
                  <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary }}>
                    Don&apos;t have an account?
                  </Typography>
                  <Link component={RouterLink} to="/signup" sx={{ ml: 0.75, fontWeight: 700, fontSize: 12 }}>
                    Sign Up
                  </Link>
                </Stack>

                <Typography
                  sx={{
                    mt: 1.25,
                    textAlign: 'center',
                    fontSize: 10,
                    color: tripooColors.textHint,
                    lineHeight: 1.6,
                    px: 0.5,
                  }}
                >
                  By continuing you agree to our Terms of Service &amp; Privacy Policy.
                </Typography>
              </form>
            </Box>
          </Card>
        </Box>
      </Box>
    </Box>
  )
}
