import { useEffect, useState } from 'react'
import { Navigate, Outlet, useParams } from 'react-router-dom'
import { Box, CircularProgress, Typography } from '@mui/material'
import { useAuth } from '../context/AuthContext'
import { subscribeTrip } from '../services/tripService'
import type { Trip } from '../types/models'
import { TripBottomNav } from '../components/TripBottomNav'
import { tripooColors } from '../theme'

export default function TripLayout() {
  const { tripId } = useParams<{ tripId: string }>()
  const { firebaseUser } = useAuth()
  const [trip, setTrip] = useState<Trip | null | undefined>(undefined)

  useEffect(() => {
    if (!tripId) return
    const unsub = subscribeTrip(tripId, setTrip)
    return unsub
  }, [tripId])

  if (!firebaseUser || !tripId) {
    return <Navigate to="/login" replace />
  }

  if (trip === undefined) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress sx={{ color: tripooColors.orange }} />
      </Box>
    )
  }

  if (!trip || !trip.memberIds.includes(firebaseUser.uid)) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography>You are not a member of this trip.</Typography>
      </Box>
    )
  }

  return (
    <Box sx={{ pb: 10 }}>
      <Outlet context={{ trip }} />
      <TripBottomNav />
    </Box>
  )
}
