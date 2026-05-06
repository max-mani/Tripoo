import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
  Chip,
  Divider,
  Card,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
import TrendingDownIcon from '@mui/icons-material/TrendingDown'
import TrendingUpIcon from '@mui/icons-material/TrendingUp'
import { useAuth } from '../context/AuthContext'
import {
  addExpense,
  deleteExpense,
  markExpenseSettled,
  subscribeExpenses,
  updateExpense,
} from '../services/expenseService'
import { canUserManageTripAsLeader, subscribeTripMembers } from '../services/tripService'
import type { Expense, Trip, TripMember } from '../types/models'
import { categoryMeta, EXPENSE_CATEGORIES } from '../lib/constants'
import { tripooColors } from '../theme'
import { formatTripDates } from '../lib/tripUtils'
import { computeYouOweYouAreOwed } from '../lib/expenseBalances'
import { formatInrFull } from '../lib/inrFormat'
import { TripTabScaffold } from '../components/TripTabScaffold'

export default function ExpensesPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
  const [expenses, setExpenses] = useState<Expense[]>([])
  const [members, setMembers] = useState<TripMember[]>([])
  const [canManage, setCanManage] = useState(false)
  const [open, setOpen] = useState(false)
  const [edit, setEdit] = useState<Expense | null>(null)
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const [category, setCategory] = useState('other')
  const [paidBy, setPaidBy] = useState('')
  const [splitWith, setSplitWith] = useState<string[]>([])
  const [q, setQ] = useState('')

  useEffect(() => {
    if (!tripId) return
    return subscribeExpenses(tripId, setExpenses)
  }, [tripId])

  useEffect(() => {
    if (!tripId) return
    return subscribeTripMembers(tripId, setMembers)
  }, [tripId])

  useEffect(() => {
    if (!tripId || !firebaseUser) return
    void canUserManageTripAsLeader(tripId, firebaseUser.uid).then(setCanManage)
  }, [tripId, firebaseUser])

  useEffect(() => {
    if (!firebaseUser) return
    if (members.length && !paidBy) setPaidBy(firebaseUser.uid)
  }, [members, firebaseUser, paidBy])

  const memberById = useMemo(() => new Map(members.map((m) => [m.userId, m])), [members])

  const balances = useMemo(() => {
    if (!firebaseUser) return { youOwe: 0, youAreOwed: 0 }
    return computeYouOweYouAreOwed(expenses, firebaseUser.uid)
  }, [expenses, firebaseUser])

  const filtered = useMemo(() => {
    const s = q.trim().toLowerCase()
    if (!s) return expenses
    return expenses.filter((e) => e.title.toLowerCase().includes(s))
  }, [expenses, q])

  function openNew() {
    setEdit(null)
    setTitle('')
    setAmount('')
    setCategory('other')
    setPaidBy(firebaseUser?.uid ?? '')
    setSplitWith(members.map((m) => m.userId))
    setOpen(true)
  }

  function openEdit(e: Expense) {
    setEdit(e)
    setTitle(e.title)
    setAmount(String(e.amount))
    setCategory(e.category)
    setPaidBy(e.paidBy)
    setSplitWith([...e.splitWith])
    setOpen(true)
  }

  function toggleSplit(uid: string) {
    setSplitWith((prev) => (prev.includes(uid) ? prev.filter((x) => x !== uid) : [...prev, uid]))
  }

  async function saveExpense() {
    if (!tripId) return
    const exp: Omit<Expense, 'id'> = {
      title: title.trim(),
      amount: Number(amount) || 0,
      category,
      paidBy,
      splitWith: splitWith.length ? splitWith : [paidBy],
      timestamp: edit?.timestamp ?? Date.now(),
      settled: edit?.settled ?? false,
    }
    if (!exp.title) return
    if (edit) {
      await updateExpense(tripId, { ...edit, ...exp, id: edit.id })
    } else {
      await addExpense(tripId, exp)
    }
    setOpen(false)
  }

  async function onToggleSettled(e: Expense) {
    if (!tripId || !canManage) return
    await markExpenseSettled(tripId, e.id, !e.settled)
  }

  async function onDelete(e: Expense) {
    if (!tripId) return
    if (!window.confirm('Delete this expense?')) return
    await deleteExpense(tripId, e.id)
    setOpen(false)
  }

  const subtitle = `${members.length} participants · ${formatTripDates(trip.startDate, trip.endDate)}`

  const header = (
    <Box
      sx={{
        bgcolor: tripooColors.surface,
        px: 2,
        pt: `calc(12px + env(safe-area-inset-top, 0px))`,
        pb: 1.5,
        borderBottom: `1px solid ${tripooColors.border}`,
        boxShadow: '0 2px 6px rgba(24,20,17,0.04)',
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1}>
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
          <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }} noWrap>
            {subtitle}
          </Typography>
        </Box>
        <IconButton
          onClick={openNew}
          sx={{
            width: 36,
            height: 36,
            bgcolor: '#FDE7D2',
            color: tripooColors.orange,
            '&:hover': { bgcolor: '#FCD9B8' },
          }}
          aria-label="Add expense"
        >
          <AddIcon />
        </IconButton>
      </Stack>
      <TextField
        size="small"
        fullWidth
        placeholder="Search expenses…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mt: 1.25, '& .MuiOutlinedInput-root': { bgcolor: tripooColors.surface } }}
        InputProps={{
          startAdornment: <SearchIcon sx={{ color: tripooColors.textSecondary, mr: 1, fontSize: 20 }} />,
        }}
      />
    </Box>
  )

  return (
    <>
      <TripTabScaffold header={header}>
        <Box sx={{ px: 1.5, pt: 1.25, pb: 0 }}>
          <Stack direction="row" spacing={0.75} sx={{ mb: 1.25 }}>
            <Card
              variant="outlined"
              sx={{
                flex: 1,
                borderRadius: 1.5,
                bgcolor: '#FEF2F2',
                borderColor: '#FECACA',
                boxShadow: 'none',
              }}
            >
              <Box sx={{ p: 1.6 }}>
                <Typography sx={{ fontSize: 9, fontWeight: 800, color: '#B91C1C', letterSpacing: 0.12 }}>
                  YOU OWE
                </Typography>
                <Typography sx={{ fontWeight: 800, fontSize: 22, mt: 0.35 }}>
                  {formatInrFull(balances.youOwe)}
                </Typography>
                <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 0.6 }}>
                  <TrendingDownIcon sx={{ fontSize: 12, color: tripooColors.red }} />
                  <Typography sx={{ fontSize: 10, color: tripooColors.red }}>unsettled splits</Typography>
                </Stack>
              </Box>
            </Card>
            <Card
              variant="outlined"
              sx={{
                flex: 1,
                borderRadius: 1.5,
                bgcolor: '#FFF7ED',
                borderColor: 'rgba(244, 140, 37, 0.35)',
                boxShadow: 'none',
              }}
            >
              <Box sx={{ p: 1.6 }}>
                <Typography sx={{ fontSize: 9, fontWeight: 800, color: tripooColors.orangeDark, letterSpacing: 0.12 }}>
                  YOU&apos;RE OWED
                </Typography>
                <Typography sx={{ fontWeight: 800, fontSize: 22, mt: 0.35 }}>
                  {formatInrFull(balances.youAreOwed)}
                </Typography>
                <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 0.6 }}>
                  <TrendingUpIcon sx={{ fontSize: 12, color: tripooColors.orange }} />
                  <Typography sx={{ fontSize: 10, color: tripooColors.orange }}>from the group</Typography>
                </Stack>
              </Box>
            </Card>
          </Stack>

          <List sx={{ bgcolor: tripooColors.surface, borderRadius: 2, border: `1px solid ${tripooColors.border}` }}>
            {filtered.length === 0 ? (
              <ListItem>
                <ListItemText
                  primary="No expenses yet"
                  secondary={q ? 'Try a different search' : 'Tap add expense to record spending'}
                />
              </ListItem>
            ) : (
              filtered.map((e) => {
                const meta = categoryMeta(e.category)
                const payer = memberById.get(e.paidBy)?.name ?? 'Someone'
                return (
                  <Box key={e.id}>
                    <ListItem
                      sx={{ alignItems: 'flex-start' }}
                      secondaryAction={
                        canManage && !e.settled ? (
                          <Button size="small" onClick={() => void onToggleSettled(e)}>
                            Settle
                          </Button>
                        ) : null
                      }
                    >
                      <Box
                        sx={{
                          width: 40,
                          height: 40,
                          borderRadius: '50%',
                          bgcolor: meta.bg,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: meta.tint,
                          mr: 1.5,
                          fontWeight: 700,
                        }}
                      >
                        {e.category[0]!.toUpperCase()}
                      </Box>
                      <ListItemText
                        primary={
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <Typography sx={{ fontWeight: 700 }}>{e.title}</Typography>
                            {e.settled && <Chip size="small" label="Settled" color="success" />}
                          </Stack>
                        }
                        secondary={
                          <>
                            <Typography variant="body2" component="span" sx={{ display: 'block' }}>
                              {formatInrFull(e.amount)} · Paid by {payer}
                            </Typography>
                            <Button size="small" onClick={() => openEdit(e)}>
                              Edit
                            </Button>
                          </>
                        }
                      />
                    </ListItem>
                    <Divider component="li" />
                  </Box>
                )
              })
            )}
          </List>
        </Box>
      </TripTabScaffold>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{edit ? 'Edit expense' : 'New expense'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Title" fullWidth value={title} onChange={(e) => setTitle(e.target.value)} />
            <TextField label="Amount" type="number" fullWidth value={amount} onChange={(e) => setAmount(e.target.value)} />
            <Typography variant="subtitle2">Category</Typography>
            <Stack direction="row" flexWrap="wrap" gap={1}>
              {EXPENSE_CATEGORIES.map((c) => (
                <Chip
                  key={c.key}
                  label={c.label}
                  onClick={() => setCategory(c.key)}
                  color={category === c.key ? 'primary' : 'default'}
                  variant={category === c.key ? 'filled' : 'outlined'}
                />
              ))}
            </Stack>
            <TextField
              label="Paid by"
              fullWidth
              select
              SelectProps={{ native: true }}
              value={paidBy}
              onChange={(e) => setPaidBy(e.target.value)}
            >
              {members.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.name}
                </option>
              ))}
            </TextField>
            <Typography variant="subtitle2">Split with</Typography>
            <FormGroup>
              {members.map((m) => (
                <FormControlLabel
                  key={m.userId}
                  control={
                    <Checkbox
                      checked={splitWith.includes(m.userId)}
                      onChange={() => toggleSplit(m.userId)}
                    />
                  }
                  label={m.name}
                />
              ))}
            </FormGroup>
          </Stack>
        </DialogContent>
        <DialogActions>
          {edit && (
            <Button color="error" onClick={() => void onDelete(edit)}>
              Delete
            </Button>
          )}
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveExpense()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
