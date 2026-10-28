import type { ReactNode } from 'react'

export default function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-stone-300 bg-white p-5 shadow-sm">
      <h2 className="text-xl font-bold">{title}</h2>
      {children}
    </section>
  )
}
