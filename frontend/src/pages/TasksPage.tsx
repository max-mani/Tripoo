import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  Fab,
  IconButton,
  InputAdornment,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
  Avatar,
  Divider,
  LinearProgress,
  Menu,
  MenuItem,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
import CloseIcon from '@mui/icons-material/Close'
import MoreHorizIcon from '@mui/icons-material/MoreHoriz'
import GroupsIcon from '@mui/icons-material/Groups'
import AssignmentIcon from '@mui/icons-material/Assignment'
import LabelOutlinedIcon from '@mui/icons-material/LabelOutlined'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ChecklistIcon from '@mui/icons-material/Checklist'
import CalendarTodayOutlinedIcon from '@mui/icons-material/CalendarTodayOutlined'
import ClearIcon from '@mui/icons-material/Clear'
import {
  addTask,
  deleteTask,
  subscribeTasks,
  updateTask,
  updateTaskCompletion,
} from '../services/taskService'
import { subscribeTripMembers, canUserManageTripAsLeader } from '../services/tripService'
import type { Task, Trip, TripMember } from '../types/models'
import { TASK_CATEGORIES } from '../lib/constants'
import { bgForSeed, letterFromName, textColorForSeed } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'
import { TripTabScaffold } from '../components/TripTabScaffold'
import { FAB_BOTTOM_FROM_VIEWPORT } from '../lib/tripChrome'
import { UllaLogo } from '../components/UllaLogo'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { useAuth } from '../context/AuthContext'

type TaskTab = 'all' | 'progress' | 'done'

