package com.example.e_commerce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoucherCalculator {

    public static final long BASE_SHIPPING_FEE = 30_000L;

    private VoucherCalculator() {
    }

    public static ProductDiscount calculateProductDiscount(List<CartItem> items, Voucher voucher) {
        if (voucher == null || !voucher.isProductVoucher()) {
            return ProductDiscount.notApplicable();
        }

        long eligibleSubtotal = 0;
        for (CartItem item : items) {
            if (isEligible(item, voucher)) {
                eligibleSubtotal += item.getSubtotal();
            }
        }
        if (eligibleSubtotal == 0 || eligibleSubtotal < voucher.getMinimumOrderAmount()) {
            return ProductDiscount.notApplicable();
        }

        Map<Integer, Long> discountsByProductId = new HashMap<>();
        long totalDiscount = 0;
        long maximumDiscount = voucher.getMaximumDiscountAmount();
        for (CartItem item : items) {
            if (!isEligible(item, voucher)) {
                continue;
            }
            long lineDiscount = calculateLineDiscount(item, voucher);
            if (maximumDiscount > 0) {
                lineDiscount = Math.min(lineDiscount, maximumDiscount - totalDiscount);
            }
            if (lineDiscount <= 0) {
                continue;
            }
            discountsByProductId.put(item.getProduct().getId(), lineDiscount);
            totalDiscount += lineDiscount;
        }
        return totalDiscount > 0
                ? new ProductDiscount(true, totalDiscount, discountsByProductId)
                : ProductDiscount.notApplicable();
    }

    public static long calculateShippingDiscount(List<CartItem> items, Voucher voucher, long shippingFee) {
        if (voucher == null || !voucher.isShippingVoucher() || shippingFee <= 0) {
            return 0;
        }
        long eligibleSubtotal = 0;
        for (CartItem item : items) {
            if (isEligible(item, voucher)) {
                eligibleSubtotal += item.getSubtotal();
            }
        }
        if (eligibleSubtotal == 0 || eligibleSubtotal < voucher.getMinimumOrderAmount()) {
            return 0;
        }

        long discount;
        switch (voucher.getDiscountType()) {
            case "free_shipping":
                discount = shippingFee;
                break;
            case "shipping_percentage":
                discount = Math.round(shippingFee * (voucher.getValue() / 100.0));
                break;
            case "shipping_fixed":
                discount = voucher.getValue();
                break;
            default:
                return 0;
        }
        discount = Math.min(shippingFee, Math.max(0, discount));
        if (voucher.getMaximumDiscountAmount() > 0) {
            discount = Math.min(discount, voucher.getMaximumDiscountAmount());
        }
        return discount;
    }

    private static long calculateLineDiscount(CartItem item, Voucher voucher) {
        long perUnitDiscount;
        switch (voucher.getDiscountType()) {
            case "fixed_per_item":
                perUnitDiscount = voucher.getValue();
                break;
            case "percentage_per_item":
                perUnitDiscount = Math.round(item.getProduct().getPrice() * (voucher.getValue() / 100.0));
                break;
            default:
                return 0;
        }
        return Math.min(item.getSubtotal(), Math.max(0, perUnitDiscount) * item.getQuantity());
    }

    private static boolean isEligible(CartItem item, Voucher voucher) {
        Product product = item.getProduct();
        if (product == null || item.getQuantity() <= 0) {
            return false;
        }
        if (voucher.isApplicableToAll()) {
            return true;
        }
        return voucher.isApplicableToCategory()
                && voucher.getCategory().equalsIgnoreCase(product.getCategory());
    }

    public static final class ProductDiscount {
        private final boolean applicable;
        private final long totalDiscount;
        private final Map<Integer, Long> discountsByProductId;

        private ProductDiscount(boolean applicable, long totalDiscount, Map<Integer, Long> discountsByProductId) {
            this.applicable = applicable;
            this.totalDiscount = totalDiscount;
            this.discountsByProductId = Collections.unmodifiableMap(new HashMap<>(discountsByProductId));
        }

        private static ProductDiscount notApplicable() {
            return new ProductDiscount(false, 0, Collections.emptyMap());
        }

        public boolean isApplicable() {
            return applicable;
        }

        public long getTotalDiscount() {
            return totalDiscount;
        }

        public long getDiscountForProduct(int productId) {
            Long discount = discountsByProductId.get(productId);
            return discount == null ? 0 : discount;
        }

        public Map<Integer, Long> getDiscountsByProductId() {
            return discountsByProductId;
        }
    }
}
