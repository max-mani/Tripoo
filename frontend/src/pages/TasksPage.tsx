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
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
  Divider,
  LinearProgress,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
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
import { tripooColors } from '../theme'
import { TripTabScaffold } from '../components/TripTabScaffold'
import { photoSrcForDisplay } from '../lib/imageToBase64'
import { letterFromName } from '../lib/avatarIdentity'

type TaskTab = 'all' | 'progress' | 'done'

export default function TasksPage() {
  const { trip } = useOutletContext<{ trip: Trip }>()
  const { tripId } = useParams<{ tripId: string }>()
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<Task[]>([])
  const [members, setMembers] = useState<TripMember[]>([])
  const [tab, setTab] = useState<TaskTab>('all')
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const [edit, setEdit] = useState<Task | null>(null)
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('general')
  const [assignedTo, setAssignedTo] = useState('everyone')
  const [priority, setPriority] = useState('medium')
  const [notes, setNotes] = useState('')
  const [due, setDue] = useState('')
  const [completed, setCompleted] = useState(false)

  useEffect(() => {
    if (!tripId) return
    return subscribeTasks(tripId, setTasks)
  }, [tripId])

  useEffect(() => {
    if (!tripId) return
    return subscribeTripMembers(tripId, setMembers)
  }, [tripId])

  const assignOptions = useMemo(() => {
    const ids = members.map((m) => ({ v: m.userId, l: m.name }))
    return [{ v: 'everyone', l: 'Everyone' }, ...ids]
  }, [members])

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
            color: on ? tripooColors.orange : tripooColors.textSecondary,
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
    <Box
      sx={{
        bgcolor: 'rgba(255,255,255,0.92)',
        px: 2,
        pt: `calc(12px + env(safe-area-inset-top, 0px))`,
        pb: 1.75,
        borderBottom: `1px solid ${tripooColors.border}`,
      }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.4 }}>
        <IconButton
          onClick={() => navigate(`/trips/${tripId}`)}
          aria-label="Back"
          sx={{ color: tripooColors.textPrimary }}
        >
          <ArrowBackIosNewIcon sx={{ fontSize: 16 }} />
        </IconButton>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ flex: 1, minWidth: 0 }}>
          <Box component="img" src="/tripoo-logo.svg" alt="" sx={{ width: 23, height: 23 }} />
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
          sx={{ width: 32, height: 32, bgcolor: '#FDE7D2', color: tripooColors.orange, ml: 0.5 }}
          aria-label="Search"
        >
          <SearchIcon sx={{ fontSize: 18 }} />
        </IconButton>
        <IconButton
          onClick={openNew}
          aria-label="Add task"
          sx={{ width: 32, height: 32, bgcolor: '#FDE7D2', color: tripooColors.orange }}
        >
          <AddIcon sx={{ fontSize: 20 }} />
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

      <TextField
        size="small"
        fullWidth
        placeholder="Search tasks…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mt: 1.25 }}
        InputProps={{ startAdornment: <SearchIcon sx={{ color: tripooColors.textSecondary, mr: 1, fontSize: 20 }} /> }}
      />
    </Box>
  )

  const headerTabs = (
    <Box sx={{ bgcolor: tripooColors.surface, borderBottom: `1px solid ${tripooColors.border}` }}>
      <Stack direction="row" sx={{ height: 46, px: 2 }}>
        {tabBtn('all', 'All')}
        {tabBtn('progress', 'In Progress')}
        {tabBtn('done', 'Done')}
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
        <Box sx={{ px: 2, pt: 1.5 }}>
          <List sx={{ bgcolor: tripooColors.surface, borderRadius: 2, border: `1px solid ${tripooColors.border}` }}>
            {visible.length === 0 ? (
              <ListItem>
                <ListItemText primary="No tasks here" secondary="Add a task or switch tabs" />
              </ListItem>
            ) : (
              visible.map((t) => (
                <Box key={t.id}>
                  <ListItem
                    secondaryAction={
                      <Checkbox edge="end" checked={t.completed} onChange={() => void toggleDone(t)} />
                    }
                  >
                    <ListItemText
                      primary={
                        <Typography sx={{ fontWeight: 700, textDecoration: t.completed ? 'line-through' : 'none' }}>
                          {t.title}
                        </Typography>
                      }
                      secondary={
                        <>
                          <Typography variant="caption" sx={{ display: 'block' }}>
                            {t.category} · {t.priority}
                            {t.dueDate ? ` · Due ${new Date(t.dueDate).toLocaleDateString()}` : ''}
                          </Typography>
                          <Button size="small" onClick={() => openEdit(t)}>
                            Edit
                          </Button>
                        </>
                      }
                    />
                  </ListItem>
                  <Divider component="li" />
                </Box>
              ))
            )}
          </List>
        </Box>
      </TripTabScaffold>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{edit ? 'Edit task' : 'New task'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
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
            <TextField
              select
              label="Assignee"
              fullWidth
              SelectProps={{ native: true }}
              value={assignedTo}
              onChange={(e) => setAssignedTo(e.target.value)}
            >
              {assignOptions.map((o) => (
                <option key={o.v} value={o.v}>
                  {o.l}
                </option>
              ))}
            </TextField>
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
        <DialogActions>
          {edit && (
            <Button color="error" onClick={() => void onDelete(edit)}>
              Delete
            </Button>
          )}
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveTask()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
