/** Mirror of Android ProfileViewModel format helpers (en-IN grouping). */

export function formatInrCompact(amount: number): string {
  if (amount <= 0) return '₹0'
  const lac = 100_000
  const cr = 10_000_000
  if (amount >= cr) return `₹${(amount / cr).toFixed(1)}Cr`
  if (amount >= lac) return `₹${(amount / lac).toFixed(1)}L`
  if (amount >= 1000) return `₹${(amount / 1000).toFixed(1)}k`
  return `₹${Math.round(amount)}`
}

export function formatInrFull(amount: number): string {
  const sym = Math.round(amount)
  return `₹${sym.toLocaleString('en-IN')}`
}
