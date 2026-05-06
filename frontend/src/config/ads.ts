/**
 * Same publisher / units as Android res/values/strings.xml (AdMob).
 * Web uses AdSense `adsbygoogle`. AdMob **banner unit IDs usually do not fill** on websites — create
 * **AdSense → Ads → Display units** for your deployed domain and set VITE_ADSENSE_* accordingly.
 */
export const ADSENSE_CLIENT =
  import.meta.env.VITE_ADSENSE_CLIENT || 'ca-pub-2688489242058123'

/** Google test requests (empty on unapproved sites; useful while debugging). */
export const ADSENSE_TEST = import.meta.env.VITE_ADSENSE_TEST === 'true'

/** Show dashed placeholder instead of AdSense (layout check without a live unit). */
export const ADSENSE_PLACEHOLDER = import.meta.env.VITE_ADSENSE_PLACEHOLDER === '1'

/** @string/admob_banner_dashboard */
export const AD_SLOT_DASHBOARD =
  import.meta.env.VITE_ADSENSE_DASHBOARD_SLOT || '8610991523'

/** @string/admob_banner_trip_group */
export const AD_SLOT_TRIP =
  import.meta.env.VITE_ADSENSE_TRIP_SLOT || '3765934933'
