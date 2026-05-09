import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  Avatar,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Menu,
  MenuItem,
  Radio,
  RadioGroup,
  FormControlLabel,
  Typography,
  Stack,
  Card,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import PersonAddIcon from '@mui/icons-material/PersonAdd'
import EditIcon from '@mui/icons-material/Edit'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import LogoutIcon from '@mui/icons-material/Logout'
import { useAuth } from '../context/AuthContext'
import {
  deleteTripForCurrentUser,
  leaveTripAsMember,
  removeMemberFromTrip,
  setMemberAdminRole,
  subscribeTripMembers,
} from '../services/tripService'
import type { Trip, TripMember } from '../types/models'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { textColorForSeed, letterFromName } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'
import { formatTripDates } from '../lib/tripUtils'
import { TripTabScaffold } from '../components/TripTabScaffold'

export default function GroupsPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const { firebaseUser, refreshUser } = useAuth()
  const navigate = useNavigate()
  const [members, setMembers] = useState<TripMember[]>([])
  const [menuEl, setMenuEl] = useState<null | HTMLElement>(null)
  const [transferOpen, setTransferOpen] = useState(false)
  const [transferPick, setTransferPick] = useState<string>('')
  const [transferList, setTransferList] = useState<TripMember[]>([])
  const [memberMenu, setMemberMenu] = useState<null | { anchor: HTMLElement; member: TripMember }>(null)

  const canManageTrip = useMemo(() => {
    if (!firebaseUser) return false
    if (trip.adminId === firebaseUser.uid) return true
    return members.some((m) => m.userId === firebaseUser.uid && m.isAdmin)
  }, [firebaseUser, members, trip.adminId])

  useEffect(() => {
    if (!tripId) return
    return subscribeTripMembers(tripId, setMembers)
  }, [tripId])

  async function onMakeCoOrganiser(m: TripMember) {
    if (!tripId || !firebaseUser) return
    try {
      await setMemberAdminRole(tripId, m.userId, true, firebaseUser.uid, trip.adminId)
    } catch (e: unknown) {
      window.alert(e instanceof Error ? e.message : 'Could not update role')
    }
  }

  async function onRemoveCoOrganiser(m: TripMember) {
    if (!tripId || !firebaseUser) return
    try {
      await setMemberAdminRole(tripId, m.userId, false, firebaseUser.uid, trip.adminId)
    } catch (e: unknown) {
      window.alert(e instanceof Error ? e.message : 'Could not update role')
    }
  }

  async function onRemoveMemberRow(m: TripMember) {
    if (!tripId || !firebaseUser) return
    const name = m.name?.trim() || m.userId
    if (!window.confirm(`Remove ${name} from this trip?`)) return
    try {
      await removeMemberFromTrip(tripId, m.userId, firebaseUser.uid, trip.adminId)
    } catch (e: unknown) {
      window.alert(e instanceof Error ? e.message : 'Could not remove member')
    }
  }

  function canShowMemberMenu(m: TripMember): boolean {
    if (!firebaseUser) return false
    return (
      firebaseUser.uid === trip.adminId &&
      m.userId !== trip.adminId &&
      m.userId !== firebaseUser.uid
    )
  }
  async function onInviteShare() {
    const code = trip.joinCode
    const text = `Join my Tripoo trip! Code: ${code}`
    try {
      if (navigator.share) {
        await navigator.share({ text })
      } else {
        await navigator.clipboard.writeText(text)
        window.alert('Invite text copied to clipboard')
      }
    } catch {
      /* user cancelled share */
    }
  }

  async function onConfirmDelete() {
    if (!firebaseUser) return
    if (!window.confirm('Delete trip? This will permanently delete the trip for everyone.')) return
    try {
      await deleteTripForCurrentUser(trip.id, firebaseUser.uid, trip.adminId)
      await refreshUser()
      navigate('/dashboard', { replace: true })
    } catch (e: unknown) {
      window.alert(e instanceof Error ? e.message : 'Delete failed')
    }
  }

  async function tryLeave() {
    if (!tripId || !firebaseUser) return
    if (!window.confirm('Leave this trip? You can rejoin with the code if invited again.')) return
    const r = await leaveTripAsMember(tripId, firebaseUser.uid, trip, members)
    if (!r.ok && r.reason === 'last_member') {
      window.alert('You are the only member. Delete the trip or invite someone before leaving.')
      return
    }
    if (!r.ok && r.reason === 'need_transfer') {
      setTransferList(r.candidates)
      setTransferPick(r.candidates[0]?.userId ?? '')
      setTransferOpen(true)
      setMenuEl(null)
      return
    }
    await refreshUser()
    navigate('/dashboard', { replace: true })
  }

  async function confirmTransferAndLeave() {
    if (!tripId || !firebaseUser || !transferPick) return
    const r = await leaveTripAsMember(tripId, firebaseUser.uid, trip, members, transferPick)
    if (!r.ok) {
      window.alert('Could not leave trip. Try again.')
      return
    }
    setTransferOpen(false)
    await refreshUser()
    navigate('/dashboard', { replace: true })
  }

  const subtitle = `${members.length} PARTICIPANTS · ${formatTripDates(trip.startDate, trip.endDate).toUpperCase()}`
  const base = `/trips/${tripId}`
  const isOrganiser = Boolean(firebaseUser && trip.adminId === firebaseUser.uid)

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
        onClick={() => navigate('/dashboard')}
        sx={{
          width: 36,
          height: 36,
          bgcolor: 'rgba(244, 140, 37, 0.12)',
          color: tripooColors.orange,
          '&:hover': { bgcolor: 'rgba(244, 140, 37, 0.18)' },
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
      <IconButton sx={{ width: 36, height: 36 }} onClick={(e) => setMenuEl(e.currentTarget)} aria-label="More">
        <MoreHorizIcon sx={{ color: tripooColors.textPrimary }} />
      </IconButton>
      <Menu anchorEl={menuEl} open={Boolean(menuEl)} onClose={() => setMenuEl(null)}>
        {canManageTrip && (
          <MenuItem
            onClick={() => {
              setMenuEl(null)
              navigate(`${base}?edit=1`)
            }}
          >
            <EditIcon sx={{ fontSize: 18, mr: 1 }} /> Edit trip
          </MenuItem>
        )}
        {isOrganiser ? (
          <MenuItem
            onClick={() => {
              setMenuEl(null)
              void onConfirmDelete()
            }}
          >
            <DeleteOutlineIcon sx={{ fontSize: 18, mr: 1 }} /> Delete trip
          </MenuItem>
        ) : null}
      </Menu>
    </Box>
  )

  return (
    <>
      <TripTabScaffold header={header}>
        <Box sx={{ px: 2, pt: 1.6, pb: 3 }}>
          <Card
            sx={{
              borderRadius: '14px',
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
                <Typography sx={{ fontWeight: 900, fontSize: 26, letterSpacing: 3.5, mt: 0.35 }}>
                  {trip.joinCode}
                </Typography>
              </Box>
              <Button
                variant="contained"
                size="small"
                startIcon={<ContentCopyIcon sx={{ fontSize: 18 }} />}
                onClick={() => void navigator.clipboard.writeText(trip.joinCode)}
                sx={{
                  bgcolor: 'rgba(255,255,255,0.2)',
                  color: '#fff',
                  fontWeight: 800,
                  textTransform: 'none',
                  borderRadius: 1,
                  boxShadow: 'none',
                  '&:hover': { bgcolor: 'rgba(255,255,255,0.3)' },
                }}
              >
                Copy
              </Button>
            </Stack>
          </Card>

          <Stack direction="row" alignItems="center" sx={{ mb: 1 }}>
            <Typography sx={{ flex: 1, fontWeight: 800, fontSize: 14, color: tripooColors.textPrimary }}>
              Members
            </Typography>
            <Stack
              direction="row"
              alignItems="center"
              onClick={() => void onInviteShare()}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  void onInviteShare()
                }
              }}
              sx={{
                cursor: 'pointer',
                borderRadius: 99,
                px: 1.4,
                py: 0.5,
                border: `1px solid rgba(244, 140, 37, 0.45)`,
                bgcolor: '#FFF7F0',
                '&:hover': { bgcolor: '#FFECD9' },
              }}
            >
              <PersonAddIcon sx={{ fontSize: 14, color: tripooColors.orange }} />
              <Typography sx={{ ml: 0.5, fontSize: 11, fontWeight: 800, color: tripooColors.orange }}>Invite</Typography>
            </Stack>
          </Stack>

          <Box sx={{ bgcolor: tripooColors.surface }}>
            <List sx={{ py: 0 }}>
              {members.map((m) => {
                const src = photoSrcForDisplay(m.photoUrl)
                const letter = m.avatarLetter?.trim() || letterFromName(m.name)
                const bg = m.avatarColorHex?.trim() || tripooColors.orange
                const tc = textColorForSeed(m.userId)
                const rowIsOrganiser = m.userId === trip.adminId
                const isCoAdmin = m.isAdmin && !rowIsOrganiser
                let roleLabel = 'Member'
                if (rowIsOrganiser) roleLabel = 'Organiser'
                else if (isCoAdmin) roleLabel = 'Co-organiser'
                return (
                  <ListItem
                    key={m.userId}
                    divider
                    secondaryAction={
                      <Stack direction="row" alignItems="center" spacing={0.25}>
                        <Typography variant="caption" sx={{ color: tripooColors.textSecondary, maxWidth: 88 }} noWrap>
                          {roleLabel}
                        </Typography>
                        {canShowMemberMenu(m) ? (
                          <IconButton
                            edge="end"
                            size="small"
                            aria-label="Member options"
                            onClick={(e) => setMemberMenu({ anchor: e.currentTarget, member: m })}
                          >
                            <MoreHorizIcon sx={{ fontSize: 20 }} />
                          </IconButton>
                        ) : null}
                      </Stack>
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

          <Button
            fullWidth
            variant="outlined"
            color="error"
            startIcon={<LogoutIcon />}
            onClick={() => void tryLeave()}
            sx={{ mt: 2, py: 1.15, fontWeight: 800, borderRadius: 1.5, borderWidth: 2 }}
          >
            Leave Trip
          </Button>
        </Box>
      </TripTabScaffold>

      <Dialog open={transferOpen} onClose={() => setTransferOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Choose new organiser</DialogTitle>
        <DialogContent>
          <Typography sx={{ fontSize: 13, color: tripooColors.textSecondary, mb: 1.5 }}>
            You must transfer the organiser role before leaving.
          </Typography>
          <RadioGroup value={transferPick} onChange={(e) => setTransferPick(e.target.value)}>
            {transferList.map((m) => (
              <FormControlLabel key={m.userId} value={m.userId} control={<Radio color="primary" />} label={m.name} />
            ))}
          </RadioGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTransferOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={!transferPick} onClick={() => void confirmTransferAndLeave()}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      <Menu
        anchorEl={memberMenu?.anchor ?? null}
        open={Boolean(memberMenu)}
        onClose={() => setMemberMenu(null)}
      >
        {memberMenu?.member ? (
          <>
            {memberMenu.member.isAdmin && memberMenu.member.userId !== trip.adminId ? (
              <MenuItem
                onClick={() => {
                  const mm = memberMenu.member
                  setMemberMenu(null)
                  void onRemoveCoOrganiser(mm)
                }}
              >
                Remove co-organiser
              </MenuItem>
            ) : !memberMenu.member.isAdmin ? (
              <MenuItem
                onClick={() => {
                  const mm = memberMenu.member
                  setMemberMenu(null)
                  void onMakeCoOrganiser(mm)
                }}
              >
                Make co-organiser
              </MenuItem>
            ) : null}
            <MenuItem
              onClick={() => {
                const mm = memberMenu.member
                setMemberMenu(null)
                void onRemoveMemberRow(mm)
              }}
            >
              Remove from trip
            </MenuItem>
          </>
        ) : null}
      </Menu>
    </>
  )
}
