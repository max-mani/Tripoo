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
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
  Chip,
  Card,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
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
import { computeYouOweYouAreOwed, computeOweOwedTrends } from '../lib/expenseBalances'
import { TripTabScaffold } from '../components/TripTabScaffold'

const SECTION_BG = '#F9FAFB'

function shortTripDates(startMs: number, endMs: number): string {
  const o = { month: 'short', day: 'numeric' } as const
  const a = new Date(startMs).toLocaleDateString(undefined, o)
  const b = new Date(endMs).toLocaleDateString(undefined, o)
  return `${a}–${b}`
}

function formatRs2(n: number) {
  return `₹${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

type ExpTab = 'all' | 'my' | 'settled' | 'stats'

const TAB_ORDER: { key: ExpTab; label: string }[] = [
  { key: 'all', label: 'All Expenses' },
  { key: 'my', label: 'My Spending' },
  { key: 'settled', label: 'Settled' },
  { key: 'stats', label: 'Stats' },
]

export default function ExpensesPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const { firebaseUser } = useAuth()
  const navigate = useNavigate()
  const [expenses, setExpenses] = useState<Expense[]>([])
  const [members, setMembers] = useState<TripMember[]>([])
  const [canManage, setCanManage] = useState(false)
  const [expTab, setExpTab] = useState<ExpTab>('all')
  const [open, setOpen] = useState(false)
  const [edit, setEdit] = useState<Expense | null>(null)
  const [title, setTitleInput] = useState('')
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

  const trends = useMemo(() => {
    if (!firebaseUser) return { oweTrendPct: 0, owedTrendPct: 0 }
    return computeOweOwedTrends(expenses, firebaseUser.uid)
  }, [expenses, firebaseUser])

  const stats = useMemo(() => {
    const total = expenses.reduce((a, e) => a + e.amount, 0)
    const n = Math.max(1, members.length)
    const avgPerPerson = total / n
    const byCategory = expenses.reduce<Record<string, number>>((m, e) => {
      m[e.category] = (m[e.category] ?? 0) + e.amount
      return m
    }, {})
    const paidByTotals = expenses.reduce<Record<string, number>>((m, e) => {
      m[e.paidBy] = (m[e.paidBy] ?? 0) + e.amount
      return m
    }, {})
    let topUid: string | null = null
    let topAmt = 0
    for (const [uid, amt] of Object.entries(paidByTotals)) {
      if (amt > topAmt) {
        topAmt = amt
        topUid = uid
      }
    }
    const topName = topUid ? memberById.get(topUid)?.name ?? 'Someone' : null
    return { total, avgPerPerson, byCategory, topSpender: topName && topAmt > 0 ? { name: topName, amt: topAmt } : null }
  }, [expenses, members.length, memberById])

  const filteredList = useMemo(() => {
    let list = expenses
    if (expTab === 'my' && firebaseUser) {
      list = list.filter((e) => e.paidBy === firebaseUser.uid || e.splitWith.includes(firebaseUser.uid))
    } else if (expTab === 'settled') {
      list = list.filter((e) => e.settled)
    } else if (expTab === 'stats') {
      return []
    }
    const s = q.trim().toLowerCase()
    if (s) list = list.filter((e) => e.title.toLowerCase().includes(s))
    return list
  }, [expenses, expTab, firebaseUser, q])

  function openNew() {
    setEdit(null)
    setTitleInput('')
    setAmount('')
    setCategory('other')
    setPaidBy(firebaseUser?.uid ?? '')
    setSplitWith(members.map((m) => m.userId))
    setOpen(true)
  }

  function openEdit(e: Expense) {
    setEdit(e)
    setTitleInput(e.title)
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

  const subtitle = `${members.length} participants · ${shortTripDates(trip.startDate, trip.endDate)}`

  const oweArrow = trends.oweTrendPct >= 0 ? '▲' : '▼'
  const owedArrow = trends.owedTrendPct >= 0 ? '▲' : '▼'

  const header = (
    <Box>
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
            <ArrowBackIcon sx={{ fontSize: 20 }} />
          </IconButton>
          <Box sx={{ flex: 1, textAlign: 'center', minWidth: 0 }}>
            <Typography sx={{ fontWeight: 800, fontSize: 17 }} noWrap>
              {trip.name}
            </Typography>
            <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary, mt: 0.1 }} noWrap>
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
            <AddIcon sx={{ fontSize: 20 }} />
          </IconButton>
          <IconButton
            sx={{
              width: 36,
              height: 36,
              bgcolor: '#FDE7D2',
              color: tripooColors.orange,
              '&:hover': { bgcolor: '#FCD9B8' },
            }}
            aria-label="Search"
          >
            <SearchIcon sx={{ fontSize: 20 }} />
          </IconButton>
          <IconButton
            sx={{
              width: 36,
              height: 36,
              marginLeft: '-4px',
              bgcolor: '#FDE7D2',
              color: tripooColors.orange,
              '&:hover': { bgcolor: '#FCD9B8' },
            }}
            aria-label="More"
          >
            <MoreHorizIcon sx={{ fontSize: 20 }} />
          </IconButton>
        </Stack>
      </Box>

      <Stack
        direction="row"
        spacing={0.75}
        sx={{
          px: 1.5,
          py: 1.25,
          bgcolor: tripooColors.surface,
        }}
      >
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
            <Typography sx={{ fontWeight: 800, fontSize: 22, mt: 0.35 }}>{formatRs2(balances.youOwe)}</Typography>
            <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 0.6 }}>
              <TrendingDownIcon sx={{ fontSize: 12, color: tripooColors.red }} />
              <Typography sx={{ fontSize: 10, color: tripooColors.red }}>
                {oweArrow} {Math.abs(trends.oweTrendPct)}% vs last week
              </Typography>
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
              YOU ARE OWED
            </Typography>
            <Typography sx={{ fontWeight: 800, fontSize: 22, mt: 0.35 }}>{formatRs2(balances.youAreOwed)}</Typography>
            <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 0.6 }}>
              <TrendingUpIcon sx={{ fontSize: 12, color: tripooColors.green }} />
              <Typography sx={{ fontSize: 10, color: tripooColors.green }}>
                {owedArrow} {Math.abs(trends.owedTrendPct)}% vs last week
              </Typography>
            </Stack>
          </Box>
        </Card>
      </Stack>

      <Box sx={{ bgcolor: tripooColors.surface, borderBottom: `1px solid ${tripooColors.border}` }}>
        <Stack
          direction="row"
          sx={{
            height: 48,
            overflowX: 'auto',
            scrollbarWidth: 'none',
            '&::-webkit-scrollbar': { display: 'none' },
            px: 2,
            alignItems: 'stretch',
          }}
        >
          {TAB_ORDER.map(({ key, label }) => {
            const on = expTab === key
            return (
              <Box
                key={key}
                component="button"
                type="button"
                onClick={() => setExpTab(key)}
                sx={{
                  border: 'none',
                  cursor: 'pointer',
                  bgcolor: 'transparent',
                  px: 1.75,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'stretch',
                  minWidth: 'fit-content',
                }}
              >
                <Typography
                  sx={{
                    flex: 1,
                    display: 'flex',
                    alignItems: 'center',
                    fontWeight: 800,
                    fontSize: 13,
                    color: on ? tripooColors.orange : tripooColors.textHint,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {label}
                </Typography>
                <Box
                  sx={{
                    height: 3,
                    width: '100%',
                    borderRadius: 99,
                    bgcolor: tripooColors.orange,
                    visibility: on ? 'visible' : 'hidden',
                    mt: 0.25,
                  }}
                />
              </Box>
            )
          })}
        </Stack>
      </Box>
    </Box>
  )

  const categoryRows = useMemo(() => {
    const total = stats.total > 0 ? stats.total : 1
    return Object.entries(stats.byCategory)
      .filter(([, amt]) => amt > 0)
      .sort((a, b) => b[1] - a[1])
      .map(([cat, amount]) => ({
        cat,
        amount,
        pct: Math.min(100, Math.round((amount / total) * 100)),
        meta: categoryMeta(cat),
      }))
  }, [stats.byCategory, stats.total])

  return (
    <>
      <TripTabScaffold header={header}>
        <Box sx={{ bgcolor: SECTION_BG, minHeight: '40%', flex: 1 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Search expenses"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            sx={{
              mx: 2,
              mt: 1,
              mb: 1,
              '& .MuiOutlinedInput-root': {
                bgcolor: tripooColors.surface,
                borderRadius: 1.25,
                '& fieldset': { borderColor: tripooColors.border },
              },
            }}
            InputProps={{
              sx: { fontSize: 13, pl: 1 },
              startAdornment: <SearchIcon sx={{ color: tripooColors.textSecondary, mr: 0.5, fontSize: 20 }} />,
            }}
          />

          {expTab === 'stats' ? (
            <Box sx={{ px: 2, py: 1.5, pb: 4 }}>
              <Card variant="outlined" sx={{ borderRadius: 2, p: 2, mb: 2, borderColor: tripooColors.border }}>
                <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary, fontWeight: 700 }}>TOTAL</Typography>
                <Typography sx={{ fontWeight: 900, fontSize: 24, mt: 0.5 }}>{formatRs2(stats.total)}</Typography>
                <Typography sx={{ fontSize: 13, color: tripooColors.textSecondary, mt: 1 }}>
                  {formatRs2(stats.avgPerPerson)} per person
                </Typography>
                <Typography sx={{ fontSize: 13, color: tripooColors.textSecondary, mt: 1.5 }}>
                  Top spender:{' '}
                  {stats.topSpender
                    ? `${stats.topSpender.name} — ${formatRs2(stats.topSpender.amt)}`
                    : '—'}
                </Typography>
              </Card>
              <Typography sx={{ fontWeight: 800, fontSize: 13, mb: 1 }}>By category</Typography>
              <Stack spacing={1.5}>
                {categoryRows.length === 0 ? (
                  <Typography sx={{ fontSize: 13, color: tripooColors.textSecondary }}>No expense data yet</Typography>
                ) : (
                  categoryRows.map(({ cat, amount, pct, meta }) => (
                    <Box key={cat}>
                      <Stack direction="row" alignItems="center" spacing={1.25}>
                        <Box
                          sx={{
                            width: 36,
                            height: 36,
                            borderRadius: 1.25,
                            bgcolor: meta.bg,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 900,
                            fontSize: 14,
                            color: meta.tint,
                          }}
                        >
                          {cat[0]!.toUpperCase()}
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography sx={{ fontWeight: 700, fontSize: 14, textTransform: 'capitalize' }}>{cat}</Typography>
                          <LinearProgress
                            variant="determinate"
                            value={pct}
                            sx={{
                              height: 8,
                              borderRadius: 99,
                              mt: 0.5,
                              bgcolor: '#E8E4DF',
                              '& .MuiLinearProgress-bar': { bgcolor: tripooColors.orange, borderRadius: 99 },
                            }}
                          />
                        </Box>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: tripooColors.textSecondary }}>
                          ₹{Math.round(amount).toLocaleString('en-IN')} ({pct}%)
                        </Typography>
                      </Stack>
                    </Box>
                  ))
                )}
              </Stack>
            </Box>
          ) : (
            <List sx={{ py: 0, pb: 2 }}>
              {filteredList.length === 0 ? (
                <Box sx={{ py: 8, textAlign: 'center', px: 2 }}>
                  <Typography sx={{ fontSize: 12, color: tripooColors.textSecondary }}>
                    {q ? 'No expenses match your search' : 'No expenses found'}
                  </Typography>
                </Box>
              ) : (
                filteredList.map((e) => {
                  const meta = categoryMeta(e.category)
                  const payer = memberById.get(e.paidBy)?.name ?? 'Someone'
                  return (
                    <Box key={e.id} sx={{ bgcolor: tripooColors.surface, mb: 1, mx: 2, borderRadius: 2 }}>
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
                                {formatRs2(e.amount)} · Paid by {payer}
                              </Typography>
                              <Button size="small" onClick={() => openEdit(e)}>
                                Edit
                              </Button>
                            </>
                          }
                        />
                      </ListItem>
                    </Box>
                  )
                })
              )}
            </List>
          )}
        </Box>
      </TripTabScaffold>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{edit ? 'Edit expense' : 'New expense'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Title" fullWidth value={title} onChange={(e) => setTitleInput(e.target.value)} />
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
