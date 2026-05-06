import { useLayoutEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { Box, Typography } from '@mui/material'
import { ADSENSE_CLIENT, ADSENSE_PLACEHOLDER, ADSENSE_TEST } from '../config/ads'

declare global {
  interface Window {
    adsbygoogle?: Record<string, unknown>[]
  }
}

type Props = {
  adSlot: string
  /** Min height avoids layout jump while ad loads */
  minHeight?: number
  /**
   * Fresh `ins` per logical placement (SPA route changes).
   * Defaults to `location.pathname` + slot.
   */
  instanceKey?: string
}

/**
 * Responsive display ad (AdSense). AdMob numeric unit IDs often will not fill on web until you
 * create real **AdSense → Ads → Display units** for this site URL and paste those slot IDs into env.
 */
export function AdBanner({ adSlot, minHeight = 90, instanceKey }: Props) {
  const location = useLocation()
  const mountId = instanceKey ?? `${location.pathname}:${adSlot}`
  const insRef = useRef<HTMLModElement>(null)
  const pushedRef = useRef(false)

  useLayoutEffect(() => {
    pushedRef.current = false
  }, [mountId])

  useLayoutEffect(() => {
    if (ADSENSE_PLACEHOLDER || !ADSENSE_CLIENT || !adSlot) return
    const ins = insRef.current
    if (!ins) return

    const tryPush = () => {
      if (pushedRef.current) return
      const status = ins.getAttribute('data-adsbygoogle-status')
      if (status === 'done' || status === 'filled') return
      try {
        window.adsbygoogle = window.adsbygoogle || []
        window.adsbygoogle.push({})
        pushedRef.current = true
      } catch {
        /* slot already filled or duplicate request */
      }
    }

    const schedulePush = () => {
      requestAnimationFrame(() => requestAnimationFrame(tryPush))
    }

    const existing = document.querySelector(
      'script[src^="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"]',
    )
    if (existing) {
      schedulePush()
      return
    }

    const script = document.createElement('script')
    script.async = true
    script.crossOrigin = 'anonymous'
    script.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT}`
    script.onload = schedulePush
    document.head.appendChild(script)
  }, [adSlot, mountId])

  if (ADSENSE_PLACEHOLDER) {
    return (
      <Box
        sx={{
          minHeight,
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'rgba(0,0,0,0.04)',
          border: '1px dashed',
          borderColor: 'divider',
          boxSizing: 'border-box',
        }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ px: 2, textAlign: 'center' }}>
          Ad space (set VITE_ADSENSE_PLACEHOLDER=0 and add web AdSense units to show live ads)
        </Typography>
      </Box>
    )
  }

  if (!ADSENSE_CLIENT || !adSlot) return null

  return (
    <Box
      sx={{
        minHeight,
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        width: '100%',
        bgcolor: 'background.default',
        overflow: 'hidden',
      }}
    >
      <ins
        key={mountId}
        ref={insRef}
        className="adsbygoogle"
        style={{ display: 'block', textAlign: 'center', width: '100%', minHeight }}
        data-ad-client={ADSENSE_CLIENT}
        data-ad-slot={adSlot}
        data-ad-format="auto"
        data-full-width-responsive="true"
        {...(ADSENSE_TEST ? { 'data-adtest': 'on' as const } : {})}
      />
    </Box>
  )
}
