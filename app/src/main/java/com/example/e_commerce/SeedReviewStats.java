package com.example.e_commerce;

import java.util.Random;

final class SeedReviewStats {

    final int productId;
    final int rating;
    final long likesCount;
    final long viewsCount;

    private SeedReviewStats(int productId, int rating, long likesCount, long viewsCount) {
        this.productId = productId;
        this.rating = rating;
        this.likesCount = likesCount;
        this.viewsCount = viewsCount;
    }

    static SeedReviewStats random(int productId, Random random) {
        long viewsCount = 10 + random.nextInt(191);
        long likesCount = random.nextInt((int) Math.min(viewsCount, 30) + 1);
        return new SeedReviewStats(productId, 2 + random.nextInt(4), likesCount, viewsCount);
    }
}
