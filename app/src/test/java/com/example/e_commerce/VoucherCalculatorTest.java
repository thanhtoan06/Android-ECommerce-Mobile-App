package com.example.e_commerce;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoucherCalculatorTest {

    @Test
    public void productPercentageAppliesToEachEligibleProduct() {
        Product headphones = product(1, "Tai nghe", 100_000);
        Product phone = new Product(2, "Điện thoại", "Điện thoại", 200_000, "", 10);
        Voucher voucher = voucher("product", "percentage_per_item", 10, "category", "Tai nghe", 0, 0);

        VoucherCalculator.ProductDiscount result = VoucherCalculator.calculateProductDiscount(
                Arrays.asList(new CartItem(1, headphones, 2), new CartItem(2, phone, 1)), voucher);

        assertTrue(result.isApplicable());
        assertEquals(20_000, result.getTotalDiscount());
        assertEquals(20_000, result.getDiscountForProduct(1));
        assertEquals(0, result.getDiscountForProduct(2));
    }

    @Test
    public void fixedProductDiscountRespectsPerVoucherMaximum() {
        Voucher voucher = voucher("product", "fixed_per_item", 500_000, "all", "", 0, 600_000);
        VoucherCalculator.ProductDiscount result = VoucherCalculator.calculateProductDiscount(
                Arrays.asList(new CartItem(1, product(1, "Laptop A", 1_000_000), 1),
                        new CartItem(2, product(2, "Laptop B", 1_000_000), 1)),
                voucher);

        assertEquals(600_000, result.getTotalDiscount());
        assertEquals(500_000, result.getDiscountForProduct(1));
        assertEquals(100_000, result.getDiscountForProduct(2));
    }

    @Test
    public void productVoucherRequiresMinimumEligibleSubtotal() {
        Voucher voucher = voucher("product", "percentage_per_item", 10, "category", "Laptop", 1_500_000, 0);
        VoucherCalculator.ProductDiscount result = VoucherCalculator.calculateProductDiscount(
                Collections.singletonList(new CartItem(1, product(1, "Laptop", 1_499_000), 1)), voucher);

        assertFalse(result.isApplicable());
        assertEquals(0, result.getTotalDiscount());
    }

    @Test
    public void productVoucherCanTargetSpecificProducts() {
        Voucher voucher = voucher("product", "fixed_per_item", 25_000, "product", "", 0, 0,
                Collections.singletonList(2));
        VoucherCalculator.ProductDiscount result = VoucherCalculator.calculateProductDiscount(
                Arrays.asList(new CartItem(1, product(1, "Phone A", 100_000), 1),
                        new CartItem(2, product(2, "Phone B", 200_000), 1)),
                voucher);

        assertEquals(25_000, result.getTotalDiscount());
        assertEquals(0, result.getDiscountForProduct(1));
        assertEquals(25_000, result.getDiscountForProduct(2));
    }

    @Test
    public void shippingDiscountIsCappedByFeeAndVoucherMaximum() {
        Voucher voucher = voucher("shipping", "shipping_percentage", 70, "all", "", 0, 15_000);
        long discount = VoucherCalculator.calculateShippingDiscount(
                Collections.singletonList(new CartItem(1, product(1, "Phone", 500_000), 1)),
                voucher,
                30_000);

        assertEquals(15_000, discount);
    }

    private static Product product(int id, String name, long price) {
        return new Product(id, name, "Tai nghe", price, "", 10);
    }

    private static Voucher voucher(String type, String discountType, long value, String appliesTo,
            String category, long minimumOrderAmount, long maximumDiscountAmount) {
        return voucher(type, discountType, value, appliesTo, category, minimumOrderAmount, maximumDiscountAmount,
                Collections.emptyList());
    }

    private static Voucher voucher(String type, String discountType, long value, String appliesTo,
            String category, long minimumOrderAmount, long maximumDiscountAmount, java.util.List<Integer> productIds) {
        return new Voucher("id", "code", "name", "", type, discountType, value, appliesTo, category,
                productIds, Collections.emptyList(),
                minimumOrderAmount, maximumDiscountAmount, 30, null, null, null, null, "saved", 1, true);
    }
}
