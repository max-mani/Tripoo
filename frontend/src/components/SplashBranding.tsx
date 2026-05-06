import { Box, CircularProgress, Typography } from '@mui/material'
import { TripooRocketLogo } from './TripooRocketLogo'
import { tripooColors } from '../theme'

/** Full-screen splash: `fragment_splash.xml` (84dp tile uses `bg_splash_icon_box` #33FFFFFF). */
export function SplashBranding() {
  return (
    <Box
      sx={{
        minHeight: '100dvh',
        height: '100%',
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
            borderRadius: '26px',
            mx: 'auto',
            mb: 2.75,
            bgcolor: 'rgba(255,255,255,0.2)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <TripooRocketLogo size={40} color="#FFFFFF" />
        </Box>
        <Typography
          sx={{
            fontSize: { xs: 36, sm: 42 },
            fontWeight: 900,
            letterSpacing: -0.56,
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
          <CircularProgress size={36} sx={{ color: tripooColors.surface }} />
        </Box>
      </Box>
    </Box>
  )
}
