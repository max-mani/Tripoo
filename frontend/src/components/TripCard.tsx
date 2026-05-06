import { Box, Card, LinearProgress, Typography } from '@mui/material'
import LocationOnIcon from '@mui/icons-material/LocationOn'
import CalendarTodayIcon from '@mui/icons-material/CalendarToday'
import GroupsIcon from '@mui/icons-material/Groups'
import ScheduleIcon from '@mui/icons-material/Schedule'
import type { TripWithMeta } from '../types/models'
import { tripooColors } from '../theme'

function fmtShortDate(ms: number): string {
  return new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function statusColors(status: string) {
  if (status === 'active') {
    return {
      bar: '#2A5E35',
      badgeBg: '#E8F5EA',
      badgeText: '#2A5E35',
    }
  }
  if (status === 'upcoming') {
    return {
      bar: '#1A5FA8',
      badgeBg: '#DDEEFF',
      badgeText: '#1A5FA8',
    }
  }
  return {
    bar: '#9CA3AF',
    badgeBg: '#F3F4F6',
    badgeText: '#6B7280',
  }
}

function daysUntilStart(startMs: number): number {
  return Math.max(0, Math.floor((startMs - Date.now()) / 86400000))
}

type Props = {
  meta: TripWithMeta
  onClick: () => void
}

export function TripCard({ meta, onClick }: Props) {
  const { trip } = meta
  const status = trip.status
  const c = statusColors(status)
  const name = trip.name?.trim() || trip.destination?.trim() || 'Trip'
  const dest = trip.destination?.trim() || '—'
  const isPast = status === 'past'

  return (
    <Card
      onClick={onClick}
      elevation={0}
      sx={{
        mx: { xs: 2, sm: 2 },
        mb: 1.4,
        borderRadius: '14px',
        border: `1px solid ${tripooColors.orange}14`,
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'row',
        overflow: 'hidden',
        bgcolor: tripooColors.surface,
        opacity: isPast ? 0.6 : 1,
        transition: 'transform 0.15s, box-shadow 0.15s',
        '&:active': { transform: 'scale(0.99)' },
      }}
    >
      <Box sx={{ width: 4, bgcolor: c.bar, flexShrink: 0 }} />
      <Box sx={{ flex: 1, py: 1.6, pl: 1.4, pr: 1.6 }}>
        <Box sx={{ display: 'flex', flexDirection: 'row', alignItems: 'flex-start', mb: 0.5 }}>
          <Typography
            sx={{
              flex: 1,
              fontSize: 15,
              fontWeight: 700,
              color: tripooColors.textPrimary,
              lineHeight: 1.25,
            }}
          >
            {name}
          </Typography>
          <Typography
            component="span"
            sx={{
              fontSize: 11,
              fontWeight: 700,
              px: 1.1,
              py: 0.4,
              borderRadius: '5px',
              bgcolor: c.badgeBg,
              color: c.badgeText,
              textTransform: 'capitalize',
              flexShrink: 0,
              ml: 1,
            }}
          >
            {status}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5 }}>
          <LocationOnIcon sx={{ fontSize: 12, color: tripooColors.textHint, mr: 0.5 }} />
          <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>{dest}</Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', mb: 0.75 }}>
          <CalendarTodayIcon sx={{ fontSize: 12, color: tripooColors.textHint, mr: 0.5 }} />
          <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>
            {fmtShortDate(trip.startDate)} – {fmtShortDate(trip.endDate)}
          </Typography>
          <Typography sx={{ fontSize: 11, color: tripooColors.textHint, mx: 0.5 }}>·</Typography>
          <GroupsIcon sx={{ fontSize: 12, color: tripooColors.textHint, mr: 0.5 }} />
          <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>
            {meta.memberCount} members
          </Typography>
        </Box>

        {status === 'active' && trip.budget > 0 ? (
          <Box>
            <LinearProgress
              variant="determinate"
              value={Math.min(100, Math.round((meta.totalSpent / trip.budget) * 100))}
              sx={{
                height: 10,
                borderRadius: 5,
                bgcolor: '#F3F4F6',
                '& .MuiLinearProgress-bar': {
                  borderRadius: 5,
                  bgcolor: tripooColors.orange,
                },
              }}
            />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.6 }}>
              <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>
                ₹{Math.round(meta.totalSpent)} / ₹{Math.round(trip.budget)} budget
              </Typography>
              <Typography sx={{ fontSize: 11, fontWeight: 700, color: tripooColors.orange }}>
                {Math.max(0, 100 - Math.round((meta.totalSpent / trip.budget) * 100))}% left
              </Typography>
            </Box>
          </Box>
        ) : null}

        {status === 'upcoming' ? (
          <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <ScheduleIcon sx={{ fontSize: 14, color: c.badgeText, mr: 0.75 }} />
            <Typography sx={{ fontSize: 12, fontWeight: 700, color: c.badgeText }}>
              Starts in {daysUntilStart(trip.startDate)} days
            </Typography>
          </Box>
        ) : null}
      </Box>
    </Card>
  )
}
