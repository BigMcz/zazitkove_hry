/** What the diploma of a game for children says. */
export interface DiplomaContent {
  name: string | null
  title: string | null
  gameTitle: string
  tasks: number
  /** ISO date of the game. */
  playedAt: string
  imageUrl: string | null
}

// A4 portrait at 150 dpi: sharp enough to print at home, small enough to send
const WIDTH = 1240
const HEIGHT = 1754
const TEXT_WIDTH = 1000
const SERIF = 'Georgia, Cambria, "Times New Roman", serif'

export function diplomaText(tasks: number): string {
  return `za vyřešení všech ${tasks} hádanek`
}

export function formatPlayedAt(playedAt: string): string {
  return new Date(playedAt).toLocaleDateString('cs-CZ')
}

/** Draws centered text at the largest size up to `size` that fits TEXT_WIDTH. */
function fitText(ctx: CanvasRenderingContext2D, text: string, y: number, size: number, style: string, color: string) {
  let fontSize = size
  do {
    ctx.font = `${style} ${fontSize}px ${SERIF}`
    fontSize -= 4
  } while (ctx.measureText(text).width > TEXT_WIDTH && fontSize > 20)
  ctx.fillStyle = color
  ctx.fillText(text, WIDTH / 2, y)
}

async function loadImage(url: string): Promise<HTMLImageElement | null> {
  const image = new Image()
  image.src = url
  try {
    await image.decode()
    return image
  } catch {
    return null
  }
}

/** The diploma as a JPEG picture to keep, print or send (a PNG of the painting would have about 2 MB). */
export async function diplomaImage(content: DiplomaContent): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new Error('Diplom se nepodařilo vytvořit.')
  }

  ctx.fillStyle = '#fffbeb'
  ctx.fillRect(0, 0, WIDTH, HEIGHT)
  ctx.strokeStyle = '#d97706'
  ctx.lineWidth = 14
  ctx.strokeRect(40, 40, WIDTH - 80, HEIGHT - 80)
  ctx.lineWidth = 4
  ctx.strokeRect(68, 68, WIDTH - 136, HEIGHT - 136)

  const image = content.imageUrl ? await loadImage(content.imageUrl) : null
  let y = 150
  if (image) {
    const height = (TEXT_WIDTH * image.naturalHeight) / image.naturalWidth
    ctx.save()
    ctx.beginPath()
    ctx.roundRect((WIDTH - TEXT_WIDTH) / 2, y, TEXT_WIDTH, height, 24)
    ctx.clip()
    ctx.drawImage(image, (WIDTH - TEXT_WIDTH) / 2, y, TEXT_WIDTH, height)
    ctx.restore()
    y += height + 150
  } else {
    y += 250
  }

  ctx.textAlign = 'center'
  ctx.textBaseline = 'alphabetic'
  fitText(ctx, 'DIPLOM', y, 130, 'bold', '#1c1917')
  y += 80
  if (content.title) {
    fitText(ctx, content.title.toUpperCase(), y, 48, 'bold', '#92400e')
    y += 140
  } else {
    y += 60
  }
  if (content.name) {
    fitText(ctx, content.name, y, 100, 'bold', '#78350f')
    y += 100
  }
  fitText(ctx, diplomaText(content.tasks), y, 44, '', '#1c1917')
  y += 64
  fitText(ctx, `ve hře ${content.gameTitle}`, y, 44, 'bold', '#1c1917')
  fitText(ctx, formatPlayedAt(content.playedAt), HEIGHT - 150, 36, '', '#57534e')

  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('Diplom se nepodařilo vytvořit.'))),
      'image/jpeg',
      0.9,
    ),
  )
}

/**
 * On a phone offers the diploma to share (also "Uložit obrázek"), on a computer downloads it: a desktop browser
 * would open the system share dialog instead of saving the file.
 */
export async function saveDiploma(content: DiplomaContent): Promise<void> {
  const file = new File([await diplomaImage(content)], 'diplom.jpg', { type: 'image/jpeg' })
  const phone = window.matchMedia?.('(pointer: coarse)').matches ?? false
  if (phone && navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: 'Diplom' })
      return
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
    }
  }
  const url = URL.createObjectURL(file)
  const link = document.createElement('a')
  link.href = url
  link.download = file.name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
