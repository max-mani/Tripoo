import { useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Card,
  IconButton,
  Link,
  Stack,
  TextField,
  Typography,
  Alert,
  InputAdornment,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import PersonOutlineIcon from '@mui/icons-material/PersonOutline'
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import AddIcon from '@mui/icons-material/Add'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import { useAuth } from '../context/AuthContext'
import { AppCheckCircleIcon } from '../components/icons/AppCheckCircleIcon'
import { fileToProfileBase64, photoSrcForDisplay } from '../lib/imageToBase64'
import { tripooColors } from '../theme'

function liveInitial(name: string): string {
  const t = name.trim()
  if (!t) return ''
  const parts = t.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return `${parts[0]![0]!}${parts[parts.length - 1]![0]!}`.toUpperCase()
  }
  return t.slice(0, 2).toUpperCase()
}

export default function SignUpPage() {
  const { signUp } = useAuth()
  const navigate = useNavigate()
  const fileRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [avatarBase64, setAvatarBase64] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const initials = useMemo(() => liveInitial(name), [name])
  const avatarPreview = photoSrcForDisplay(avatarBase64)

  async function onPick(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (!f) return
    try {
      const b64 = await fileToProfileBase64(f)
      setAvatarBase64(b64)
    } catch {
      setErr('Could not read image')
    }
  }

  async function onSubmit(ev: React.FormEvent) {
    ev.preventDefault()
    setErr(null)
    if (!name.trim()) {
      setErr('Name is required')
      return
    }
    if (!email.trim()) {
      setErr('Email is required')
      return
    }
    if (password.length < 8) {
      setErr('Password must be at least 8 characters')
      return
    }
    if (password !== confirm) {
      setErr('Passwords do not match')
      return
    }
    setLoading(true)
    try {
      await signUp(name, email, password, avatarBase64)
      navigate('/dashboard', { replace: true })
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Sign up failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box sx={{ minHeight: '100dvh', bgcolor: tripooColors.bg, display: 'flex', flexDirection: 'column' }}>
      <Box
        sx={{
          flexShrink: 0,
          bgcolor: tripooColors.surface,
          px: 2,
          pt: `calc(12px + env(safe-area-inset-top, 0px))`,
          pb: 1.5,
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <IconButton
          onClick={() => navigate('/login')}
          sx={{
            width: 36,
            height: 36,
            bgcolor: '#FDE7D2',
            color: tripooColors.orange,
            '&:hover': { bgcolor: '#FCD9B8' },
          }}
          aria-label="Back"
        >
          <ArrowBackIosNewIcon sx={{ fontSize: 16, ml: 0.5 }} />
        </IconButton>
        <Box>
          <Typography sx={{ fontWeight: 700, fontSize: 17, color: tripooColors.textPrimary }}>
            Create Account
          </Typography>
          <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary, mt: 0.1 }}>
            Set up your Tripoo profile
          </Typography>
        </Box>
      </Box>

      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
        }}
      >
        <Box component="form" onSubmit={onSubmit} sx={{ px: 2.5, py: 2.5, pb: 4, maxWidth: 520, mx: 'auto' }}>
          {err && (
            <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErr(null)}>
              {err}
            </Alert>
          )}

          <Stack alignItems="center" sx={{ mb: 2 }}>
            <Box
              role="button"
              tabIndex={0}
              onClick={() => fileRef.current?.click()}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  fileRef.current?.click()
                }
              }}
              sx={{
                width: 86,
                height: 86,
                borderRadius: '50%',
                boxSizing: 'border-box',
                bgcolor: 'rgba(244, 140, 37, 0.12)',
                border: '3px dashed rgba(244, 140, 37, 0.35)',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <Box
                sx={{
                  width: 80,
                  height: 80,
                  borderRadius: '50%',
                  overflow: 'hidden',
                  bgcolor: avatarPreview ? tripooColors.surface : initials ? tripooColors.orange : 'rgba(255,255,255,0.94)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                {avatarPreview ? (
                  <Box component="img" src={avatarPreview} alt="" sx={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : initials ? (
                  <Typography sx={{ color: tripooColors.surface, fontWeight: 800, fontSize: 22 }}>
                    {initials}
                  </Typography>
                ) : (
                  <AddIcon sx={{ color: tripooColors.orange, fontSize: 28 }} />
                )}
              </Box>
            </Box>
            <input ref={fileRef} type="file" accept="image/*" hidden onChange={(e) => void onPick(e)} />
            <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary, mt: 1.1 }}>
              Profile Photo
            </Typography>
          </Stack>

          <Typography sx={{ fontSize: 12, fontWeight: 700, color: tripooColors.textSecondary, mb: 0.75 }}>
            Full Name
          </Typography>
          <TextField
            fullWidth
            placeholder="e.g. Faizal Noor"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="name"
            sx={{ mb: 1.75 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <PersonOutlineIcon sx={{ color: tripooColors.textSecondary }} />
                </InputAdornment>
              ),
            }}
          />

          <Typography sx={{ fontSize: 12, fontWeight: 700, color: tripooColors.textSecondary, mb: 0.75 }}>
            Email Address
          </Typography>
          <TextField
            fullWidth
            type="email"
            placeholder="faizal@email.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            sx={{ mb: 1.75 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <EmailOutlinedIcon sx={{ color: tripooColors.textSecondary }} />
                </InputAdornment>
              ),
            }}
          />

          <Typography sx={{ fontSize: 12, fontWeight: 700, color: tripooColors.textSecondary, mb: 0.75 }}>
            Password
          </Typography>
          <TextField
            fullWidth
            type="password"
            placeholder="At least 8 characters"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            sx={{ mb: 1.75 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <LockOutlinedIcon sx={{ color: tripooColors.textSecondary }} />
                </InputAdornment>
              ),
            }}
          />

          <Typography sx={{ fontSize: 12, fontWeight: 700, color: tripooColors.textSecondary, mb: 0.75 }}>
            Confirm Password
          </Typography>
          <TextField
            fullWidth
            type="password"
            placeholder="Repeat password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            sx={{ mb: 1.75 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <LockOutlinedIcon sx={{ color: tripooColors.textSecondary }} />
                </InputAdornment>
              ),
            }}
          />

          <Card
            variant="outlined"
            sx={{
              mb: 1.75,
              borderRadius: 1.5,
              bgcolor: '#FFF7F0',
              borderColor: 'rgba(244, 140, 37, 0.35)',
              boxShadow: 'none',
            }}
          >
            <Box sx={{ p: 1.75 }}>
              <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.6 }}>
                <InfoOutlinedIcon sx={{ color: tripooColors.orange, fontSize: 18 }} />
                <Typography sx={{ fontWeight: 700, fontSize: 13 }}>Why we need this</Typography>
              </Stack>
              <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary, lineHeight: 1.6 }}>
                Your name and photo are visible to trip members so everyone knows who paid for what and who&apos;s
                assigned each task.
              </Typography>
            </Box>
          </Card>

          <Button
            type="submit"
            variant="contained"
            disabled={loading}
            fullWidth
            size="large"
            startIcon={
              <AppCheckCircleIcon sx={{ color: tripooColors.surface, fontSize: 22 }} />
            }
            sx={{
              py: 1.2,
              fontWeight: 700,
              bgcolor: tripooColors.orange,
              color: tripooColors.surface,
              '&:hover': { bgcolor: tripooColors.orangeDark },
            }}
          >
            Create Account
          </Button>

          <Stack direction="row" justifyContent="center" alignItems="center" sx={{ mt: 1.5 }}>
            <Typography sx={{ fontSize: 13, color: tripooColors.textSecondary }}>
              Already have an account?
            </Typography>
            <Link component={RouterLink} to="/login" sx={{ ml: 0.5, fontWeight: 700, fontSize: 13 }}>
              Log In
            </Link>
          </Stack>
        </Box>
      </Box>
    </Box>
  )
}
