package com.example.e_commerce;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class OrderReturnRules {

    public static final String REASON_WRONG_MODEL = "wrong_model";
    public static final String REASON_MISSING_ITEM = "missing_item";
    public static final String REASON_OTHER = "other";
    public static final String STATUS_RETURNED = "Đã hoàn trả";
    public static final long RETURN_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private OrderReturnRules() {
    }

    public static boolean isEligible(String status, Date deliveredAt, String orderDate, Date now) {
        if (!ReviewRules.isDelivered(status) || now == null) {
            return false;
        }
        Date deliveryDate = deliveredAt == null ? parseOrderDate(orderDate) : deliveredAt;
        if (deliveryDate == null || now.before(deliveryDate)) {
            return false;
        }
        return now.getTime() - deliveryDate.getTime() <= RETURN_WINDOW_MILLIS;
    }

    public static boolean isValidReason(String reasonType, String reason) {
        if (REASON_WRONG_MODEL.equals(reasonType) || REASON_MISSING_ITEM.equals(reasonType)) {
            return true;
        }
        return REASON_OTHER.equals(reasonType)
                && reason != null
                && !reason.trim().isEmpty()
                && reason.trim().length() <= 500;
    }

    private static Date parseOrderDate(String orderDate) {
        if (orderDate == null || orderDate.trim().isEmpty()) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        format.setLenient(false);
        try {
            return format.parse(orderDate.trim());
        } catch (ParseException error) {
            return null;
        }
    }
}
