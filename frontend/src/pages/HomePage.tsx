import { useMutation } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client.ts'
import { useStartSession } from '../api/queries.ts'
import logoUrl from '../assets/storyhold.svg'
import { formatAccessCode } from '../components/formatAccessCode.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'
import type { HomeLocationState } from '../session/usePlaySession.ts'
import type { StartLocationState } from './StartPage.tsx'

/**
 * Landing page of StoryHold (design in docs/image.png):
 * hero, entering the code from the card, three steps and how it works on site.
 */
export default function HomePage() {
  const location = useLocation()
  const message = (location.state as HomeLocationState | null)?.message

  // Dark page also behind the content (overscroll on phones); the game pages keep the light body
  useEffect(() => {
    document.body.classList.add('bg-night')
    return () => document.body.classList.remove('bg-night')
  }, [])

  return (
    <div className="min-h-dvh bg-night font-display text-white">
      <div className="mx-auto flex w-full max-w-6xl flex-col px-5">
        <SiteHeader />
        <main className="flex flex-col gap-20 pt-10 pb-20 md:gap-28 md:pt-16">
          <Hero />
          <CodeEntry message={message} />
          <Steps />
          <OnSite />
        </main>
        <SiteFooter />
      </div>
    </div>
  )
}

function SiteHeader() {
  return (
    <header className="flex items-center justify-between gap-4 border-b border-night-line py-5">
      <img src={logoUrl} alt="StoryHold" className="h-7 w-auto" />
      <span className="text-xs font-bold tracking-[0.18em] text-lime uppercase">Historie. Jinak.</span>
    </header>
  )
}

function Eyebrow({ children }: { children: ReactNode }) {
  return <p className="text-xs font-bold tracking-[0.18em] text-lime uppercase">{children}</p>
}

function Hero() {
  return (
    <section className="grid items-center gap-10 md:grid-cols-2">
      <div className="flex flex-col items-start gap-6">
        <Eyebrow>Váš další quest? Český hrad.</Eyebrow>
        <h1 className="text-5xl leading-[1.02] font-extrabold tracking-tight sm:text-6xl lg:text-7xl">
          Staré zdi.
          <br />
          Nová pravidla hry.
        </h1>
        <p className="max-w-md text-lg leading-relaxed text-stone-300">
          Proměňte návštěvu hradu v dobrodružství. Odemkněte příběh v mobilu, hledejte stopy ve skutečném světě a
          rozluštěte jeho tajemství.
        </p>
        <a
          href="#jak-to-funguje"
          className="inline-flex min-h-12 items-center rounded-full bg-lime px-6 font-bold text-night transition-colors hover:bg-lime-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-lime"
        >
          Ukažte mi, jak na to
        </a>
        <p className="text-xs font-bold tracking-[0.18em] text-lime uppercase">Mobil v ruce. Dobrodružství všude kolem.</p>
      </div>
      <HeroVisual />
    </section>
  )
}

/** Castle picture from the demo game with a preview of a game chapter on a phone. */
function HeroVisual() {
  return (
    <div className="relative min-h-96 overflow-hidden rounded-3xl border border-night-line bg-gradient-to-br from-stone-700 via-stone-800 to-night md:min-h-[30rem]">
      <img
        src="/images/demo/st01.jpg"
        alt="Hrad Loket na skále nad řekou Ohří"
        className="absolute inset-0 size-full object-cover"
      />
      {/* Darker bottom edge keeps the caption readable over the picture */}
      <div className="absolute inset-0 bg-gradient-to-t from-night/80 via-transparent to-transparent" />
      <PhonePreview />
      <p className="absolute bottom-5 left-5 text-xs font-bold tracking-[0.18em] text-white/90 uppercase">
        Skutečná místa. Nečekané příběhy.
      </p>
    </div>
  )
}

