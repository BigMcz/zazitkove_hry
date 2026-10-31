import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client.ts'
import { useStartSession } from '../api/queries.ts'
import { formatAccessCode } from '../components/formatAccessCode.ts'
import { operator } from '../components/operator.ts'
import SiteLayout from '../components/SiteLayout.tsx'
import { useSessionToken } from '../session/sessionTokenContext.ts'
import type { HomeLocationState } from '../session/usePlaySession.ts'
import type { StartLocationState } from './StartPage.tsx'

/**
 * Landing page of StoryHold (design in docs/image.png):
 * hero, entering the code from the card, three steps, our games and how it works on site.
 */
export default function HomePage() {
  const location = useLocation()
  const message = (location.state as HomeLocationState | null)?.message

  return (
    <SiteLayout>
      <main className="flex flex-col gap-20 pt-10 pb-20 md:gap-28 md:pt-16">
        <Hero />
        <CodeEntry message={message} />
        <Steps />
        <OurGames />
        <OnSite />
        <BeforeYouGo />
        <Faq />
        <About />
        <ForHeritageSites />
      </main>
    </SiteLayout>
  )
}

function Eyebrow({ children }: { children: ReactNode }) {
  return <p className="text-xs font-bold tracking-[0.18em] text-lime uppercase">{children}</p>
}

/**
 * Castle picture across the whole hero (grafika/hero.png). On phones it is a banner above the text,
 * from lg up it lies under the text, darkened from the left so that the text stays readable.
 */
function Hero() {
  return (
    <section className="relative overflow-hidden rounded-3xl border border-night-line bg-night">
      <div className="relative lg:absolute lg:inset-0">
        <img
          src="/images/hero.jpg"
          alt="Hrad Loket na skále nad řekou Ohří při západu slunce"
          fetchPriority="high"
          className="h-64 w-full object-cover object-[62%_center] sm:h-80 md:h-96 lg:h-full"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-night via-transparent to-transparent lg:bg-gradient-to-r lg:from-night lg:from-10% lg:via-night/70 lg:via-40% lg:to-transparent lg:to-70%" />
        <div className="absolute inset-0 hidden bg-gradient-to-t from-night/80 via-transparent via-30% to-transparent lg:block" />
        <p className="absolute right-6 bottom-4 text-xs font-bold tracking-[0.18em] text-white/90 uppercase lg:right-8 lg:bottom-6">
          Skutečná místa. Nečekané příběhy.
        </p>
      </div>
      <div className="relative grid items-center gap-10 px-6 pt-4 pb-10 lg:min-h-[36rem] lg:grid-cols-[minmax(0,1fr)_auto] lg:p-12 xl:px-14">
        <div className="flex max-w-xl flex-col items-start gap-6">
          <Eyebrow>Váš další quest? Historická památka.</Eyebrow>
          <h1 className="text-5xl leading-[1.02] font-extrabold tracking-tight sm:text-6xl lg:text-7xl">
            Staré zdi.
            <br />
            Nová pravidla hry.
          </h1>
          <p className="max-w-md text-lg leading-relaxed text-stone-200">
            Proměňte návštěvu památky v dobrodružství. Odemkněte příběh v mobilu, hledejte stopy ve skutečném světě a
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
        <PhonePreview />
      </div>
    </section>
  )
}

