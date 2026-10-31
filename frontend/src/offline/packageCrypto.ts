import type { Sealed, SealedStation, Unlocked } from './types.ts'

/**
 * Opening the offline game package with the Web Crypto API, matching the backend PackageCrypto (shared case
 * test-fixtures/offline-crypto.json): AES-256-GCM, answer keys by PBKDF2-HMAC-SHA256 from the normalized answer.
 */

function fromBase64(base64: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
}

function toBase64(bytes: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
}

export async function answerKey(normalizedAnswer: string, salt: string, iterations: number): Promise<CryptoKey> {
  const password = await crypto.subtle.importKey('raw', new TextEncoder().encode(normalizedAnswer), 'PBKDF2', false, [
    'deriveKey',
  ])
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt: fromBase64(salt), iterations },
    password,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt'],
  )
}

export function contentKey(rawKey: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', fromBase64(rawKey), 'AES-GCM', false, ['decrypt'])
}

/** Rejects when the key is wrong (the tag does not match). */
export function open(key: CryptoKey, sealed: Sealed): Promise<ArrayBuffer> {
  return crypto.subtle.decrypt({ name: 'AES-GCM', iv: fromBase64(sealed.iv) }, key, fromBase64(sealed.data))
}

/** What a station reveals, opened by its content key (base64). */
export async function openStation(station: SealedStation, rawKey: string): Promise<Unlocked> {
  const content = await open(await contentKey(rawKey), station.content)
  return JSON.parse(new TextDecoder().decode(content)) as Unlocked
}

/**
 * Tries the normalized answer on a station: its key opens one of the wrapped content keys only when it is one
 * of the accepted answers. Returns the content key (base64) and what the station reveals, or null.
 */
export async function solveStation(
  station: SealedStation,
  normalizedAnswer: string,
  iterations: number,
): Promise<{ rawKey: string; unlocked: Unlocked } | null> {
  if (normalizedAnswer === '') {
    return null
  }
  const key = await answerKey(normalizedAnswer, station.salt, iterations)
  for (const wrapped of station.keys) {
    let rawKey: string
    try {
      rawKey = toBase64(await open(key, wrapped))
    } catch {
      continue
    }
    return { rawKey, unlocked: await openStation(station, rawKey) }
  }
  return null
}