function PhonePreview() {
  return (
    <div
      aria-label="Ukázka hry"
      className="absolute top-1/2 right-5 w-60 -translate-y-1/2 rounded-[2rem] border-4 border-night bg-night p-3 shadow-2xl sm:right-8 sm:w-64"
    >
      <p className="px-1 pt-1 text-[0.65rem] font-bold tracking-[0.15em] text-lime uppercase">
        Ukázka hry | Kapitola 01
      </p>
      <img
        src="/images/demo/st02.jpg"
        alt=""
        className="mt-2 aspect-[16/9] w-full rounded-xl bg-stone-700 object-cover"
      />
      <h2 className="mt-3 px-1 text-xl font-extrabold">Znamení ve zdi</h2>
      <p className="mt-1 px-1 text-xs leading-relaxed text-stone-300">
        Někdo tu zanechal vzkaz. Prozkoumejte okolí brány. Co vám napoví staré kameny?
      </p>
      <p className="mt-3 flex items-center gap-2 rounded-xl bg-night-card px-3 py-2.5 text-xs font-semibold">
        <SearchIcon className="size-4 text-lime" />
        Najděte skryté znamení
      </p>
    </div>
  )
}

/** Entering the code from the card (a QR code on the card opens /?kod=ABCD-2345 with the code filled in). */
function CodeEntry({ message }: { message?: string }) {
  const { token, setToken } = useSessionToken()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [code, setCode] = useState(() => formatAccessCode(searchParams.get('kod') ?? '', ''))
  const checkCode = useMutation({ mutationFn: api.checkCode })
  const startSession = useStartSession()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const typed = code.trim()
    if (typed === '') {
      return
    }
    checkCode.mutate(typed, {
      onSuccess: (check) => {
        if (!check.resumesGame) {
          const state: StartLocationState = { check }
          navigate('/start', { state })
          return
        }
        // The code is already in use (e.g. on another phone): continue that game.
        startSession.mutate(
          { code: check.code, teamName: null },
          {
            onSuccess: (session) => {
              if (session.token) {
                setToken(session.token)
              }
              navigate('/play')
            },
          },
        )
      },
    })
  }

  const error = checkCode.error ?? startSession.error
  const pending = checkCode.isPending || startSession.isPending

  return (
    <section id="kod" className="grid gap-6 rounded-3xl border border-night-line bg-night-card p-6 md:grid-cols-2 md:p-8">
      <div className="flex flex-col gap-3">
        <Eyebrow>Máte kartičku?</Eyebrow>
        <h2 className="text-3xl font-extrabold tracking-tight">Zadejte kód a hra začne.</h2>
        <p className="text-stone-300">Kód najdete na kartičce z pokladny hradu. Velikost písmen ani pomlčka nehrají roli.</p>
        {token && (
          <Link
            to="/play"
            className="mt-2 inline-flex min-h-12 w-fit items-center rounded-full border-2 border-lime px-6 font-bold text-lime hover:bg-lime hover:text-night"
          >
            Pokračovat ve hře
          </Link>
        )}
      </div>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3 self-center">
        {message && (
          <p role="status" className="rounded-xl border-l-4 border-lime bg-night px-4 py-3 text-stone-200">
            {message}
          </p>
        )}
        <label htmlFor="code" className="text-sm font-semibold text-stone-300">
          Zadej kód z kartičky
        </label>
        <div className="flex flex-col gap-3 sm:flex-row">
          <input
            id="code"
            name="code"
            type="text"
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="characters"
            spellCheck={false}
            enterKeyHint="go"
            maxLength={9}
            placeholder="ABCD-2345"
            value={code}
            onChange={(event) => setCode((previous) => formatAccessCode(event.target.value, previous))}
            className="min-h-14 w-full min-w-0 rounded-full sm:w-60 border-2 border-night-line bg-night px-5 text-center font-mono text-xl tracking-widest text-white uppercase placeholder:text-stone-600 focus:border-lime focus:outline-none"
          />
          <button
            type="submit"
            disabled={pending || code.trim() === ''}
            className="min-h-14 rounded-full bg-lime px-7 font-bold text-night transition-colors hover:bg-lime-dark disabled:cursor-not-allowed disabled:opacity-40"
          >
            {pending ? 'Ověřuji…' : 'Použít kód'}
          </button>
        </div>
        {error && (
          <p role="alert" className="rounded-xl border-l-4 border-red-500 bg-night px-4 py-3 text-red-200">
            {error.message}
          </p>
        )}
      </form>
    </section>
  )
}

