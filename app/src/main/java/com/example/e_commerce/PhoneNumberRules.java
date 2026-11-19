package com.example.e_commerce;

import java.util.regex.Pattern;

public final class PhoneNumberRules {

    private static final Pattern PHONE_FORMAT = Pattern.compile("\\+?(?:[0-9]|\\([0-9]{2,4}\\))[0-9\\s().-]*");

    private PhoneNumberRules() {
    }

    public static boolean isValid(String phone) {
        if (phone == null) {
            return false;
        }
        String normalized = phone.trim();
        if (!PHONE_FORMAT.matcher(normalized).matches()) {
            return false;
        }
        int digitCount = normalized.replaceAll("[^0-9]", "").length();
        return digitCount >= 9 && digitCount <= 11;
    }
}
