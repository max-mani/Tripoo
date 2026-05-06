import type { ReactNode } from 'react'
import { Box } from '@mui/material'
import { SCROLL_PAD_BELOW_TRIP_NAV } from '../lib/tripChrome'
import { tripooColors } from '../theme'

type Props = {
  header: ReactNode
  children: ReactNode
}

/** Fixed header + scrollable body (fills trip tab area above bottom nav + banner). */
export function TripTabScaffold({ header, children }: Props) {
  return (
    <Box
      sx={{
        flex: 1,
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        bgcolor: tripooColors.bg,
      }}
    >
      <Box sx={{ flexShrink: 0 }}>{header}</Box>
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
          pb: SCROLL_PAD_BELOW_TRIP_NAV,
        }}
      >
        {children}
      </Box>
    </Box>
  )
}
