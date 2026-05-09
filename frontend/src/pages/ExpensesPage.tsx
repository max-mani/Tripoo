import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  Fab,
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
  Avatar,
  Menu,
  MenuItem,
  Divider,
  InputAdornment,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
import CloseIcon from '@mui/icons-material/Close'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import TrendingDownIcon from '@mui/icons-material/TrendingDown'
import TrendingUpIcon from '@mui/icons-material/TrendingUp'
import HomeOutlinedIcon from '@mui/icons-material/HomeOutlined'
import RestaurantOutlinedIcon from '@mui/icons-material/RestaurantOutlined'
import DirectionsCarOutlinedIcon from '@mui/icons-material/DirectionsCarOutlined'
import LocalBarOutlinedIcon from '@mui/icons-material/LocalBarOutlined'
import SurfingOutlinedIcon from '@mui/icons-material/SurfingOutlined'
import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined'
import PersonOutlineIcon from '@mui/icons-material/PersonOutline'
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined'
import TuneIcon from '@mui/icons-material/Tune'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import CalendarTodayOutlinedIcon from '@mui/icons-material/CalendarTodayOutlined'
import type { SvgIconComponent } from '@mui/icons-material'
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
import { bgForSeed, letterFromName, textColorForSeed } from '../lib/avatarIdentity'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { tripooColors } from '../theme'
import { computeYouOweYouAreOwed, computeOweOwedTrends } from '../lib/expenseBalances'
import { TripTabScaffold } from '../components/TripTabScaffold'
import { FAB_BOTTOM_FROM_VIEWPORT } from '../lib/tripChrome'
import { AppCheckCircleIcon } from '../components/icons/AppCheckCircleIcon'

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

