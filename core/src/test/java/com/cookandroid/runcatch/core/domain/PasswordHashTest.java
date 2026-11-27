package com.cookandroid.runcatch.core.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordHashTest {

    @Test
    public void matches_samePassword() {
        String stored = PasswordHash.hash("runcatch123");
        assertTrue(PasswordHash.matches("runcatch123", stored));
        assertFalse(PasswordHash.matches("runcatch124", stored));
    }

    @Test
    public void hash_usesRandomSalt() {
        assertNotEquals(PasswordHash.hash("runcatch123"), PasswordHash.hash("runcatch123"));
    }

    @Test
    public void matches_rejectsPlainOrBrokenValue() {
        // 예전 버전이 평문으로 저장한 비밀번호는 통과시키지 않음
        assertFalse(PasswordHash.matches("runcatch123", "runcatch123"));
        assertFalse(PasswordHash.matches("runcatch123", "pbkdf2$x$zz$zz"));
        assertFalse(PasswordHash.matches("runcatch123", null));
    }
}
