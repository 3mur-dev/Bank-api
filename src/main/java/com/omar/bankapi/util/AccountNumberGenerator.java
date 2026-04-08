package com.omar.bankapi.util;

import java.security.SecureRandom;

public class AccountNumberGenerator {

    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
