/** Space for fixed bottom nav + home indicator (no ads). */
export const TRIP_NAV_BASE_PX = 56

export const SCROLL_PAD_BELOW_TRIP_NAV = `calc(${TRIP_NAV_BASE_PX}px + env(safe-area-inset-bottom, 0px) + 16px)`

/** FAB position: 18dp above bottom nav (matches `fragment_expenses` / `fragment_tasks` without ad). */
export const FAB_BOTTOM_FROM_VIEWPORT = `calc(${TRIP_NAV_BASE_PX}px + env(safe-area-inset-bottom, 0px) + 18px)`
