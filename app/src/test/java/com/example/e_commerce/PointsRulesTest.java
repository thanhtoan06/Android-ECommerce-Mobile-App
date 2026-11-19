package com.example.e_commerce;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PointsRulesTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void rewardsIncreaseAndWrapAfterDaySeven() {
        for (int day = 1; day <= 7; day++) {
            assertEquals(day * 100L, PointsRules.rewardForDay(day));
        }

        PointsRules.CheckInState next = PointsRules.evaluateCheckIn(
                "2026-11-18", 7, date("2026-11-19"), UTC);
        assertEquals(1, next.getDay());
        assertEquals(0, next.getCompletedDays());
    }

    @Test
    public void missedDayRestartsAtDayOne() {
        PointsRules.CheckInState next = PointsRules.evaluateCheckIn(
                "2026-11-17", 4, date("2026-11-19"), UTC);
        assertEquals(1, next.getDay());
        assertEquals(0, next.getCompletedDays());
        assertFalse(next.isCheckedInToday());
    }

    @Test
    public void sameDateCannotBeClaimedTwice() {
        PointsRules.CheckInState state = PointsRules.evaluateCheckIn(
                "2026-11-19", 3, date("2026-11-19"), UTC);
        assertTrue(state.isCheckedInToday());
        assertEquals(3, state.getDay());
    }

    @Test
    public void recoveryCannotDeductMoreThanTheAvailableBalance() {
        assertEquals(300, PointsRules.recoverableAmount(300, 500));
        assertEquals(300, PointsRules.recoverableAmount(500, 300));
        assertEquals(0, PointsRules.recoverableAmount(0, 500));
    }

    @Test
    public void pointsExpireAtTheEndOfTheFollowingCalendarMonth() {
        assertEquals(date("2026-12-31 23:59:59.999").getTime(),
                PointsRules.expirationDate(date("2026-11-19 10:00:00.000"), UTC).getTime());
        assertEquals(date("2024-02-29 23:59:59.999").getTime(),
                PointsRules.expirationDate(date("2024-01-31 10:00:00.000"), UTC).getTime());
    }

    private static Date date(String value) {
        String pattern = value.length() == 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss.SSS";
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(UTC);
        format.setLenient(false);
        try {
            return format.parse(value);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
