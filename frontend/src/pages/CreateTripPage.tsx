import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AppBar,
  Box,
  Button,
  IconButton,
  Stack,
  TextField,
  Toolbar,
  Typography,
  Alert,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useAuth } from '../context/AuthContext'
import { createTrip } from '../services/tripService'
import { addTripToUser, getUser } from '../services/userService'
import type { Trip, TripMember } from '../types/models'
import { tripooColors } from '../theme'
import { bgForSeed, letterFromName } from '../lib/avatarIdentity'

export default function CreateTripPage() {
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [destination, setDestination] = useState('')
  const [description, setDescription] = useState('')
  const [budget, setBudget] = useState('0')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setErr(null)
    if (!firebaseUser) return
    if (!name.trim()) {
      setErr('Trip name is required')
      return
    }
    const startMs = start ? new Date(start).getTime() : Date.now()
    const endMs = end ? new Date(end).getTime() : startMs + 86400000
    if (Number.isNaN(startMs) || Number.isNaN(endMs)) {
      setErr('Invalid dates')
      return
    }
    setLoading(true)
    try {
      const u = await getUser(firebaseUser.uid)
      const userName = u?.name?.trim() || firebaseUser.displayName || 'User'
      const photo = u?.photoUrl || firebaseUser.photoURL || null
      const member: TripMember = {
        userId: firebaseUser.uid,
        name: userName,
        email: firebaseUser.email || '',
        photoUrl: photo,
        isAdmin: true,
        avatarLetter: u?.avatarLetter || letterFromName(userName),
        avatarColorHex: u?.avatarColorHex || bgForSeed(firebaseUser.uid),
      }
      const trip: Trip = {
        id: '',
        name: name.trim(),
        destination: destination.trim(),
        description: description.trim(),
        startDate: startMs,
        endDate: endMs,
        budget: Number(budget) || 0,
        adminId: firebaseUser.uid,
        joinCode: '',
        memberIds: [],
        status: 'upcoming',
        type: 'trip',
      }
      const tripId = await createTrip(trip, member)
      await addTripToUser(firebaseUser.uid, tripId)
      navigate(`/trips/${tripId}`, { replace: true })
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Failed to create trip')
    } finally {
      setLoading(false)
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
          <IconButton edge="start" onClick={() => navigate(-1)} sx={{ color: 'inherit' }}>
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
            New trip
          </Typography>
        </Toolbar>
      </AppBar>
      <Box component="form" onSubmit={submit} sx={{ p: 2, maxWidth: 560, mx: 'auto' }}>
        {err && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {err}
          </Alert>
        )}
        <Stack spacing={2}>
          <TextField label="Trip name" required fullWidth value={name} onChange={(e) => setName(e.target.value)} />
          <TextField label="Destination" fullWidth value={destination} onChange={(e) => setDestination(e.target.value)} />
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
            label="Start date"
            type="date"
            fullWidth
            InputLabelProps={{ shrink: true }}
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
          <TextField
            label="End date"
            type="date"
            fullWidth
            InputLabelProps={{ shrink: true }}
            value={end}
            onChange={(e) => setEnd(e.target.value)}
          />
          <Button type="submit" variant="contained" size="large" disabled={loading}>
            Create trip
          </Button>
        </Stack>
      </Box>
    </>
  )
}