function toYmdLocal(ms: number): string {
  const d = new Date(ms)
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${mo}-${day}`
}

const EXPENSE_CATEGORY_ICONS: SvgIconComponent[] = [
  HomeOutlinedIcon,
  RestaurantOutlinedIcon,
  DirectionsCarOutlinedIcon,
  LocalBarOutlinedIcon,
  SurfingOutlinedIcon,
  MoreHorizOutlinedIcon,
]

type SortMode = 'latest' | 'oldest' | 'highest' | 'lowest'

function sortExpensesList(list: Expense[], mode: SortMode): Expense[] {
  const copy = [...list]
  switch (mode) {
    case 'oldest':
      return copy.sort((a, b) => a.timestamp - b.timestamp)
    case 'highest':
      return copy.sort((a, b) => b.amount - a.amount)
    case 'lowest':
      return copy.sort((a, b) => a.amount - b.amount)
    default:
      return copy.sort((a, b) => b.timestamp - a.timestamp)
  }
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
  const [sortMode, setSortMode] = useState<SortMode>('latest')
  const [searchOpen, setSearchOpen] = useState(false)
  const [moreEl, setMoreEl] = useState<null | HTMLElement>(null)
  const [expenseRowMenu, setExpenseRowMenu] = useState<null | { anchor: HTMLElement; expense: Expense }>(null)
  const [splitMode, setSplitMode] = useState<'equally' | 'custom' | 'justme'>('equally')
  const [expenseDateMs, setExpenseDateMs] = useState(() => Date.now())
  const [paidByMenuAnchor, setPaidByMenuAnchor] = useState<null | HTMLElement>(null)
  const expenseDateInputRef = useRef<HTMLInputElement>(null)

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
    return sortExpensesList(list, sortMode)
  }, [expenses, expTab, firebaseUser, q, sortMode])

  function openNew() {
    setEdit(null)
    setTitleInput('')
    setAmount('')
    setCategory('accommodation')
    setPaidBy(firebaseUser?.uid ?? '')
    setSplitMode('equally')
    setSplitWith(members.map((m) => m.userId))
    setExpenseDateMs(Date.now())
    setOpen(true)
  }

  function openEdit(e: Expense) {
    setEdit(e)
    setTitleInput(e.title)
    setAmount(String(e.amount))
    setCategory(e.category)
    setPaidBy(e.paidBy)
    setSplitMode('custom')
    setSplitWith([...e.splitWith])
    setExpenseDateMs(e.timestamp)
    setOpen(true)
  }

  function toggleSplitMember(uid: string) {
    if (splitMode !== 'custom') return
    setSplitWith((prev) => (prev.includes(uid) ? prev.filter((x) => x !== uid) : [...prev, uid]))
  }

  function applySplitMode(mode: 'equally' | 'custom' | 'justme') {
    setSplitMode(mode)
    const uid = firebaseUser?.uid ?? ''
    if (mode === 'equally') setSplitWith(members.map((m) => m.userId))
    else if (mode === 'justme') setSplitWith(uid ? [uid] : [])
  }

  async function saveExpense() {
    if (!tripId) return
    const uid = firebaseUser?.uid ?? paidBy
    let resolvedSplit = splitWith
    if (splitMode === 'equally') resolvedSplit = members.map((m) => m.userId)
    else if (splitMode === 'justme') resolvedSplit = uid ? [uid] : [paidBy]
    else resolvedSplit = splitWith.length ? splitWith : [paidBy]

    const exp: Omit<Expense, 'id'> = {
      title: title.trim(),
      amount: Number(amount) || 0,
      category,
      paidBy,
      splitWith: resolvedSplit,
      timestamp: expenseDateMs,
      settled: edit?.settled ?? false,
      createdBy: edit?.createdBy,
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
    if (!window.confirm(`Delete "${e.title}"?`)) return
    await deleteExpense(tripId, e.id)
    setOpen(false)
  }

  const subtitle = `${members.length} participants · ${shortTripDates(trip.startDate, trip.endDate)}`

  const oweArrow = trends.oweTrendPct >= 0 ? '▲' : '▼'
  const owedArrow = trends.owedTrendPct >= 0 ? '▲' : '▼'

  const uid = firebaseUser?.uid ?? ''

  function canModifyExpense(e: Expense): boolean {
    if (canManage) return true
    return Boolean(e.createdBy && uid && e.createdBy === uid)
  }

  function showExpenseRowMenu(e: Expense): boolean {
    return canModifyExpense(e) || (canManage && !e.settled)
  }

  const header = (
    <Box>
      <Box
        sx={{
          bgcolor: tripooColors.surface,
          px: 2,
          pt: `calc(12px + env(safe-area-inset-top, 0px))`,
          pb: 1.5,
          boxShadow: '0 2px 4px rgba(24,20,17,0.06)',
        }}
      >
        <Stack direction="row" alignItems="center" spacing={0.75}>
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
            <Typography sx={{ fontWeight: 800, fontSize: 17 }} noWrap>
              {trip.name}
            </Typography>
            <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary, mt: 0.1 }} noWrap>
              {subtitle}
            </Typography>
          </Box>
          <IconButton
            onClick={() => {
              if (searchOpen) {
                setSearchOpen(false)
                setQ('')
              } else {
                setSearchOpen(true)
              }
            }}
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
            onClick={(e) => setMoreEl(e.currentTarget)}
            sx={{
              width: 36,
              height: 36,
              ml: 0.25,
              bgcolor: '#FDE7D2',
              color: tripooColors.orange,
              '&:hover': { bgcolor: '#FCD9B8' },
            }}
            aria-label="More"
          >
            <MoreHorizIcon sx={{ fontSize: 20 }} />
          </IconButton>
          <Menu anchorEl={moreEl} open={Boolean(moreEl)} onClose={() => setMoreEl(null)}>
            <MenuItem
              onClick={() => {
                setSortMode('latest')
                setMoreEl(null)
              }}
            >
              Latest first
            </MenuItem>
            <MenuItem
              onClick={() => {
                setSortMode('oldest')
                setMoreEl(null)
              }}
            >
              Oldest first
            </MenuItem>
            <MenuItem
              onClick={() => {
                setSortMode('highest')
                setMoreEl(null)
              }}
            >
              Highest amount
            </MenuItem>
            <MenuItem
              onClick={() => {
                setSortMode('lowest')
                setMoreEl(null)
              }}
            >
              Lowest amount
            </MenuItem>
          </Menu>
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
        <Box sx={{ bgcolor: SECTION_BG, minHeight: '40%', flex: 1, pb: '88px' }}>
          {searchOpen && expTab !== 'stats' ? (
            <TextField
              fullWidth
              size="small"
              placeholder="Search expenses"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              autoFocus
              sx={{
                mx: 2,
                mt: 1,
                mb: 1,
                '& .MuiOutlinedInput-root': {
                  minHeight: 40,
                  bgcolor: tripooColors.surface,
                  borderRadius: 1.25,
                  fontSize: 13,
                  '& fieldset': { borderColor: tripooColors.border },
                },
              }}
            />
          ) : null}

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
                        sx={{ alignItems: 'flex-start', pr: 1 }}
                        secondaryAction={
                          showExpenseRowMenu(e) ? (
                          <IconButton
                            edge="end"
                            size="small"
                            aria-label="Expense options"
                            onClick={(ev) => setExpenseRowMenu({ anchor: ev.currentTarget, expense: e })}
                          >
                            <MoreHorizIcon sx={{ color: tripooColors.textHint }} />
                          </IconButton>
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
                            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', pr: 4 }}>
                              <Typography sx={{ fontWeight: 700 }}>{e.title}</Typography>
                              {e.settled && <Chip size="small" label="Settled" color="success" />}
                            </Stack>
                          }
                          secondary={
                            <Typography variant="body2" component="span" sx={{ display: 'block' }}>
                              {formatRs2(e.amount)} · Paid by {payer}
                            </Typography>
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

      <Fab
        color="primary"
        aria-label="Add expense"
        onClick={openNew}
        sx={{
          position: 'fixed',
          right: 18,
          bottom: FAB_BOTTOM_FROM_VIEWPORT,
          zIndex: 1100,
          width: 56,
          height: 56,
          bgcolor: tripooColors.orange,
          boxShadow: '0 8px 16px rgba(24,20,17,0.18)',
          '&:hover': { bgcolor: tripooColors.orangeDark },
        }}
      >
        <AddIcon sx={{ color: tripooColors.surface }} />
      </Fab>

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        fullWidth
        maxWidth="sm"
        slotProps={{
          paper: {
            sx: {
              m: 0,
              mx: 'auto',
              width: '100%',
              maxWidth: 480,
              position: 'fixed',
              bottom: 0,
              maxHeight: 'min(92vh, 640px)',
              borderRadius: '16px 16px 0 0',
              overflow: 'hidden',
            },
          },
        }}
      >
        <Box sx={{ width: 36, height: 4, borderRadius: 99, bgcolor: '#E0D8CF', mx: 'auto', mt: 1.5 }} />
        <Stack direction="row" alignItems="center" sx={{ px: 2.25, pt: 1.75, pb: 1 }}>
          <Typography sx={{ flex: 1, fontWeight: 800, fontSize: 17, color: tripooColors.textPrimary }}>
            {edit ? 'Edit Expense' : 'Add Expense'}
          </Typography>
          <IconButton aria-label="Close" onClick={() => setOpen(false)} size="small" sx={{ color: tripooColors.textPrimary }}>
            <CloseIcon fontSize="small" />
          </IconButton>
        </Stack>
        <Divider sx={{ borderColor: 'rgba(244,140,37,0.08)' }} />
        <DialogContent sx={{ px: 2.25, pt: 2, pb: 2, overflowY: 'auto' }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                AMOUNT
              </Typography>
              <TextField
                fullWidth
                placeholder="0.00"
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Typography sx={{ fontWeight: 900, fontSize: 22, color: '#8A7560' }}>₹</Typography>
                    </InputAdornment>
                  ),
                  sx: { pl: 1, '& input': { fontWeight: 800, fontSize: 22 } },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    minHeight: 56,
                    bgcolor: tripooColors.surface,
                    borderRadius: 1.25,
                    '& fieldset': { borderColor: tripooColors.border },
                  },
                }}
              />
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                DESCRIPTION
              </Typography>
              <TextField
                fullWidth
                placeholder="e.g. Hotel booking, Dinner, Taxi..."
                value={title}
                onChange={(e) => setTitleInput(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start" sx={{ alignSelf: 'flex-start', mt: 1.75 }}>
                      <ReceiptLongIcon sx={{ color: '#8A7560', fontSize: 22 }} />
                    </InputAdornment>
                  ),
                  sx: { pl: 0.5, alignItems: 'flex-start' },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    bgcolor: tripooColors.surface,
                    borderRadius: 1.25,
                    '& fieldset': { borderColor: tripooColors.border },
                  },
                }}
              />
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                CATEGORY
              </Typography>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: 1,
                }}
              >
                {EXPENSE_CATEGORIES.map((c, idx) => {
                  const Icon = EXPENSE_CATEGORY_ICONS[idx]!
                  const sel = category === c.key
                  return (
                    <Box
                      key={c.key}
                      onClick={() => setCategory(c.key)}
                      sx={{
                        borderRadius: 2,
                        py: 1,
                        px: 0.75,
                        cursor: 'pointer',
                        border: sel ? `2px solid ${tripooColors.orange}` : `1px solid ${tripooColors.border}`,
                        bgcolor: sel ? 'rgba(244,140,37,0.08)' : tripooColors.surface,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                      }}
                    >
                      <Box
                        sx={{
                          width: 44,
                          height: 44,
                          borderRadius: '50%',
                          bgcolor: c.bg,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Icon sx={{ fontSize: 22, color: c.tint }} />
                      </Box>
                      <Typography sx={{ fontSize: 10, fontWeight: 800, mt: 0.75, textAlign: 'center', lineHeight: 1.15 }}>
                        {c.label}
                      </Typography>
                    </Box>
                  )
                })}
              </Box>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                PAID BY
              </Typography>
              <Box
                onClick={(e) => setPaidByMenuAnchor(e.currentTarget)}
                sx={{
                  height: 50,
                  px: 1.6,
                  borderRadius: 1.25,
                  border: `1px solid ${tripooColors.border}`,
                  bgcolor: tripooColors.surface,
                  display: 'flex',
                  alignItems: 'center',
                  cursor: 'pointer',
                }}
              >
                <PersonOutlineIcon sx={{ fontSize: 20, color: '#8A7560', mr: 1.25 }} />
                <Typography sx={{ flex: 1, fontSize: 15, color: tripooColors.textPrimary, fontWeight: 600 }}>
                  {memberById.get(paidBy)?.name?.trim() || 'Select'}
                </Typography>
                <ExpandMoreIcon sx={{ fontSize: 18, color: '#8A7560' }} />
              </Box>
              <Menu
                anchorEl={paidByMenuAnchor}
                open={Boolean(paidByMenuAnchor)}
                onClose={() => setPaidByMenuAnchor(null)}
              >
                {members.map((m) => (
                  <MenuItem
                    key={m.userId}
                    onClick={() => {
                      setPaidBy(m.userId)
                      setPaidByMenuAnchor(null)
                    }}
                  >
                    {m.name}
                  </MenuItem>
                ))}
              </Menu>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                SPLIT
              </Typography>
              <Stack direction="row" spacing={1} sx={{ '& .MuiButton-root': { flex: 1, py: 0.75, fontSize: 11, fontWeight: 800, textTransform: 'none' } }}>
                <Button
                  variant="outlined"
                  startIcon={<GroupsOutlinedIcon sx={{ fontSize: 16 }} />}
                  onClick={() => applySplitMode('equally')}
                  sx={{
                    borderColor: splitMode === 'equally' ? tripooColors.orange : tripooColors.border,
                    color: splitMode === 'equally' ? tripooColors.orange : '#8A7560',
                    bgcolor: splitMode === 'equally' ? 'rgba(244,140,37,0.12)' : tripooColors.surface,
                  }}
                >
                  Equally
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<TuneIcon sx={{ fontSize: 16 }} />}
                  onClick={() => applySplitMode('custom')}
                  sx={{
                    borderColor: splitMode === 'custom' ? tripooColors.orange : tripooColors.border,
                    color: splitMode === 'custom' ? tripooColors.orange : '#8A7560',
                    bgcolor: splitMode === 'custom' ? 'rgba(244,140,37,0.12)' : tripooColors.surface,
                  }}
                >
                  Custom
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<PersonOutlineIcon sx={{ fontSize: 16 }} />}
                  onClick={() => applySplitMode('justme')}
                  sx={{
                    borderColor: splitMode === 'justme' ? tripooColors.orange : tripooColors.border,
                    color: splitMode === 'justme' ? tripooColors.orange : '#8A7560',
                    bgcolor: splitMode === 'justme' ? 'rgba(244,140,37,0.12)' : tripooColors.surface,
                  }}
                >
                  Just Me
                </Button>
              </Stack>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                SPLIT WITH
              </Typography>
              <Stack direction="row" flexWrap="wrap" useFlexGap gap={1}>
                {members.map((m) => {
                  const src = photoSrcForDisplay(m.photoUrl)
                  const letter = m.avatarLetter?.trim() || letterFromName(m.name)
                  const bg = m.avatarColorHex?.trim() || bgForSeed(m.userId)
                  const tc = textColorForSeed(m.userId)
                  const checked = splitWith.includes(m.userId)
                  const locked = splitMode !== 'custom'
                  const first = m.name.split(/\s+/)[0] ?? m.name
                  return (
                    <Chip
                      key={m.userId}
                      avatar={
                        <Avatar src={src || undefined} sx={{ width: 24, height: 24, fontSize: 12, fontWeight: 800, bgcolor: bg, color: tc }}>
                          {!src ? letter : undefined}
                        </Avatar>
                      }
                      label={first}
                      onClick={() => toggleSplitMember(m.userId)}
                      variant="outlined"
                      sx={{
                        borderRadius: 99,
                        opacity: locked ? 0.72 : 1,
                        pointerEvents: locked ? 'none' : 'auto',
                        borderColor: checked ? tripooColors.orange : tripooColors.border,
                        bgcolor: checked ? 'rgba(244,140,37,0.12)' : tripooColors.surface,
                        color: checked ? tripooColors.orange : tripooColors.textPrimary,
                        fontWeight: 800,
                        '& .MuiChip-avatar': { ml: 0.75 },
                      }}
                    />
                  )
                })}
              </Stack>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                DATE
              </Typography>
              <input
                ref={expenseDateInputRef}
                type="date"
                value={toYmdLocal(expenseDateMs)}
                onChange={(e) => {
                  const v = e.target.value
                  if (v) setExpenseDateMs(new Date(v + 'T12:00:00').getTime())
                }}
                style={{ position: 'absolute', width: 0, height: 0, opacity: 0, pointerEvents: 'none' }}
                tabIndex={-1}
              />
              <Box
                onClick={() => expenseDateInputRef.current?.showPicker?.() ?? expenseDateInputRef.current?.click()}
                sx={{
                  height: 50,
                  px: 1.6,
                  borderRadius: 1.25,
                  border: `1px solid ${tripooColors.border}`,
                  bgcolor: tripooColors.surface,
                  display: 'flex',
                  alignItems: 'center',
                  cursor: 'pointer',
                }}
              >
                <CalendarTodayOutlinedIcon sx={{ fontSize: 20, color: '#8A7560', mr: 1.25 }} />
                <Typography sx={{ flex: 1, fontSize: 15, color: tripooColors.textPrimary }}>
                  {new Date(expenseDateMs).toLocaleDateString(undefined, {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </Typography>
              </Box>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 2.25, pb: 2, pt: 0, flexDirection: 'column', gap: 1, alignItems: 'stretch' }}>
          {edit && canModifyExpense(edit) ? (
            <Button color="error" variant="outlined" onClick={() => void onDelete(edit)}>
              Delete expense
            </Button>
          ) : null}
          <Button
            variant="contained"
            fullWidth
            size="large"
            startIcon={<AppCheckCircleIcon sx={{ color: tripooColors.surface, fontSize: 20 }} />}
            onClick={() => void saveExpense()}
            sx={{
              py: 1.35,
              fontWeight: 800,
              bgcolor: tripooColors.orange,
              color: tripooColors.surface,
              '&:hover': { bgcolor: tripooColors.orangeDark },
            }}
          >
            {edit ? 'Save changes' : 'Add Expense'}
          </Button>
        </DialogActions>
      </Dialog>

      <Menu
        anchorEl={expenseRowMenu?.anchor ?? null}
        open={Boolean(expenseRowMenu)}
        onClose={() => setExpenseRowMenu(null)}
      >
        {expenseRowMenu?.expense && canModifyExpense(expenseRowMenu.expense) ? (
          <MenuItem
            onClick={() => {
              const row = expenseRowMenu
              setExpenseRowMenu(null)
              if (row) openEdit(row.expense)
            }}
          >
            Edit
          </MenuItem>
        ) : null}
        {canManage && expenseRowMenu?.expense && !expenseRowMenu.expense.settled ? (
          <MenuItem
            onClick={() => {
              const row = expenseRowMenu
              setExpenseRowMenu(null)
              if (row) void onToggleSettled(row.expense)
            }}
          >
            Mark as settled
          </MenuItem>
        ) : null}
        {expenseRowMenu?.expense && canModifyExpense(expenseRowMenu.expense) ? (
          <MenuItem
            onClick={() => {
              const row = expenseRowMenu
              setExpenseRowMenu(null)
              if (row) void onDelete(row.expense)
            }}
            sx={{ color: tripooColors.red }}
          >
            Delete
          </MenuItem>
        ) : null}
      </Menu>
    </>
  )
}
