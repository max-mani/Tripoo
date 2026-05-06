import { Box, Typography } from '@mui/material'
import { useAuth } from '../context/AuthContext'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { tripooColors } from '../theme'

function dashboardInitials(name: string, email: string): string {
  const n = name?.trim() || email?.split('@')[0] || ''
  const parts = n.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return `${parts[0]![0]!}${parts[parts.length - 1]![0]!}`.toUpperCase()
  }
  return n.slice(0, 2).toUpperCase() || '?'
}

type Props = {
  onClick: () => void
}

export function DashboardProfileAvatar({ onClick }: Props) {
  const { user } = useAuth()
  const src = photoSrcForDisplay(user?.photoUrl)
  const letter = dashboardInitials(user?.name || '', user?.email || '')

  return (
    <Box
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick()
        }
      }}
      aria-label="Profile"
      sx={{
        width: 40,
        height: 40,
        borderRadius: '50%',
        p: '2px',
        background: `linear-gradient(180deg, ${tripooColors.orange} 0%, ${tripooColors.orangeDark} 100%)`,
        cursor: 'pointer',
        flexShrink: 0,
      }}
    >
      <Box
        sx={{
          width: '100%',
          height: '100%',
          borderRadius: '50%',
          overflow: 'hidden',
          bgcolor: tripooColors.orange,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {src ? (
          <Box
            component="img"
            src={src}
            alt=""
            sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <Typography
            sx={{
              color: tripooColors.surface,
              fontWeight: 900,
              fontSize: 13,
              lineHeight: 1,
            }}
          >
            {letter}
          </Typography>
        )}
      </Box>
    </Box>
  )
}
