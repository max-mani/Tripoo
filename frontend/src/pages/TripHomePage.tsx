import { useEffect, useMemo, useState } from 'react'
import {
  Box,
  Button,
  Card,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  Menu,
  MenuItem,
  Stack,
  TextField,
  Typography,
  Alert,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
import EditIcon from '@mui/icons-material/Edit'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import ScheduleIcon from '@mui/icons-material/Schedule'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import AssignmentIcon from '@mui/icons-material/Assignment'
import GroupIcon from '@mui/icons-material/Group'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import LocationOnIcon from '@mui/icons-material/LocationOn'
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet'
import { useNavigate, useOutletContext, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  canUserManageTripAsLeader,
  deleteTripForCurrentUser,
  updateTripDetails,
} from '../services/tripService'
import { subscribeExpenses } from '../services/expenseService'
import type { Expense, Trip } from '../types/models'
import { deriveStatus, formatTripDates } from '../lib/tripUtils'
import { openDestinationInMaps } from '../lib/mapsOpen'
import { tripooColors } from '../theme'
import { TripTabScaffold } from '../components/TripTabScaffold'
import { formatInrFull } from '../lib/inrFormat'

function useCountdownRemaining(targetMs: number | null) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (targetMs == null) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [targetMs])
  if (targetMs == null) return null
  return Math.max(0, targetMs - now)
}

