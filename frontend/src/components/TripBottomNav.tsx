import type { ReactNode } from 'react'
import { useNavigate, useParams, useLocation } from 'react-router-dom'
import { Box, BottomNavigation, BottomNavigationAction, Paper } from '@mui/material'
import HomeIcon from '@mui/icons-material/Home'
import PaymentsIcon from '@mui/icons-material/Payments'
import ChecklistIcon from '@mui/icons-material/Checklist'
import GroupsIcon from '@mui/icons-material/Groups'
import { tripooColors } from '../theme'

type Props = {
  /** e.g. trip-group AdSense banner (Android `admob_banner_trip_group`) */
  bottomAd?: ReactNode
}

export function TripBottomNav({ bottomAd }: Props) {
  const { tripId } = useParams<{ tripId: string }>()
  const navigate = useNavigate()
  const loc = useLocation()
  const base = `/trips/${tripId}`

  let value = 0
  if (loc.pathname.endsWith('/expenses')) value = 1
  else if (loc.pathname.endsWith('/tasks')) value = 2
  else if (loc.pathname.endsWith('/groups')) value = 3

  return (
    <Box
      sx={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 1200,
        bgcolor: 'background.default',
      }}
    >
      {bottomAd}
      <Paper
        elevation={8}
        sx={{
          borderRadius: 0,
          borderTop: `1px solid ${tripooColors.border}`,
          pb: 'env(safe-area-inset-bottom, 0px)',
        }}
      >
        <BottomNavigation
          showLabels
          value={value}
          onChange={(_, v) => {
            if (v === 0) navigate(base)
            else if (v === 1) navigate(`${base}/expenses`)
            else if (v === 2) navigate(`${base}/tasks`)
            else navigate(`${base}/groups`)
          }}
          sx={{
            bgcolor: tripooColors.surface,
            '& .MuiBottomNavigationAction-root': { color: tripooColors.textSecondary },
            '& .MuiBottomNavigationAction-root.Mui-selected': { color: tripooColors.orange },
          }}
        >
          <BottomNavigationAction label="Home" icon={<HomeIcon />} />
          <BottomNavigationAction label="Expenses" icon={<PaymentsIcon />} />
          <BottomNavigationAction label="Tasks" icon={<ChecklistIcon />} />
          <BottomNavigationAction label="Groups" icon={<GroupsIcon />} />
        </BottomNavigation>
      </Paper>
    </Box>
  )
}