/** Preview of a game chapter on a phone, only where it fits next to the text without hiding the castle. */
function PhonePreview() {
  return (
    <div
      aria-label="Ukázka hry"
      className="hidden w-64 rounded-[2rem] border-4 border-night bg-night p-3 shadow-2xl xl:block"
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

/**
 * Uses an access code: a new code opens the game intro, a code already in use (e.g. on another phone)
 * continues that game.
 */
function useEnterCode() {
  const { setToken } = useSessionToken()
  const navigate = useNavigate()
  const checkCode = useMutation({ mutationFn: api.checkCode })
  const startSession = useStartSession()

  function enterCode(code: string) {
    checkCode.mutate(code, {
      onSuccess: (check) => {
        if (!check.resumesGame) {
          const state: StartLocationState = { check }
          navigate('/start', { state })
          return
        }
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

  return {
    enterCode,
    error: checkCode.error ?? startSession.error,
    pending: checkCode.isPending || startSession.isPending,
  }
}

/** Entering the code from the card (a QR code on the card opens /?kod=ABCD-2345 with the code filled in). */
function CodeEntry({ message }: { message?: string }) {
  const { token } = useSessionToken()
  const [searchParams] = useSearchParams()
  const [code, setCode] = useState(() => formatAccessCode(searchParams.get('kod') ?? '', ''))
  const { enterCode, error, pending } = useEnterCode()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const typed = code.trim()
    if (typed !== '') {
      enterCode(typed)
    }
  }

  return (
    <section id="kod" className="grid gap-6 rounded-3xl border border-night-line bg-night-card p-6 md:grid-cols-2 md:p-8">
      <div className="flex flex-col gap-3">
        <Eyebrow>Máte kartičku?</Eyebrow>
        <h2 className="text-3xl font-extrabold tracking-tight">Zadejte kód a hra začne.</h2>
        <p className="text-stone-300">Kód najdete na kartičce z pokladny památky. Velikost písmen ani pomlčka nehrají roli.</p>
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
    text: 'Na pokladně památky si zakupte hru a získáte kartičku s přístupovým kódem.',
    icon: TicketIcon,
  },
  {
    title: '02 / Odemkněte příběh',
    text: 'Použijte kód podle pokynů na kartičce. V mobilu se před vámi otevře příběh památky.',
    icon: PhoneIcon,
  },
  {
    title: '03 / Rozhlédněte se',
    text: 'Luštěte úkoly a hledejte odpovědi v okolí památky. Každá nalezená stopa vás posune dál.',
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

/** Development code of the demo game (seeded in the dev profile): never expires, every use starts a new game. */
const DEMO_CODE = 'DEMO-KATA'

const demoFacts = ['Skutečný příběh z roku 1822', '10 zastavení', 'asi 30 minut', 'Namluvený příběh']

function OurGames() {
  const { enterCode, error, pending } = useEnterCode()

  return (
    <section id="nase-hry" aria-labelledby="nase-hry-nadpis" className="flex scroll-mt-8 flex-col gap-6">
      <Eyebrow>Naše hry</Eyebrow>
      <h2 id="nase-hry-nadpis" className="text-4xl font-extrabold tracking-tight sm:text-5xl">
        Příběhy, které se staly.
      </h2>
      <article className="mt-4 grid overflow-hidden rounded-3xl border border-night-line bg-night-card md:grid-cols-2">
        <img
          src="/images/demo/cover.jpg"
          alt="Starý kat Karl Huss s úředním dopisem v zámeckém muzeu"
          loading="lazy"
          className="aspect-[4/3] size-full object-cover"
        />
        <div className="flex flex-col items-start gap-5 p-6 md:p-8">
          <p className="rounded-full bg-lime px-3 py-1 text-xs font-bold tracking-[0.12em] text-night uppercase">
            Hrad Loket · ukázka zdarma
          </p>
          <h3 className="text-3xl font-extrabold tracking-tight">Katova povinnost</h3>
          <p className="leading-relaxed text-stone-300">
            Léto 1822. Do loketského žaláře přichází Karl Huss, poslední chebský kat. Muž, který psal kroniku, učil
            odsouzeného jeho poslední roli a o třináct let později musel vracet přeplatek za popravu.
          </p>
          <p className="leading-relaxed text-stone-300">
            Projděte s ním jeho cestu hradem, poslouchejte jeho vyprávění, rozluštěte deset hádanek a složte tajenku:
            slovo, podle kterého mistr Huss žil.
          </p>
          <ul aria-label="O hře" className="flex flex-wrap gap-2">
            {demoFacts.map((fact) => (
              <li key={fact} className="rounded-full border border-night-line px-3 py-1 text-sm text-stone-300">
                {fact}
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => enterCode(DEMO_CODE)}
            disabled={pending}
            className="min-h-12 rounded-full bg-lime px-6 font-bold text-night transition-colors hover:bg-lime-dark disabled:cursor-not-allowed disabled:opacity-40"
          >
            {pending ? 'Spouštím…' : 'Zahrát ukázku'}
          </button>
          <p className="-mt-2 text-sm text-stone-400">Hned teď a bez kartičky, stačí mobil.</p>
          {error && (
            <p role="alert" className="rounded-xl border-l-4 border-red-500 bg-night px-4 py-3 text-red-200">
              {error.message}
            </p>
          )}
        </div>
      </article>
    </section>
  )
}

function OnSite() {
  return (
    <section className="grid items-end gap-8 md:grid-cols-[1fr_20rem]">
      <div className="flex flex-col gap-4">
        <Eyebrow>Méně scrollování. Víc objevování.</Eyebrow>
        <h2 className="text-4xl font-extrabold tracking-tight sm:text-5xl">Displej vypráví. Památka napovídá.</h2>
        <p className="max-w-2xl text-lg leading-relaxed text-stone-300">
          Tohle není hra jen na obrazovce. Rozhlížejte se po památce, všímejte si detailů a spojte stopy, které ostatní
          míjejí. Historie se stává součástí vašeho příběhu.
        </p>
      </div>
      <aside className="flex flex-col gap-3 rounded-2xl border border-night-line p-6">
        <h3 className="text-xl font-extrabold">Začíná to na pokladně.</h3>
        <p className="text-stone-400">
          Kartičku s kódem získáte přímo na památce. Na dostupnost hry se informujte u její pokladny.
        </p>
      </aside>
    </section>
  )
}

/** Practical tips so that nothing spoils the game on site. */
function BeforeYouGo() {
  return (
    <section id="nez-vyrazite" aria-labelledby="nez-vyrazite-nadpis" className="flex scroll-mt-8 flex-col gap-6">
      <Eyebrow>Než vyrazíte</Eyebrow>
      <h2 id="nez-vyrazite-nadpis" className="text-4xl font-extrabold tracking-tight sm:text-5xl">
        Pár tipů na cestu.
      </h2>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {tips.map((tip) => (
          <li key={tip.title} className="flex flex-col gap-2 rounded-2xl border border-night-line bg-night-card p-6">
            <h3 className="text-lg font-extrabold">{tip.title}</h3>
            <p className="text-stone-400">{tip.text}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

const tips = [
  { title: 'Nabitý mobil', text: 'Hra běží v prohlížeči celou dobu. Hodí se i powerbanka.' },
  { title: 'Sluchátka', text: 'Příběh vám vypráví namluvený hlas. Ve sluchátkách ho uslyšíte i v ruchu nádvoří.' },
  { title: 'Mobilní data', text: 'Odpovědi se ověřují přes internet, stačí běžné připojení.' },
  { title: 'Pohodlná obuv', text: 'Trasa vede po nádvořích, schodech a dlažbě památky.' },
]

/** Questions players ask before buying a card; answers must match how the game really works. */
function Faq() {
  return (
    <section id="caste-otazky" aria-labelledby="caste-otazky-nadpis" className="flex scroll-mt-8 flex-col gap-6">
      <Eyebrow>Časté otázky</Eyebrow>
      <h2 id="caste-otazky-nadpis" className="text-4xl font-extrabold tracking-tight sm:text-5xl">
        Na co se nejčastěji ptáte.
      </h2>
      <div className="flex flex-col gap-3">
        {faq.map((item) => (
          <details key={item.question} className="group rounded-2xl border border-night-line bg-night-card">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 p-5 text-lg font-bold">
              {item.question}
              <span aria-hidden="true" className="text-2xl text-lime transition-transform group-open:rotate-45">
                +
              </span>
            </summary>
            <p className="px-5 pb-5 leading-relaxed text-stone-300">{item.answer}</p>
          </details>
        ))}
      </div>
      <p className="text-stone-400">
        Podrobnosti najdete v{' '}
        <Link to="/pravidla" className="text-lime underline">
          pravidlech hry
        </Link>
        . Nenašli jste odpověď? Napište nám na{' '}
        <a href={`mailto:${operator.email}`} className="text-lime underline">
          {operator.email}
        </a>
        .
      </p>
    </section>
  )
}

const faq = [
  {
    question: 'Kolik hra stojí a kde koupím kartičku?',
    answer: 'Kartičku s kódem koupíte na pokladně památky, kde se hra hraje. Tam se dozvíte i aktuální cenu.',
  },
  {
    question: 'Pro koho je hra vhodná?',
    answer:
      'Pro všechny. Nejmenší děti, které ještě nečtou nebo neumí zacházet s mobilem, si hru užijí s rodiči. Příběh poslouchají a stopy hledají společně s nimi.',
  },
  {
    question: 'Kolik lidí může hrát na jednu kartičku?',
    answer:
      'Jedna kartička je pro jeden tým, třeba celou rodinu nebo partu kamarádů. Hrát můžete z jednoho mobilu a vyprávění poslouchat společně.',
  },
  {
    question: 'Jak dlouho hra trvá?',
    answer:
      'Záleží na hře a na vašem tempu. Ukázková hra na hradě Loket trvá asi 30 minut. Kód platí 5 hodin od prvního zadání, takže nikam nespěchejte.',
  },
  {
    question: 'Musím si instalovat aplikaci?',
    answer:
      'Ne. Hra běží v prohlížeči na každém běžném chytrém telefonu. Stačí zadat kód z kartičky nebo naskenovat QR kód.',
  },
  {
    question: 'Co když zavřu prohlížeč nebo se mi vybije mobil?',
    answer:
      'Nic se neztratí. Průběh hry se ukládá průběžně a po opětovném zadání kódu pokračujete tam, kde jste skončili, klidně i na jiném telefonu.',
  },
  {
    question: 'Co když nevím, jak dál?',
    answer:
      'Ke každému úkolu jsou nápovědy. Každá použitá nápověda vám jen přidá pár minut k celkovému času, hru tedy dohraje každý.',
  },
  {
    question: 'Je trasa bezbariérová?',
    answer:
      'Záleží na památce. Hry vedou po skutečných místech, často po schodech a nerovné dlažbě. Na přístupnost trasy se zeptejte na pokladně.',
  },
]

/** Why StoryHold exists: games that make people look around and leave knowing more. */
function About() {
  return (
    <section id="o-nas" aria-labelledby="o-nas-nadpis" className="flex scroll-mt-8 flex-col gap-6">
      <Eyebrow>Proč StoryHold</Eyebrow>
      <h2 id="o-nas-nadpis" className="max-w-3xl text-4xl font-extrabold tracking-tight sm:text-5xl">
        Hra, ze které si odnesete víc než fotku.
      </h2>
      <div className="grid gap-6 text-lg leading-relaxed text-stone-300 md:grid-cols-2">
        <p>
          Na památky jezdíme rádi. Projdeme nádvoří, vyfotíme hradby a podíváme se, jak místo vypadá dnes. Co se tu ale
          kdysi odehrálo, kdo tu žil a jaké příběhy tyhle zdi pamatují, se většinou nedozvíme. Na informační tabule při
          prohlídce obvykle nezbude čas ani chuť.
        </p>
        <p>
          Proto vznikl StoryHold. Mobil, který máte stejně v ruce, se stane vaším průvodcem. Příběhy skutečných lidí vás
          zavedou k místům a detailům, kolem kterých byste jinak prošli, a odpovědi na úkoly najdete jen tak, že se
          pozorně rozhlédnete. Domů si odnesete zážitek i spoustu zajímavostí, které se při běžné prohlídce nedozvíte.
        </p>
      </div>
      <ul aria-label="Co hra přináší" className="mt-2 grid gap-4 md:grid-cols-3">
        {aboutPoints.map((point) => (
          <li key={point.title} className="flex flex-col gap-2 rounded-2xl border border-night-line bg-night-card p-6">
            <h3 className="text-xl font-extrabold">{point.title}</h3>
            <p className="text-stone-400">{point.text}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

const aboutPoints = [
  {
    title: 'Vzdělává hrou',
    text: 'Historie podaná jako příběh a hádanky. Fakta si zapamatujete, protože jste na ně sami přišli.',
  },
  {
    title: 'Vrací pohled kolem sebe',
    text: 'Mobil jen vypráví a ptá se. Odpovědi najdete ve zdech, na portálech a v detailech památky.',
  },
  {
    title: 'Baví celou rodinu',
    text: 'Děti luští, dospělí poslouchají příběh. Hrát můžete sami, s rodinou i jako tým kamarádů.',
  },
]

const heritageSitePoints = ['Příběh přímo z vaší památky', 'Pro rodiny, školy i dospělé', 'Bez instalace aplikace']

/** Offer for castles and other heritage sites, the actual customers of StoryHold. */
function ForHeritageSites() {
  return (
    <section
      id="pro-pamatky"
      aria-labelledby="pro-pamatky-nadpis"
      className="grid scroll-mt-8 gap-8 rounded-3xl border border-night-line bg-night-card p-6 md:grid-cols-[1fr_auto] md:items-end md:p-10"
    >
      <div className="flex flex-col gap-4">
        <Eyebrow>Pro památky</Eyebrow>
        <h2 id="pro-pamatky-nadpis" className="text-3xl font-extrabold tracking-tight sm:text-4xl">
          Spravujete hrad, zámek nebo jinou památku?
        </h2>
        <p className="max-w-2xl text-lg leading-relaxed text-stone-300">
          Připravíme pro vás hru na míru. Příběh podle skutečné historie místa, namluvené texty a úkoly, které provedou
          návštěvníky po zajímavých místech. Návštěvníci objeví zákoutí, kolem kterých běžně projdou, zdrží se u vás déle
          a odnesou si zážitek, o kterém budou vyprávět.
        </p>
        <ul aria-label="Co hra památce přináší" className="flex flex-wrap gap-2">
          {heritageSitePoints.map((point) => (
            <li key={point} className="rounded-full border border-night-line px-3 py-1 text-sm text-stone-300">
              {point}
            </li>
          ))}
        </ul>
        <p className="text-stone-400">Ozvěte se nám, rádi vám ukážeme, jak by hra mohla vypadat právě u vás.</p>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row md:flex-col">
        <a
          href={`mailto:${operator.email}?subject=${encodeURIComponent('Hra StoryHold pro naši památku')}`}
          className="inline-flex min-h-12 items-center justify-center rounded-full bg-lime px-6 font-bold text-night transition-colors hover:bg-lime-dark"
        >
          Napište nám
        </a>
        <a
          href="#nase-hry"
          className="inline-flex min-h-12 items-center justify-center rounded-full border-2 border-lime px-6 font-bold text-lime hover:bg-lime hover:text-night"
        >
          Vyzkoušejte ukázku
        </a>
      </div>
    </section>
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
