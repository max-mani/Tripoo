import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  AppBar,
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
  Toolbar,
  Typography,
  Chip,
  Divider,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import AddIcon from '@mui/icons-material/Add'
import { useAuth } from '../context/AuthContext'
import {
  addExpense,
  deleteExpense,
  markExpenseSettled,
  subscribeExpenses,
  updateExpense,
} from '../services/expenseService'
import { canUserManageTripAsLeader, subscribeTripMembers } from '../services/tripService'
import type { Expense, TripMember } from '../types/models'
import { categoryMeta, EXPENSE_CATEGORIES } from '../lib/constants'
import { tripooColors } from '../theme'

export default function ExpensesPage() {
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
            Expenses
          </Typography>
          <IconButton color="primary" onClick={openNew} aria-label="Add expense">
            <AddIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2 }}>
        <List sx={{ bgcolor: 'background.paper', borderRadius: 3 }}>
          {expenses.length === 0 ? (
            <ListItem>
              <ListItemText primary="No expenses yet" secondary="Tap + to add one" />
            </ListItem>
          ) : (
            expenses.map((e) => {
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
                            {e.amount.toLocaleString()} · Paid by {payer}
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
