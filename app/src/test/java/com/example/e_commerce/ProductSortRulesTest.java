package com.example.e_commerce;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ProductSortRulesTest {

    @Test
    public void criteriaAreAppliedInSelectionOrder() {
        Product expensiveLowerSales = product(1, "A", 500, 4, 4.0, 3, null);
        Product cheapLowerSales = product(2, "B", 300, 4, 4.0, 3, null);
        Product bestSeller = product(3, "C", 700, 10, 3.0, 1, null);

        List<Product> products = Arrays.asList(expensiveLowerSales, bestSeller, cheapLowerSales);
        products.sort(ProductSortRules.comparatorFor(Arrays.asList(
                ProductSortRules.Criterion.BEST_SELLING,
                ProductSortRules.Criterion.PRICE_ASC)));

        assertEquals(Arrays.asList(bestSeller, cheapLowerSales, expensiveLowerSales), products);
    }

    @Test
    public void highestRatingUsesReviewCountToBreakTies() {
        Product fewReviews = product(1, "A", 500, 0, 5.0, 2, null);
        Product manyReviews = product(2, "B", 500, 0, 5.0, 8, null);
        Product lowerRating = product(3, "C", 500, 0, 4.9, 20, null);

        List<Product> products = Arrays.asList(fewReviews, lowerRating, manyReviews);
        products.sort(ProductSortRules.comparatorFor(
                Collections.singletonList(ProductSortRules.Criterion.TOP_RATED)));

        assertEquals(Arrays.asList(manyReviews, fewReviews, lowerRating), products);
    }

    @Test
    public void newestUsesCreationDateAndFallsBackToDescendingIdWhenDatesAreMissing() {
        Product older = product(3, "Older", 500, 0, 0, 0, new Date(100));
        Product newer = product(2, "Newer", 500, 0, 0, 0, new Date(200));
        Product legacyHigherId = product(15, "Legacy", 500, 0, 0, 0, null);

        List<Product> products = Arrays.asList(older, newer, legacyHigherId);
        products.sort(ProductSortRules.comparatorFor(
                Collections.singletonList(ProductSortRules.Criterion.NEWEST)));

        assertEquals(Arrays.asList(newer, older, legacyHigherId), products);
    }

    @Test
    public void nameSortUsesVietnameseCollation() {
        Product apple = product(1, "Apple", 500, 0, 0, 0, null);
        Product banana = product(2, "Banana", 500, 0, 0, 0, null);
        Product coffee = product(3, "Cà phê", 500, 0, 0, 0, null);

        List<Product> products = Arrays.asList(coffee, banana, apple);
        products.sort(ProductSortRules.comparatorFor(
                Collections.singletonList(ProductSortRules.Criterion.NAME_A_TO_Z)));

        assertEquals(Arrays.asList(apple, banana, coffee), products);
    }

    private static Product product(int id, String name, long price, int soldCount, double rating,
            int reviewCount, Date createdAt) {
        return new Product(id, name, "Test", price, "", 1, soldCount, rating,
                reviewCount, Collections.emptyMap(), createdAt);
    }
}