function toYmdLocal(ms: number): string {
  const d = new Date(ms)
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${mo}-${day}`
}

const TASK_PRIORITY_SEGMENTS = [
  { key: 'low', label: 'Low', dot: '#16A34A', bg: '#DCFCE7', border: '#16A34A' },
  { key: 'medium', label: 'Medium', dot: '#D97706', bg: '#FEF3C7', border: '#D97706' },
  { key: 'high', label: 'High', dot: '#DC2626', bg: '#FEF2F2', border: '#DC2626' },
] as const

function normalizeTaskCategoryKey(raw: string): string {
  const c = raw.toLowerCase()
  if (c === 'booking') return 'bookings'
  return TASK_CATEGORIES.some((x) => x.key === c) ? c : 'general'
}

export default function TasksPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const navigate = useNavigate()
  const { firebaseUser } = useAuth()
  const [tasks, setTasks] = useState<Task[]>([])
  const [members, setMembers] = useState<TripMember[]>([])
  const [canManage, setCanManage] = useState(false)
  const [tab, setTab] = useState<TaskTab>('all')
  const [q, setQ] = useState('')
  const [searchOpen, setSearchOpen] = useState(false)
  const [open, setOpen] = useState(false)
  const [edit, setEdit] = useState<Task | null>(null)
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('general')
  const [assignedTo, setAssignedTo] = useState('everyone')
  const [priority, setPriority] = useState('medium')
  const [notes, setNotes] = useState('')
  const [dueMs, setDueMs] = useState<number | null>(null)
  const [taskRowMenu, setTaskRowMenu] = useState<null | { anchor: HTMLElement; task: Task }>(null)
  const dueDateInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!tripId) return
    return subscribeTasks(tripId, setTasks)
  }, [tripId])

  useEffect(() => {
    if (!tripId) return
    return subscribeTripMembers(tripId, setMembers)
  }, [tripId])

  useEffect(() => {
    if (!tripId || !firebaseUser) return
    void canUserManageTripAsLeader(tripId, firebaseUser.uid).then(setCanManage)
  }, [tripId, firebaseUser])

  const { doneCount, pct } = useMemo(() => {
    const total = tasks.length
    const done = tasks.filter((t) => t.completed).length
    const p = total ? Math.round((done / total) * 100) : 0
    return { doneCount: done, pct: p }
  }, [tasks])

  const visible = useMemo(() => {
    let list = tasks
    if (tab === 'progress') list = tasks.filter((t) => !t.completed)
    if (tab === 'done') list = tasks.filter((t) => t.completed)
    const s = q.trim().toLowerCase()
    if (s) list = list.filter((t) => t.title.toLowerCase().includes(s))
    return list
  }, [tasks, tab, q])

  const uid = firebaseUser?.uid ?? ''

  function canModifyTask(t: Task): boolean {
    if (canManage) return true
    return Boolean(t.createdBy && uid && t.createdBy === uid)
  }

  const avatarStack = useMemo(() => members.slice(0, 4), [members])

  function openNew() {
    setEdit(null)
    setTitle('')
    setCategory('general')
    setAssignedTo('everyone')
    setPriority('medium')
    setNotes('')
    setDueMs(null)
    setOpen(true)
  }

  function openEdit(t: Task) {
    setEdit(t)
    setTitle(t.title)
    setCategory(normalizeTaskCategoryKey(t.category))
    setAssignedTo(t.assignedTo)
    setPriority(t.priority)
    setNotes(t.notes ?? '')
    setDueMs(t.dueDate ?? null)
    setOpen(true)
  }

  async function saveTask() {
    if (!tripId) return
    const body: Omit<Task, 'id'> = {
      title: title.trim(),
      category,
      assignedTo,
      completed: edit?.completed ?? false,
      dueDate: dueMs,
      priority,
      notes: notes.trim() || null,
    }
    if (!body.title) return
    if (edit) {
      await updateTask(tripId, edit.id, { ...edit, ...body, id: edit.id })
    } else {
      await addTask(tripId, body)
    }
    setOpen(false)
  }

  async function toggleDone(t: Task) {
    if (!tripId || !canManage) return
    await updateTaskCompletion(tripId, t.id, !t.completed)
  }

  async function onDelete(t: Task) {
    if (!tripId) return
    if (!window.confirm('Delete this task?')) return
    await deleteTask(tripId, t.id)
    setOpen(false)
  }

  function tabBtn(key: TaskTab, label: string) {
    const on = tab === key
    return (
      <Box
        component="button"
        type="button"
        onClick={() => setTab(key)}
        sx={{
          flex: 1,
          border: 'none',
          cursor: 'pointer',
          bgcolor: 'transparent',
          pt: 1,
          pb: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
        }}
      >
        <Typography
          sx={{
            fontWeight: 800,
            fontSize: 14,
            color: on ? tripooColors.orange : '#9CA3AF',
            flex: 1,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          {label}
        </Typography>
        <Box
          sx={{
            mt: 0.5,
            height: 3,
            width: '100%',
            borderRadius: 99,
            bgcolor: on ? tripooColors.orange : 'transparent',
            visibility: on ? 'visible' : 'hidden',
          }}
        />
      </Box>
    )
  }

  const headerTop = (
    <Box>
      <Box
        sx={{
          bgcolor: 'rgba(255,255,255,0.91)',
          px: 2,
          pt: `calc(12px + env(safe-area-inset-top, 0px))`,
          pb: 1.75,
        }}
      >
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.4 }}>
          <IconButton
            onClick={() => navigate('/dashboard')}
            aria-label="Back"
            sx={{ color: tripooColors.textPrimary, width: 36, height: 36 }}
          >
            <ArrowBackIosNewIcon sx={{ fontSize: 16, ml: 0.5 }} />
          </IconButton>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ flex: 1, minWidth: 0 }}>
            <UllaLogo size={23} variant="full" />
            <Typography sx={{ fontWeight: 900, fontSize: 18 }} noWrap>
              {trip.name}
            </Typography>
          </Stack>
          <Stack direction="row" sx={{ alignItems: 'center' }}>
            {avatarStack.map((m, i) => {
              const src = photoSrcForDisplay(m.photoUrl)
              const letter = m.avatarLetter?.trim() || letterFromName(m.name)
              const bg = m.avatarColorHex?.trim() || tripooColors.orange
              return (
                <Box
                  key={m.userId}
                  sx={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    border: '2px solid #fff',
                    ml: i > 0 ? -0.8 : 0,
                    overflow: 'hidden',
                    bgcolor: bg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    fontWeight: 900,
                    color: '#fff',
                  }}
                >
                  {src ? (
                    <Box component="img" src={src} alt="" sx={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    letter.slice(0, 2)
                  )}
                </Box>
              )
            })}
          </Stack>
          <IconButton
            onClick={() => {
              if (searchOpen) {
                setSearchOpen(false)
                setQ('')
              } else {
                setSearchOpen(true)
              }
            }}
            sx={{ width: 32, height: 32, bgcolor: '#FDE7D2', color: tripooColors.orange, ml: 0.5 }}
            aria-label="Search"
          >
            <SearchIcon sx={{ fontSize: 18 }} />
          </IconButton>
        </Stack>

        <Stack direction="row" alignItems="center" sx={{ mb: 0.6 }}>
          <Typography sx={{ flex: 1, fontSize: 13, color: tripooColors.textSecondary }}>Trip Completion</Typography>
          <Typography sx={{ fontSize: 13, fontWeight: 800, color: tripooColors.orange }}>{pct}%</Typography>
        </Stack>
        <LinearProgress
          variant="determinate"
          value={pct}
          sx={{
            height: 10,
            borderRadius: 99,
            bgcolor: '#E8E4DF',
            '& .MuiLinearProgress-bar': { borderRadius: 99, bgcolor: tripooColors.orange },
          }}
        />
        <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary, mt: 0.75 }}>
          {doneCount} of {tasks.length} tasks completed
        </Typography>

        {searchOpen ? (
          <TextField
            size="small"
            fullWidth
            placeholder="Search tasks…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            autoFocus
            sx={{
              mt: 1.25,
              '& .MuiOutlinedInput-root': {
                minHeight: 40,
                fontSize: 13,
                bgcolor: tripooColors.surface,
                borderRadius: 1.25,
                '& fieldset': { borderColor: tripooColors.border },
              },
            }}
          />
        ) : null}
      </Box>
      <Box sx={{ height: 1, bgcolor: '#E6E0DB' }} />
    </Box>
  )

  const headerTabs = (
    <Box sx={{ bgcolor: tripooColors.surface, borderBottom: '1px solid #F3F4F6' }}>
      <Stack direction="row" sx={{ height: 46, px: 2 }}>
        {tabBtn('all', 'All')}
        {tabBtn('progress', 'In Progress')}
        {tabBtn('done', 'Completed')}
      </Stack>
    </Box>
  )

  const header = (
    <Box>
      {headerTop}
      {headerTabs}
    </Box>
  )

  return (
    <>
      <TripTabScaffold header={header}>
        <Box sx={{ px: 2, pt: 1.5, pb: '88px', bgcolor: tripooColors.bg, minHeight: 200 }}>
          {visible.length === 0 ? (
            <Stack alignItems="center" sx={{ py: 10, px: 2 }}>
              <AssignmentIcon sx={{ fontSize: 48, color: tripooColors.textSecondary, opacity: 0.35, mb: 1.5 }} />
              <Typography sx={{ fontSize: 16, fontWeight: 800, color: tripooColors.textSecondary }}>
                No tasks yet
              </Typography>
              <Typography sx={{ fontSize: 13, color: tripooColors.textHint, mt: 0.75, textAlign: 'center' }}>
                Tap + to add a task for this trip
              </Typography>
            </Stack>
          ) : (
            <List sx={{ bgcolor: tripooColors.surface, borderRadius: 2, border: `1px solid ${tripooColors.border}` }}>
              {visible.map((t) => (
                <Box key={t.id}>
                  <ListItem
                    secondaryAction={
                      <Stack direction="row" alignItems="center" spacing={0}>
                        {canModifyTask(t) ? (
                          <IconButton
                            size="small"
                            aria-label="Task options"
                            onClick={(e) => setTaskRowMenu({ anchor: e.currentTarget, task: t })}
                          >
                            <MoreHorizIcon sx={{ color: tripooColors.textHint, fontSize: 22 }} />
                          </IconButton>
                        ) : null}
                        <Checkbox
                          edge="end"
                          checked={t.completed}
                          onChange={() => void toggleDone(t)}
                          disabled={!canManage}
                          sx={{ opacity: canManage ? 1 : 0.45 }}
                        />
                      </Stack>
                    }
                  >
                    <ListItemText
                      primary={
                        <Typography sx={{ fontWeight: 700, textDecoration: t.completed ? 'line-through' : 'none', pr: 2 }}>
                          {t.title}
                        </Typography>
                      }
                      secondary={
                        <Typography variant="caption" sx={{ display: 'block' }}>
                          {t.category} · {t.priority}
                          {t.dueDate ? ` · Due ${new Date(t.dueDate).toLocaleDateString()}` : ''}
                        </Typography>
                      }
                    />
                  </ListItem>
                  <Divider component="li" />
                </Box>
              ))}
            </List>
          )}
        </Box>
      </TripTabScaffold>

      <Fab
        aria-label="Add task"
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
          <Typography sx={{ flex: 1, fontWeight: 800, fontSize: 17 }}>
            {edit ? 'Edit task' : 'New task'}
          </Typography>
          <IconButton aria-label="Close" onClick={() => setOpen(false)} size="small">
            <CloseIcon fontSize="small" />
          </IconButton>
        </Stack>
        <Divider sx={{ borderColor: 'rgba(244,140,37,0.08)' }} />
        <DialogContent sx={{ px: 2.25, pt: 2, pb: 1 }}>
          <Stack spacing={2}>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                TASK NAME
              </Typography>
              <TextField
                fullWidth
                placeholder="e.g. Book Airport Transfer"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <ChecklistIcon sx={{ color: '#8A7560', fontSize: 20 }} />
                    </InputAdornment>
                  ),
                  sx: { pl: 0.5 },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    minHeight: 50,
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
              <TextField
                select
                fullWidth
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                SelectProps={{ IconComponent: ExpandMoreIcon }}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <LabelOutlinedIcon sx={{ color: '#8A7560', fontSize: 20 }} />
                    </InputAdornment>
                  ),
                  sx: { pl: 0.5 },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    minHeight: 50,
                    borderRadius: 1.25,
                    '& fieldset': { borderColor: tripooColors.border },
                  },
                  '& .MuiSelect-select': { py: 1.5, display: 'flex', alignItems: 'center' },
                }}
              >
                {TASK_CATEGORIES.map((c) => (
                  <MenuItem key={c.key} value={c.key}>
                    {c.label}
                  </MenuItem>
                ))}
              </TextField>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                ASSIGN TO
              </Typography>
              <Box
                onClick={() => setAssignedTo('everyone')}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  minHeight: 50,
                  px: 1.5,
                  borderRadius: '10px',
                  border: `1.5px solid ${assignedTo === 'everyone' ? tripooColors.orange : tripooColors.border}`,
                  bgcolor: assignedTo === 'everyone' ? 'rgba(244,140,37,0.12)' : '#F8F7F5',
                  cursor: 'pointer',
                  mb: 1,
                }}
              >
                <GroupsIcon
                  sx={{
                    fontSize: 20,
                    color: assignedTo === 'everyone' ? tripooColors.orange : tripooColors.textSecondary,
                  }}
                />
                <Typography
                  sx={{
                    fontWeight: 800,
                    fontSize: 13,
                    color: assignedTo === 'everyone' ? tripooColors.orange : tripooColors.textSecondary,
                  }}
                >
                  Everyone
                </Typography>
              </Box>
              <Stack direction="row" flexWrap="wrap" useFlexGap gap={1}>
                {members.map((m) => {
                  const src = photoSrcForDisplay(m.photoUrl)
                  const letter = m.avatarLetter?.trim() || letterFromName(m.name)
                  const bg = m.avatarColorHex?.trim() || bgForSeed(m.userId)
                  const tc = textColorForSeed(m.userId)
                  const sel = assignedTo === m.userId
                  const first = m.name.split(/\s+/)[0] ?? m.name
                  return (
                    <Chip
                      key={m.userId}
                      avatar={
                        <Avatar
                          src={src || undefined}
                          sx={{ width: 24, height: 24, fontSize: 12, fontWeight: 800, bgcolor: bg, color: tc }}
                        >
                          {!src ? letter : undefined}
                        </Avatar>
                      }
                      label={first}
                      onClick={() => setAssignedTo(m.userId)}
                      variant="outlined"
                      sx={{
                        borderRadius: 99,
                        borderColor: sel ? tripooColors.orange : tripooColors.border,
                        bgcolor: sel ? 'rgba(244,140,37,0.12)' : tripooColors.surface,
                        color: sel ? tripooColors.orange : tripooColors.textPrimary,
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
                DUE DATE (OPTIONAL)
              </Typography>
              <input
                ref={dueDateInputRef}
                type="date"
                value={dueMs != null ? toYmdLocal(dueMs) : ''}
                onChange={(e) => {
                  const v = e.target.value
                  setDueMs(v ? new Date(v + 'T12:00:00').getTime() : null)
                }}
                style={{ position: 'absolute', width: 0, height: 0, opacity: 0, pointerEvents: 'none' }}
                tabIndex={-1}
              />
              <Stack direction="row" alignItems="center" spacing={0.5}>
                <Box
                  onClick={() => dueDateInputRef.current?.showPicker?.() ?? dueDateInputRef.current?.click()}
                  sx={{
                    flex: 1,
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
                  <Typography sx={{ fontSize: 15, color: dueMs != null ? tripooColors.textPrimary : '#BBA898' }}>
                    {dueMs != null
                      ? new Date(dueMs).toLocaleDateString(undefined, {
                          weekday: 'short',
                          month: 'short',
                          day: 'numeric',
                          year: 'numeric',
                        })
                      : 'No due date'}
                  </Typography>
                </Box>
                {dueMs != null ? (
                  <IconButton
                    aria-label="Clear due date"
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation()
                      setDueMs(null)
                    }}
                    sx={{ color: tripooColors.textHint }}
                  >
                    <ClearIcon fontSize="small" />
                  </IconButton>
                ) : null}
              </Stack>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                PRIORITY
              </Typography>
              <Stack direction="row" spacing={0.5} sx={{ width: '100%' }}>
                {TASK_PRIORITY_SEGMENTS.map((seg) => {
                  const sel = priority === seg.key
                  return (
                    <Box
                      key={seg.key}
                      onClick={() => setPriority(seg.key)}
                      sx={{
                        flex: 1,
                        height: 40,
                        borderRadius: 1.125,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        border: sel ? `1.5px solid ${seg.border}` : `1px solid ${tripooColors.border}`,
                        bgcolor: sel ? seg.bg : tripooColors.surface,
                      }}
                    >
                      <Box
                        sx={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          bgcolor: seg.dot,
                          mr: 0.625,
                          flexShrink: 0,
                        }}
                      />
                      <Typography
                        sx={{
                          fontWeight: 800,
                          fontSize: 12,
                          color: sel ? seg.border : '#8A7560',
                        }}
                      >
                        {seg.label}
                      </Typography>
                    </Box>
                  )
                })}
              </Stack>
            </Box>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                NOTES (OPTIONAL)
              </Typography>
              <TextField
                fullWidth
                multiline
                minRows={2}
                placeholder="Any details or reminders..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    borderRadius: 1.25,
                    alignItems: 'flex-start',
                    '& fieldset': { borderColor: tripooColors.border },
                  },
                }}
              />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 2.25, pb: 2, flexDirection: 'column', gap: 1, alignItems: 'stretch' }}>
          {edit && canModifyTask(edit) ? (
            <Button color="error" variant="outlined" onClick={() => void onDelete(edit)}>
              Delete task
            </Button>
          ) : null}
          <Button variant="contained" fullWidth size="large" onClick={() => void saveTask()} sx={{ fontWeight: 800 }}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Menu
        anchorEl={taskRowMenu?.anchor ?? null}
        open={Boolean(taskRowMenu)}
        onClose={() => setTaskRowMenu(null)}
      >
        {taskRowMenu?.task && canModifyTask(taskRowMenu.task) ? (
          <>
            <MenuItem
              onClick={() => {
                const row = taskRowMenu
                setTaskRowMenu(null)
                if (row) openEdit(row.task)
              }}
            >
              Edit
            </MenuItem>
            <MenuItem
              onClick={() => {
                const row = taskRowMenu
                setTaskRowMenu(null)
                if (row) void onDelete(row.task)
              }}
              sx={{ color: tripooColors.red }}
            >
              Delete
            </MenuItem>
          </>
        ) : null}
      </Menu>
    </>
  )
}
