import { selectableYears } from './shared.ts'

export default function YearSelect({ year, onChange }: { year: number; onChange: (year: number) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm font-semibold text-stone-700">
      Rok
      <select
        value={year}
        onChange={(event) => onChange(Number(event.target.value))}
        className="min-h-11 rounded-xl border-2 border-stone-400 bg-white px-3 text-base text-stone-900"
      >
        {selectableYears.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  )
}
