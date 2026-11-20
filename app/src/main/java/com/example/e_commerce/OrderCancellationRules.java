package com.example.e_commerce;

public final class OrderCancellationRules {

    public static final String STATUS_CANCELLED = "Đã hủy";

    private OrderCancellationRules() {
    }

    public static boolean isEligible(String status) {
        return "Đặt hàng thành công".equals(status)
                || "Chờ xác nhận".equals(status)
                || "Đang giao".equals(status);
    }
}
