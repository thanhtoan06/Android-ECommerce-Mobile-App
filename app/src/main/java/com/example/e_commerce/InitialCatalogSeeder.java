package com.example.e_commerce;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InitialCatalogSeeder {

    private static final String SEED_MARKER = "demoSeed";
    private static final long REVIEW_STATS_VERSION = 1L;
    private static final long PRODUCT_IMAGE_VERSION = 1L;
    private static final ExecutorService SEED_EXECUTOR = Executors.newSingleThreadExecutor();
    private static Task<Boolean> seedTask;

    private InitialCatalogSeeder() {
    }

    public static synchronized Task<Boolean> seedCatalogAndVouchersIfNeeded(FirebaseFirestore firestore) {
        if (seedTask != null) {
            return seedTask;
        }

        TaskCompletionSource<Boolean> completion = new TaskCompletionSource<>();
        seedTask = completion.getTask();

        firestore.collection("appMetadata").document(SEED_MARKER).get()
                .addOnSuccessListener(marker -> {
                    if (marker.exists()) {
                        Long version = marker.getLong("reviewStatsVersion");
                        if (version != null && version >= REVIEW_STATS_VERSION) {
                            seedProductImagesIfNeeded(firestore, completion);
                        } else {
                            migrateSeedReviewStatsIfNeeded(firestore, completion);
                        }
                        return;
                    }
                    seedAll(firestore, completion);
                })
                .addOnFailureListener(completion::setException);

        seedTask.addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                synchronized (InitialCatalogSeeder.class) {
                    seedTask = null;
                }
            }
        });
        return seedTask;
    }

    private static void seedAll(FirebaseFirestore firestore,
            TaskCompletionSource<Boolean> completion) {
        SEED_EXECUTOR.execute(() -> {
            try {
                Context context = FirebaseApp.getInstance().getApplicationContext();
                Map<Integer, byte[]> productImages = SeedProductImages.load(context);
                List<Map<String, Object>> operations = new ArrayList<>();
                Map<String, SeedReviewStats> reviewStats = randomizedSeedReviewStats();
                Map<Integer, ProductRatingStats> productRatingStats = productRatingStats(reviewStats);

                addCatalogData(operations, productRatingStats, productImages);
                addAccountsData(operations, reviewStats);

                commitOperations(firestore, operations, 0, completion);
            } catch (IOException | RuntimeException error) {
                completion.setException(error);
            }
        });
    }

    private static Map<String, SeedReviewStats> randomizedSeedReviewStats() {
        Map<String, SeedReviewStats> result = new LinkedHashMap<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (SeedOrder seedOrder : seedOrders()) {
            if (!seedOrder.delivered || seedOrder.rating == 0) {
                continue;
            }

            String reviewId = ReviewRules.documentId(seedOrder.id, seedOrder.productId);
            result.put(reviewId, SeedReviewStats.random(seedOrder.productId, random));
        }
        return result;
    }

    private static Map<Integer, ProductRatingStats> productRatingStats(
            Map<String, SeedReviewStats> reviewStats) {
        Map<Integer, ProductRatingStats> result = new HashMap<>();
        for (SeedReviewStats stats : reviewStats.values()) {
            ProductRatingStats productStats = result.get(stats.productId);
            if (productStats == null) {
                productStats = new ProductRatingStats();
                result.put(stats.productId, productStats);
            }
            productStats.add(stats.rating);
        }
        return result;
    }

    private static void migrateSeedReviewStatsIfNeeded(FirebaseFirestore firestore,
            TaskCompletionSource<Boolean> completion) {
        Map<String, SeedReviewStats> reviewStats = randomizedSeedReviewStats();
        List<SeedReviewRecord> seededReviews = new ArrayList<>();
        for (SeedOrder seedOrder : seedOrders()) {
            if (seedOrder.delivered && seedOrder.rating > 0) {
                String reviewId = ReviewRules.documentId(seedOrder.id, seedOrder.productId);
                seededReviews.add(new SeedReviewRecord(reviewId, seedOrder.productId,
                        seedOrder.rating, reviewStats.get(reviewId)));
            }
        }

        DocumentReference markerRef = firestore.collection("appMetadata").document(SEED_MARKER);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot marker = transaction.get(markerRef);
            Long version = marker.getLong("reviewStatsVersion");
            if (!marker.exists() || (version != null && version >= REVIEW_STATS_VERSION)) {
                return false;
            }

            Map<SeedReviewRecord, DocumentSnapshot> existingReviews = new LinkedHashMap<>();
            Map<Integer, DocumentSnapshot> products = new LinkedHashMap<>();
            for (SeedReviewRecord seededReview : seededReviews) {
                DocumentReference reviewRef = firestore.collection("reviews").document(seededReview.id);
                DocumentSnapshot review = transaction.get(reviewRef);
                if (!review.exists()) {
                    continue;
                }

                existingReviews.put(seededReview, review);
                if (!products.containsKey(seededReview.productId)) {
                    DocumentReference productRef = firestore.collection("products")
                            .document(String.valueOf(seededReview.productId));
                    products.put(seededReview.productId, transaction.get(productRef));
                }
            }

            Map<Integer, Integer> ratingDeltas = new HashMap<>();
            for (Map.Entry<SeedReviewRecord, DocumentSnapshot> entry : existingReviews.entrySet()) {
                SeedReviewRecord seededReview = entry.getKey();
                DocumentSnapshot product = products.get(seededReview.productId);
                if (product == null || !product.exists()) {
                    throw new IllegalStateException(
                            "Missing seeded product while migrating review " + seededReview.id);
                }

                Object rawRating = entry.getValue().get("rating");
                int oldRating = rawRating instanceof Number
                        ? ((Number) rawRating).intValue()
                        : seededReview.originalRating;
                SeedReviewStats stats = seededReview.stats;
                Map<String, Object> reviewUpdate = new HashMap<>();
                reviewUpdate.put("rating", stats.rating);
                reviewUpdate.put("likesCount", stats.likesCount);
                reviewUpdate.put("viewsCount", stats.viewsCount);
                transaction.update(firestore.collection("reviews").document(seededReview.id), reviewUpdate);

                int delta = stats.rating - oldRating;
                Integer previousDelta = ratingDeltas.get(seededReview.productId);
                ratingDeltas.put(seededReview.productId, (previousDelta == null ? 0 : previousDelta) + delta);
            }

            for (Map.Entry<Integer, Integer> entry : ratingDeltas.entrySet()) {
                DocumentSnapshot product = products.get(entry.getKey());
                Object rawReviewCount = product.get("reviewCount");
                Object rawAverage = product.get("averageRating");
                int reviewCount = rawReviewCount instanceof Number
                        ? ((Number) rawReviewCount).intValue()
                        : 0;
                double currentAverage = rawAverage instanceof Number
                        ? ((Number) rawAverage).doubleValue()
                        : 0.0;
                long updatedRatingSum = Math.round(currentAverage * reviewCount) + entry.getValue();
                double updatedAverage = reviewCount == 0
                        ? 0.0
                        : (double) updatedRatingSum / reviewCount;

                Map<String, Object> productUpdate = new HashMap<>();
                productUpdate.put("ratingSum", updatedRatingSum);
                productUpdate.put("averageRating", updatedAverage);
                transaction.update(firestore.collection("products").document(String.valueOf(entry.getKey())),
                        productUpdate);
            }

            Map<String, Object> markerUpdate = new HashMap<>();
            markerUpdate.put("reviewStatsVersion", REVIEW_STATS_VERSION);
            markerUpdate.put("reviewStatsUpdatedAt", FieldValue.serverTimestamp());
            transaction.update(markerRef, markerUpdate);
            return true;
        }).addOnSuccessListener(ignored -> seedProductImagesIfNeeded(firestore, completion))
                .addOnFailureListener(completion::setException);
    }

    private static void seedProductImagesIfNeeded(FirebaseFirestore firestore,
            TaskCompletionSource<Boolean> completion) {
        DocumentReference markerRef = firestore.collection("appMetadata").document(SEED_MARKER);
        markerRef.get().addOnSuccessListener(marker -> {
            Long version = marker.getLong("productImageVersion");
            if (version != null && version >= PRODUCT_IMAGE_VERSION) {
                completion.setResult(false);
                return;
            }

            SEED_EXECUTOR.execute(() -> {
                try {
                    Context context = FirebaseApp.getInstance().getApplicationContext();
                    Map<Integer, byte[]> productImages = SeedProductImages.load(context);
                    WriteBatch batch = firestore.batch();
                    for (Map.Entry<Integer, byte[]> entry : productImages.entrySet()) {
                        batch.update(firestore.collection("products")
                                        .document(String.valueOf(entry.getKey())),
                                "imageBlob", Blob.fromBytes(entry.getValue()),
                                "icon", FieldValue.delete());
                    }

                    Map<String, Object> markerUpdate = new HashMap<>();
                    markerUpdate.put("productImageVersion", PRODUCT_IMAGE_VERSION);
                    markerUpdate.put("productImagesUpdatedAt", FieldValue.serverTimestamp());
                    batch.set(markerRef, markerUpdate, SetOptions.merge());
                    batch.commit()
                            .addOnSuccessListener(ignored -> completion.setResult(true))
                            .addOnFailureListener(completion::setException);
                } catch (IOException | RuntimeException error) {
                    completion.setException(error);
                }
            });
        }).addOnFailureListener(completion::setException);
    }

    private static void addCatalogData(List<Map<String, Object>> operations,
            Map<Integer, ProductRatingStats> productRatingStats, Map<Integer, byte[]> productImages) {
        for (Product product : catalogProducts()) {
            operations.add(document(firestoreDocumentKey("products", product.getId()),
                    productData(product, productRatingStats.get(product.getId()),
                            productImages.get(product.getId())), false));
        }

        for (Map<String, Object> category : catalogCategories()) {
            operations.add(document(firestoreDocumentKey("categories", category.get("id")),
                    category, false));
        }

        for (Map<String, Object> voucher : catalogVouchers()) {
            operations.add(document(firestoreDocumentKey("vouchers", voucher.get("id")),
                    voucher, false));
        }

    }

    private static void addAccountsData(List<Map<String, Object>> operations,
            Map<String, SeedReviewStats> reviewStats) {
        Date now = new Date();
        Timestamp nowTimestamp = new Timestamp(now);
        Timestamp expiresAt = new Timestamp(daysFromNow(30));

        for (DemoAccount account : demoAccounts()) {
            Map<String, Object> user = new HashMap<>();
            user.put("id", account.id);
            user.put("uid", reviewerUid(account));
            user.put("fullName", account.name);
            user.put("username", account.name);
            user.put("email", account.email);
            user.put("phone", account.phone);
            user.put("address", account.address);
            user.put("pointsBalance", account.points);
            user.put("pointsDebt", 0L);
            user.put("attendanceDay", 0L);
            user.put("lastAttendanceDate", "");
            user.put("pointsUpdatedAt", nowTimestamp);
            user.put("createdAt", nowTimestamp);
            user.put("demoAccount", true);
            operations.add(document(firestoreDocumentKey("users", account.email), user, false));

            operations.add(document(firestoreDocumentKey(
                            "users/" + account.email + "/pointLots", account.lotId),
                    pointLot(account.pointGrant, account.points, nowTimestamp, expiresAt), false));
            operations.add(document(firestoreDocumentKey(
                            "users/" + account.email + "/pointTransactions", account.grantId),
                    pointTransaction(account.pointGrant, account.pointGrant, nowTimestamp, expiresAt), false));
        }

        Map<String, Object> spendTransaction = new HashMap<>();
        spendTransaction.put("type", "spend");
        spendTransaction.put("amount", -500L);
        spendTransaction.put("netAmount", -500L);
        spendTransaction.put("offsetAmount", 0L);
        spendTransaction.put("balanceAfter", 2000L);
        spendTransaction.put("debtAfter", 0L);
        spendTransaction.put("description", "Dùng Xu thanh toán đơn hàng.");
        spendTransaction.put("source", "checkout");
        spendTransaction.put("referenceId", "100001");
        spendTransaction.put("createdAt", nowTimestamp);
        spendTransaction.put("allocations", Arrays.asList(pointAllocation("demo-lot-1", 500L)));
        spendTransaction.put("refundedAmount", 0L);
        operations.add(document(firestoreDocumentKey(
                "users/" + DEMO_1.email + "/pointTransactions",
                "a2cb8aaa-9dcc-3f79-8aef-26f66cb56bcc"),
                spendTransaction, false));

        addSavedAndUsedVouchers(operations, nowTimestamp, expiresAt);
        addCarts(operations);
        addSeedOrders(operations, now, reviewStats);
    }

    private static void addSavedAndUsedVouchers(List<Map<String, Object>> operations,
            Timestamp now, Timestamp expiresAt) {
        SeedWalletVoucher[] walletVouchers = {
                new SeedWalletVoucher(DEMO_1, "ITEM_5K_ALL", "used", 200001, 5000),
                new SeedWalletVoucher(DEMO_1, "SHIP_FREE_ALL", "used", 200001, 30000),
                new SeedWalletVoucher(DEMO_1, "ITEM_20P_ALL", "used", 200006, 2000000),
                new SeedWalletVoucher(DEMO_1, "SHIP_70P_ALL", "used", 200011, 21000),
                new SeedWalletVoucher(DEMO_1, "ITEM_15P_ACCESSORY", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_1, "SHIP_FREE_AUDIO", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_2, "ITEM_5K_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_2, "SHIP_FREE_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_3, "ITEM_5K_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_3, "SHIP_FREE_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_4, "ITEM_5K_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_4, "SHIP_FREE_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_5, "ITEM_5K_ALL", "saved", 0, 0),
                new SeedWalletVoucher(DEMO_5, "SHIP_FREE_ALL", "saved", 0, 0)
        };
        for (SeedWalletVoucher walletVoucher : walletVouchers) {
            Map<String, Object> voucher = catalogVoucherById(walletVoucher.voucherId);
            voucher.put("state", walletVoucher.state);
            voucher.put("savedAt", now);
            voucher.put("expiresAt", expiresAt);
            voucher.put("usedAt", "used".equals(walletVoucher.state) ? now : null);
            voucher.put("orderId", walletVoucher.orderId);
            voucher.put("discountAmount", walletVoucher.discountAmount);
            operations.add(document(firestoreDocumentKey(
                            "users/" + walletVoucher.account.email + "/vouchers",
                            walletVoucher.voucherId),
                    voucher, false));
        }
    }

    private static void addCarts(List<Map<String, Object>> operations) {
        SeedCart[] carts = {
                new SeedCart(DEMO_1, 1, 1),
                new SeedCart(DEMO_1, 7, 2),
                new SeedCart(DEMO_1, 13, 1),
                new SeedCart(DEMO_2, 2, 1),
                new SeedCart(DEMO_2, 8, 2),
                new SeedCart(DEMO_2, 14, 1),
                new SeedCart(DEMO_3, 3, 1),
                new SeedCart(DEMO_3, 9, 2),
                new SeedCart(DEMO_3, 15, 1),
                new SeedCart(DEMO_4, 4, 1),
                new SeedCart(DEMO_4, 10, 2),
                new SeedCart(DEMO_4, 13, 1),
                new SeedCart(DEMO_5, 5, 1),
                new SeedCart(DEMO_5, 11, 2),
                new SeedCart(DEMO_5, 14, 1)
        };
        for (SeedCart seedCart : carts) {
            Map<String, Object> cart = new HashMap<>();
            cart.put("userEmail", seedCart.account.email);
            cart.put("productId", seedCart.productId);
            cart.put("quantity", seedCart.quantity);
            operations.add(document(firestoreDocumentKey("carts",
                    seedCart.account.email + "_" + seedCart.productId), cart, false));
        }
    }

    private static void addSeedOrders(List<Map<String, Object>> operations, Date now,
            Map<String, SeedReviewStats> reviewStats) {
        SeedOrder[] orders = seedOrders();
        for (SeedOrder seedOrder : orders) {
            Product product = productById(seedOrder.productId);
            Timestamp timestamp = new Timestamp(now);

            Map<String, Object> order = new HashMap<>();
            order.put("id", seedOrder.id);
            order.put("userEmail", seedOrder.account.email);
            order.put("receiverName", seedOrder.account.name);
            order.put("receiverPhone", seedOrder.account.phone);
            order.put("receiverAddress", seedOrder.account.address);
            order.put("note", seedOrder.note);
            order.put("paymentMethod", "Thanh toán khi nhận hàng (COD)");
            order.put("itemsSubtotal", seedOrder.itemsSubtotal);
            order.put("productDiscount", seedOrder.productDiscount);
            order.put("shippingFee", seedOrder.shippingFee);
            order.put("shippingDiscount", seedOrder.shippingDiscount);
            order.put("pointsUsed", seedOrder.pointsUsed);
            order.put("pointsDiscount", seedOrder.pointsUsed);
            order.put("pointsBalanceBefore", seedOrder.pointsBalanceBefore);
            order.put("pointsBalanceAfter", seedOrder.pointsBalanceAfter);
            order.put("pointsSpendReferenceId", seedOrder.pointsSpendReferenceId);
            order.put("productVoucherId", seedOrder.productVoucherId);
            order.put("productVoucherCode", seedOrder.productVoucherCode);
            order.put("shippingVoucherId", seedOrder.shippingVoucherId);
            order.put("shippingVoucherCode", seedOrder.shippingVoucherCode);
            order.put("totalAmount", seedOrder.totalAmount);
            order.put("orderDate", seedOrder.orderDate);
            order.put("status", seedOrder.status);
            order.put("createdAt", timestamp);
            order.put("updatedAt", timestamp);
            order.put("deliveredAt", seedOrder.delivered
                    || "Đã hoàn trả".equals(seedOrder.status) ? timestamp : null);
            order.put("returnStatus", seedOrder.returnStatus);
            order.put("returnReasonType", seedOrder.returnReasonType);
            order.put("returnReason", seedOrder.returnReason);
            operations.add(document(firestoreDocumentKey("orders", seedOrder.id), order, false));

            operations.add(document(firestoreDocumentKey(
                            "orders/" + seedOrder.id + "/items", 1),
                    orderItem(seedOrder.id, product, seedOrder.quantity,
                            seedOrder.itemsSubtotal, seedOrder.productDiscount,
                            seedOrder.itemSubtotal), false));

            operations.add(document(firestoreDocumentKey("notifications",
                            "order_" + seedOrder.id + "_placed"),
                    statusNotification(seedOrder.account.email, seedOrder.id,
                            "Đặt hàng thành công",
                            "Đơn hàng #" + orderCode(seedOrder.id) + " đã được tiếp nhận.",
                            "order_placed", "Đặt hàng thành công", now), false));
            operations.add(document(firestoreDocumentKey("notifications",
                            "order_" + seedOrder.id + "_status_"
                                    + Integer.toHexString(seedOrder.status.hashCode())),
                    statusNotification(seedOrder.account.email, seedOrder.id,
                            "Cập nhật đơn hàng",
                            "Đơn hàng #" + orderCode(seedOrder.id)
                                    + " chuyển sang trạng thái: " + seedOrder.status + ".",
                            "order_status", seedOrder.status, now), false));

            if (seedOrder.delivered) {
                operations.add(document(firestoreDocumentKey(
                                "products/" + product.getId() + "/soldOrders", seedOrder.id),
                        soldOrder(seedOrder.id, seedOrder.quantity, now), false));
                if (seedOrder.rating > 0) {
                    SeedReviewStats stats = reviewStats.get(
                            ReviewRules.documentId(seedOrder.id, product.getId()));
                    if (stats == null) {
                        throw new IllegalStateException(
                                "Missing randomized review data for order " + seedOrder.id);
                    }
                    Map<String, Object> review = orderReview(seedOrder.id, product, seedOrder.account,
                            stats, seedOrder.reviewComment, now);
                    operations.add(document(firestoreDocumentKey("reviews",
                            ReviewRules.documentId(seedOrder.id, product.getId())), review, false));
                }
            }
        }
    }

    private static Product productById(int productId) {
        for (Product product : catalogProducts()) {
            if (product.getId() == productId) {
                return product;
            }
        }
        throw new IllegalArgumentException("Unknown seeded product: " + productId);
    }

    private static SeedOrder deliveredOrder(int id, DemoAccount account, int productId,
            long unitPrice, long totalAmount, int rating, String reviewComment) {
        return new SeedOrder(id, account, productId, 1, unitPrice, 0, unitPrice,
                30000, 0, 0, totalAmount, "", "", "", "",
                account.points, account.points, "", "20/11/2026 10:00", "Đã giao",
                "Đơn hàng giao thành công.", true, "", "", "", rating, reviewComment);
    }

    private static SeedOrder[] seedOrders() {
        return new SeedOrder[]{
                new SeedOrder(200001, DEMO_1, 1, 1, 5990000, 5000, 5985000, 30000, 30000, 0, 5985000,
                        "ITEM_5K_ALL", "TECH5K", "SHIP_FREE_ALL", "SHIPFREE", 2000, 2000, "",
                        "20/11/2026 08:00", "Đã giao", "Đơn mẫu dùng voucher TECH5K và SHIPFREE.",
                        true, "", "", "", 5, "Máy chạy mượt, pin dùng ổn định."),
                deliveredOrder(200002, DEMO_2, 1, 5990000, 6020000, 5,
                        "Máy đẹp, đúng mô tả và dùng mượt."),
                deliveredOrder(200003, DEMO_3, 1, 5990000, 6020000, 5,
                        "Màn hình sáng, pin tốt, giao hàng nhanh."),
                deliveredOrder(200004, DEMO_4, 1, 5990000, 6020000, 5,
                        "Sản phẩm hoạt động ổn định, đóng gói kỹ."),
                deliveredOrder(200005, DEMO_5, 1, 5990000, 6020000, 5,
                        "Camera rõ, hiệu năng đáp ứng tốt."),
                new SeedOrder(200006, DEMO_1, 2, 1, 12990000, 2000000, 10990000, 30000, 0, 0, 11020000,
                        "ITEM_20P_ALL", "TECH20", "", "", 2000, 2000, "",
                        "20/11/2026 08:05", "Đã giao", "Đơn mẫu dùng voucher TECH20 đạt mức giảm tối đa.",
                        true, "", "", "", 5, "Màn hình đẹp, máy hoạt động mượt."),
                deliveredOrder(200007, DEMO_2, 2, 12990000, 13020000, 5,
                        "Máy chạy nhanh, camera chụp sắc nét."),
                deliveredOrder(200008, DEMO_3, 2, 12990000, 13020000, 5,
                        "Thiết kế đẹp, màn hình hiển thị rõ."),
                deliveredOrder(200009, DEMO_4, 2, 12990000, 13020000, 5,
                        "Sản phẩm mới, cấu hình đúng mô tả."),
                deliveredOrder(200010, DEMO_5, 2, 12990000, 13020000, 5,
                        "Sử dụng mượt, pin đáp ứng nhu cầu."),
                new SeedOrder(200011, DEMO_1, 3, 1, 19990000, 0, 19990000, 30000, 21000, 0, 19999000,
                        "", "", "SHIP_70P_ALL", "SHIP70", 2000, 2000, "",
                        "20/11/2026 08:10", "Đã giao", "Đơn mẫu giảm 70% phí vận chuyển.",
                        true, "", "", "", 4, "Camera Leica chụp đẹp, màn hình sắc nét."),
                deliveredOrder(200012, DEMO_2, 3, 19990000, 20020000, 4,
                        "Hiệu năng mạnh, máy hoạt động ổn định."),
                deliveredOrder(200013, DEMO_3, 3, 19990000, 20020000, 4,
                        "Màn hình đẹp, camera chụp chi tiết."),
                deliveredOrder(200014, DEMO_4, 3, 19990000, 20020000, 4,
                        "Máy đúng cấu hình, giao hàng cẩn thận."),
                deliveredOrder(200015, DEMO_5, 3, 19990000, 20020000, 4,
                        "Thiết kế đẹp, hiệu năng tốt."),
                new SeedOrder(200016, DEMO_1, 4, 1, 14990000, 0, 14990000, 30000, 0, 0, 15020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Laptop khởi động nhanh, gõ phím thoải mái."),
                deliveredOrder(200017, DEMO_2, 4, 14990000, 15020000, 5,
                        "Máy phù hợp học tập và làm việc."),
                deliveredOrder(200018, DEMO_3, 4, 14990000, 15020000, 5,
                        "Màn hình rõ, máy chạy ổn định."),
                deliveredOrder(200019, DEMO_4, 4, 14990000, 15020000, 5,
                        "Thiết kế gọn, hiệu năng tốt."),
                deliveredOrder(200020, DEMO_5, 4, 14990000, 15020000, 5,
                        "Đóng gói chắc chắn, giao nhanh."),
                new SeedOrder(200021, DEMO_1, 5, 1, 22990000, 0, 22990000, 30000, 0, 0, 23020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Máy nhẹ, màn hình đẹp và pin ổn."),
                deliveredOrder(200022, DEMO_2, 5, 22990000, 23020000, 5,
                        "MacBook hoạt động mượt, hoàn thiện tốt."),
                deliveredOrder(200023, DEMO_3, 5, 22990000, 23020000, 5,
                        "Màn hình sắc nét, thời lượng pin tốt."),
                deliveredOrder(200024, DEMO_4, 5, 22990000, 23020000, 5,
                        "Sản phẩm đúng mô tả, giao hàng nhanh."),
                deliveredOrder(200025, DEMO_5, 5, 22990000, 23020000, 5,
                        "Thiết kế đẹp, sử dụng rất mượt."),
                new SeedOrder(200026, DEMO_1, 6, 1, 31990000, 0, 31990000, 30000, 0, 0, 32020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 4, "Tản nhiệt tốt, chơi game ổn."),
                deliveredOrder(200027, DEMO_2, 6, 31990000, 32020000, 4,
                        "Màn hình 144Hz hiển thị mượt."),
                deliveredOrder(200028, DEMO_3, 6, 31990000, 32020000, 4,
                        "Cấu hình mạnh, chạy ứng dụng nhanh."),
                deliveredOrder(200029, DEMO_4, 6, 31990000, 32020000, 4,
                        "Laptop chơi game ổn định."),
                deliveredOrder(200030, DEMO_5, 6, 31990000, 32020000, 4,
                        "Máy đúng cấu hình và đóng gói tốt."),
                new SeedOrder(200031, DEMO_1, 7, 1, 1290000, 0, 1290000, 30000, 0, 0, 1320000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Tai nghe kết nối nhanh, âm thanh rõ."),
                deliveredOrder(200032, DEMO_2, 7, 1290000, 1320000, 5,
                        "Chống ồn tốt, đeo thoải mái."),
                deliveredOrder(200033, DEMO_3, 7, 1290000, 1320000, 5,
                        "Âm thanh cân bằng, hộp sạc gọn."),
                deliveredOrder(200034, DEMO_4, 7, 1290000, 1320000, 5,
                        "Tai nghe dùng ổn, kết nối ổn định."),
                deliveredOrder(200035, DEMO_5, 7, 1290000, 1320000, 5,
                        "Sản phẩm nhỏ gọn, chất lượng tốt."),
                new SeedOrder(200036, DEMO_1, 8, 1, 2490000, 0, 2490000, 30000, 0, 0, 2520000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 4, "Âm thanh rõ, hộp sạc nhỏ gọn."),
                deliveredOrder(200037, DEMO_2, 8, 2490000, 2520000, 4,
                        "Kết nối nhanh, đeo vừa tai."),
                deliveredOrder(200038, DEMO_3, 8, 2490000, 2520000, 4,
                        "Âm thanh tốt, thiết kế đẹp."),
                deliveredOrder(200039, DEMO_4, 8, 2490000, 2520000, 4,
                        "Tai nghe hoạt động ổn định."),
                deliveredOrder(200040, DEMO_5, 8, 2490000, 2520000, 4,
                        "Hộp sạc tiện dụng, âm thanh rõ."),
                new SeedOrder(200041, DEMO_1, 9, 1, 6990000, 0, 6990000, 30000, 0, 0, 7020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Chống ồn hiệu quả, âm thanh cân bằng."),
                deliveredOrder(200042, DEMO_2, 9, 6990000, 7020000, 5,
                        "Đeo êm, nghe nhạc rõ và chi tiết."),
                deliveredOrder(200043, DEMO_3, 9, 6990000, 7020000, 5,
                        "Chống ồn tốt, kết nối ổn định."),
                deliveredOrder(200044, DEMO_4, 9, 6990000, 7020000, 5,
                        "Thiết kế đẹp, chất âm tốt."),
                deliveredOrder(200045, DEMO_5, 9, 6990000, 7020000, 5,
                        "Sản phẩm chính hãng, đóng gói kỹ."),
                new SeedOrder(200046, DEMO_1, 10, 1, 1990000, 0, 1990000, 30000, 0, 0, 2020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 4, "Vòng đeo nhẹ, theo dõi hoạt động tiện lợi."),
                deliveredOrder(200047, DEMO_2, 10, 1990000, 2020000, 4,
                        "Màn hình sáng, thao tác dễ dàng."),
                deliveredOrder(200048, DEMO_3, 10, 1990000, 2020000, 4,
                        "Theo dõi sức khỏe ổn định."),
                deliveredOrder(200049, DEMO_4, 10, 1990000, 2020000, 4,
                        "Pin tốt, đeo thoải mái."),
                deliveredOrder(200050, DEMO_5, 10, 1990000, 2020000, 4,
                        "Sản phẩm nhẹ, giao hàng nhanh."),
                new SeedOrder(200051, DEMO_1, 11, 1, 4590000, 0, 4590000, 30000, 0, 0, 4620000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Đồng hồ đẹp, GPS bắt nhanh."),
                deliveredOrder(200052, DEMO_2, 11, 4590000, 4620000, 5,
                        "Màn hình sáng, kết nối ổn định."),
                deliveredOrder(200053, DEMO_3, 11, 4590000, 4620000, 5,
                        "Thiết kế đẹp, theo dõi hoạt động tốt."),
                deliveredOrder(200054, DEMO_4, 11, 4590000, 4620000, 5,
                        "Đồng hồ hoạt động mượt."),
                deliveredOrder(200055, DEMO_5, 11, 4590000, 4620000, 5,
                        "Sản phẩm đúng mô tả, đóng gói kỹ."),
                new SeedOrder(200056, DEMO_1, 12, 1, 8990000, 0, 8990000, 30000, 0, 0, 9020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Đồng hồ chạy bộ chuyên dụng, pin lâu."),
                deliveredOrder(200057, DEMO_2, 12, 8990000, 9020000, 5,
                        "GPS chính xác, màn hình hiển thị đẹp."),
                deliveredOrder(200058, DEMO_3, 12, 8990000, 9020000, 5,
                        "Theo dõi chạy bộ tốt, dễ sử dụng."),
                deliveredOrder(200059, DEMO_4, 12, 8990000, 9020000, 5,
                        "Đồng hồ nhẹ, pin sử dụng lâu."),
                deliveredOrder(200060, DEMO_5, 12, 8990000, 9020000, 5,
                        "Sản phẩm chất lượng, giao nhanh."),
                new SeedOrder(200061, DEMO_1, 13, 1, 790000, 0, 790000, 30000, 0, 0, 820000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 4, "Củ sạc nhỏ gọn, sạc nhanh."),
                deliveredOrder(200062, DEMO_2, 13, 790000, 820000, 4,
                        "Sạc ổn định, kích thước tiện mang theo."),
                deliveredOrder(200063, DEMO_3, 13, 790000, 820000, 4,
                        "Công suất đúng mô tả, hoàn thiện tốt."),
                deliveredOrder(200064, DEMO_4, 13, 790000, 820000, 4,
                        "Củ sạc gọn, sử dụng thuận tiện."),
                deliveredOrder(200065, DEMO_5, 13, 790000, 820000, 4,
                        "Sạc nhanh, đóng gói cẩn thận."),
                new SeedOrder(200066, DEMO_1, 14, 1, 1190000, 0, 1190000, 30000, 0, 0, 1220000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 5, "Dung lượng lớn, tiện mang đi."),
                deliveredOrder(200067, DEMO_2, 14, 1190000, 1220000, 5,
                        "Pin dự phòng sạc ổn định."),
                deliveredOrder(200068, DEMO_3, 14, 1190000, 1220000, 5,
                        "Dung lượng đúng thông tin sản phẩm."),
                deliveredOrder(200069, DEMO_4, 14, 1190000, 1220000, 5,
                        "Thiết kế chắc chắn, dễ mang theo."),
                deliveredOrder(200070, DEMO_5, 14, 1190000, 1220000, 5,
                        "Sạc nhanh, sản phẩm đúng mô tả."),
                new SeedOrder(200071, DEMO_1, 15, 1, 1490000, 0, 1490000, 30000, 0, 0, 1520000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:00", "Đã giao", "Đơn hàng giao thành công.",
                        true, "", "", "", 4, "Hub đủ cổng, xuất HDMI ổn định."),
                deliveredOrder(200072, DEMO_2, 15, 1490000, 1520000, 4,
                        "Kết nối ổn định, cổng đa dạng."),
                deliveredOrder(200073, DEMO_3, 15, 1490000, 1520000, 4,
                        "Xuất hình rõ, thiết bị hoàn thiện tốt."),
                deliveredOrder(200074, DEMO_4, 15, 1490000, 1520000, 4,
                        "Hub hoạt động ổn định, gọn nhẹ."),
                deliveredOrder(200075, DEMO_5, 15, 1490000, 1520000, 4,
                        "Đủ cổng kết nối, đóng gói cẩn thận."),
                new SeedOrder(200076, DEMO_1, 1, 1, 5990000, 0, 5990000, 30000, 0, 0, 6020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 10:10", "Đã giao", "Đơn giao thành công, chưa gửi đánh giá.",
                        true, "", "", "", 0, ""),
                new SeedOrder(100001, DEMO_1, 1, 1, 5990000, 0, 5990000, 30000, 0, 500, 6019500,
                        "", "", "", "", 2500, 2000, "100001",
                        "20/11/2026 09:15", "Đặt hàng thành công", "Đơn mẫu đã đặt, chờ cửa hàng xác nhận.",
                        false, "", "", "", 0, ""),
                new SeedOrder(100002, DEMO_1, 2, 1, 12990000, 0, 12990000, 30000, 0, 0, 13020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 09:20", "Chờ xác nhận", "Đơn mẫu đang chờ cửa hàng xác nhận.",
                        false, "", "", "", 0, ""),
                new SeedOrder(100003, DEMO_1, 3, 1, 19990000, 0, 19990000, 30000, 0, 0, 20020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 09:25", "Đang giao", "Đơn mẫu đang trên đường giao đến bạn.",
                        false, "", "", "", 0, ""),
                new SeedOrder(100004, DEMO_1, 4, 1, 14990000, 0, 14990000, 30000, 0, 0, 15020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 09:30", "Đã hoàn trả", "Đơn mẫu đã được chấp nhận hoàn trả.",
                        false, "accepted", "wrong_model", "Không đúng mẫu", 0, ""),
                new SeedOrder(100005, DEMO_1, 5, 1, 22990000, 0, 22990000, 30000, 0, 0, 23020000,
                        "", "", "", "", 2000, 2000, "",
                        "20/11/2026 09:35", "Đã hủy", "Đơn mẫu đã được hủy trước khi giao.",
                        false, "", "", "", 0, "")
        };
    }

    private static void commitOperations(FirebaseFirestore firestore,
            List<Map<String, Object>> operations, int start,
            TaskCompletionSource<Boolean> completion) {
        if (start >= operations.size()) {
            firestore.collection("appMetadata").document(SEED_MARKER).set(seedMarker())
                    .addOnSuccessListener(ignored -> completion.setResult(true))
                    .addOnFailureListener(completion::setException);
            return;
        }

        WriteBatch batch = firestore.batch();
        int end = Math.min(start + 450, operations.size());
        for (int index = start; index < end; index++) {
            writeOperation(firestore, batch, operations.get(index));
        }
        batch.commit()
                .addOnSuccessListener(ignored ->
                        commitOperations(firestore, operations, end, completion))
                .addOnFailureListener(completion::setException);
    }

    private static void writeOperation(FirebaseFirestore firestore, WriteBatch batch,
            Map<String, Object> operation) {
        String collection = (String) operation.get("_collection");
        Object rawId = operation.get("_id");
        Object data = operation.get("_data");
        boolean merge = Boolean.TRUE.equals(operation.get("_merge"));
        DocumentReference reference = firestore.collection(collection)
                .document(String.valueOf(rawId));
        if (merge) {
            batch.set(reference, (Map<String, Object>) data,
                    com.google.firebase.firestore.SetOptions.merge());
        } else {
            batch.set(reference, (Map<String, Object>) data);
        }
    }

    private static Map<String, Object> document(String collectionAndId,
            Map<String, Object> data, boolean merge) {
        Map<String, Object> operation = new HashMap<>();
        int separator = collectionAndId.indexOf('|');
        operation.put("_collection", collectionAndId.substring(0, separator));
        operation.put("_id", collectionAndId.substring(separator + 1));
        operation.put("_data", data);
        operation.put("_merge", merge);
        return operation;
    }

    private static String firestoreDocumentKey(String collection, Object id) {
        return collection + "|" + String.valueOf(id);
    }

    private static Map<String, Object> pointLot(long amount, long remainingAmount,
            Timestamp receivedAt, Timestamp expiresAt) {
        Map<String, Object> lot = new HashMap<>();
        lot.put("amount", amount);
        lot.put("remainingAmount", remainingAmount);
        lot.put("receivedAt", receivedAt);
        lot.put("expiresAt", expiresAt);
        lot.put("source", "demo_seed");
        lot.put("referenceId", "demo_seed");
        return lot;
    }

    private static Map<String, Object> pointTransaction(long amount, long balanceAfter,
            Timestamp createdAt, Timestamp expiresAt) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("type", "earn");
        transaction.put("amount", amount);
        transaction.put("netAmount", amount);
        transaction.put("offsetAmount", 0L);
        transaction.put("balanceAfter", balanceAfter);
       transaction.put("debtAfter", 0L);
        transaction.put("description", "Xu demo khởi tạo.");
        transaction.put("source", "demo_seed");
        transaction.put("referenceId", "demo_seed");
        transaction.put("createdAt", createdAt);
        transaction.put("expiresAt", expiresAt);
        return transaction;
    }

    private static Map<String, Object> pointAllocation(String lotId, long amount) {
        Map<String, Object> allocation = new HashMap<>();
        allocation.put("lotId", lotId);
        allocation.put("amount", amount);
        return allocation;
    }

    private static final DemoAccount DEMO_1 = new DemoAccount(1, "initial-customer-1",
            "demo01@example.com", "Nguyen Van A", "0912345001",
            "12 Nguyen Hue, Quan 1, TP. Ho Chi Minh", 2000, 2500,
            "demo-lot-1", "demo-grant-1");
    private static final DemoAccount DEMO_2 = new DemoAccount(2, "initial-customer-2",
            "demo02@example.com", "Tran Thi B", "0912345002",
            "45 Le Loi, Quan 1, TP. Ho Chi Minh", 3000, 3000,
            "demo-lot-2", "demo-grant-2");
    private static final DemoAccount DEMO_3 = new DemoAccount(3, "initial-customer-3",
            "demo03@example.com", "Le Minh C", "0912345003",
            "78 Dien Bien Phu, Quan Binh Thanh, TP. Ho Chi Minh", 3500, 3500,
            "demo-lot-3", "demo-grant-3");
    private static final DemoAccount DEMO_4 = new DemoAccount(4, "initial-customer-4",
            "demo04@example.com", "Pham Hoang D", "0912345004",
            "23 Cach Mang Thang Tam, Quan 3, TP. Ho Chi Minh", 4000, 4000,
            "demo-lot-4", "demo-grant-4");
    private static final DemoAccount DEMO_5 = new DemoAccount(5, "initial-customer-5",
            "demo05@example.com", "Vu Thanh H", "0912345005",
            "56 Phan Xich Long, Quan Phu Nhuan, TP. Ho Chi Minh", 4500, 4500,
            "demo-lot-5", "demo-grant-5");

    private static List<DemoAccount> demoAccounts() {
        return Arrays.asList(DEMO_1, DEMO_2, DEMO_3, DEMO_4, DEMO_5);
    }

    private static String reviewerUid(DemoAccount account) {
        FirebaseUser authenticatedUser = FirebaseAuth.getInstance().getCurrentUser();
        String authenticatedEmail = authenticatedUser == null ? null : authenticatedUser.getEmail();
        if (authenticatedEmail != null && account.email.equalsIgnoreCase(authenticatedEmail)) {
            return authenticatedUser.getUid();
        }
        return account.uid;
    }

    private static List<Product> catalogProducts() {
        List<Product> products = new ArrayList<>();
        products.add(product(1, "Samsung Galaxy A16 5G 128GB", "Điện thoại", 5990000,
                "Điện thoại 5G màn hình Super AMOLED 6,7 inch 90Hz, camera chính 50MP và pin 5.000mAh.",
                38, 6, 5.0, 5,
                specs("Màn hình", "Super AMOLED 6,7 inch 90Hz", "RAM", "8GB",
                        "Bộ nhớ", "128GB", "Camera chính", "50MP", "Pin", "5.000mAh")));
        products.add(product(2, "Samsung Galaxy A55 5G 256GB", "Điện thoại", 12990000,
                "Điện thoại 5G với màn hình Super AMOLED 120Hz, camera chính 50MP chống rung quang học.",
                21, 5, 5.0, 5,
                specs("Màn hình", "Super AMOLED 6,6 inch 120Hz", "Chip", "Exynos 1480",
                        "RAM", "8GB", "Bộ nhớ", "256GB", "Camera chính", "50MP OIS")));
        products.add(product(3, "Xiaomi 14T 512GB", "Điện thoại", 19990000,
                "Điện thoại Xiaomi 14T camera Leica, màn hình AMOLED 144Hz và chip Dimensity 8300-Ultra.",
                12, 5, 4.0, 5,
                specs("Màn hình", "AMOLED 6,67 inch 144Hz", "Chip", "Dimensity 8300-Ultra",
                        "RAM", "12GB", "Bộ nhớ", "512GB", "Camera chính", "Leica 50MP OIS")));
        products.add(product(4, "ASUS Vivobook 15 X1504VA Core i5 16GB 512GB", "Laptop", 14990000,
                "Laptop 15,6 inch Full HD dùng Intel Core i5-1335U, RAM 16GB và SSD 512GB.",
                17, 5, 5.0, 5,
                specs("Màn hình", "15,6 inch Full HD", "CPU", "Intel Core i5-1335U",
                        "RAM", "16GB", "Ổ cứng", "512GB SSD")));
        products.add(product(5, "Apple MacBook Air 13-inch M3 16GB 256GB", "Laptop", 22990000,
                "MacBook Air 13,6 inch Liquid Retina dùng chip Apple M3.",
                10, 5, 5.0, 5,
                specs("Màn hình", "Liquid Retina 13,6 inch", "Chip", "Apple M3",
                        "Bộ nhớ thống nhất", "16GB", "Ổ cứng", "256GB SSD")));
        products.add(product(6, "Lenovo LOQ 15IRX9 Core i5 RTX 4050", "Laptop", 31990000,
                "Laptop gaming màn hình 144Hz, Intel Core i5-13450HX và RTX 4050.",
                7, 5, 4.0, 5,
                specs("Màn hình", "15,6 inch Full HD 144Hz", "CPU", "Intel Core i5-13450HX",
                        "RAM", "16GB", "Đồ họa", "GeForce RTX 4050 6GB", "Ổ cứng", "512GB SSD")));
        products.add(product(7, "Samsung Galaxy Buds FE", "Tai nghe", 1290000,
                "Tai nghe true wireless có chống ồn chủ động.",
                52, 5, 5.0, 5,
                specs("Kết nối", "Bluetooth", "Chống ồn", "ANC",
                        "Thời lượng nghe", "Tối đa 6 giờ", "Chống nước", "IPX2")));
        products.add(product(8, "Samsung Galaxy Buds3", "Tai nghe", 2490000,
                "Tai nghe true wireless hỗ trợ chống ồn chủ động.",
                29, 5, 4.0, 5,
                specs("Kết nối", "Bluetooth 5.4", "Chống ồn", "ANC",
                        "Thời lượng nghe", "Tối đa 5 giờ", "Chống nước", "IP57")));
        products.add(product(9, "Sony WH-1000XM5", "Tai nghe", 6990000,
                "Tai nghe chụp tai chống ồn chủ động và kết nối đa điểm.",
                14, 5, 5.0, 5,
                specs("Kiểu tai nghe", "Chụp tai không dây", "Chống ồn", "ANC",
                        "Thời lượng pin", "Tối đa 30 giờ", "Kết nối", "Bluetooth")));
        products.add(product(10, "Xiaomi Smart Band 9 Pro", "Đồng hồ thông minh", 1990000,
                "Vòng đeo thông minh màn hình AMOLED 1,74 inch.",
                34, 5, 4.0, 5,
                specs("Màn hình", "AMOLED 1,74 inch", "GPS", "Tích hợp",
                        "Chống nước", "5 ATM", "Thời lượng pin", "Tối đa 21 ngày")));
        products.add(product(11, "Samsung Galaxy Watch7 44mm Bluetooth", "Đồng hồ thông minh", 4590000,
                "Đồng hồ thông minh màn hình Super AMOLED.",
                19, 5, 5.0, 5,
                specs("Màn hình", "Super AMOLED 1,5 inch", "Chip", "Exynos W1000",
                        "Kết nối", "Bluetooth", "Chống nước", "5 ATM")));
        products.add(product(12, "Garmin Forerunner 265", "Đồng hồ thông minh", 8990000,
                "Đồng hồ chạy bộ GPS với màn hình AMOLED.",
                8, 5, 5.0, 5,
                specs("Màn hình", "AMOLED 1,3 inch", "GPS", "Đa băng tần",
                        "Chống nước", "5 ATM", "Thời lượng pin", "Tối đa 13 ngày")));
        products.add(product(13, "Anker 735 Charger GaNPrime 65W", "Phụ kiện & Sạc", 790000,
                "Củ sạc GaN 65W nhỏ gọn.",
                64, 5, 4.0, 5,
                specs("Công suất", "Tối đa 65W", "Cổng", "2 USB-C, 1 USB-A",
                        "Công nghệ", "GaNPrime")));
        products.add(product(14, "Anker 325 Power Bank PowerCore 20K", "Phụ kiện & Sạc", 1190000,
                "Pin dự phòng dung lượng 20.000mAh.",
                47, 5, 5.0, 5,
                specs("Dung lượng", "20.000mAh", "Cổng ra", "2 USB-A",
                        "Cổng vào", "USB-C")));
        products.add(product(15, "UGREEN Revodok 8-in-1 USB-C Hub", "Phụ kiện & Sạc", 1490000,
                "Bộ chuyển đổi USB-C đa cổng với HDMI 4K.",
                25, 5, 4.0, 5,
                specs("Cổng", "HDMI, USB-A, USB-C, SD/microSD",
                        "HDMI", "Tối đa 4K", "Sạc PD", "Tối đa 100W")));
        return products;
    }

    private static Product product(int id, String name, String category, long price,
            String description, int stock, int soldCount, double averageRating,
            int reviewCount, Map<String, String> specifications) {
        return new Product(id, name, category, price, description, stock,
                soldCount, averageRating, reviewCount, specifications);
    }

    private static Map<String, String> specs(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }

    private static List<Map<String, Object>> catalogCategories() {
        return Arrays.asList(
                category("phones", "Điện thoại", 1),
                category("laptops", "Laptop", 2),
                category("headphones", "Tai nghe", 3),
                category("smartwatches", "Đồng hồ thông minh", 4),
                category("accessories-charging", "Phụ kiện & Sạc", 5));
    }

    private static Map<String, Object> category(String id, String name, int sortOrder) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("sortOrder", sortOrder);
        data.put("isActive", true);
        return data;
    }

    private static List<Map<String, Object>> catalogVouchers() {
        List<Map<String, Object>> vouchers = new ArrayList<>();
        Date campaignEnd = daysFromNow(180);
        vouchers.add(voucher("ITEM_5K_ALL", "TECH5K", "Giảm 5.000đ mỗi sản phẩm",
                "Giảm 5.000đ cho từng sản phẩm đủ điều kiện trong toàn shop.",
                "product", "fixed_per_item", 5000, "all", "", 0, 0, 30, campaignEnd, 1));
        vouchers.add(voucher("ITEM_20P_ALL", "TECH20", "Giảm 20% toàn shop",
                "Giảm 20% trên từng sản phẩm đủ điều kiện, tối đa 2.000.000đ.",
                "product", "percentage_per_item", 20, "all", "", 0, 2000000, 30, campaignEnd, 2));
        vouchers.add(voucher("ITEM_10P_HEADPHONE", "AUDIO10", "Giảm 10% tai nghe",
                "Áp dụng cho các sản phẩm thuộc danh mục Tai nghe.",
                "product", "percentage_per_item", 10, "category", "Tai nghe", 0, 1000000, 30, campaignEnd, 3));
        vouchers.add(voucher("ITEM_15P_ACCESSORY", "GEAR15", "Giảm 15% phụ kiện & sạc",
                "Áp dụng cho danh mục Phụ kiện & Sạc.",
                "product", "percentage_per_item", 15, "category", "Phụ kiện & Sạc", 0, 500000, 30, campaignEnd, 4));
        vouchers.add(voucher("ITEM_LAPTOP_500K", "LAPTOP500", "Giảm 500.000đ laptop",
                "Giảm 500.000đ trên từng laptop khi đơn laptop đạt từ 15.000.000đ.",
                "product", "fixed_per_item", 500000, "category", "Laptop", 15000000, 1000000, 30, campaignEnd, 5));
        vouchers.add(voucher("SHIP_FREE_ALL", "SHIPFREE", "Miễn phí vận chuyển",
                "Miễn phí vận chuyển tối đa 50.000đ.",
                "shipping", "free_shipping", 100, "all", "", 0, 50000, 30, campaignEnd, 6));
        vouchers.add(voucher("SHIP_70P_ALL", "SHIP70", "Giảm 70% phí vận chuyển",
                "Giảm 70% phí vận chuyển, tối đa 30.000đ.",
                "shipping", "shipping_percentage", 70, "all", "", 0, 30000, 30, campaignEnd, 7));
        vouchers.add(voucher("SHIP_FREE_AUDIO", "AUDIO-SHIP", "Miễn phí ship tai nghe",
                "Miễn phí vận chuyển đơn có sản phẩm Tai nghe.",
                "shipping", "free_shipping", 100, "category", "Tai nghe", 0, 40000, 30, campaignEnd, 8));
        vouchers.add(voucher("SHIP_20K_500", "SHIP20K", "Giảm 20.000đ phí ship",
                "Giảm 20.000đ phí vận chuyển cho đơn từ 500.000đ.",
                "shipping", "shipping_fixed", 20000, "all", "", 500000, 20000, 30, campaignEnd, 9));
        vouchers.add(voucher("SHIP_FREE_3M", "SHIP3M", "Miễn phí ship đơn lớn",
                "Miễn phí vận chuyển cho đơn từ 3.000.000đ.",
                "shipping", "free_shipping", 100, "all", "", 3000000, 50000, 30, campaignEnd, 10));
        return vouchers;
    }

    private static Map<String, Object> voucher(String id, String code, String name,
            String description, String type, String discountType, long value,
            String appliesTo, String category, long minimumOrderAmount,
            long maximumDiscountAmount, int validityDays, Date campaignEnd, int sortOrder) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("code", code);
        data.put("name", name);
        data.put("description", description);
        data.put("type", type);
        data.put("discountType", discountType);
        data.put("value", value);
        data.put("appliesTo", appliesTo);
        data.put("category", category);
        data.put("minimumOrderAmount", minimumOrderAmount);
        data.put("maximumDiscountAmount", maximumDiscountAmount);
        data.put("validityAfterSaveDays", validityDays);
        data.put("campaignEndsAt", new Timestamp(campaignEnd));
        data.put("isActive", true);
        data.put("sortOrder", sortOrder);
        return data;
    }

    private static Map<String, Object> catalogVoucherById(String voucherId) {
        for (Map<String, Object> voucher : catalogVouchers()) {
            if (voucherId.equals(voucher.get("id"))) {
                return new HashMap<>(voucher);
            }
        }
        throw new IllegalArgumentException("Unknown seeded voucher: " + voucherId);
    }

    private static Map<String, Object> productData(Product product, ProductRatingStats ratingStats,
            byte[] imageBytes) {
        if (ratingStats == null || ratingStats.reviewCount != product.getReviewCount()) {
            throw new IllegalStateException(
                    "Seed review totals do not match product " + product.getId());
        }
        if (imageBytes == null || imageBytes.length == 0
                || imageBytes.length > ImageDataUtils.MAX_IMAGE_BYTES) {
            throw new IllegalStateException("Missing or oversized seed image for product " + product.getId());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", product.getId());
        data.put("name", product.getName());
        data.put("category", product.getCategory());
        data.put("price", product.getPrice());
        data.put("description", product.getDescription());
        data.put("imageBlob", Blob.fromBytes(imageBytes));
        data.put("stock", product.getStock());
        data.put("soldCount", product.getSoldCount());
        data.put("averageRating", (double) ratingStats.ratingSum / ratingStats.reviewCount);
        data.put("ratingSum", ratingStats.ratingSum);
        data.put("reviewCount", ratingStats.reviewCount);
        data.put("specifications", product.getSpecifications());
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private static Map<String, Object> orderItem(int orderId, Product product,
            int quantity, long subtotal, long discountAmount, long discountedSubtotal) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", 1);
        item.put("orderId", orderId);
        item.put("productId", product.getId());
        item.put("productName", product.getName());
        item.put("price", product.getPrice());
        item.put("quantity", quantity);
        item.put("originalSubtotal", subtotal);
        item.put("discountAmount", discountAmount);
        item.put("subtotal", discountedSubtotal);
        return item;
    }

    private static Map<String, Object> soldOrder(int orderId, int quantity, Date date) {
        Map<String, Object> soldOrder = new HashMap<>();
        soldOrder.put("orderId", orderId);
        soldOrder.put("quantity", quantity);
        soldOrder.put("createdAt", new Timestamp(date));
        return soldOrder;
    }

    private static Map<String, Object> statusNotification(String email, int orderId,
            String title, String message, String type, String status, Date now) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userEmail", email);
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("orderId", orderId);
        notification.put("status", status);
        notification.put("createdAt", new Timestamp(now));
        notification.put("isRead", false);
        return notification;
    }

    private static Map<String, Object> orderReview(int orderId, Product product,
            DemoAccount account, SeedReviewStats stats, String comment, Date date) {
        Map<String, Object> review = new HashMap<>();
        review.put("orderId", orderId);
        review.put("orderItemId", 1);
        review.put("productId", product.getId());
        review.put("productName", product.getName());
        review.put("reviewerId", reviewerUid(account));
        review.put("reviewerEmail", account.email);
        review.put("reviewerName", account.name);
        review.put("anonymous", false);
        review.put("rating", stats.rating);
        review.put("comment", comment);
        review.put("likesCount", stats.likesCount);
        review.put("viewsCount", stats.viewsCount);
        review.put("createdAt", new Timestamp(date));
        review.put("updatedAt", new Timestamp(date));
        return review;
    }

    private static Map<String, Object> seedMarker() {
        Map<String, Object> marker = new HashMap<>();
        marker.put("version", 1);
        marker.put("reviewStatsVersion", REVIEW_STATS_VERSION);
        marker.put("productImageVersion", PRODUCT_IMAGE_VERSION);
        marker.put("seededAt", FieldValue.serverTimestamp());
        List<String> emails = new ArrayList<>();
        for (DemoAccount account : demoAccounts()) {
            emails.add(account.email);
        }
        marker.put("accountEmails", emails);
        return marker;
    }

    private static String orderCode(int orderId) {
        return "DH" + String.format(Locale.getDefault(), "%05d", orderId);
    }

    private static Date daysFromNow(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }

    private static final class ProductRatingStats {
        private int reviewCount;
        private int ratingSum;

        private void add(int rating) {
            reviewCount++;
            ratingSum += rating;
        }
    }

    private static final class SeedReviewRecord {
        private final String id;
        private final int productId;
        private final int originalRating;
        private final SeedReviewStats stats;

        private SeedReviewRecord(String id, int productId, int originalRating, SeedReviewStats stats) {
            this.id = id;
            this.productId = productId;
            this.originalRating = originalRating;
            this.stats = stats;
        }
    }

    private static final class DemoAccount {
        private final int id;
        private final String uid;
        private final String email;
        private final String name;
        private final String phone;
        private final String address;
        private final long points;
        private final long pointGrant;
        private final String lotId;
        private final String grantId;

        private DemoAccount(int id, String uid, String email, String name, String phone,
                String address, long points, long pointGrant, String lotId, String grantId) {
            this.id = id;
            this.uid = uid;
            this.email = email;
            this.name = name;
            this.phone = phone;
            this.address = address;
            this.points = points;
            this.pointGrant = pointGrant;
            this.lotId = lotId;
            this.grantId = grantId;
        }
    }

    private static final class SeedWalletVoucher {
        private final DemoAccount account;
        private final String voucherId;
        private final String state;
        private final int orderId;
        private final long discountAmount;

        private SeedWalletVoucher(DemoAccount account, String voucherId, String state,
                int orderId, long discountAmount) {
            this.account = account;
            this.voucherId = voucherId;
            this.state = state;
            this.orderId = orderId;
            this.discountAmount = discountAmount;
        }
    }

    private static final class SeedCart {
        private final DemoAccount account;
        private final int productId;
        private final int quantity;

        private SeedCart(DemoAccount account, int productId, int quantity) {
            this.account = account;
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    private static final class SeedOrder {
        private final int id;
        private final DemoAccount account;
        private final int productId;
        private final int quantity;
        private final long itemsSubtotal;
        private final long productDiscount;
        private final long itemSubtotal;
        private final long shippingFee;
        private final long shippingDiscount;
        private final long pointsUsed;
        private final long totalAmount;
        private final String productVoucherId;
        private final String productVoucherCode;
        private final String shippingVoucherId;
        private final String shippingVoucherCode;
        private final long pointsBalanceBefore;
        private final long pointsBalanceAfter;
        private final String pointsSpendReferenceId;
        private final String orderDate;
        private final String status;
        private final String note;
        private final boolean delivered;
        private final String returnStatus;
        private final String returnReasonType;
        private final String returnReason;
        private final int rating;
        private final String reviewComment;

        private SeedOrder(int id, DemoAccount account, int productId, int quantity,
                long itemsSubtotal, long productDiscount, long itemSubtotal, long shippingFee,
                long shippingDiscount, long pointsUsed, long totalAmount, String productVoucherId,
                String productVoucherCode, String shippingVoucherId, String shippingVoucherCode,
                long pointsBalanceBefore, long pointsBalanceAfter, String pointsSpendReferenceId,
                String orderDate, String status,
                String note, boolean delivered, String returnStatus, String returnReasonType,
                String returnReason, int rating, String reviewComment) {
            this.id = id;
            this.account = account;
            this.productId = productId;
            this.quantity = quantity;
            this.itemsSubtotal = itemsSubtotal;
            this.productDiscount = productDiscount;
            this.itemSubtotal = itemSubtotal;
            this.shippingFee = shippingFee;
            this.shippingDiscount = shippingDiscount;
            this.pointsUsed = pointsUsed;
            this.totalAmount = totalAmount;
            this.productVoucherId = productVoucherId;
            this.productVoucherCode = productVoucherCode;
            this.shippingVoucherId = shippingVoucherId;
            this.shippingVoucherCode = shippingVoucherCode;
            this.pointsBalanceBefore = pointsBalanceBefore;
            this.pointsBalanceAfter = pointsBalanceAfter;
            this.pointsSpendReferenceId = pointsSpendReferenceId;
            this.orderDate = orderDate;
            this.status = status;
            this.note = note;
            this.delivered = delivered;
            this.returnStatus = returnStatus;
            this.returnReasonType = returnReasonType;
            this.returnReason = returnReason;
            this.rating = rating;
            this.reviewComment = reviewComment;
        }
    }
}
