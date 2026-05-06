/**
 * Same publisher / units as Android res/values/strings.xml (AdMob).
 * Web uses AdSense `adsbygoogle` — link AdMob to AdSense or create matching display units if empty ads.
 */
export const ADSENSE_CLIENT =
  import.meta.env.VITE_ADSENSE_CLIENT || 'ca-pub-2688489242058123'

/** @string/admob_banner_dashboard */
export const AD_SLOT_DASHBOARD =
  import.meta.env.VITE_ADSENSE_DASHBOARD_SLOT || '8610991523'

/** @string/admob_banner_trip_group */
export const AD_SLOT_TRIP =
  import.meta.env.VITE_ADSENSE_TRIP_SLOT || '3765934933'
