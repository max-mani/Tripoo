import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AppBar,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Fab,
  IconButton,
  Stack,
  Toolbar,
  Typography,
  Chip,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import PersonIcon from '@mui/icons-material/Person'
import { useAuth } from '../context/AuthContext'
import { fetchTripsForUser } from '../services/tripService'
import { setLastActiveTrip } from '../services/userService'
import type { Trip } from '../types/models'
import { deriveStatus, formatTripDates, statusLabel } from '../lib/tripUtils'
import { tripooColors } from '../theme'

function statusChipColor(status: string) {
  if (status === 'active') return { bg: '#E8F5EA', color: '#2A5E35' }
  if (status === 'upcoming') return { bg: '#DDEEFF', color: '#1A5FA8' }
  return { bg: '#F3F4F6', color: '#6B7280' }
}

export default function DashboardPage() {
  const { firebaseUser, user } = useAuth()
  const navigate = useNavigate()
  const [trips, setTrips] = useState<Trip[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      if (!user?.tripIds?.length) {
        setTrips([])
        setLoading(false)
        return
      }
      setLoading(true)
      const list = await fetchTripsForUser(user.tripIds)
      if (!cancelled) {
        setTrips(list)
        setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [user?.tripIds])

  async function openTrip(t: Trip) {
    if (!firebaseUser) return
    await setLastActiveTrip(firebaseUser.uid, t.id)
    navigate(`/trips/${t.id}`)
  }

  return (
    <>
      <AppBar
        position="sticky"
        elevation={0}
        sx={{
          bgcolor: tripooColors.surface,
          borderBottom: `1px solid ${tripooColors.border}`,
          color: tripooColors.textPrimary,
        }}
      >
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 800 }}>
            Tripoo
          </Typography>
          <IconButton color="inherit" onClick={() => navigate('/profile')} aria-label="Profile">
            <PersonIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2, pb: 10, maxWidth: 600, mx: 'auto' }}>
        <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
          <Button variant="contained" fullWidth onClick={() => navigate('/trips/new')}>
            New trip
          </Button>
          <Button variant="outlined" fullWidth onClick={() => navigate('/trips/join')}>
            Join
          </Button>
        </Stack>

        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
          Your trips
        </Typography>

        {loading ? (
          <Typography color="text.secondary">Loading…</Typography>
        ) : trips.length === 0 ? (
          <Card sx={{ borderRadius: 3 }}>
            <CardContent>
              <Typography color="text.secondary">
                No trips yet. Create one or join with a code.
              </Typography>
            </CardContent>
          </Card>
        ) : (
          <Stack spacing={2}>
            {trips.map((t) => {
              const live = deriveStatus(t.startDate, t.endDate)
              const chip = statusChipColor(live)
              return (
                <Card key={t.id} sx={{ borderRadius: 3 }}>
                  <CardActionArea onClick={() => void openTrip(t)}>
                    <CardContent>
                      <Stack direction="row" spacing={1} sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <Typography variant="h6" sx={{ fontWeight: 700 }}>
                          {t.name || 'Trip'}
                        </Typography>
                        <Chip
                          label={statusLabel(live)}
                          size="small"
                          sx={{ bgcolor: chip.bg, color: chip.color, fontWeight: 600 }}
                        />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {t.destination}
                      </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                        {formatTripDates(t.startDate, t.endDate)}
                      </Typography>
                    </CardContent>
                  </CardActionArea>
                </Card>
              )
            })}
          </Stack>
        )}
      </Box>

      <Fab
        color="primary"
        sx={{ position: 'fixed', right: 24, bottom: 24 }}
        onClick={() => navigate('/trips/new')}
      >
        <AddIcon />
      </Fab>
    </>
  )
}
