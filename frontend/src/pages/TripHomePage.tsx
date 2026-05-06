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
import ScheduleIcon from '@mui/icons-material/Schedule'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { canUserManageTripAsLeader, updateTripDetails } from '../services/tripService'
import { subscribeExpenses } from '../services/expenseService'
import type { Expense, Trip } from '../types/models'
import { deriveStatus, formatTripDates, statusLabel } from '../lib/tripUtils'
import { tripooColors } from '../theme'
import { TripTabScaffold } from '../components/TripTabScaffold'
import { formatInrFull } from '../lib/inrFormat'

function useCountdownRemaining(targetMs: number | null) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (targetMs == null) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
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

export default function TripHomePage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
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

  const live = deriveStatus(trip.startDate, trip.endDate)
  const totalSpent = useMemo(() => expenses.reduce((a, e) => a + e.amount, 0), [expenses])
  const budgetNum = trip.budget > 0 ? trip.budget : 0
  const spentRatio = budgetNum > 0 ? Math.min(1, totalSpent / budgetNum) : 0
  const remainingPct = budgetNum > 0 ? Math.max(0, Math.round((1 - spentRatio) * 100)) : 0

  const countdownTarget =
    live === 'upcoming' ? trip.startDate : live === 'active' ? trip.endDate : null
  const remainMs = useCountdownRemaining(countdownTarget)
  const dhms = remainMs != null ? splitDhms(remainMs) : null

  const headerChip =
    live === 'active'
      ? { bg: '#E8F5EA', color: '#2A5E35', label: statusLabel(live) }
      : live === 'upcoming'
        ? { bg: '#DDEEFF', color: '#1A5FA8', label: statusLabel(live) }
        : { bg: '#F3F4F6', color: '#6B7280', label: statusLabel(live) }

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
          bgcolor: '#FDE7D2',
          color: tripooColors.orange,
          '&:hover': { bgcolor: '#FCD9B8' },
        }}
        aria-label="Back"
      >
        <ArrowBackIcon sx={{ fontSize: 20 }} />
      </IconButton>
      <Box sx={{ flex: 1, textAlign: 'center', minWidth: 0 }}>
        <Typography sx={{ fontWeight: 800, fontSize: 17, lineHeight: 1.2 }} noWrap>
          {trip.name}
        </Typography>
        <Typography sx={{ fontSize: 11, fontWeight: 700, color: tripooColors.orange, mt: 0.35 }} noWrap>
          {formatTripDates(trip.startDate, trip.endDate)}
        </Typography>
      </Box>
      <IconButton
        onClick={(e) => setMenuEl(e.currentTarget)}
        sx={{ width: 36, height: 36 }}
        aria-label="More"
      >
        <MoreHorizIcon />
      </IconButton>
      <Menu anchorEl={menuEl} open={Boolean(menuEl)} onClose={() => setMenuEl(null)}>
        {canManage && (
          <MenuItem
            onClick={() => {
              setMenuEl(null)
              setOpen(true)
            }}
          >
            <EditIcon sx={{ fontSize: 18, mr: 1 }} /> Edit trip
          </MenuItem>
        )}
        <MenuItem
          onClick={() => {
            setMenuEl(null)
            void navigator.clipboard.writeText(trip.joinCode)
          }}
        >
          Copy join code
        </MenuItem>
      </Menu>
    </Box>
  )

  return (
    <>
      <TripTabScaffold header={header}>
        <Box sx={{ px: 2, pt: 1.75 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
            <Box
              sx={{
                px: 1,
                py: 0.25,
                borderRadius: 1,
                bgcolor: headerChip.bg,
                color: headerChip.color,
                fontWeight: 700,
                fontSize: 11,
              }}
            >
              {headerChip.label}
            </Box>
            <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>
              Code: {trip.joinCode}
            </Typography>
          </Stack>

          <Box
            sx={{
              borderRadius: 2,
              background: `linear-gradient(135deg, ${tripooColors.orange} 0%, ${tripooColors.orangeDark} 100%)`,
              p: 2.25,
              position: 'relative',
              overflow: 'hidden',
              mb: 1.5,
            }}
          >
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.75 }}>
              <ScheduleIcon sx={{ fontSize: 16, color: 'rgba(255,255,255,0.85)' }} />
              <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.85)', fontWeight: 600 }}>
                {live === 'upcoming'
                  ? 'Trip starts in'
                  : live === 'active'
                    ? 'Trip ends in'
                    : 'Trip recap'}
              </Typography>
            </Stack>
            <Typography sx={{ fontWeight: 800, fontSize: 15, color: '#fff', mb: 0.75 }}>
              {trip.destination || trip.name}
            </Typography>
            {trip.description ? (
              <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.88)', lineHeight: 1.5, mb: 1 }}>
                {trip.description}
              </Typography>
            ) : null}
            {dhms && live !== 'past' ? (
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                {(
                  [
                    ['d', dhms.d, 'DAYS'],
                    ['h', dhms.h, 'HOURS'],
                    ['m', dhms.m, 'MINS'],
                    ['s', dhms.s, 'SECS'],
                  ] as const
                ).map(([k, v, lab]) => (
                  <Box
                    key={k}
                    sx={{
                      flex: 1,
                      py: 1.2,
                      borderRadius: 1,
                      bgcolor: 'rgba(255,255,255,0.14)',
                      textAlign: 'center',
                    }}
                  >
                    <Typography sx={{ fontWeight: 900, fontSize: 18, color: '#fff' }}>{v}</Typography>
                    <Typography sx={{ fontSize: 10, fontWeight: 800, color: 'rgba(255,255,255,0.8)', mt: 0.35 }}>
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

          <Card variant="outlined" sx={{ borderRadius: 2, borderColor: tripooColors.border, boxShadow: 'none' }}>
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
                  <Typography sx={{ fontWeight: 900, color: tripooColors.orange }}>₹</Typography>
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
                  <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>
                    {remainingPct}% remaining
                  </Typography>
                </>
              ) : (
                <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary }}>
                  Set a budget when editing the trip to track spending.
                </Typography>
              )}
            </Box>
          </Card>
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
