package com.uba.mbp.sharedplatform.auth.mfa;

/**
 * Minimal RFC 4648 Base32 encoder — plain encoding, not cryptography, so
 * hand-rolling it (unlike TOTP itself) carries no security risk. Used only
 * to derive deterministic, documented dev-seed MFA secrets from a fixed seed
 * string, so the same secret is reproducible without checking in a literal
 * that might silently contain an invalid character.
 */
final class Base32Codec {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32Codec() {
    }

    static String encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(ALPHABET.charAt(index));
        }
        return result.toString();
    }
}
