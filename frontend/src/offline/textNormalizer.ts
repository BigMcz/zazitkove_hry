/**
 * Answer normalization in the phone for the offline game. Must give exactly the same results as the backend
 * TextNormalizer (shared cases in test-fixtures/text-normalizer.json), otherwise a correct answer would not
 * unlock the next station.
 */

// Java's \s without UNICODE_CHARACTER_CLASS: ASCII whitespace only (a non-breaking space is punctuation there)
const WHITESPACE = /[ \t\n\v\f\r]+/g
const COMBINING_MARKS = /\p{M}+/gu
const PUNCTUATION = /[^\p{L}\p{N} ]/gu
const NON_DIGITS = /[^0-9]/g

/** Lower case, strip diacritics and punctuation, collapse whitespace: " Díra! " → "dira". */
export function normalize(input: string): string {
  let text = input.toLowerCase().normalize('NFD').replace(COMBINING_MARKS, '')
  // Whitespace first, so that a newline or tab between words is not removed as punctuation
  text = text.replace(WHITESPACE, ' ').replace(PUNCTUATION, '')
  return text.replace(WHITESPACE, ' ').trim()
}

/** Keeps only ASCII digits: "56 schodů" → "56". */
export function normalizeNumber(input: string): string {
  return input.replace(NON_DIGITS, '')
}
