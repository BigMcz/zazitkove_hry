import type { CardLayout } from './adminApi.ts'

/** Card sizes the printers are ready for (paper, cutting, price list). */
export const cardSizes = [
  { id: 'vizitka', label: 'Vizitka 90 × 50 mm', long: 90, short: 50 },
  { id: 'eurovizitka', label: 'Eurovizitka 85 × 55 mm', long: 85, short: 55 },
] as const

export type CardSizeId = (typeof cardSizes)[number]['id']

export type Orientation = 'landscape' | 'portrait'

/** Margin of the QR code from the trim; the safe zone is 3 mm. */
const MARGIN = 5
const QR_SIZE = 24
/** On a card on its side the QR code is bigger, the steps and the address of the graphic fit around it. */
const QR_SIZE_LANDSCAPE = 30

/**
 * Default layout of the back. On a card on its side it matches the graphic of grafika/karticky/karticky-prompty.md:
 * the QR code at the top left, the two steps of the graphic to its right, below them the field with the code and
 * the serial number, at the bottom a band with the address. On a standing card the QR code is at the top and the
 * code below it.
 */
/** Bleed the printers usually ask for with business cards. */
export const DEFAULT_BLEED_MM = 2

export function defaultLayout(sizeId: CardSizeId, orientation: Orientation, bleedMm = DEFAULT_BLEED_MM): CardLayout {
  const size = cardSizes.find((candidate) => candidate.id === sizeId) ?? cardSizes[0]
  const widthMm = orientation === 'landscape' ? size.long : size.short
  const heightMm = orientation === 'landscape' ? size.short : size.long
  const common = { widthMm, heightMm, bleedMm, qrSizeMm: QR_SIZE, qrWhiteBox: true, codeFontPt: 16, codeAlign: 'CENTER' as const, codeColor: '#000000', serialFontPt: 7 }
  if (orientation === 'landscape') {
    // In the middle between the QR code and the same margin on the right
    const textX = (MARGIN + QR_SIZE_LANDSCAPE + widthMm - MARGIN) / 2
    return {
      ...common,
      qrXMm: MARGIN,
      qrYMm: 6,
      qrSizeMm: QR_SIZE_LANDSCAPE,
      codeXMm: textX,
      codeYMm: 32,
      serialXMm: textX,
      serialYMm: 37.5,
      serialFontPt: 6,
    }
  }
  return {
    ...common,
    qrXMm: (widthMm - QR_SIZE) / 2,
    qrYMm: 12,
    codeXMm: widthMm / 2,
    codeYMm: 12 + QR_SIZE + 12,
    serialXMm: widthMm / 2,
    serialYMm: heightMm - 6,
  }
}

/** Which offered size and orientation a layout has; null for another size (the DB allows it, the UI does not offer it). */
export function sizeOf(layout: CardLayout): { sizeId: CardSizeId; orientation: Orientation } | null {
  for (const size of cardSizes) {
    if (layout.widthMm === size.long && layout.heightMm === size.short) {
      return { sizeId: size.id, orientation: 'landscape' }
    }
    if (layout.widthMm === size.short && layout.heightMm === size.long) {
      return { sizeId: size.id, orientation: 'portrait' }
    }
  }
  return null
}
