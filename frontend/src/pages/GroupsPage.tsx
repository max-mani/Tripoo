import { useEffect, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  Avatar,
  Box,
  Button,
  IconButton,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Switch,
  Typography,
  Stack,
  Card,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import { useAuth } from '../context/AuthContext'
import { setMemberAdminRole, subscribeTripMembers } from '../services/tripService'
import type { Trip, TripMember } from '../types/models'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { textColorForSeed, letterFromName } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'
import { formatTripDates } from '../lib/tripUtils'
import { TripTabScaffold } from '../components/TripTabScaffold'

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

  const subtitle = `${members.length} PARTICIPANTS · ${formatTripDates(trip.startDate, trip.endDate).toUpperCase()}`

  const header = (
    <Box
      sx={{
        bgcolor: tripooColors.surface,
        px: 2,
        pt: `calc(12px + env(safe-area-inset-top, 0px))`,
        pb: 1.5,
        borderBottom: `1px solid ${tripooColors.border}`,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
    >
      <IconButton
        onClick={() => navigate(`/trips/${tripId}`)}
        sx={{
          width: 36,
          height: 36,
          bgcolor: '#FDE7D2',
          color: tripooColors.orange,
          '&:hover': { bgcolor: '#FCD9B8' },
        }}
        aria-label="Back"
      >
        <ArrowBackIosNewIcon sx={{ fontSize: 16, ml: 0.5 }} />
      </IconButton>
      <Box sx={{ flex: 1, textAlign: 'center', minWidth: 0 }}>
        <Typography sx={{ fontWeight: 800, fontSize: 17 }} noWrap>
          {trip.name}
        </Typography>
        <Typography
          sx={{
            fontSize: 11,
            fontWeight: 800,
            color: tripooColors.orange,
            letterSpacing: 0.04,
            mt: 0.25,
          }}
          noWrap
        >
          {subtitle}
        </Typography>
      </Box>
      <IconButton sx={{ width: 36, height: 36 }}>
        <MoreHorizIcon />
      </IconButton>
    </Box>
  )

  return (
    <TripTabScaffold header={header}>
      <Box sx={{ px: 2, pt: 1.6 }}>
        <Card
          sx={{
            borderRadius: 2,
            bgcolor: tripooColors.orange,
            color: '#fff',
            boxShadow: '0 6px 20px rgba(244, 140, 37, 0.35)',
            mb: 2,
            overflow: 'hidden',
          }}
        >
          <Stack direction="row" alignItems="center" sx={{ px: 2.25, py: 2 }}>
            <Box sx={{ flex: 1 }}>
              <Typography sx={{ fontSize: 11, color: 'rgba(255,255,255,0.76)', fontWeight: 600 }}>
                Trip Join Code
              </Typography>
              <Typography sx={{ fontWeight: 900, fontSize: 26, letterSpacing: 2, mt: 0.35 }}>
                {trip.joinCode}
              </Typography>
            </Box>
            <Button
              variant="contained"
              size="small"
              startIcon={<ContentCopyIcon sx={{ fontSize: 18 }} />}
              onClick={() => void navigator.clipboard.writeText(trip.joinCode)}
              sx={{
                bgcolor: 'rgba(255,255,255,0.22)',
                color: '#fff',
                fontWeight: 800,
                textTransform: 'none',
                boxShadow: 'none',
                '&:hover': { bgcolor: 'rgba(255,255,255,0.32)' },
              }}
            >
              Copy
            </Button>
          </Stack>
        </Card>

        <Typography sx={{ fontWeight: 900, fontSize: 13, color: tripooColors.textPrimary, mb: 1 }}>
          Members ({members.length})
        </Typography>

        <List sx={{ bgcolor: tripooColors.surface, borderRadius: 2, border: `1px solid ${tripooColors.border}` }}>
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
    </TripTabScaffold>
  )
}
