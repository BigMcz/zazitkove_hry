/** Story text from the database; paragraphs are separated by an empty line. */
export default function StoryText({ text }: { text: string }) {
  const paragraphs = text.split(/\n\s*\n/).map((paragraph) => paragraph.trim()).filter(Boolean)
  return (
    <div className="flex flex-col gap-3 font-serif text-lg leading-relaxed text-stone-900">
      {paragraphs.map((paragraph, index) => (
        <p key={index}>{paragraph}</p>
      ))}
    </div>
  )
}
