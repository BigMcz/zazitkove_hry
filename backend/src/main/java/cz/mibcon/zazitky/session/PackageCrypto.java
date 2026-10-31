package cz.mibcon.zazitky.session;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import cz.mibcon.zazitky.session.SessionPackage.Sealed;

/**
 * Encryption of the offline game package (docs/02-architektura.md, "Offline režim"), matching the Web Crypto
 * API in the phone (frontend/src/offline/packageCrypto.ts, shared case test-fixtures/offline-crypto.json):
 * AES-256-GCM with a 12-byte IV and 128-bit tag, answer keys by PBKDF2-HMAC-SHA256 from the normalized answer.
 */
final class PackageCrypto {

    static final int KDF_ITERATIONS = 100_000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private PackageCrypto() {
    }

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static SecretKey contentKey(byte[] raw) {
        return new SecretKeySpec(raw, "AES");
    }

    static SecretKey answerKey(String normalizedAnswer, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(normalizedAnswer.toCharArray(), salt, iterations, 256);
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(raw, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        }
    }

    static Sealed seal(SecretKey key, byte[] plaintext) {
        return seal(key, randomBytes(12), plaintext);
    }

    static Sealed seal(SecretKey key, byte[] iv, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new Sealed(encode(iv), encode(cipher.doFinal(plaintext)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM failed", e);
        }
    }

    /** @throws GeneralSecurityException when the key is wrong (the tag does not match) */
    static byte[] open(SecretKey key, Sealed sealed) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, decode(sealed.iv())));
        return cipher.doFinal(decode(sealed.data()));
    }

    static String encode(byte[] bytes) {
        return BASE64.encodeToString(bytes);
    }

    static byte[] decode(String base64) {
        return BASE64_DECODER.decode(base64);
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
