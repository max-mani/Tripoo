import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Card,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  LinearProgress,
  Radio,
  RadioGroup,
  Stack,
  Switch,
  TextField,
  Typography,
  Alert,
} from '@mui/material'
import ArrowBackIosNewIcon from '@mui/icons-material/ArrowBackIosNew'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import PersonOutlineIcon from '@mui/icons-material/PersonOutline'
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined'
import PhoneIphoneIcon from '@mui/icons-material/PhoneIphone'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import LuggageIcon from '@mui/icons-material/Luggage'
import PaymentsIcon from '@mui/icons-material/Payments'
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone'
import LanguageIcon from '@mui/icons-material/Language'
import HelpOutlineIcon from '@mui/icons-material/HelpOutline'
import FeedbackOutlinedIcon from '@mui/icons-material/FeedbackOutlined'
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import DeleteForeverIcon from '@mui/icons-material/DeleteForever'
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera'
import {
  EmailAuthProvider,
  reauthenticateWithCredential,
  updatePassword,
  updateEmail,
} from 'firebase/auth'
import { useAuth } from '../context/AuthContext'
import {
  updateProfile,
  updatePreferences,
  updateDocumentEmail,
  updatePhone,
  syncMemberProfileFromUser,
} from '../services/userService'
import { loadProfileStats, type ProfileStats } from '../services/profileStatsService'
import { auth } from '../firebase'
import { fileToProfileBase64, photoSrcForDisplay } from '../lib/imageToBase64'
import { letterFromName } from '../lib/avatarIdentity'
import { tripooColors } from '../theme'

const NOTIFY_KEY = 'tripoo_notifications_on'

const LANG_OPTIONS = [
  { code: 'en', label: 'English' },
  { code: 'ta', label: 'Tamil' },
  { code: 'hi', label: 'Hindi' },
]

const CUR_OPTIONS = ['INR (₹)', 'USD ($)', 'EUR (€)', 'GBP (£)']

function iconBox(bg: string, child: ReactNode) {
  return (
    <Box
      sx={{
        width: 36,
        height: 36,
        borderRadius: 1.25,
        bgcolor: bg,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
      }}
    >
      {child}
    </Box>
  )
}

function RowBtn({
  left,
  title,
  subtitle,
  danger,
  onClick,
}: {
  left: ReactNode
  title: string
  subtitle?: string
  danger?: boolean
  onClick?: () => void
}) {
  return (
    <Box
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick?.()
        }
      }}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        py: 1.6,
        px: 1.75,
        cursor: onClick ? 'pointer' : 'default',
        '&:hover': onClick ? { bgcolor: 'rgba(0,0,0,0.02)' } : {},
      }}
    >
      {left}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography
          sx={{
            fontWeight: 700,
            fontSize: 14,
            color: danger ? tripooColors.red : tripooColors.textPrimary,
          }}
        >
          {title}
        </Typography>
        {subtitle ? (
          <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary, mt: 0.15 }} noWrap>
            {subtitle}
          </Typography>
        ) : null}
      </Box>
      {onClick ? <ChevronRightIcon sx={{ color: tripooColors.textHint, fontSize: 18 }} /> : null}
    </Box>
  )
}

function RowDivider() {
  return <Box sx={{ height: 1, bgcolor: tripooColors.bg, mx: 1.75 }} />
}

