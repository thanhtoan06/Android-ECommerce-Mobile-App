package com.example.e_commerce;

import java.text.Collator;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ProductSortRules {

    public enum Criterion {
        PRICE_ASC("Giá tăng dần (Thấp → Cao)", "Giá ↑"),
        PRICE_DESC("Giá giảm dần (Cao → Thấp)", "Giá ↓"),
        BEST_SELLING("Bán chạy nhất", "Bán chạy"),
        TOP_RATED("Đánh giá cao nhất", "Đánh giá"),
        NAME_A_TO_Z("Tên A–Z", "Tên A–Z"),
        NEWEST("Mới nhất", "Mới nhất");

        private final String label;
        private final String shortLabel;

        Criterion(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        public String getLabel() {
            return label;
        }

        public String getShortLabel() {
            return shortLabel;
        }
    }

    private ProductSortRules() {
    }

    public static Comparator<Product> comparatorFor(List<Criterion> criteria) {
        Comparator<Product> comparator = (first, second) -> 0;
        for (Criterion criterion : criteria) {
            comparator = comparator.thenComparing(comparatorFor(criterion));
        }
        return comparator;
    }

    private static Comparator<Product> comparatorFor(Criterion criterion) {
        switch (criterion) {
        case PRICE_ASC:
            return Comparator.comparingLong(Product::getPrice);
        case PRICE_DESC:
            return (first, second) -> Long.compare(second.getPrice(), first.getPrice());
        case BEST_SELLING:
            return (first, second) -> Integer.compare(second.getSoldCount(), first.getSoldCount());
        case TOP_RATED:
            return (first, second) -> {
                int ratingComparison = Double.compare(second.getAverageRating(), first.getAverageRating());
                if (ratingComparison != 0) {
                    return ratingComparison;
                }
                return Integer.compare(second.getReviewCount(), first.getReviewCount());
            };
        case NAME_A_TO_Z:
            Collator collator = Collator.getInstance(new Locale("vi", "VN"));
            return (first, second) -> compareNames(first.getName(), second.getName(), collator);
        case NEWEST:
            return ProductSortRules::compareNewest;
        default:
            throw new IllegalArgumentException("Unsupported product sort criterion: " + criterion);
        }
    }

    private static int compareNames(String firstName, String secondName, Collator collator) {
        if (firstName == null) {
            return secondName == null ? 0 : 1;
        }
        if (secondName == null) {
            return -1;
        }
        return collator.compare(firstName, secondName);
    }

    private static int compareNewest(Product first, Product second) {
        Date firstCreatedAt = first.getCreatedAt();
        Date secondCreatedAt = second.getCreatedAt();
        if (firstCreatedAt != null && secondCreatedAt != null) {
            int dateComparison = Long.compare(secondCreatedAt.getTime(), firstCreatedAt.getTime());
            if (dateComparison != 0) {
                return dateComparison;
            }
        } else if (firstCreatedAt != null) {
            return -1;
        } else if (secondCreatedAt != null) {
            return 1;
        }
        return Integer.compare(second.getId(), first.getId());
    }
}
