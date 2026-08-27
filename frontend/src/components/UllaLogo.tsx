/**
 * Ulla app mark — circular logo assets cropped for each context.
 */
export function UllaLogo({
  size = 40,
  variant = 'full',
}: {
  size?: number
  variant?: 'full' | 'white'
}) {
  const src = variant === 'white' ? '/ulla-logo-white.png' : '/ulla-logo.png'
  return (
    <img
      src={src}
      width={size}
      height={size}
      alt=""
      aria-hidden
      style={{
        display: 'block',
        objectFit: 'contain',
        borderRadius: '50%',
      }}
    />
  )
}

/** @deprecated Use UllaLogo */
export const TripooRocketLogo = UllaLogo
