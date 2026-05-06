import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  CircularProgress,
  Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import AddLinkIcon from '@mui/icons-material/AddLink'
import { useAuth } from '../context/AuthContext'
import { fetchTripsForUser } from '../services/tripService'
import { setLastActiveTrip } from '../services/userService'
import { getTotalExpenses } from '../services/expenseService'
import type { TripWithMeta } from '../types/models'
import { tripooColors } from '../theme'
import { AdBanner } from '../components/AdBanner'
import { AD_SLOT_DASHBOARD } from '../config/ads'
import { DashboardProfileAvatar } from '../components/DashboardProfileAvatar'
import { TripCard } from '../components/TripCard'

type Filter = 'all' | 'active' | 'upcoming' | 'past'

function greetingFirstName(userName: string, email: string): string {
  const n = userName?.trim() || email?.split('@')[0] || 'there'
  return n.split(/\s+/)[0] || n
}

export default function DashboardPage() {
  const { firebaseUser, user } = useAuth()
  const navigate = useNavigate()
  const [allMeta, setAllMeta] = useState<TripWithMeta[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      if (!user?.tripIds?.length) {
        setAllMeta([])
        setLoading(false)
        return
      }
      setLoading(true)
      const trips = await fetchTripsForUser(user.tripIds)
      const withMeta: TripWithMeta[] = await Promise.all(
        trips.map(async (trip) => ({
          trip,
          memberCount: trip.memberIds.length,
          totalSpent: await getTotalExpenses(trip.id),
        })),
      )
      if (!cancelled) {
        setAllMeta(withMeta)
        setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [user?.tripIds])

  const filtered = useMemo(() => {
    if (filter === 'all') return allMeta
    return allMeta.filter((m) => m.trip.status === filter)
  }, [allMeta, filter])

  const activeCount = useMemo(
    () => allMeta.filter((m) => m.trip.status === 'active').length,
    [allMeta],
  )

  async function openTrip(m: TripWithMeta) {
    if (!firebaseUser) return
    await setLastActiveTrip(firebaseUser.uid, m.trip.id)
    navigate(`/trips/${m.trip.id}`)
  }

  const first = greetingFirstName(user?.name || '', user?.email || '')

  const chip = (key: Filter, label: string) => {
    const on = filter === key
    return (
      <Typography
        component="button"
        type="button"
        key={key}
        onClick={() => setFilter(key)}
        sx={{
          border: 'none',
          cursor: 'pointer',
          fontSize: 12,
          fontWeight: 600,
          px: 1.5,
          py: 0.6,
          borderRadius: 2,
          mr: 0.9,
          whiteSpace: 'nowrap',
          flexShrink: 0,
          bgcolor: on ? tripooColors.orange : tripooColors.surface,
          color: on ? tripooColors.surface : tripooColors.textSecondary,
          boxShadow: on ? 'none' : `inset 0 0 0 1px ${tripooColors.border}`,
        }}
      >
        {label}
      </Typography>
    )
  }

  return (
    <Box
      sx={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: 'column',
        bgcolor: tripooColors.bg,
        maxWidth: '100%',
        overflow: 'hidden',
      }}
    >
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
          borderBottom: `1px solid ${tripooColors.border}`,
        }}
      >
        <Box
          component="img"
          src="/tripoo-logo.svg"
          alt=""
          sx={{ width: 23, height: 23 }}
        />
        <Typography
          sx={{
            fontSize: 21,
            fontWeight: 900,
            color: tripooColors.textPrimary,
            letterSpacing: -0.3,
          }}
        >
          Tripoo
        </Typography>
        <Box sx={{ flex: 1 }} />
        <DashboardProfileAvatar onClick={() => navigate('/profile')} />
      </Box>

      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
        }}
      >
        <Box sx={{ px: 2, pt: 1.75, pb: 1 }}>
          <Typography
            sx={{
              fontSize: 20,
              fontWeight: 800,
              color: tripooColors.textPrimary,
              lineHeight: 1.2,
            }}
          >
            Hey {first} 👋
          </Typography>
          <Typography sx={{ fontSize: 13, color: tripooColors.textSecondary, mt: 0.4 }}>
            You have {activeCount} active {activeCount === 1 ? 'trip' : 'trips'}
          </Typography>
        </Box>

        <Box
          sx={{
            display: 'flex',
            flexDirection: 'row',
            overflowX: 'auto',
            px: 2,
            pt: 1.25,
            pb: 0.5,
            scrollbarWidth: 'none',
            '&::-webkit-scrollbar': { display: 'none' },
          }}
        >
          {chip('all', 'All Trips')}
          {chip('active', 'Active')}
          {chip('upcoming', 'Upcoming')}
          {chip('past', 'Past')}
        </Box>

        <Typography
          sx={{
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: 0.8,
            color: tripooColors.textSecondary,
            px: 2,
            pt: 1.75,
            pb: 1,
            textTransform: 'uppercase',
          }}
        >
          Your trips
        </Typography>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={36} sx={{ color: tripooColors.orange }} />
          </Box>
        ) : filtered.length === 0 ? (
          <Typography sx={{ px: 2, color: tripooColors.textSecondary, pb: 4 }}>
            No trips yet. Join with a code or create a new trip.
          </Typography>
        ) : (
          <Box sx={{ pb: 2 }}>
            {filtered.map((m) => (
              <TripCard key={m.trip.id} meta={m} onClick={() => void openTrip(m)} />
            ))}
          </Box>
        )}
      </Box>

      <Box sx={{ flexShrink: 0 }}>
        <AdBanner adSlot={AD_SLOT_DASHBOARD} minHeight={100} />
      </Box>

      <Box
        sx={{
          flexShrink: 0,
          display: 'flex',
          gap: 1.25,
          px: 2,
          py: 1.25,
          pb: `calc(10px + env(safe-area-inset-bottom, 0px))`,
          bgcolor: tripooColors.bg,
          borderTop: `1px solid ${tripooColors.border}`,
          boxShadow: '0 -2px 8px rgba(24,20,17,0.06)',
        }}
      >
        <Button
          variant="outlined"
          fullWidth
          onClick={() => navigate('/trips/join')}
          startIcon={<AddLinkIcon sx={{ color: tripooColors.orange }} />}
          sx={{
            py: 1,
            borderRadius: 2,
            borderColor: tripooColors.orange,
            color: tripooColors.orange,
            fontWeight: 600,
            textTransform: 'none',
          }}
        >
          Join Trip
        </Button>
        <Button
          variant="contained"
          fullWidth
          onClick={() => navigate('/trips/new')}
          startIcon={<AddIcon />}
          sx={{
            py: 1,
            borderRadius: 2,
            fontWeight: 600,
            textTransform: 'none',
            bgcolor: tripooColors.orange,
            '&:hover': { bgcolor: tripooColors.orangeDark },
          }}
        >
          New Trip
        </Button>
      </Box>
    </Box>
  )
}
