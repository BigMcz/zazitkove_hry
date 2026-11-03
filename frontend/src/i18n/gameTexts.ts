import { createContext, use } from 'react'
import { ApiError } from '../api/client.ts'
import { praiseForHints } from '../components/praiseForHints.ts'

/**
 * Texts of the game screens in the languages of the games. A game is shown in its own language (the player chooses
 * the language version before the start); the web and the administration stay in Czech.
 */
export interface GameTexts {
  language: 'cs' | 'de'
  /** The language in itself, for choosing it: Čeština, Deutsch. */
  languageName: string
  chooseLanguage: string
  /** Picture of the flag (3:2). */
  flagUrl: string
  /** BCP 47 locale for dates. */
  locale: string

  back: string
  cancel: string
  loading: string
  loadingGame: string
  retry: string
  backToStart: string

  beforeStart: string
  voiceHeading: string
  voiceInfo: string
  safetyHeading: string
  safetyRules: string[]
  playIntro: string
  continue: string
  playMuted: string
  stations: (count: number) => string
  aboutMinutes: (minutes: number) => string
  teamName: string
  diplomaName: string
  optional: string
  diplomaNameHint: string
  newGameEndsRunning: string
  /** Around the code: "Po spuštění hry začne běžet platnost kódu ABCD-2345." */
  codeValidity: [before: string, after: string]
  starting: string
  startGame: string

  wrongAnswer: string
  notANumber: string
  gameLeft: string
  hintPenalty: (duration: string) => string
  task: string
  leaveGame: string
  leaveConfirm: string
  leaveYes: string
  leaveNo: string
  listenTask: string
  showTranscript: string
  gameNotRunning: string

  chooseAnswer: string
  yourAnswer: string
  sendAnswer: string

  hints: string
  hint: (orderNo: number) => string
  noMoreHints: string
  needHint: string
  nextHint: string
  hintCosts: string
  showHint: string

  play: string
  pause: string
  unmute: string
  mute: string

  station: (current: number, total: number) => string
  progressLabel: string
  puzzle: (collected: number, total: number) => string
  puzzleLabel: string
  puzzleEmpty: string
  downloading: string
  downloadProgress: (done: number, total: number) => string
  downloaded: string

  solved: (solved: number, total: number) => string
  lastClue: string
  clueFound: string
  whereNext: string
  toFinale: string
  toNextStation: string

  gameCompleted: string
  congratulations: string
  allTasksSolved: (count: number) => string
  praise: (hintsUsed: number) => string
  teamResult: (teamName: string) => string
  yourResult: string
  totalTime: string
  penaltyPart: string
  hintsUsed: string

  diploma: string
  diplomaFor: (tasks: number) => string
  inGame: string
  saveDiploma: string
  savingDiploma: string
  diplomaSaveFailed: string
  diplomaCreateFailed: string
  diplomaFile: string

  /** Message of a failed request; the server answers in Czech, other languages translate it by its type. */
  error: (error: Error) => string
}