function splitDhms(ms: number) {
  const sec = Math.floor(ms / 1000)
  const d = Math.floor(sec / 86400)
  const h = Math.floor((sec % 86400) / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return { d, h, m, s }
}

function countdownTitle(live: string): string {
  if (live === 'upcoming') return 'Countdown to Adventure'
  if (live === 'active') return 'Adventure in Progress'
  return 'Time Since Adventure'
}

export default function TripHomePage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { firebaseUser, refreshUser } = useAuth()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [expenses, setExpenses] = useState<Expense[]>([])
  const [canManage, setCanManage] = useState(false)
  const [open, setOpen] = useState(false)
  const [menuEl, setMenuEl] = useState<null | HTMLElement>(null)
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
    return subscribeExpenses(trip.id, setExpenses)
  }, [trip.id])

  useEffect(() => {
    if (!firebaseUser) return
    void canUserManageTripAsLeader(trip.id, firebaseUser.uid).then(setCanManage)
  }, [trip.id, firebaseUser])

  useEffect(() => {
    setName(trip.name)
    setDestination(trip.destination)
    setDescription(trip.description)
    setBudget(String(trip.budget))
    setStart(trip.startDate ? new Date(trip.startDate).toISOString().slice(0, 10) : '')
    setEnd(trip.endDate ? new Date(trip.endDate).toISOString().slice(0, 10) : '')
  }, [trip])

  useEffect(() => {
    if (searchParams.get('edit') !== '1' || !canManage) return
    setOpen(true)
    const next = new URLSearchParams(searchParams)
    next.delete('edit')
    setSearchParams(next, { replace: true })
  }, [canManage, searchParams, setSearchParams])

  const live = deriveStatus(trip.startDate, trip.endDate)
  const isOrganiser = Boolean(firebaseUser && trip.adminId === firebaseUser.uid)
  const totalSpent = useMemo(() => expenses.reduce((a, e) => a + e.amount, 0), [expenses])
  const budgetNum = trip.budget > 0 ? trip.budget : 0
  const spentRatio = budgetNum > 0 ? Math.min(1, totalSpent / budgetNum) : 0
  const remainingPct = budgetNum > 0 ? Math.max(0, Math.round((1 - spentRatio) * 100)) : 0

  const countdownTarget =
    live === 'upcoming' ? trip.startDate : live === 'active' ? trip.endDate : null
  const remainMs = useCountdownRemaining(countdownTarget)
  const dhms = remainMs != null ? splitDhms(remainMs) : null

  const dateSubtitle = formatTripDates(trip.startDate, trip.endDate).toUpperCase()
  const base = `/trips/${trip.id}`

  async function saveEdit() {
    setErr(null)
    const startMs = start ? new Date(start).getTime() : trip.startDate
    const endMs = end ? new Date(end).getTime() : trip.endDate
    try {
      await updateTripDetails(trip.id, {
        name: name.trim(),
        destination: destination.trim(),
        description: description.trim(),
        startDate: startMs,
        endDate: endMs,
        budget: Number(budget) || 0,
      })
      setOpen(false)
      setMenuEl(null)
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Update failed')
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
      setErr(e instanceof Error ? e.message : 'Delete failed')
    }
  }

  function onViewMaps() {
    const dest = trip.destination?.trim()
    if (!dest) return
    openDestinationInMaps(dest)
  }

  const header = (
    <Box
      sx={{
        bgcolor: tripooColors.surface,
        px: 2,
        pt: `calc(12px + env(safe-area-inset-top, 0px))`,
        pb: 1.5,
        borderBottom: `1px solid ${tripooColors.border}`,
        boxShadow: '0 2px 6px rgba(24,20,17,0.04)',
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
          color: tripooColors.orange,
          '&:hover': { bgcolor: 'rgba(244, 140, 37, 0.08)' },
        }}
        aria-label="Back"
      >
        <ArrowBackIcon sx={{ fontSize: 20 }} />
      </IconButton>
      <Box sx={{ flex: 1, textAlign: 'center', minWidth: 0 }}>
        <Typography sx={{ fontWeight: 800, fontSize: 17, lineHeight: 1.2 }} noWrap>
          {trip.name}
        </Typography>
        <Typography sx={{ fontSize: 11, fontWeight: 800, color: tripooColors.orange, mt: 0.35 }} noWrap>
          {dateSubtitle}
        </Typography>
      </Box>
      <IconButton
        onClick={(e) => setMenuEl(e.currentTarget)}
        sx={{ width: 36, height: 36 }}
        aria-label="More"
      >
        <MoreHorizIcon sx={{ color: tripooColors.textPrimary }} />
      </IconButton>
      <Menu anchorEl={menuEl} open={Boolean(menuEl)} onClose={() => setMenuEl(null)}>
        {canManage ? (
          <MenuItem
            onClick={() => {
              setMenuEl(null)
              setOpen(true)
            }}
          >
            <EditIcon sx={{ fontSize: 18, mr: 1 }} /> Edit trip
          </MenuItem>
        ) : null}
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
        <Box sx={{ px: 2, pt: 1.75 }}>
          <Box
            sx={{
              borderRadius: '14px',
              bgcolor: tripooColors.orange,
              position: 'relative',
              overflow: 'hidden',
              mb: 1.5,
            }}
          >
            <Box
              sx={{
                position: 'absolute',
                right: -24,
                top: -24,
                width: 110,
                height: 110,
                opacity: 0.1,
                backgroundImage: 'radial-gradient(circle at 30% 30%, #fff 0%, transparent 55%)',
                pointerEvents: 'none',
              }}
            />
            <Box sx={{ px: 2.25, py: 2.1, position: 'relative' }}>
              <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.75 }}>
                <ScheduleIcon sx={{ fontSize: 16, color: 'rgba(255,255,255,0.88)' }} />
                <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.88)', fontWeight: 600 }}>
                  {countdownTitle(live)}
                </Typography>
              </Stack>
              <Typography sx={{ fontWeight: 800, fontSize: 15, color: tripooColors.surface, mb: 0.75 }}>
                {trip.destination?.trim() || trip.name}
              </Typography>
              {trip.description?.trim() ? (
                <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.85)', lineHeight: 1.5, mb: 1 }}>
                  {trip.description.trim()}
                </Typography>
              ) : null}
              {dhms && live !== 'past' ? (
                <Stack direction="row" spacing={1.25} sx={{ mt: 1 }}>
                  {(
                    [
                      [dhms.d, 'DAYS'],
                      [dhms.h, 'HOURS'],
                      [dhms.m, 'MINS'],
                      [dhms.s, 'SECS'],
                    ] as const
                  ).map(([v, lab]) => (
                    <Box
                      key={lab}
                      sx={{
                        flex: 1,
                        py: 1.15,
                        borderRadius: '12px',
                        bgcolor: 'rgba(255,255,255,0.13)',
                        textAlign: 'center',
                      }}
                    >
                      <Typography sx={{ fontWeight: 900, fontSize: 18, color: tripooColors.surface }}>
                        {v}
                      </Typography>
                      <Typography
                        sx={{
                          fontSize: 10,
                          fontWeight: 800,
                          color: 'rgba(255,255,255,0.88)',
                          mt: 0.35,
                        }}
                      >
                        {lab}
                      </Typography>
                    </Box>
                  ))}
                </Stack>
              ) : null}
              {live === 'past' ? (
                <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.88)' }}>
                  This trip has ended. You can still review expenses and tasks.
                </Typography>
              ) : null}
            </Box>
          </Box>

          <Card
            variant="outlined"
            sx={{
              borderRadius: '14px',
              borderColor: tripooColors.border,
              boxShadow: 'none',
              mb: 1.5,
            }}
          >
            <Box sx={{ p: 2 }}>
              <Stack direction="row" alignItems="center" spacing={1.25} sx={{ mb: 1.5 }}>
                <Box
                  sx={{
                    width: 38,
                    height: 38,
                    borderRadius: 1.5,
                    bgcolor: '#FFF3E6',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <AccountBalanceWalletIcon sx={{ color: tripooColors.orange, fontSize: 22 }} />
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Typography sx={{ fontWeight: 800, fontSize: 14 }}>Group Budget Status</Typography>
                  <Typography sx={{ fontSize: 10, color: tripooColors.textSecondary }}>
                    Shared across {trip.memberIds.length} people
                  </Typography>
                </Box>
                <Typography sx={{ fontWeight: 800, fontSize: 13, color: tripooColors.orange }}>
                  {formatInrFull(totalSpent)}
                  {budgetNum > 0 ? ` / ${formatInrFull(budgetNum)}` : ''}
                </Typography>
              </Stack>
              {budgetNum > 0 ? (
                <>
                  <LinearProgress
                    variant="determinate"
                    value={spentRatio * 100}
                    sx={{
                      height: 10,
                      borderRadius: 99,
                      bgcolor: '#E8E4DF',
                      mb: 1,
                      '& .MuiLinearProgress-bar': {
                        borderRadius: 99,
                        bgcolor: tripooColors.orange,
                      },
                    }}
                  />
                  <Stack direction="row" alignItems="center" spacing={0.5}>
                    <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary, flex: 1 }}>
                      {remainingPct}% remaining
                    </Typography>
                    <Box
                      role="button"
                      tabIndex={0}
                      onClick={() => navigate(`${base}/expenses`)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault()
                          navigate(`${base}/expenses`)
                        }
                      }}
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        cursor: 'pointer',
                      }}
                    >
                      <Typography sx={{ fontSize: 11, fontWeight: 800, color: tripooColors.orange }}>
                        Details
                      </Typography>
                      <ChevronRightIcon sx={{ fontSize: 16, color: tripooColors.orange }} />
                    </Box>
                  </Stack>
                </>
              ) : (
                <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary }}>
                  Set a budget when editing the trip to track spending.
                </Typography>
              )}
            </Box>
          </Card>

          <Card
            variant="outlined"
            sx={{
              borderRadius: '14px',
              borderColor: tripooColors.border,
              boxShadow: 'none',
              mb: 1.75,
              overflow: 'hidden',
            }}
          >
            <Stack
              direction="row"
              alignItems="center"
              sx={{ px: 1.75, py: 1.5, bgcolor: tripooColors.surface }}
            >
              <Typography sx={{ flex: 1, fontWeight: 800, fontSize: 13, color: tripooColors.textPrimary }}>
                {trip.destination?.trim()
                  ? `Destination: ${trip.destination.trim()}`
                  : 'Destination: Add in trip settings'}
              </Typography>
              <Typography sx={{ fontSize: 11, fontWeight: 800, color: tripooColors.orange }}>Location</Typography>
            </Stack>
            <Box
              sx={{
                height: 128,
                background: 'linear-gradient(140deg, #B8D4E8 0%, #7EA8C0 45%, #5B8FA8 100%)',
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Box
                role="button"
                tabIndex={0}
                onClick={onViewMaps}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault()
                    onViewMaps()
                  }
                }}
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.75,
                  px: 1.5,
                  py: 0.75,
                  borderRadius: 99,
                  bgcolor: 'rgba(255,255,255,0.92)',
                  cursor: trip.destination?.trim() ? 'pointer' : 'default',
                  opacity: trip.destination?.trim() ? 1 : 0.5,
                  pointerEvents: trip.destination?.trim() ? 'auto' : 'none',
                }}
              >
                <LocationOnIcon sx={{ color: tripooColors.orange, fontSize: 18 }} />
                <Typography sx={{ fontSize: 11, fontWeight: 800, color: tripooColors.textPrimary }}>
                  View in Maps
                </Typography>
              </Box>
            </Box>
            <Box sx={{ height: 12 }} />
          </Card>

          <Typography
            sx={{
              fontSize: 10,
              fontWeight: 800,
              letterSpacing: 1.2,
              color: tripooColors.textSecondary,
              mb: 1.25,
            }}
          >
            QUICK ACCESS
          </Typography>

          <Stack direction="row" spacing={1.15}>
            {[
              {
                label: 'Expenses',
                icon: <ReceiptLongIcon sx={{ fontSize: 20, color: tripooColors.orange }} />,
                to: `${base}/expenses`,
              },
              {
                label: 'To-Do List',
                icon: <AssignmentIcon sx={{ fontSize: 20, color: tripooColors.orange }} />,
                to: `${base}/tasks`,
              },
              {
                label: 'Participants',
                icon: <GroupIcon sx={{ fontSize: 20, color: tripooColors.orange }} />,
                to: `${base}/groups`,
              },
            ].map((qa) => (
              <Card
                key={qa.label}
                variant="outlined"
                onClick={() => navigate(qa.to)}
                sx={{
                  flex: 1,
                  borderRadius: '12px',
                  borderColor: tripooColors.border,
                  cursor: 'pointer',
                  boxShadow: 'none',
                  '&:hover': { bgcolor: 'rgba(244,140,37,0.04)' },
                }}
              >
                <Stack alignItems="center" sx={{ py: 1.6 }}>
                  <Box
                    sx={{
                      width: 42,
                      height: 42,
                      borderRadius: '50%',
                      bgcolor: '#FDE7D2',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {qa.icon}
                  </Box>
                  <Typography sx={{ fontSize: 10, fontWeight: 800, color: tripooColors.textPrimary, mt: 1 }}>
                    {qa.label}
                  </Typography>
                </Stack>
              </Card>
            ))}
          </Stack>
        </Box>
      </TripTabScaffold>

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
            <TextField label="Budget" type="number" fullWidth value={budget} onChange={(e) => setBudget(e.target.value)} />
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
