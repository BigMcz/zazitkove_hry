import { useState } from 'react'

/** Picture to a story; hides itself while the file does not exist yet (the graphic is still being made). */
export default function StoryImage({ src, alt }: { src: string; alt: string }) {
  const [missing, setMissing] = useState(false)
  if (missing) {
    return null
  }
  return (
    <img
      src={src}
      alt={alt}
      onError={() => setMissing(true)}
      className="aspect-[4/3] w-full rounded-2xl bg-stone-200 object-cover"
    />
  )
}
