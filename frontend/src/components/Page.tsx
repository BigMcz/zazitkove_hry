import type { ReactNode } from 'react'

export default function Page({ children }: { children: ReactNode }) {
  return <main className="mx-auto flex w-full max-w-lg flex-col gap-6 px-4 pt-6 pb-12">{children}</main>
}
