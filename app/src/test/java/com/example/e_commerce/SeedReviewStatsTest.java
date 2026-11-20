package com.example.e_commerce;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SeedReviewStatsTest {

    @Test
    public void generatedReviewStatsStayWithinDemoRanges() {
        Random random = new Random(42);

        for (int index = 0; index < 500; index++) {
            SeedReviewStats stats = SeedReviewStats.random(1, random);

            assertTrue(stats.rating >= 2 && stats.rating <= 5);
            assertTrue(stats.viewsCount >= 10 && stats.viewsCount <= 200);
            assertTrue(stats.likesCount >= 0 && stats.likesCount <= 30);
            assertTrue(stats.likesCount <= stats.viewsCount);
        }
    }
}
