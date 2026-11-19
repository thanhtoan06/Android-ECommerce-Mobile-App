package com.example.e_commerce;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderReturnRulesTest {

    @Test
    public void deliveredOrderCanBeReturnedThroughSevenDayBoundary() {
        Date deliveredAt = date("2026-11-10 12:00");

        assertTrue(OrderReturnRules.isEligible("Đã giao", deliveredAt, null,
                date("2026-11-17 12:00")));
        assertFalse(OrderReturnRules.isEligible("Đã giao", deliveredAt, null,
                date("2026-11-17 12:00:00.001")));
    }

    @Test
    public void undeliveredOrFutureDatedOrdersCannotBeReturned() {
        Date now = date("2026-11-10 12:00");

        assertFalse(OrderReturnRules.isEligible("Đang giao", now, null, now));
        assertFalse(OrderReturnRules.isEligible("Đã giao", date("2026-11-10 12:01"), null, now));
    }

    @Test
    public void missingDeliveryTimestampUsesTheStoredOrderDateConservatively() {
        assertTrue(OrderReturnRules.isEligible(
                "Đã giao", null, "10/11/2026 12:00", date("2026-11-17 12:00")));
        assertFalse(OrderReturnRules.isEligible(
                OrderReturnRules.STATUS_RETURNED, date("2026-11-10 12:00"), null,
                date("2026-11-11 12:00")));
    }

    @Test
    public void otherReasonRequiresAValidDescription() {
        assertTrue(OrderReturnRules.isValidReason(OrderReturnRules.REASON_WRONG_MODEL, ""));
        assertTrue(OrderReturnRules.isValidReason(OrderReturnRules.REASON_MISSING_ITEM, ""));
        assertFalse(OrderReturnRules.isValidReason(OrderReturnRules.REASON_OTHER, "  "));
        assertTrue(OrderReturnRules.isValidReason(OrderReturnRules.REASON_OTHER, "Mô tả lý do"));
        assertFalse(OrderReturnRules.isValidReason(
                OrderReturnRules.REASON_OTHER, new String(new char[501]).replace('\0', 'x')));
    }

    private static Date date(String value) {
        String pattern = value.length() == 16 ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd HH:mm:ss.SSS";
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setLenient(false);
        try {
            return format.parse(value);
        } catch (ParseException error) {
            throw new AssertionError(error);
        }
    }
}
