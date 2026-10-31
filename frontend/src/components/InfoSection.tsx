import type { ReactNode } from 'react'

/** Titled block of text on the information pages (privacy, rules). */
export default function InfoSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4 leading-relaxed text-stone-300">
      <h2 className="text-2xl font-extrabold tracking-tight text-white">{title}</h2>
      {children}
    </section>
  )
}
