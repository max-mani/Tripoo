import { useEffect, useState } from 'react'
import { Box } from '@mui/material'
import { SplashBranding } from './SplashBranding'

const SPLASH_MS = 1400

/** Shows startup splash on every full reload (fixed overlay). */
export function SplashOverlay() {
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    const id = window.setTimeout(() => setVisible(false), SPLASH_MS)
    return () => window.clearTimeout(id)
  }, [])

  if (!visible) return null

  return (
    <Box
      sx={{
        position: 'fixed',
        inset: 0,
        zIndex: 10000,
        pointerEvents: 'auto',
      }}
    >
      <SplashBranding />
    </Box>
  )
}
