import type { ReactNode } from 'react'

export interface Tab<T extends string> {
  id: T
  label: string
}

interface TabsProps<T extends string> {
  tabs: Tab<T>[]
  active: T
  onChange: (id: T) => void
  children: ReactNode
}

/** Simple tabs; the panel content is the children for the active tab. */
export default function Tabs<T extends string>({ tabs, active, onChange, children }: TabsProps<T>) {
  return (
    <div className="flex flex-col gap-6">
      <div role="tablist" aria-label="Sekce administrace" className="flex flex-wrap gap-1 border-b-2 border-stone-300">
        {tabs.map((tab) => {
          const selected = tab.id === active
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`panel-${tab.id}`}
              onClick={() => onChange(tab.id)}
              className={`-mb-0.5 min-h-11 rounded-t-lg border-b-4 px-4 font-semibold ${
                selected
                  ? 'border-stone-900 text-stone-950'
                  : 'border-transparent text-stone-600 hover:border-stone-400 hover:text-stone-900'
              }`}
            >
              {tab.label}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`panel-${active}`} aria-labelledby={`tab-${active}`} className="flex flex-col gap-8">
        {children}
      </div>
    </div>
  )
}
