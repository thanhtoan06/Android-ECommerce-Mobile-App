package com.example.e_commerce;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderCancellationRulesTest {

    @Test
    public void cancellationIsAllowedUntilDelivery() {
        assertTrue(OrderCancellationRules.isEligible("Đặt hàng thành công"));
        assertTrue(OrderCancellationRules.isEligible("Chờ xác nhận"));
        assertTrue(OrderCancellationRules.isEligible("Đang giao"));
    }

    @Test
    public void deliveredReturnedAndCancelledOrdersCannotBeCancelled() {
        assertFalse(OrderCancellationRules.isEligible("Đã giao"));
        assertFalse(OrderCancellationRules.isEligible(OrderReturnRules.STATUS_RETURNED));
        assertFalse(OrderCancellationRules.isEligible(OrderCancellationRules.STATUS_CANCELLED));
        assertFalse(OrderCancellationRules.isEligible(null));
    }
}
