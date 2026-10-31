import { Link } from 'react-router'
import { operator } from '../components/operator.ts'
import InfoSection from '../components/InfoSection.tsx'
import SiteLayout from '../components/SiteLayout.tsx'

/** Short rules of the game for players: the card, its validity, hints, safety and what to do when it does not work. */
export default function RulesPage() {
  return (
    <SiteLayout>
      <title>Pravidla hry | StoryHold</title>
      <main className="flex max-w-3xl flex-col gap-10 pt-10 pb-20 md:pt-16">
        <header className="flex flex-col gap-4">
          <p className="text-xs font-bold tracking-[0.18em] text-lime uppercase">Pravidla hry</p>
          <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl">Jak to u nás chodí</h1>
          <p className="text-lg leading-relaxed text-stone-300">
            Pár pravidel, aby si hru užil každý a památka zůstala v pořádku i pro další návštěvníky.
          </p>
        </header>

        <InfoSection title="Kartička a kód">
          <ul className="list-disc space-y-2 pl-5">
            <li>Kartičku s přístupovým kódem koupíte na pokladně památky. Kód platí pro jednu hru na této památce.</li>
            <li>Jedna kartička je pro jeden tým. Tým může být rodina, skupina kamarádů nebo jen vy sami.</li>
            <li>
              Kód začne platit prvním zadáním a pak platí 5 hodin. Během té doby můžete hru přerušit a po zadání kódu
              pokračovat, i na jiném telefonu. Po vypršení už hru dohrát nejde.
            </li>
            <li>Kód nikomu nesdílejte, další zadání by pokračovalo ve stejné hře.</li>
          </ul>
        </InfoSection>

        <InfoSection title="Průběh hry">
          <ul className="list-disc space-y-2 pl-5">
            <li>Hra běží v prohlížeči, k vyhodnocení odpovědí potřebujete připojení k internetu.</li>
            <li>Úkoly řešíte postupně. Odpověď najdete vždy na památce nebo v příběhu, nemusíte nic dohledávat na internetu.</li>
            <li>
              Když si nevíte rady, použijte nápovědu. Každá nápověda přidá k celkovému času pár minut. Kolik, to vidíte
              předem.
            </li>
            <li>Na konci uvidíte souhrn: celkový čas, počet nápověd a penalizaci.</li>
          </ul>
        </InfoSection>

        <InfoSection title="Bezpečnost a ohleduplnost">
          <ul className="list-disc space-y-2 pl-5">
            <li>Řiďte se návštěvním řádem památky a pokyny jejích zaměstnanců.</li>
            <li>
              Hra vás nikdy nepošle do uzavřených prostor ani nevyžaduje lézt na zdi nebo sahat na exponáty. Všechno
              potřebné najdete z míst přístupných návštěvníkům.
            </li>
            <li>Při chůzi se dívejte kolem sebe, ne do mobilu, hlavně na schodech a nerovné dlažbě.</li>
            <li>Na děti dohlíží jejich doprovod.</li>
            <li>Ohleduplnost k ostatním návštěvníkům: zvuk pusťte do sluchátek nebo potichu.</li>
          </ul>
        </InfoSection>

        <InfoSection title="Když něco nefunguje">
          <p>
            Zkontrolujte nejdřív připojení k internetu a zkuste stránku obnovit, hra pokračuje tam, kde jste skončili.
            Pokud hru kvůli technické chybě nemůžete dohrát, napište nám na{' '}
            <a href={`mailto:${operator.email}`} className="text-lime underline">
              {operator.email}
            </a>{' '}
            nebo zavolejte na {operator.phone}. Uveďte kód z kartičky a společně najdeme řešení, například náhradní kód.
          </p>
          <p>Reklamaci zakoupené kartičky můžete uplatnit také na pokladně památky, kde jste ji koupili.</p>
        </InfoSection>

        <InfoSection title="Další informace">
          <p>
            Hru provozuje {operator.name}, IČO {operator.companyId}, {operator.address}. Jak zacházíme s údaji, popisuje
            stránka{' '}
            <Link to="/ochrana-udaju" className="text-lime underline">
              Provozovatel a ochrana osobních údajů
            </Link>
            .
          </p>
        </InfoSection>

        <p className="text-sm text-stone-500">Tato pravidla platí od 1. 11. 2026.</p>
      </main>
    </SiteLayout>
  )
}
