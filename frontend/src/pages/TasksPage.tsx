import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import {
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  Fab,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
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
import {
  addTask,
  deleteTask,
  subscribeTasks,
  updateTask,
  updateTaskCompletion,
} from '../services/taskService'
import { subscribeTripMembers } from '../services/tripService'
import type { Task, Trip, TripMember } from '../types/models'
import { TASK_CATEGORIES, TASK_PRIORITIES } from '../lib/constants'
import { bgForSeed, letterFromName, textColorForSeed } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'
import { TripTabScaffold } from '../components/TripTabScaffold'
import { FAB_BOTTOM_FROM_VIEWPORT } from '../lib/tripChrome'
import { TripooRocketLogo } from '../components/TripooRocketLogo'
import { photoSrcForDisplay } from '../lib/imageToBase64'

type TaskTab = 'all' | 'progress' | 'done'

function AssigneePickerCircle({
  selected,
  onClick,
  size,
  label,
  children,
}: {
  selected: boolean
  onClick: () => void
  size: number
  label: string
  children: ReactNode
}) {
  return (
    <Box onClick={onClick} sx={{ cursor: 'pointer', textAlign: 'center', flex: '0 0 auto' }}>
      <Box
        sx={{
          width: size,
          height: size,
          borderRadius: '50%',
          border: selected ? `3px solid ${tripooColors.orange}` : `2px solid ${tripooColors.border}`,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          mx: 'auto',
          bgcolor: tripooColors.surface,
        }}
      >
        {children}
      </Box>
      <Typography sx={{ fontSize: 10, fontWeight: 700, mt: 0.35, maxWidth: size + 28 }} noWrap>
        {label}
      </Typography>
    </Box>
  )
}

export default function TasksPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<Task[]>([])
  const [members, setMembers] = useState<TripMember[]>([])
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
  const [due, setDue] = useState('')
  const [completed, setCompleted] = useState(false)
  const [taskRowMenu, setTaskRowMenu] = useState<null | { anchor: HTMLElement; task: Task }>(null)

  useEffect(() => {
    if (!tripId) return
    return subscribeTasks(tripId, setTasks)
  }, [tripId])

  useEffect(() => {
    if (!tripId) return
    return subscribeTripMembers(tripId, setMembers)
  }, [tripId])

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

  const avatarStack = useMemo(() => members.slice(0, 4), [members])

  function openNew() {
    setEdit(null)
    setTitle('')
    setCategory('general')
    setAssignedTo('everyone')
    setPriority('medium')
    setNotes('')
    setDue('')
    setCompleted(false)
    setOpen(true)
  }

  function openEdit(t: Task) {
    setEdit(t)
    setTitle(t.title)
    setCategory(t.category)
    setAssignedTo(t.assignedTo)
    setPriority(t.priority)
    setNotes(t.notes ?? '')
    setDue(t.dueDate ? new Date(t.dueDate).toISOString().slice(0, 10) : '')
    setCompleted(t.completed)
    setOpen(true)
  }

  async function saveTask() {
    if (!tripId) return
    const dueMs = due ? new Date(due).getTime() : null
    const body: Omit<Task, 'id'> = {
      title: title.trim(),
      category,
      assignedTo,
      completed,
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
    if (!tripId) return
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
            <TripooRocketLogo size={23} color={tripooColors.orange} />
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
                        <IconButton
                          size="small"
                          aria-label="Task options"
                          onClick={(e) => setTaskRowMenu({ anchor: e.currentTarget, task: t })}
                        >
                          <MoreHorizIcon sx={{ color: tripooColors.textHint, fontSize: 22 }} />
                        </IconButton>
                        <Checkbox edge="end" checked={t.completed} onChange={() => void toggleDone(t)} />
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
            <TextField label="Title" fullWidth value={title} onChange={(e) => setTitle(e.target.value)} />
            <TextField
              select
              label="Category"
              fullWidth
              SelectProps={{ native: true }}
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              {TASK_CATEGORIES.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </TextField>
            <Box>
              <Typography sx={{ fontSize: 10, fontWeight: 800, letterSpacing: 1, color: tripooColors.textSecondary, mb: 0.9 }}>
                ASSIGNED TO
              </Typography>
              <Stack direction="row" spacing={1.25} sx={{ overflowX: 'auto', pb: 0.5, pt: 0.25 }}>
                <AssigneePickerCircle
                  selected={assignedTo === 'everyone'}
                  size={48}
                  label="Everyone"
                  onClick={() => setAssignedTo('everyone')}
                >
                  <Box
                    sx={{
                      width: 1,
                      height: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      bgcolor: 'rgba(244,140,37,0.12)',
                    }}
                  >
                    <GroupsIcon sx={{ color: tripooColors.orange, fontSize: 26 }} />
                  </Box>
                </AssigneePickerCircle>
                {members.map((m) => {
                  const src = photoSrcForDisplay(m.photoUrl)
                  const letter = m.avatarLetter?.trim() || letterFromName(m.name)
                  const bg = m.avatarColorHex?.trim() || bgForSeed(m.userId)
                  const tc = textColorForSeed(m.userId)
                  const sel = assignedTo === m.userId
                  const shortName = m.name.split(/\s+/)[0] ?? m.name
                  return (
                    <AssigneePickerCircle
                      key={m.userId}
                      selected={sel}
                      size={48}
                      label={shortName}
                      onClick={() => setAssignedTo(m.userId)}
                    >
                      {src ? (
                        <Box component="img" src={src} alt="" sx={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        <Typography
                          sx={{
                            fontWeight: 900,
                            fontSize: 18,
                            color: tc,
                            bgcolor: bg,
                            width: 1,
                            height: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          {letter}
                        </Typography>
                      )}
                    </AssigneePickerCircle>
                  )
                })}
              </Stack>
            </Box>
            <TextField
              select
              label="Priority"
              fullWidth
              SelectProps={{ native: true }}
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
            >
              {TASK_PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </TextField>
            <TextField
              label="Due date"
              type="date"
              fullWidth
              InputLabelProps={{ shrink: true }}
              value={due}
              onChange={(e) => setDue(e.target.value)}
            />
            <TextField label="Notes" fullWidth multiline minRows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
            <FormControlLabel
              control={<Checkbox checked={completed} onChange={(e) => setCompleted(e.target.checked)} />}
              label="Completed"
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 2.25, pb: 2, flexDirection: 'column', gap: 1, alignItems: 'stretch' }}>
          {edit ? (
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
      </Menu>
    </>
  )
}
