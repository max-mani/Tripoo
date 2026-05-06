import { useEffect, useState } from 'react'
import { Navigate, Outlet, useParams } from 'react-router-dom'
import { Box, CircularProgress, Typography } from '@mui/material'
import { useAuth } from '../context/AuthContext'
import { subscribeTrip } from '../services/tripService'
import type { Trip } from '../types/models'
import { AdBanner } from '../components/AdBanner'
import { TripBottomNav } from '../components/TripBottomNav'
import { AD_SLOT_TRIP } from '../config/ads'
import { tripooColors } from '../theme'

/** Trip tabs: banner (~90px) + bottom nav (~56px) + safe area */
const TRIP_MAIN_PADDING_BOTTOM = 'calc(90px + 56px + env(safe-area-inset-bottom, 0px))'

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
    <Box sx={{ pb: TRIP_MAIN_PADDING_BOTTOM }}>
      <Outlet context={{ trip }} />
      <TripBottomNav bottomAd={<AdBanner adSlot={AD_SLOT_TRIP} minHeight={90} />} />
    </Box>
  )
}
