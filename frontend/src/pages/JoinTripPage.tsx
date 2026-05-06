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
import { joinTrip } from '../services/tripService'
import { addTripToUser, getUser } from '../services/userService'
import type { TripMember } from '../types/models'
import { tripooColors } from '../theme'
import { bgForSeed, letterFromName } from '../lib/avatarIdentity'

export default function JoinTripPage() {
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setErr(null)
    if (!firebaseUser) return
    const trimmed = code.trim().toUpperCase()
    if (!trimmed) {
      setErr('Enter a trip code')
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
        isAdmin: false,
        avatarLetter: u?.avatarLetter || letterFromName(userName),
        avatarColorHex: u?.avatarColorHex || bgForSeed(firebaseUser.uid),
      }
      const tripId = await joinTrip(trimmed, member)
      if (!tripId) {
        setErr('Invalid trip code')
        return
      }
      await addTripToUser(firebaseUser.uid, tripId)
      navigate(`/trips/${tripId}`, { replace: true })
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Could not join trip')
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
            Join trip
          </Typography>
        </Toolbar>
      </AppBar>
      <Box component="form" onSubmit={submit} sx={{ p: 2, maxWidth: 480, mx: 'auto' }}>
        {err && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {err}
          </Alert>
        )}
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Enter the join code (e.g. TRP-ABC) from your organiser.
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Trip code"
            fullWidth
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="TRP-XXX"
          />
          <Button type="submit" variant="contained" size="large" disabled={loading}>
            Join
          </Button>
        </Stack>
      </Box>
    </>
  )
}
