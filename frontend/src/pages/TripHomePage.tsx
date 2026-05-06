import { useEffect, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  AppBar,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Toolbar,
  Typography,
  Alert,
  Chip,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import EditIcon from '@mui/icons-material/Edit'
import { useAuth } from '../context/AuthContext'
import { canUserManageTripAsLeader, updateTripDetails } from '../services/tripService'
import type { Trip } from '../types/models'
import { deriveStatus, formatTripDates, statusLabel } from '../lib/tripUtils'
import { tripooColors } from '../theme'

export default function TripHomePage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
  const [canManage, setCanManage] = useState(false)
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(trip.name)
  const [destination, setDestination] = useState(trip.destination)
  const [description, setDescription] = useState(trip.description)
  const [budget, setBudget] = useState(String(trip.budget))
  const [start, setStart] = useState(
    trip.startDate ? new Date(trip.startDate).toISOString().slice(0, 10) : '',
  )
  const [end, setEnd] = useState(trip.endDate ? new Date(trip.endDate).toISOString().slice(0, 10) : '')
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    if (!tripId || !firebaseUser) return
    void canUserManageTripAsLeader(tripId, firebaseUser.uid).then(setCanManage)
  }, [tripId, firebaseUser])

  useEffect(() => {
    setName(trip.name)
    setDestination(trip.destination)
    setDescription(trip.description)
    setBudget(String(trip.budget))
    setStart(trip.startDate ? new Date(trip.startDate).toISOString().slice(0, 10) : '')
    setEnd(trip.endDate ? new Date(trip.endDate).toISOString().slice(0, 10) : '')
  }, [trip])

  const live = deriveStatus(trip.startDate, trip.endDate)
  const chip =
    live === 'active'
      ? { bg: '#E8F5EA', color: '#2A5E35' }
      : live === 'upcoming'
        ? { bg: '#DDEEFF', color: '#1A5FA8' }
        : { bg: '#F3F4F6', color: '#6B7280' }

  async function saveEdit() {
    if (!tripId) return
    setErr(null)
    const startMs = start ? new Date(start).getTime() : trip.startDate
    const endMs = end ? new Date(end).getTime() : trip.endDate
    try {
      await updateTripDetails(tripId, {
        name: name.trim(),
        destination: destination.trim(),
        description: description.trim(),
        startDate: startMs,
        endDate: endMs,
        budget: Number(budget) || 0,
      })
      setOpen(false)
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Update failed')
    }
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
          <IconButton edge="start" onClick={() => navigate('/dashboard')} sx={{ color: 'inherit' }}>
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }} noWrap>
            {trip.name || 'Trip'}
          </Typography>
          {canManage && (
            <IconButton onClick={() => setOpen(true)} aria-label="Edit trip">
              <EditIcon />
            </IconButton>
          )}
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
          <Chip
            label={statusLabel(live)}
            size="small"
            sx={{ bgcolor: chip.bg, color: chip.color, fontWeight: 600 }}
          />
          <Typography variant="caption" color="text.secondary">
            Code: {trip.joinCode}
          </Typography>
        </Stack>

        <Typography variant="body2" color="text.secondary" gutterBottom>
          {formatTripDates(trip.startDate, trip.endDate)}
        </Typography>
        {trip.destination ? (
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mt: 1 }}>
            {trip.destination}
          </Typography>
        ) : null}
        {trip.description ? (
          <Typography variant="body2" sx={{ mt: 1 }}>
            {trip.description}
          </Typography>
        ) : null}
        <Typography variant="body2" sx={{ mt: 2 }}>
          Budget: {trip.budget.toLocaleString()}
        </Typography>
      </Box>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Edit trip</DialogTitle>
        <DialogContent>
          {err && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {err}
            </Alert>
          )}
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Name" fullWidth value={name} onChange={(e) => setName(e.target.value)} />
            <TextField
              label="Destination"
              fullWidth
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
            />
            <TextField
              label="Description"
              fullWidth
              multiline
              minRows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <TextField
              label="Budget"
              type="number"
              fullWidth
              value={budget}
              onChange={(e) => setBudget(e.target.value)}
            />
            <TextField
              label="Start"
              type="date"
              fullWidth
              InputLabelProps={{ shrink: true }}
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
            <TextField
              label="End"
              type="date"
              fullWidth
              InputLabelProps={{ shrink: true }}
              value={end}
              onChange={(e) => setEnd(e.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveEdit()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