const cs: GameTexts = {
  language: 'cs',
  languageName: 'Čeština',
  chooseLanguage: 'Vyberte jazyk',
  flagUrl: '/images/flags/cs.svg',
  locale: 'cs-CZ',

  back: '← Zpět',
  cancel: 'Zpět',
  loading: 'Načítám…',
  loadingGame: 'Načítám hru…',
  retry: 'Zkusit znovu',
  backToStart: 'Zpět na začátek',

  beforeStart: 'Než začnete',
  voiceHeading: '🔊 Hra mluví',
  voiceInfo:
    'Příběh vám vypráví namluvený hlas. Zapněte zvuk telefonu, vypněte tichý režim a zesilte hlasitost. Nejlépe uslyšíte ve sluchátkách. Text máte vždy i na obrazovce.',
  safetyHeading: 'Bezpečně a ohleduplně',
  safetyRules: [
    'Při chůzi se dívejte kolem sebe, ne do mobilu, hlavně na schodech.',
    'Hra vás nikdy nepošle do uzavřených prostor ani na zdi. Řiďte se návštěvním řádem památky.',
    'Na děti dohlíží jejich doprovod.',
  ],
  playIntro: 'Pustit úvod',
  continue: 'Pokračovat',
  playMuted: 'Hrát bez zvuku',
  stations: (count) => `${count} stanovišť`,
  aboutMinutes: (minutes) => `cca ${minutes} min`,
  teamName: 'Název týmu',
  diplomaName: 'Jméno na diplom',
  optional: '(nepovinné)',
  diplomaNameHint: 'Na konci hry dostane malý hráč diplom. Třeba Anička, nebo Anička a Tomáš.',
  newGameEndsRunning: 'Začátkem nové hry ukončíš tu rozehranou.',
  codeValidity: ['Po spuštění hry začne běžet platnost kódu ', '.'],
  starting: 'Začínám…',
  startGame: 'Začít hru',

  wrongAnswer: 'To není ono. Zkus to znovu.',
  notANumber: 'Odpověď musí obsahovat číslo.',
  gameLeft: 'Hra byla ukončena. Můžeš začít novou.',
  hintPenalty: (duration) => `Penalizace za nápovědy: +${duration}`,
  task: 'Úkol',
  leaveGame: 'Opustit hru',
  leaveConfirm: 'Opravdu chceš hru ukončit? Postup se ztratí.',
  leaveYes: 'Ano, ukončit hru',
  leaveNo: 'Ne, hrát dál',
  listenTask: 'Poslechni si, co se tu říká. Odpověď zazní v nahrávce.',
  showTranscript: 'Zobrazit přepis',
  gameNotRunning: 'Tato hra už není rozehraná.',

  chooseAnswer: 'Vyber odpověď',
  yourAnswer: 'Tvoje odpověď',
  sendAnswer: 'Odeslat odpověď',

  hints: 'Nápovědy',
  hint: (orderNo) => `Nápověda ${orderNo}`,
  noMoreHints: 'Další nápověda už není.',
  needHint: 'Potřebuju nápovědu',
  nextHint: 'Další nápověda',
  hintCosts: 'Nápověda ti k celkovému času přičte',
  showHint: 'Ano, ukaž nápovědu',

  play: 'Poslechnout',
  pause: 'Pozastavit',
  unmute: 'Zapnout hlas',
  mute: 'Ztlumit',

  station: (current, total) => `Stanoviště ${current} / ${total}`,
  progressLabel: 'Postup hrou',
  puzzle: (collected, total) => `Tajenka ${collected} / ${total}`,
  puzzleLabel: 'Tajenka',
  puzzleEmpty: 'zatím nic',
  downloading: 'Stahuji hru do telefonu pro hraní bez signálu…',
  downloadProgress: (done, total) => ` ${done} z ${total}`,
  downloaded: '✓ Hra je stažená, můžete hrát i bez signálu.',

  solved: (solved, total) => `Vyřešeno ${solved} / ${total}`,
  lastClue: 'Poslední stopa!',
  clueFound: 'Stopa nalezena',
  whereNext: 'Kam dál',
  toFinale: 'Na závěr příběhu',
  toNextStation: 'Na další stanoviště',

  gameCompleted: 'Hra dokončena',
  congratulations: 'Gratulujeme!',
  allTasksSolved: (count) => `Vyřešili jste všech ${count} úkolů.`,
  praise: praiseForHints,
  teamResult: (teamName) => `Výsledek týmu ${teamName}`,
  yourResult: 'Tvůj výsledek',
  totalTime: 'Celkový čas',
  penaltyPart: 'Z toho penalizace',
  hintsUsed: 'Použité nápovědy',

  diploma: 'Diplom',
  diplomaFor: (tasks) => `za vyřešení všech ${tasks} hádanek`,
  inGame: 've hře',
  saveDiploma: 'Uložit diplom',
  savingDiploma: 'Připravuji diplom…',
  diplomaSaveFailed: 'Diplom se nepodařilo uložit. Zkuste to prosím znovu.',
  diplomaCreateFailed: 'Diplom se nepodařilo vytvořit.',
  diplomaFile: 'diplom.jpg',

  error: (error) => error.message,
}

