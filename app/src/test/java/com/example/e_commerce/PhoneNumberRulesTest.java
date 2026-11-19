package com.example.e_commerce;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneNumberRulesTest {

    @Test
    public void acceptsVietnamesePhoneNumbersWithCommonFormatting() {
        assertTrue(PhoneNumberRules.isValid("0912345678"));
        assertTrue(PhoneNumberRules.isValid("+84 912-345-678"));
        assertTrue(PhoneNumberRules.isValid("(0912) 345 678"));
    }

    @Test
    public void rejectsNondigitAndOutOfRangePhoneNumbers() {
        assertFalse(PhoneNumberRules.isValid("abcdefghij"));
        assertFalse(PhoneNumberRules.isValid("0912abc678"));
        assertFalse(PhoneNumberRules.isValid("12345678"));
        assertFalse(PhoneNumberRules.isValid("091234567890"));
        assertFalse(PhoneNumberRules.isValid(null));
    }
}
