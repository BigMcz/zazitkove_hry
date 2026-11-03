import { describe, expect, it } from 'vitest'
import { defaultLayout, sizeOf } from './cardLayout.ts'

describe('defaultLayout', () => {
  it('fits the graphic of the back with the steps and the address for a business card on its side', () => {
    expect(defaultLayout('vizitka', 'landscape')).toMatchObject({
      widthMm: 90,
      heightMm: 50,
      bleedMm: 2,
      qrXMm: 5,
      qrYMm: 6,
      qrSizeMm: 30,
      codeXMm: 60,
      codeYMm: 32,
      serialXMm: 60,
      serialYMm: 37.5,
      serialFontPt: 6,
    })
  })

  it('puts the QR code at the top of a standing card', () => {
    const layout = defaultLayout('eurovizitka', 'portrait', 2)
    expect(layout).toMatchObject({ widthMm: 55, heightMm: 85, bleedMm: 2, qrXMm: 15.5, qrYMm: 12, codeXMm: 27.5 })
    expect(sizeOf(layout)).toEqual({ sizeId: 'eurovizitka', orientation: 'portrait' })
  })

  it('knows no other size', () => {
    expect(sizeOf({ ...defaultLayout('vizitka', 'landscape'), widthMm: 100 })).toBeNull()
  })
})
