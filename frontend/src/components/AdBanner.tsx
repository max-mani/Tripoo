import { useEffect, useRef } from 'react'
import { Box } from '@mui/material'
import { ADSENSE_CLIENT } from '../config/ads'

declare global {
  interface Window {
    adsbygoogle?: Record<string, unknown>[]
  }
}

type Props = {
  adSlot: string
  /** Min height avoids layout jump while ad loads */
  minHeight?: number
}

/**
 * Responsive display ad (AdSense). Uses the same pub id as the Android AdMob app id prefix.
 */
export function AdBanner({ adSlot, minHeight = 90 }: Props) {
  const insRef = useRef<HTMLModElement>(null)
  const doneRef = useRef(false)

  useEffect(() => {
    if (!ADSENSE_CLIENT || !adSlot) return

    const push = () => {
      if (doneRef.current || !insRef.current) return
      try {
        window.adsbygoogle = window.adsbygoogle || []
        window.adsbygoogle.push({})
        doneRef.current = true
      } catch {
        /* filled slot or strict mode double-run */
      }
    }

    const existing = document.querySelector(
      'script[src^="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"]',
    ) as HTMLScriptElement | null

    if (existing) {
      const t = window.setTimeout(push, 0)
      return () => window.clearTimeout(t)
    }

    const script = document.createElement('script')
    script.async = true
    script.crossOrigin = 'anonymous'
    script.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT}`
    script.onload = () => push()
    document.head.appendChild(script)
    return () => {
      /* keep script for other banners */
    }
  }, [adSlot])

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
        ref={insRef}
        className="adsbygoogle"
        style={{ display: 'block', textAlign: 'center', width: '100%', minHeight }}
        data-ad-client={ADSENSE_CLIENT}
        data-ad-slot={adSlot}
        data-ad-format="auto"
        data-full-width-responsive="true"
      />
    </Box>
  )
}
