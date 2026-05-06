/**
 * Center-crop square, scale to maxEdge px, JPEG ~quality 0.82 — aligned with Android ImageUtils.
 */
export function fileToProfileBase64(file: File, maxEdge = 384, quality = 0.82): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      try {
        const { width: w, height: h } = img
        const side = Math.min(w, h)
        const sx = Math.floor((w - side) / 2)
        const sy = Math.floor((h - side) / 2)
        const canvas = document.createElement('canvas')
        canvas.width = maxEdge
        canvas.height = maxEdge
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          reject(new Error('Canvas not supported'))
          return
        }
        ctx.drawImage(img, sx, sy, side, side, 0, 0, maxEdge, maxEdge)
        const dataUrl = canvas.toDataURL('image/jpeg', quality)
        const base64 = dataUrl.includes(',') ? dataUrl.split(',')[1]! : dataUrl
        resolve(base64)
      } catch (e) {
        reject(e)
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('Failed to load image'))
    }
    img.src = url
  })
}

export function photoSrcForDisplay(photoUrl: string | null | undefined): string | null {
  if (!photoUrl || !photoUrl.trim()) return null
  const p = photoUrl.trim()
  if (p.startsWith('http://') || p.startsWith('https://')) return p
  if (p.startsWith('data:')) return p
  return `data:image/jpeg;base64,${p}`
}
