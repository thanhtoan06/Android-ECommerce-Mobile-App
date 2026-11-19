package com.example.e_commerce;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class PointsRules {

    public static final int CHECK_IN_DAYS = 7;

    private PointsRules() {
    }

    public static long rewardForDay(int day) {
        if (day < 1 || day > CHECK_IN_DAYS) {
            throw new IllegalArgumentException("Check-in day must be between 1 and 7.");
        }
        return day * 100L;
    }

    public static long recoverableAmount(long availableBalance, long requestedAmount) {
        if (availableBalance < 0 || requestedAmount < 0) {
            throw new IllegalArgumentException("Point amounts cannot be negative.");
        }
        return Math.min(availableBalance, requestedAmount);
    }

    public static String dateKey(Date date, TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(timeZone);
        return format.format(date);
    }

    public static CheckInState evaluateCheckIn(String lastDate, int lastDay, Date now, TimeZone timeZone) {
        String today = dateKey(now, timeZone);
        int validLastDay = lastDay >= 1 && lastDay <= CHECK_IN_DAYS ? lastDay : 0;
        if (!isValidDateKey(lastDate, timeZone)) {
            return new CheckInState(1, 0, false, false);
        }
        if (lastDate.compareTo(today) > 0) {
            return new CheckInState(1, 0, false, true);
        }
        if (lastDate.equals(today)) {
            int checkedDay = validLastDay == 0 ? 1 : validLastDay;
            return new CheckInState(checkedDay, checkedDay, true, false);
        }

        Calendar yesterday = Calendar.getInstance(timeZone);
        yesterday.setTime(now);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (lastDate.equals(dateKey(yesterday.getTime(), timeZone)) && validLastDay > 0) {
            int nextDay = validLastDay == CHECK_IN_DAYS ? 1 : validLastDay + 1;
            int completedDays = nextDay == 1 ? 0 : validLastDay;
            return new CheckInState(nextDay, completedDays, false, false);
        }
        return new CheckInState(1, 0, false, false);
    }

    public static Date expirationDate(Date receivedAt, TimeZone timeZone) {
        Calendar expiration = Calendar.getInstance(timeZone);
        expiration.setTime(receivedAt);
        expiration.add(Calendar.MONTH, 1);
        expiration.set(Calendar.DAY_OF_MONTH, expiration.getActualMaximum(Calendar.DAY_OF_MONTH));
        expiration.set(Calendar.HOUR_OF_DAY, 23);
        expiration.set(Calendar.MINUTE, 59);
        expiration.set(Calendar.SECOND, 59);
        expiration.set(Calendar.MILLISECOND, 999);
        return expiration.getTime();
    }

    private static boolean isValidDateKey(String value, TimeZone timeZone) {
        if (value == null || value.length() != 10) {
            return false;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        format.setTimeZone(timeZone);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return parsed != null && position.getIndex() == value.length() && value.equals(format.format(parsed));
    }

    public static final class CheckInState {
        private final int day;
        private final int completedDays;
        private final boolean checkedInToday;
        private final boolean deviceDateInvalid;

        private CheckInState(int day, int completedDays, boolean checkedInToday, boolean deviceDateInvalid) {
            this.day = day;
            this.completedDays = completedDays;
            this.checkedInToday = checkedInToday;
            this.deviceDateInvalid = deviceDateInvalid;
        }

        public int getDay() {
            return day;
        }

        public int getCompletedDays() {
            return completedDays;
        }

        public boolean isCheckedInToday() {
            return checkedInToday;
        }

        public boolean isDeviceDateInvalid() {
            return deviceDateInvalid;
        }
    }
}
