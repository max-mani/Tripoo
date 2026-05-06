import { createTheme } from '@mui/material/styles'

export const tripooColors = {
  orange: '#F48C25',
  orangeDark: '#C45E1A',
  bg: '#F8F7F5',
  surface: '#FFFFFF',
  textPrimary: '#181411',
  textSecondary: '#8A7560',
  textHint: '#9CA3AF',
  border: '#E6E0DB',
  red: '#DC2626',
  green: '#16A34A',
  blue: '#2563EB',
}

export const tripooTheme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: tripooColors.orange, dark: tripooColors.orangeDark },
    secondary: { main: tripooColors.orange },
    error: { main: tripooColors.red },
    success: { main: tripooColors.green },
    background: { default: tripooColors.bg, paper: tripooColors.surface },
    text: {
      primary: tripooColors.textPrimary,
      secondary: tripooColors.textSecondary,
    },
    divider: '#F3F4F6',
  },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { textTransform: 'none', borderRadius: 12, fontWeight: 600 },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: 16, boxShadow: '0 1px 3px rgba(24,20,17,0.06)' },
      },
    },
    MuiTextField: {
      defaultProps: { variant: 'outlined' },
    },
  },
})
