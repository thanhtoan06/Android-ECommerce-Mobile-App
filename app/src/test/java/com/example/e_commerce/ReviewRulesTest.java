package com.example.e_commerce;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReviewRulesTest {

    @Test
    public void reviewIdentityIsOnePerProductPerOrder() {
        assertEquals("12_7", ReviewRules.documentId(12, 7));
        assertFalse(ReviewRules.documentId(12, 7).equals(ReviewRules.documentId(13, 7)));
        assertFalse(ReviewRules.documentId(12, 7).equals(ReviewRules.documentId(12, 8)));
    }

    @Test
    public void deliveredStatusIsRecognizedForReviewEligibility() {
        assertTrue(ReviewRules.isDelivered("Đã giao"));
        assertFalse(ReviewRules.isDelivered("Đang giao"));
    }

    @Test
    public void orderStatusFiltersIncludeNewOrdersAsWaiting() {
        assertTrue(ReviewRules.matchesStatusFilter("Đặt hàng thành công", ReviewRules.FILTER_WAITING));
        assertTrue(ReviewRules.matchesStatusFilter("Chờ xác nhận", ReviewRules.FILTER_WAITING));
        assertFalse(ReviewRules.matchesStatusFilter("Đang giao", ReviewRules.FILTER_WAITING));
        assertTrue(ReviewRules.matchesStatusFilter(
                OrderReturnRules.STATUS_RETURNED, ReviewRules.FILTER_RETURNED));
        assertFalse(ReviewRules.matchesStatusFilter(
                "Đã hủy", ReviewRules.FILTER_RETURNED));
        assertTrue(ReviewRules.matchesStatusFilter(
                "Đã hủy", ReviewRules.FILTER_CANCELLED));
    }

    @Test
    public void editingReviewChangesAverageWithoutIncreasingCount() {
        assertEquals(3, ReviewRules.reviewCountAfter(2, true));
        assertEquals(2, ReviewRules.reviewCountAfter(2, false));
        assertEquals(4.0, ReviewRules.averageAfter(2, 4.0, 0, 4, true), 0.0001);
        assertEquals(3.0, ReviewRules.averageAfter(2, 4.5, 5, 2, false), 0.0001);
    }
}
