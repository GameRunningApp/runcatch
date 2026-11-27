package com.cookandroid.runcatch.core.domain;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 비밀번호를 DB에 평문 대신 저장하기 위한 해시.
 * 저장 형식: "pbkdf2$반복횟수$솔트(hex)$해시(hex)"
 * minSdk 24에서 쓸 수 있는 PBKDF2WithHmacSHA1을 사용한다.
 */
public final class PasswordHash {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int ITERATIONS = 10000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHash() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + toHex(salt) + "$"
                + toHex(derive(password, salt, ITERATIONS));
    }

    // 저장된 값이 형식에 맞지 않으면 (예: 예전 평문 비밀번호) false
    public static boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] expected = fromHex(parts[3]);
            byte[] actual = derive(password, fromHex(parts[2]), iterations);
            // 비교 시간이 내용에 따라 달라지지 않도록 MessageDigest.isEqual 사용
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " 사용 불가", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("hex 길이 오류");
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
