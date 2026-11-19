package com.example.e_commerce;

public final class ReviewRules {

    public static final String FILTER_ALL = "all";
    public static final String FILTER_WAITING = "waiting";
    public static final String FILTER_SHIPPING = "shipping";
    public static final String FILTER_DELIVERED = "delivered";
    public static final String FILTER_RETURNED = "returned";

    private ReviewRules() {
    }

    public static String documentId(int orderId, int productId) {
        return orderId + "_" + productId;
    }

    public static int reviewCountAfter(int currentCount, boolean isNewReview) {
        int count = Math.max(0, currentCount);
        return isNewReview ? count + 1 : Math.max(1, count);
    }

    public static double averageAfter(int currentCount, double currentAverage, int oldRating,
            int newRating, boolean isNewReview) {
        int count = Math.max(0, currentCount);
        if (isNewReview) {
            int updatedCount = count + 1;
            return ((currentAverage * count) + newRating) / updatedCount;
        }
        if (count == 0) {
            return newRating;
        }
        return ((currentAverage * count) - oldRating + newRating) / count;
    }

    public static boolean isDelivered(String status) {
        return "Đã giao".equals(status);
    }

    public static String nextDemoStatus(String currentStatus) {
        if (currentStatus == null || currentStatus.isEmpty() || "Đặt hàng thành công".equals(currentStatus)) {
            return "Chờ xác nhận";
        }
        if ("Chờ xác nhận".equals(currentStatus)) {
            return "Đang giao";
        }
        if ("Đang giao".equals(currentStatus)) {
            return "Đã giao";
        }
        return null;
    }

    public static boolean matchesStatusFilter(String status, String filter) {
        if (FILTER_ALL.equals(filter)) {
            return true;
        }
        if (FILTER_WAITING.equals(filter)) {
            return status == null || status.isEmpty()
                    || "Đặt hàng thành công".equals(status)
                    || "Chờ xác nhận".equals(status);
        }
        if (FILTER_SHIPPING.equals(filter)) {
            return "Đang giao".equals(status);
        }
        if (FILTER_DELIVERED.equals(filter)) {
            return isDelivered(status);
        }
        if (FILTER_RETURNED.equals(filter)) {
            return OrderReturnRules.STATUS_RETURNED.equals(status)
                    || "Hoàn trả".equals(status)
                    || "Đã trả hàng".equals(status)
                    || "Đã hủy".equals(status);
        }
        return false;
    }
}
