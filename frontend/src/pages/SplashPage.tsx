import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Box, CircularProgress, Typography } from '@mui/material'
import { useAuth } from '../context/AuthContext'
import { tripooColors } from '../theme'

/** Matches `fragment_splash.xml`: gradient, icon tile, Tripoo, subtitle, indeterminate progress. */
export default function SplashPage() {
  const { firebaseUser, loading } = useAuth()
  const navigate = useNavigate()
  const [minElapsed, setMinElapsed] = useState(false)

  useEffect(() => {
    const id = window.setTimeout(() => setMinElapsed(true), 1400)
    return () => window.clearTimeout(id)
  }, [])

  useEffect(() => {
    if (!minElapsed || loading) return
    if (firebaseUser) {
      navigate('/dashboard', { replace: true })
    } else {
      navigate('/login', { replace: true })
    }
  }, [minElapsed, loading, firebaseUser, navigate])

  return (
    <Box
      sx={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: `linear-gradient(165deg, ${tripooColors.orange} 0%, ${tripooColors.orangeDark} 50%, #9A4A12 100%)`,
        px: 2,
      }}
    >
      <Box sx={{ textAlign: 'center', maxWidth: 360 }}>
        <Box
          sx={{
            width: 84,
            height: 84,
            borderRadius: 2.5,
            mx: 'auto',
            mb: 2.75,
            background: 'rgba(255,255,255,0.18)',
            border: '1px solid rgba(255,255,255,0.35)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
          }}
        >
          <Box
            component="img"
            src="/tripoo-logo.svg"
            alt=""
            sx={{ width: 40, height: 40, filter: 'brightness(0) invert(1)' }}
          />
        </Box>
        <Typography
          sx={{
            fontSize: { xs: 36, sm: 42 },
            fontWeight: 900,
            letterSpacing: -0.035 * 16,
            color: tripooColors.surface,
            lineHeight: 1.05,
          }}
        >
          Tripoo
        </Typography>
        <Typography
          sx={{
            mt: 0.5,
            fontSize: 15,
            color: tripooColors.surface,
            opacity: 0.72,
          }}
        >
          Plan together. Travel better.
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6.5 }}>
          <CircularProgress
            size={36}
            sx={{
              color: tripooColors.surface,
            }}
          />
        </Box>
      </Box>
    </Box>
  )
}
