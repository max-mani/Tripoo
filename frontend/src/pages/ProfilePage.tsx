import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AppBar,
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  IconButton,
  Stack,
  TextField,
  Toolbar,
  Typography,
  Alert,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { updateEmail } from 'firebase/auth'
import { useAuth } from '../context/AuthContext'
import {
  updateProfile,
  updatePreferences,
  updateDocumentEmail,
  updatePhone,
  syncMemberProfileFromUser,
} from '../services/userService'
import { auth } from '../firebase'
import { fileToProfileBase64, photoSrcForDisplay } from '../lib/imageToBase64'
import { letterFromName } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'

const LANGS = ['en', 'ta', 'hi']
const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP']

export default function ProfilePage() {
  const { firebaseUser, user, signOut, refreshUser } = useAuth()
  const navigate = useNavigate()
  const fileRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState(user?.name ?? '')
  const [email, setEmail] = useState(user?.email ?? firebaseUser?.email ?? '')
  const [phone, setPhone] = useState(user?.phoneNumber ?? '')
  const [language, setLanguage] = useState(user?.preferredLanguage ?? 'en')
  const [currency, setCurrency] = useState(user?.preferredCurrency ?? 'INR')
  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!user) return
    setName(user.name)
    setEmail(user.email)
    setPhone(user.phoneNumber ?? '')
    setLanguage(user.preferredLanguage ?? 'en')
    setCurrency(user.preferredCurrency ?? 'INR')
  }, [user])

  const displaySrc = photoSrcForDisplay(user?.photoUrl)

  async function saveProfile() {
    if (!firebaseUser) return
    setErr(null)
    setMsg(null)
    setSaving(true)
    try {
      const uid = firebaseUser.uid
      await updateProfile(uid, name, user?.photoUrl ?? null)
      if (user?.email !== email.trim() && email.trim()) {
        const cu = auth.currentUser
        if (!cu) throw new Error('Not signed in')
        await updateEmail(cu, email.trim())
        await updateDocumentEmail(uid, email.trim())
      }
      if ((user?.phoneNumber ?? '') !== phone.trim()) {
        await updatePhone(uid, phone)
      }
      await updatePreferences(uid, language || null, currency || null)
      await syncMemberProfileFromUser(uid)
      await refreshUser()
      setMsg('Profile updated')
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  async function onPickPhoto(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (!f || !firebaseUser) return
    try {
      const b64 = await fileToProfileBase64(f)
      await updateProfile(
        firebaseUser.uid,
        name.trim() || user?.name || 'User',
        b64,
      )
      await syncMemberProfileFromUser(firebaseUser.uid)
      await refreshUser()
      setMsg('Photo updated')
    } catch (ex: unknown) {
      setErr(ex instanceof Error ? ex.message : 'Photo failed')
    }
  }

  const letter = user?.avatarLetter?.trim() || letterFromName(name || user?.name)

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
          <IconButton edge="start" onClick={() => navigate('/dashboard')} sx={{ color: 'inherit' }}>
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700 }}>
            Profile
          </Typography>
        </Toolbar>
      </AppBar>

      <Box sx={{ p: 2, maxWidth: 560, mx: 'auto' }}>
        {msg && (
          <Alert severity="success" sx={{ mb: 2 }} onClose={() => setMsg(null)}>
            {msg}
          </Alert>
        )}
        {err && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErr(null)}>
            {err}
          </Alert>
        )}

        <Card sx={{ borderRadius: 3, mb: 2 }}>
          <CardContent>
            <Stack alignItems="center" spacing={2}>
              {displaySrc ? (
                <Avatar src={displaySrc} sx={{ width: 96, height: 96 }} />
              ) : (
                <Avatar
                  sx={{
                    width: 96,
                    height: 96,
                    bgcolor: user?.avatarColorHex || tripooColors.orange,
                    fontSize: 36,
                    fontWeight: 800,
                  }}
                >
                  {letter}
                </Avatar>
              )}
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                hidden
                onChange={(e) => void onPickPhoto(e)}
              />
              <Button variant="outlined" onClick={() => fileRef.current?.click()}>
                Change photo
              </Button>
            </Stack>
          </CardContent>
        </Card>

        <Stack spacing={2}>
          <TextField label="Name" fullWidth value={name} onChange={(e) => setName(e.target.value)} />
          <TextField
            label="Email"
            type="email"
            fullWidth
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <TextField label="Phone" fullWidth value={phone} onChange={(e) => setPhone(e.target.value)} />
          <TextField
            select
            label="Language"
            fullWidth
            SelectProps={{ native: true }}
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
          >
            {LANGS.map((l) => (
              <option key={l} value={l}>
                {l.toUpperCase()}
              </option>
            ))}
          </TextField>
          <TextField
            select
            label="Currency"
            fullWidth
            SelectProps={{ native: true }}
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
          >
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </TextField>
          <Button variant="contained" size="large" disabled={saving} onClick={() => void saveProfile()}>
            Save
          </Button>
          <Button color="inherit" onClick={() => void signOut().then(() => navigate('/login'))}>
            Sign out
          </Button>
        </Stack>
      </Box>
    </>
  )
}