const de: GameTexts = {
  language: 'de',
  languageName: 'Deutsch',
  chooseLanguage: 'Sprache wählen',
  flagUrl: '/images/flags/de.svg',
  locale: 'de-DE',

  back: '← Zurück',
  cancel: 'Zurück',
  loading: 'Wird geladen …',
  loadingGame: 'Spiel wird geladen …',
  retry: 'Noch einmal versuchen',
  backToStart: 'Zurück zum Anfang',

  beforeStart: 'Bevor es losgeht',
  voiceHeading: '🔊 Das Spiel spricht',
  voiceInfo:
    'Eine Stimme erzählt euch die Geschichte. Schaltet den Ton des Handys ein, beendet den Lautlos-Modus und dreht die Lautstärke auf. Am besten hört ihr mit Kopfhörern. Den Text habt ihr immer auch auf dem Bildschirm.',
  safetyHeading: 'Sicher und rücksichtsvoll',
  safetyRules: [
    'Schaut beim Gehen um euch und nicht aufs Handy, vor allem auf Treppen.',
    'Das Spiel schickt euch nie in gesperrte Bereiche oder auf Mauern. Beachtet die Besucherordnung des Denkmals.',
    'Kinder werden von ihrer Begleitung beaufsichtigt.',
  ],
  playIntro: 'Einleitung abspielen',
  continue: 'Weiter',
  playMuted: 'Ohne Ton spielen',
  stations: (count) => `${count} Stationen`,
  aboutMinutes: (minutes) => `ca. ${minutes} Min.`,
  teamName: 'Teamname',
  diplomaName: 'Name auf der Urkunde',
  optional: '(optional)',
  diplomaNameHint: 'Am Ende des Spiels bekommt das Kind eine Urkunde. Zum Beispiel Anna, oder Anna und Tom.',
  newGameEndsRunning: 'Wenn du ein neues Spiel beginnst, endet das laufende.',
  codeValidity: ['Mit dem Start des Spiels beginnt die Gültigkeit des Codes ', '.'],
  starting: 'Wird gestartet …',
  startGame: 'Spiel starten',

  wrongAnswer: 'Das ist es nicht. Versuch es noch einmal.',
  notANumber: 'Die Antwort muss eine Zahl enthalten.',
  gameLeft: 'Das Spiel wurde beendet. Du kannst ein neues beginnen.',
  hintPenalty: (duration) => `Strafzeit für Hinweise: +${duration}`,
  task: 'Aufgabe',
  leaveGame: 'Spiel verlassen',
  leaveConfirm: 'Willst du das Spiel wirklich beenden? Der Fortschritt geht verloren.',
  leaveYes: 'Ja, Spiel beenden',
  leaveNo: 'Nein, weiterspielen',
  listenTask: 'Hör dir an, was hier gesagt wird. Die Antwort steckt in der Aufnahme.',
  showTranscript: 'Text anzeigen',
  gameNotRunning: 'Dieses Spiel läuft nicht mehr.',

  chooseAnswer: 'Wähle eine Antwort',
  yourAnswer: 'Deine Antwort',
  sendAnswer: 'Antwort senden',

  hints: 'Hinweise',
  hint: (orderNo) => `Hinweis ${orderNo}`,
  noMoreHints: 'Weitere Hinweise gibt es nicht.',
  needHint: 'Ich brauche einen Hinweis',
  nextHint: 'Nächster Hinweis',
  hintCosts: 'Der Hinweis erhöht deine Gesamtzeit um',
  showHint: 'Ja, Hinweis zeigen',

  play: 'Anhören',
  pause: 'Pause',
  unmute: 'Stimme einschalten',
  mute: 'Stumm',

  station: (current, total) => `Station ${current} / ${total}`,
  progressLabel: 'Fortschritt im Spiel',
  puzzle: (collected, total) => `Rätselwort ${collected} / ${total}`,
  puzzleLabel: 'Rätselwort',
  puzzleEmpty: 'noch leer',
  downloading: 'Das Spiel wird für das Spielen ohne Empfang aufs Handy geladen …',
  downloadProgress: (done, total) => ` ${done} von ${total}`,
  downloaded: '✓ Das Spiel ist geladen, ihr könnt auch ohne Empfang spielen.',

  solved: (solved, total) => `Gelöst ${solved} / ${total}`,
  lastClue: 'Die letzte Spur!',
  clueFound: 'Spur gefunden',
  whereNext: 'Wohin jetzt',
  toFinale: 'Zum Schluss der Geschichte',
  toNextStation: 'Zur nächsten Station',

  gameCompleted: 'Spiel geschafft',
  congratulations: 'Glückwunsch!',
  allTasksSolved: (count) => `Ihr habt alle ${count} Aufgaben gelöst.`,
  praise: (hintsUsed) => {
    if (hintsUsed === 0) {
      return 'Und das ohne einen einzigen Hinweis, eine meisterhafte Leistung!'
    }
    if (hintsUsed <= 3) {
      return 'Hinweise habt ihr nur selten gebraucht. Ausgezeichnet!'
    }
    return 'Die Hinweise haben geholfen, aber ans Ziel seid ihr selbst gekommen. Ausgezeichnet!'
  },
  teamResult: (teamName) => `Ergebnis des Teams ${teamName}`,
  yourResult: 'Dein Ergebnis',
  totalTime: 'Gesamtzeit',
  penaltyPart: 'Davon Strafzeit',
  hintsUsed: 'Verwendete Hinweise',

  diploma: 'Urkunde',
  diplomaFor: (tasks) => `für das Lösen aller ${tasks} Rätsel`,
  inGame: 'im Spiel',
  saveDiploma: 'Urkunde speichern',
  savingDiploma: 'Urkunde wird vorbereitet …',
  diplomaSaveFailed: 'Die Urkunde konnte nicht gespeichert werden. Bitte versucht es noch einmal.',
  diplomaCreateFailed: 'Die Urkunde konnte nicht erstellt werden.',
  diplomaFile: 'urkunde.jpg',

  error: (error) => {
    if (!(error instanceof ApiError)) {
      return 'Etwas ist schiefgelaufen. Bitte versuch es noch einmal.'
    }
    switch (error.type) {
      case '/problems/session-expired':
        return 'Das Spiel ist abgelaufen. Bitte beginne ein neues Spiel.'
      case '/problems/code-expired':
        return 'Der Code ist abgelaufen.'
      case '/problems/invalid-code':
        return 'Diesen Code gibt es nicht. Prüfe ihn bitte auf der Karte.'
      case '/problems/invalid-token':
        return 'Dieses Spiel ist auf diesem Handy nicht mehr gespeichert. Gib den Code von der Karte noch einmal ein.'
      case '/problems/invalid-state':
        return 'Das geht im Spiel gerade nicht. Lade die Seite bitte neu.'
      case '/problems/too-many-requests':
        return 'Zu viele Versuche. Warte bitte eine Minute.'
    }
    if (error.status === 0) {
      return 'Keine Verbindung zum Server. Prüfe bitte die Internetverbindung.'
    }
    return 'Etwas ist schiefgelaufen. Bitte versuch es noch einmal.'
  },
}

const texts: Record<GameTexts['language'], GameTexts> = { cs, de }

/** Texts in the language of the game; Czech for an unknown language or before the game is known. */
export function gameTexts(language: string | null | undefined): GameTexts {
  return language === 'de' ? texts.de : texts.cs
}

/** Texts of the game being shown, provided by the page (Page). */
export const GameTextsContext = createContext<GameTexts>(cs)

export function useGameTexts(): GameTexts {
  return use(GameTextsContext)
}