export default function ProfilePage() {
  const { firebaseUser, user, signOut, refreshUser } = useAuth()
  const navigate = useNavigate()
  const fileRef = useRef<HTMLInputElement>(null)

  const [stats, setStats] = useState<ProfileStats | null>(null)
  const [statsLoading, setStatsLoading] = useState(true)
  const [notify, setNotify] = useState(() => {
    try {
      const v = localStorage.getItem(NOTIFY_KEY)
      if (v === null) return true
      return v === '1'
    } catch {
      return true
    }
  })

  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)

  const [nameOpen, setNameOpen] = useState(false)
  const [nameDraft, setNameDraft] = useState('')
  const [emailOpen, setEmailOpen] = useState(false)
  const [emailDraft, setEmailDraft] = useState('')
  const [phoneOpen, setPhoneOpen] = useState(false)
  const [phoneDraft, setPhoneDraft] = useState('')
  const [pwOpen, setPwOpen] = useState(false)
  const [pwOld, setPwOld] = useState('')
  const [pwNew, setPwNew] = useState('')
  const [pwConfirm, setPwConfirm] = useState('')
  const [langOpen, setLangOpen] = useState(false)
  const [curOpen, setCurOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const displaySrc = photoSrcForDisplay(user?.photoUrl)
  const letter = user?.avatarLetter?.trim() || letterFromName(user?.name || user?.email || '')
  const heroBg = user?.avatarColorHex?.trim() || tripooColors.orange

  const reloadStats = useCallback(async () => {
    if (!firebaseUser || !user?.tripIds) {
      setStats(null)
      setStatsLoading(false)
      return
    }
    setStatsLoading(true)
    try {
      const s = await loadProfileStats(firebaseUser.uid, user.tripIds)
      setStats(s)
    } finally {
      setStatsLoading(false)
    }
  }, [firebaseUser, user?.tripIds])

  useEffect(() => {
    void reloadStats()
  }, [reloadStats])

  useEffect(() => {
    try {
      localStorage.setItem(NOTIFY_KEY, notify ? '1' : '0')
    } catch {
      /* ignore */
    }
  }, [notify])

  async function onPickPhoto(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (!f || !firebaseUser || !user) return
    try {
      const b64 = await fileToProfileBase64(f)
      await updateProfile(firebaseUser.uid, user.name, b64)
      await syncMemberProfileFromUser(firebaseUser.uid)
      await refreshUser()
      setMsg('Photo updated')
    } catch (ex: unknown) {
      setErr(ex instanceof Error ? ex.message : 'Photo failed')
    }
  }

  async function saveName() {
    if (!firebaseUser || !user) return
    setErr(null)
    try {
      await updateProfile(firebaseUser.uid, nameDraft.trim(), user.photoUrl ?? null)
      await syncMemberProfileFromUser(firebaseUser.uid)
      await refreshUser()
      setNameOpen(false)
      setMsg('Name updated')
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Update failed')
    }
  }

  async function saveEmail() {
    if (!firebaseUser) return
    setErr(null)
    try {
      const cu = auth.currentUser
      if (!cu) throw new Error('Not signed in')
      await updateEmail(cu, emailDraft.trim())
      await updateDocumentEmail(firebaseUser.uid, emailDraft.trim())
      await syncMemberProfileFromUser(firebaseUser.uid)
      await refreshUser()
      setEmailOpen(false)
      setMsg('Email updated')
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Could not update email')
    }
  }

  async function savePhone() {
    if (!firebaseUser) return
    setErr(null)
    try {
      await updatePhone(firebaseUser.uid, phoneDraft)
      await syncMemberProfileFromUser(firebaseUser.uid)
      await refreshUser()
      setPhoneOpen(false)
      setMsg('Phone saved')
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Failed to save phone')
    }
  }

  async function savePassword() {
    const cu = auth.currentUser
    if (!cu || !cu.email) {
      setErr('No email on this account')
      return
    }
    if (pwNew.length < 8) {
      setErr('New password must be at least 8 characters')
      return
    }
    if (pwNew !== pwConfirm) {
      setErr('Passwords do not match')
      return
    }
    setErr(null)
    try {
      const cred = EmailAuthProvider.credential(cu.email, pwOld)
      await reauthenticateWithCredential(cu, cred)
      await updatePassword(cu, pwNew)
      setPwOpen(false)
      setPwOld('')
      setPwNew('')
      setPwConfirm('')
      setMsg('Password updated')
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Could not update password')
    }
  }

  function langLabel(code: string | null | undefined) {
    const c = code?.trim()
    const f = LANG_OPTIONS.find((x) => x.code === c)
    return f?.label ?? 'English'
  }

  async function saveLangCode(code: string) {
    if (!firebaseUser) return
    await updatePreferences(firebaseUser.uid, code, null)
    await refreshUser()
    setLangOpen(false)
    setMsg('Language saved')
  }

  async function saveCurrency(cur: string) {
    if (!firebaseUser) return
    await updatePreferences(firebaseUser.uid, null, cur)
    await refreshUser()
    setCurOpen(false)
    setMsg('Currency saved')
  }

  const phoneDisplay = user?.phoneNumber?.trim() || firebaseUser?.phoneNumber || 'Not set'

  return (
    <Box sx={{ minHeight: '100dvh', bgcolor: tripooColors.bg, pb: 3 }}>
      <Box
        sx={{
          flexShrink: 0,
          bgcolor: tripooColors.surface,
          px: 2,
          pt: `calc(10px + env(safe-area-inset-top, 0px))`,
          pb: 1.25,
          display: 'flex',
          alignItems: 'center',
          borderBottom: `1px solid ${tripooColors.border}`,
        }}
      >
        <IconButton
          onClick={() => navigate('/dashboard')}
          sx={{
            width: 36,
            height: 36,
            bgcolor: '#FDE7D2',
            color: tripooColors.orange,
            mr: 1,
            '&:hover': { bgcolor: '#FCD9B8' },
          }}
          aria-label="Back"
        >
          <ArrowBackIosNewIcon sx={{ fontSize: 16, ml: 0.5 }} />
        </IconButton>
        <Typography sx={{ fontWeight: 800, fontSize: 18 }}>Profile</Typography>
      </Box>

      <Box sx={{ flex: 1, overflowY: 'auto', WebkitOverflowScrolling: 'touch' }}>
        {msg && (
          <Alert severity="success" sx={{ m: 2, mb: 0 }} onClose={() => setMsg(null)}>
            {msg}
          </Alert>
        )}
        {err && (
          <Alert severity="error" sx={{ m: 2, mb: 0 }} onClose={() => setErr(null)}>
            {err}
          </Alert>
        )}

        <Box
          sx={{
            background: `linear-gradient(165deg, ${tripooColors.orange} 0%, ${tripooColors.orangeDark} 55%, #9A4A12 100%)`,
            pt: 3,
            pb: 3,
            px: 2,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          <Box
            sx={{
              position: 'absolute',
              right: -40,
              top: -20,
              width: 220,
              height: 220,
              borderRadius: '50%',
              bgcolor: 'rgba(255,255,255,0.07)',
            }}
          />
          <Stack alignItems="center" sx={{ position: 'relative' }}>
            <Box
              sx={{
                width: 90,
                height: 90,
                borderRadius: '50%',
                border: '3px solid rgba(255,255,255,0.5)',
                overflow: 'hidden',
                position: 'relative',
                bgcolor: 'rgba(255,255,255,0.15)',
              }}
            >
              {displaySrc ? (
                <Box component="img" src={displaySrc} alt="" sx={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <Typography
                  sx={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 800,
                    fontSize: 28,
                    color: tripooColors.textPrimary,
                    bgcolor: `${heroBg}cc`,
                  }}
                >
                  {letter}
                </Typography>
              )}
              <Box
                onClick={() => fileRef.current?.click()}
                role="button"
                tabIndex={0}
                sx={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  bottom: 0,
                  height: 28,
                  bgcolor: 'rgba(0,0,0,0.45)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                }}
              >
                <PhotoCameraIcon sx={{ color: '#fff', fontSize: 16 }} />
              </Box>
              <input ref={fileRef} type="file" accept="image/*" hidden onChange={(e) => void onPickPhoto(e)} />
            </Box>
            <Typography sx={{ fontWeight: 800, fontSize: 22, color: '#fff', mt: 1.5 }}>
              {user?.name || '—'}
            </Typography>
            <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.78)', mt: 0.35 }}>
              {user?.email || firebaseUser?.email || ''}
            </Typography>

            {statsLoading ? (
              <LinearProgress sx={{ width: '100%', maxWidth: 280, mt: 2, borderRadius: 99 }} />
            ) : (
              <Stack direction="row" spacing={4} sx={{ mt: 2.25 }}>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography sx={{ fontWeight: 800, fontSize: 22, color: '#fff' }}>
                    {stats?.tripCount ?? 0}
                  </Typography>
                  <Typography sx={{ fontWeight: 700, fontSize: 13, color: 'rgba(255,255,255,0.65)' }}>Trips</Typography>
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography sx={{ fontWeight: 800, fontSize: 22, color: '#fff' }}>
                    {stats?.spentCompact ?? '₹0'}
                  </Typography>
                  <Typography sx={{ fontWeight: 700, fontSize: 13, color: 'rgba(255,255,255,0.65)' }}>Spent</Typography>
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography sx={{ fontWeight: 800, fontSize: 22, color: '#fff' }}>
                    {stats?.friendsUnique ?? 0}
                  </Typography>
                  <Typography sx={{ fontWeight: 700, fontSize: 13, color: 'rgba(255,255,255,0.65)' }}>Friends</Typography>
                </Box>
              </Stack>
            )}
          </Stack>
        </Box>

        <Box sx={{ px: 2 }}>
          <Typography sx={{ ...sectionLabel, mt: 2 }}>Account</Typography>
          <Card variant="outlined" sx={{ borderRadius: 1.5, borderColor: tripooColors.border, boxShadow: 'none' }}>
            <RowBtn
              left={iconBox('#FFF3E6', <PersonOutlineIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Full Name"
              subtitle={user?.name}
              onClick={() => {
                setNameDraft(user?.name ?? '')
                setNameOpen(true)
              }}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <EmailOutlinedIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Email"
              subtitle={user?.email}
              onClick={() => {
                setEmailDraft(user?.email ?? firebaseUser?.email ?? '')
                setEmailOpen(true)
              }}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <PhoneIphoneIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Phone Number"
              subtitle={phoneDisplay}
              onClick={() => {
                setPhoneDraft(user?.phoneNumber ?? '')
                setPhoneOpen(true)
              }}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <LockOutlinedIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Change Password"
              onClick={() => {
                setPwOld('')
                setPwNew('')
                setPwConfirm('')
                setPwOpen(true)
              }}
            />
          </Card>

          <Typography sx={{ ...sectionLabel, mt: 2 }}>Trip History</Typography>
          <Card variant="outlined" sx={{ borderRadius: 1.5, borderColor: tripooColors.border, boxShadow: 'none' }}>
            <RowBtn
              left={iconBox('#E8F5EA', <LuggageIcon sx={{ color: '#2A5E35', fontSize: 20 }} />)}
              title="My Trips"
              subtitle={
                stats
                  ? `${stats.tripCount} trips · ${stats.activeTripCount} active`
                  : `${user?.tripIds?.length ?? 0} trips`
              }
              onClick={() => navigate('/dashboard')}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF8E6', <PaymentsIcon sx={{ color: '#A16207', fontSize: 20 }} />)}
              title="Total Spent"
              subtitle={stats ? `${stats.spentFullInr} across all trips` : '—'}
              onClick={() => navigate('/dashboard')}
            />
          </Card>

          <Typography sx={{ ...sectionLabel, mt: 2 }}>Preferences</Typography>
          <Card variant="outlined" sx={{ borderRadius: 1.5, borderColor: tripooColors.border, boxShadow: 'none' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 1.6, px: 1.75 }}>
              {iconBox('#FFF3E6', <NotificationsNoneIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              <Box sx={{ flex: 1 }}>
                <Typography sx={{ fontWeight: 700, fontSize: 14 }}>Notifications</Typography>
                <Typography sx={{ fontSize: 11, color: tripooColors.textSecondary }}>
                  Expenses, tasks, trip updates
                </Typography>
              </Box>
              <Switch checked={notify} onChange={(_, c) => setNotify(c)} color="warning" />
            </Box>
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <LanguageIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Language"
              subtitle={langLabel(user?.preferredLanguage)}
              onClick={() => setLangOpen(true)}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <PaymentsIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Default Currency"
              subtitle={user?.preferredCurrency?.trim() || 'INR (₹)'}
              onClick={() => setCurOpen(true)}
            />
          </Card>

          <Typography sx={{ ...sectionLabel, mt: 2 }}>Support</Typography>
          <Card variant="outlined" sx={{ borderRadius: 1.5, borderColor: tripooColors.border, boxShadow: 'none', mb: 2 }}>
            <RowBtn
              left={iconBox('#FFF3E6', <HelpOutlineIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Help & FAQ"
              onClick={() => window.open('https://github.com/max-mani/Tripoo', '_blank', 'noopener,noreferrer')}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <FeedbackOutlinedIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Send Feedback"
              onClick={() => window.open('https://github.com/max-mani/Tripoo/issues', '_blank', 'noopener,noreferrer')}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FFF3E6', <ShieldOutlinedIcon sx={{ color: tripooColors.orange, fontSize: 20 }} />)}
              title="Privacy Policy"
              onClick={() => window.open('https://github.com/max-mani/Tripoo', '_blank', 'noopener,noreferrer')}
            />
          </Card>

          <Card variant="outlined" sx={{ borderRadius: 1.5, borderColor: tripooColors.border, boxShadow: 'none', mb: 2 }}>
            <RowBtn
              left={iconBox('#FEE2E2', <LogoutIcon sx={{ color: tripooColors.red, fontSize: 20 }} />)}
              title="Log Out"
              danger
              onClick={() => void signOut().then(() => navigate('/login'))}
            />
            <RowDivider />
            <RowBtn
              left={iconBox('#FEE2E2', <DeleteForeverIcon sx={{ color: tripooColors.red, fontSize: 20 }} />)}
              title="Delete Account"
              danger
              onClick={() => setDeleteOpen(true)}
            />
          </Card>

          <Button
            variant="outlined"
            fullWidth
            startIcon={<ArrowBackIosNewIcon sx={{ fontSize: 14 }} />}
            onClick={() => navigate('/dashboard')}
            sx={{ borderColor: tripooColors.border, color: tripooColors.textPrimary, py: 1.1, fontWeight: 600 }}
          >
            Back to Dashboard
          </Button>
        </Box>
      </Box>

      <Dialog open={nameOpen} onClose={() => setNameOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Edit name</DialogTitle>
        <DialogContent>
          <TextField autoFocus fullWidth label="Full name" value={nameDraft} onChange={(e) => setNameDraft(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setNameOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveName()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={emailOpen} onClose={() => setEmailOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Edit email</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            type="email"
            label="Email"
            value={emailDraft}
            onChange={(e) => setEmailDraft(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEmailOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void saveEmail()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={phoneOpen} onClose={() => setPhoneOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Phone number</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            label="Phone"
            value={phoneDraft}
            onChange={(e) => setPhoneDraft(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPhoneOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void savePhone()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={pwOpen} onClose={() => setPwOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Change password</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <TextField type="password" label="Current password" fullWidth value={pwOld} onChange={(e) => setPwOld(e.target.value)} />
            <TextField type="password" label="New password" fullWidth value={pwNew} onChange={(e) => setPwNew(e.target.value)} />
            <TextField type="password" label="Confirm new password" fullWidth value={pwConfirm} onChange={(e) => setPwConfirm(e.target.value)} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPwOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void savePassword()}>
            Update
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={langOpen} onClose={() => setLangOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Language</DialogTitle>
        <DialogContent>
          <RadioGroup
            value={user?.preferredLanguage ?? 'en'}
            onChange={(e) => void saveLangCode(e.target.value)}
          >
            {LANG_OPTIONS.map((o) => (
              <FormControlLabel key={o.code} value={o.code} control={<Radio color="warning" />} label={o.label} />
            ))}
          </RadioGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLangOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={curOpen} onClose={() => setCurOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Default currency</DialogTitle>
        <DialogContent>
          <RadioGroup
            value={user?.preferredCurrency?.trim() || 'INR (₹)'}
            onChange={(e) => void saveCurrency(e.target.value)}
          >
            {CUR_OPTIONS.map((c) => (
              <FormControlLabel key={c} value={c} control={<Radio color="warning" />} label={c} />
            ))}
          </RadioGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCurOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}>
        <DialogTitle>Delete account</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Full account deletion (including trip cleanup) is available in the Ulla Android app. On the web you can
            sign out and contact support if you need your data removed.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)}>OK</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

const sectionLabel = {
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: 0.8,
  color: tripooColors.textSecondary,
  textTransform: 'uppercase' as const,
  mb: 1.25,
  mt: 0,
}
