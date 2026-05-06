/** Match Android `geo:0,0?q=` → open Google Maps with destination query. */
export function openDestinationInMaps(destination: string): void {
  const q = destination.trim()
  if (!q) return
  const encoded = encodeURIComponent(q)
  window.open(`https://www.google.com/maps/search/?api=1&query=${encoded}`, '_blank', 'noopener,noreferrer')
}
