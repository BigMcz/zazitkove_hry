import { operator } from '../components/operator.ts'
import InfoSection from '../components/InfoSection.tsx'
import SiteLayout from '../components/SiteLayout.tsx'

/**
 * Information on processing personal data (Art. 13 GDPR) and on storing data in the browser
 * (§ 89(3) of Act 127/2005 Sb.). Describes what the application really stores — keep it in sync
 * with docs/01-analyza.md Q17 when the processing changes.
 */
export default function PrivacyPage() {
  return (
    <SiteLayout>
      <title>Ochrana osobních údajů | StoryHold</title>
      <main className="flex max-w-3xl flex-col gap-10 pt-10 pb-20 md:pt-16">
        <header className="flex flex-col gap-4">
          <p className="text-xs font-bold tracking-[0.18em] text-lime uppercase">Ochrana osobních údajů</p>
          <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl">Jak zacházíme s vašimi údaji</h1>
          <p className="text-lg leading-relaxed text-stone-300">
            Hrát u nás můžete bez registrace, bez jména a bez e-mailu. Zpracováváme jen to, co hra nezbytně potřebuje.
            Níže najdete, co to je, proč a jak dlouho to uchováváme a jaká máte práva.
          </p>
        </header>

        <InfoSection title="Kdo vaše údaje zpracovává">
          <p>
            Správcem osobních údajů je <strong>{operator.name}</strong>, IČO {operator.companyId}, se sídlem{' '}
            {operator.address} (dále „my“), provozovatel služby StoryHold. {operator.register}
          </p>
          <p>
            S jakýmkoli dotazem nebo žádostí se na nás obraťte e-mailem na{' '}
            <a href={`mailto:${operator.email}`} className="text-lime underline">
              {operator.email}
            </a>
            , telefonicky na {operator.phone} nebo poštou na adresu sídla. Pověřence pro ochranu osobních údajů jmenovaného
            nemáme, zákon nám to neukládá.
          </p>
        </InfoSection>

        <InfoSection title="Co zpracováváme a proč">
          <Purpose
            title="Hraní hry"
            data="Přístupový kód z kartičky, průběh hry (vyřešená stanoviště, zadané odpovědi, použité nápovědy, časy) a název týmu, pokud ho vyplníte. Název týmu je nepovinný, nepište do něj prosím celá jména ani jiné osobní údaje."
            basis="Plnění smlouvy: poskytujeme vám hru, kterou jste si koupili nebo kterou jste si sami spustili (čl. 6 odst. 1 písm. b) GDPR)."
            retention="Údaje o konkrétní hře 12 měsíců od vypršení kódu. Potom název týmu automaticky smažeme; průběh hry zůstane bez vazby na vás jen jako anonymní statistika (např. obtížnost úkolů nebo počty aktivovaných kódů)."
          />
          <Purpose
            title="Ochrana proti zneužití"
            data="IP adresa zařízení při zadávání přístupového kódu a při přihlášení do administrace."
            basis="Oprávněný zájem, tedy chránit kódy a účty před hádáním a útoky (čl. 6 odst. 1 písm. f) GDPR)."
            retention="Jen v operační paměti serveru po dobu 1 minuty, nikam se neukládá. Záznamy serveru (logy) IP adresy návštěvníků neobsahují."
          />
          <Purpose
            title="Komunikace s vámi"
            data="Jméno, e-mail nebo telefon a obsah zprávy, pokud nás kontaktujete."
            basis="Oprávněný zájem, tedy odpovědět vám a vyřešit váš dotaz (čl. 6 odst. 1 písm. f) GDPR)."
            retention="Po dobu vyřizování a poté nejdéle 3 roky, pro případ, že se k dotazu budete vracet."
          />
          <Purpose
            title="Spolupráce s památkami"
            data="Údaje kontaktních osob a uživatelů administrace u partnerů (jméno, přihlašovací jméno, e-mail, telefon, datum posledního přihlášení)."
            basis="Plnění smlouvy s partnerem a oprávněný zájem na komunikaci s ním (čl. 6 odst. 1 písm. b) a f) GDPR), u fakturace plnění právní povinnosti (čl. 6 odst. 1 písm. c) GDPR)."
            retention="Po dobu spolupráce; údaje na daňových dokladech po dobu stanovenou daňovými předpisy (zpravidla 10 let)."
          />
          <p>
            Údaje nepoužíváme k marketingu, neprodáváme je, neprofilujeme vás a nerozhodujeme o vás automatizovaně.
            Zvláštní kategorie osobních údajů (např. o zdraví) nezpracováváme.
          </p>
        </InfoSection>

        <InfoSection title="Cookies a ukládání v prohlížeči">
          <p>
            Nepoužíváme žádné reklamní ani analytické cookies a na webu nejsou nástroje třetích stran, které by vás
            sledovaly. Proto se vás na souhlas s cookies neptáme. Do vašeho prohlížeče (do tzv. místního úložiště) ukládáme
            jen údaje, bez kterých by služba, o kterou jste požádali, nefungovala (§ 89 odst. 3 zákona č. 127/2005 Sb.,
            o elektronických komunikacích):
          </p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[32rem] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-night-line text-stone-400">
                  <th className="py-2 pr-4 font-semibold">Název</th>
                  <th className="py-2 pr-4 font-semibold">K čemu slouží</th>
                  <th className="py-2 font-semibold">Jak dlouho</th>
                </tr>
              </thead>
              <tbody className="align-top text-stone-300">
                <tr className="border-b border-night-line">
                  <td className="py-2 pr-4 font-mono text-xs">zazitky.sessionToken</td>
                  <td className="py-2 pr-4">Identifikuje rozehranou hru, abyste mohli pokračovat i po zavření prohlížeče.</td>
                  <td className="py-2">Dokud kód platí; po jeho vypršení ho aplikace při další návštěvě smaže.</td>
                </tr>
                <tr className="border-b border-night-line">
                  <td className="py-2 pr-4 font-mono text-xs">zazitky.voiceMuted</td>
                  <td className="py-2 pr-4">Pamatuje si, že jste si ztlumili namluvený hlas.</td>
                  <td className="py-2">Dokud hlas znovu nezapnete.</td>
                </tr>
                <tr className="border-b border-night-line">
                  <td className="py-2 pr-4 font-mono text-xs">zazitky.gamePackage, game-media</td>
                  <td className="py-2 pr-4">
                    Stažená hra (zašifrovaný příběh, obrázky a nahrávky), abyste mohli hrát i bez signálu.
                  </td>
                  <td className="py-2">Do konce hry; obrázky a nahrávky, dokud je prohlížeč neuvolní.</td>
                </tr>
                <tr>
                  <td className="py-2 pr-4 font-mono text-xs">JSESSIONID, XSRF-TOKEN</td>
                  <td className="py-2 pr-4">Přihlášení do administrace a ochrana formulářů, jen pro správce a partnery.</td>
                  <td className="py-2">Do odhlášení nebo zavření prohlížeče.</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p>Uložené údaje můžete kdykoli smazat v nastavení prohlížeče; rozehranou hru pak obnovíte zadáním kódu z kartičky.</p>
        </InfoSection>

        <InfoSection title="Komu údaje předáváme">
          <p>
            Údaje zpracováváme sami. Technicky nám pomáhají zpracovatelé, se kterými máme smlouvu a kteří s údaji smějí
            nakládat jen podle našich pokynů:
          </p>
          <ul className="list-disc space-y-1 pl-5">
            <li>WEDOS Internet, a.s.: provoz serveru (datové centrum v České republice),</li>
            <li>Seznam.cz, a.s.: e-mailová schránka.</li>
          </ul>
          <p>
            Památce, na jejíž pokladně jste kartičku koupili, předáváme jen souhrnné počty aktivovaných kódů pro
            vyúčtování, žádné údaje o vás ani o průběhu vaší hry. Údaje můžeme předat také státním orgánům, pokud nám to
            ukládá zákon. Mimo Evropskou unii údaje nepředáváme.
          </p>
        </InfoSection>

        <InfoSection title="Vaše práva">
          <p>Máte právo:</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>získat informaci, jaké údaje o vás zpracováváme, a jejich kopii,</li>
            <li>na opravu nepřesných údajů,</li>
            <li>na výmaz, pokud údaje už nepotřebujeme nebo je zpracováváme neoprávněně,</li>
            <li>na omezení zpracování,</li>
            <li>na přenositelnost údajů, které jste nám poskytli,</li>
            <li>
              <strong>vznést námitku</strong> proti zpracování založenému na oprávněném zájmu; pak údaje dál nezpracujeme,
              pokud neprokážeme závažné důvody,
            </li>
            <li>
              podat stížnost u Úřadu pro ochranu osobních údajů, Pplk. Sochora 27, 170 00 Praha 7,{' '}
              <a href="https://uoou.gov.cz" className="text-lime underline">
                uoou.gov.cz
              </a>
              .
            </li>
          </ul>
          <p>
            Žádost pošlete na{' '}
            <a href={`mailto:${operator.email}`} className="text-lime underline">
              {operator.email}
            </a>
            , vyřídíme ji bez zbytečného odkladu, nejpozději do jednoho měsíce. Protože hrajete bez registrace, uveďte
            prosím přístupový kód z kartičky, jinak nedokážeme poznat, která hra je vaše.
          </p>
        </InfoSection>

        <InfoSection title="Zabezpečení">
          <p>
            Spojení s webem je šifrované (HTTPS), server je v datovém centru v České republice a přístup k údajům má jen
            provozovatel. Správné odpovědi a vyhodnocení hry zůstávají na serveru.
          </p>
        </InfoSection>

        <p className="text-sm text-stone-500">Tyto informace platí od 1. 11. 2026.</p>
      </main>
    </SiteLayout>
  )
}

function Purpose({ title, data, basis, retention }: { title: string; data: string; basis: string; retention: string }) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl border border-night-line bg-night-card p-5">
      <h3 className="text-lg font-bold text-white">{title}</h3>
      <dl className="grid gap-2 text-sm sm:grid-cols-[9rem_1fr]">
        <dt className="font-semibold text-stone-400">Údaje</dt>
        <dd>{data}</dd>
        <dt className="font-semibold text-stone-400">Právní základ</dt>
        <dd>{basis}</dd>
        <dt className="font-semibold text-stone-400">Jak dlouho</dt>
        <dd>{retention}</dd>
      </dl>
    </div>
  )
}