const steps = [
  {
    title: '01 / Kartička je váš klíč',
    text: 'Na pokladně hradu si zakupte hru a získáte kartičku s přístupovým kódem.',
    icon: TicketIcon,
  },
  {
    title: '02 / Odemkněte příběh',
    text: 'Použijte kód podle pokynů na kartičce. V mobilu se před vámi otevře hradní příběh.',
    icon: PhoneIcon,
  },
  {
    title: '03 / Rozhlédněte se',
    text: 'Luštěte úkoly a hledejte odpovědi v okolí hradu. Každá nalezená stopa vás posune dál.',
    icon: CompassIcon,
  },
]

function Steps() {
  return (
    <section id="jak-to-funguje" className="flex scroll-mt-8 flex-col gap-6">
      <Eyebrow>Od kartičky k dobrodružství</Eyebrow>
      <h2 className="text-4xl font-extrabold tracking-tight sm:text-5xl">Tři kroky. A jste ve hře.</h2>
      <ol className="mt-4 grid gap-4 md:grid-cols-3">
        {steps.map((step, index) => {
          const highlighted = index === steps.length - 1
          const Icon = step.icon
          return (
            <li
              key={step.title}
              className={`flex flex-col gap-4 rounded-2xl p-6 ${
                highlighted ? 'bg-lime text-night' : 'border border-night-line bg-night-card'
              }`}
            >
              <Icon className={`size-7 ${highlighted ? 'text-night' : 'text-lime'}`} />
              <h3 className="text-xl font-extrabold">{step.title}</h3>
              <p className={highlighted ? 'text-night/80' : 'text-stone-400'}>{step.text}</p>
            </li>
          )
        })}
      </ol>
    </section>
  )
}

function OnSite() {
  return (
    <section className="grid items-end gap-8 md:grid-cols-[1fr_20rem]">
      <div className="flex flex-col gap-4">
        <Eyebrow>Méně scrollování. Víc objevování.</Eyebrow>
        <h2 className="text-4xl font-extrabold tracking-tight sm:text-5xl">Displej vypráví. Hrad napovídá.</h2>
        <p className="max-w-2xl text-lg leading-relaxed text-stone-300">
          Tohle není hra jen na obrazovce. Rozhlížejte se po nádvoří, všímejte si detailů a spojte stopy, které ostatní
          míjejí. Historie se stává součástí vašeho příběhu.
        </p>
      </div>
      <aside className="flex flex-col gap-3 rounded-2xl border border-night-line p-6">
        <h3 className="text-xl font-extrabold">Začíná to na pokladně.</h3>
        <p className="text-stone-400">
          Kartičku s kódem získáte přímo na hradě. Na dostupnost hry se informujte u jeho pokladny.
        </p>
      </aside>
    </section>
  )
}

function SiteFooter() {
  return (
    <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-night-line py-8">
      <img src={logoUrl} alt="StoryHold" className="h-5 w-auto" />
      <span className="text-sm text-stone-400">Historie čeká na další tah.</span>
    </footer>
  )
}

// Icons (inline SVG, no icon library — keeps the bundle small)

type IconProps = { className?: string }

function svgProps(className?: string) {
  return {
    className,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }
}

function TicketIcon({ className }: IconProps) {
  return (
    <svg {...svgProps(className)}>
      <path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z" />
      <path d="M9 9v6M15 9v6" />
    </svg>
  )
}

function PhoneIcon({ className }: IconProps) {
  return (
    <svg {...svgProps(className)}>
      <rect x="6" y="2" width="12" height="20" rx="2.5" />
      <path d="M11 18h2" />
    </svg>
  )
}

function CompassIcon({ className }: IconProps) {
  return (
    <svg {...svgProps(className)}>
      <circle cx="12" cy="12" r="9" />
      <path d="m15.5 8.5-2 5-5 2 2-5z" />
    </svg>
  )
}

function SearchIcon({ className }: IconProps) {
  return (
    <svg {...svgProps(className)}>
      <circle cx="11" cy="11" r="6" />
      <path d="m20 20-4.5-4.5" />
    </svg>
  )
}
