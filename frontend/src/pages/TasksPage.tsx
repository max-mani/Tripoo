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
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Toolbar,
  Typography,
  Divider,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import AddIcon from '@mui/icons-material/Add'
import {
  addTask,
  deleteTask,
  subscribeTasks,
  updateTask,
  updateTaskCompletion,
} from '../services/taskService'
import { subscribeTripMembers } from '../services/tripService'
import type { Task, TripMember } from '../types/models'
import { TASK_CATEGORIES, TASK_PRIORITIES } from '../lib/constants'
import { tripooColors } from '../theme'

export default function TasksPage() {
  const { tripId } = useParams<{ tripId: string }>()
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<Task[]>([])
  const [members, setMembers] = useState<TripMember[]>([])
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
            Tasks
          </Typography>
          <IconButton color="primary" onClick={openNew} aria-label="Add task">
            <AddIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2 }}>
        <List sx={{ bgcolor: 'background.paper', borderRadius: 3 }}>
          {tasks.length === 0 ? (
            <ListItem>
              <ListItemText primary="No tasks yet" />
            </ListItem>
          ) : (
            tasks.map((t) => (
              <Box key={t.id}>
                <ListItem
                  secondaryAction={
                    <Checkbox edge="end" checked={t.completed} onChange={() => void toggleDone(t)} />
                  }
                >
                  <ListItemText
                    primary={
                      <Typography fontWeight={700} sx={{ textDecoration: t.completed ? 'line-through' : 'none' }}>
                        {t.title}
                      </Typography>
                    }
                    secondary={
                      <>
                        <Typography variant="caption" display="block">
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
