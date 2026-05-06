import { useEffect, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  AppBar,
  Avatar,
  Box,
  IconButton,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Switch,
  Toolbar,
  Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useAuth } from '../context/AuthContext'
import { setMemberAdminRole, subscribeTripMembers } from '../services/tripService'
import type { Trip, TripMember } from '../types/models'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { textColorForSeed, letterFromName } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'

export default function GroupsPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
  const [members, setMembers] = useState<TripMember[]>([])
  const isOrganiser = firebaseUser?.uid === trip.adminId

  useEffect(() => {
    if (!tripId) return
    return subscribeTripMembers(tripId, setMembers)
  }, [tripId])

  async function toggleAdmin(m: TripMember, checked: boolean) {
    if (!tripId || !firebaseUser) return
    try {
      await setMemberAdminRole(tripId, m.userId, checked, firebaseUser.uid, trip.adminId)
    } catch (e: unknown) {
      window.alert(e instanceof Error ? e.message : 'Could not update role')
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
          <IconButton edge="start" onClick={() => navigate(`/trips/${tripId}`)} sx={{ color: 'inherit' }}>
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
            Participants
          </Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2 }}>
        <List sx={{ bgcolor: 'background.paper', borderRadius: 3 }}>
          {members.map((m) => {
            const src = photoSrcForDisplay(m.photoUrl)
            const letter = m.avatarLetter?.trim() || letterFromName(m.name)
            const bg = m.avatarColorHex?.trim() || tripooColors.orange
            const tc = textColorForSeed(m.userId)
            const showSwitch = isOrganiser && m.userId !== trip.adminId
            return (
              <ListItem
                key={m.userId}
                secondaryAction={
                  showSwitch ? (
                    <Switch
                      edge="end"
                      checked={m.isAdmin}
                      onChange={(e) => void toggleAdmin(m, e.target.checked)}
                      inputProps={{ 'aria-label': 'Co-organiser' }}
                    />
                  ) : m.isAdmin ? (
                    <Typography variant="caption" color="text.secondary">
                      {m.userId === trip.adminId ? 'Organiser' : 'Co-organiser'}
                    </Typography>
                  ) : null
                }
              >
                <ListItemAvatar>
                  {src ? (
                    <Avatar src={src} />
                  ) : (
                    <Avatar sx={{ bgcolor: bg, color: tc, fontWeight: 700 }}>{letter}</Avatar>
                  )}
                </ListItemAvatar>
                <ListItemText primary={m.name} secondary={m.email} />
              </ListItem>
            )
          })}
        </List>
      </Box>
    </>
  )
}
