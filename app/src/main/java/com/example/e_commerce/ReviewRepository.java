package com.example.e_commerce;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReviewRepository {

    public static final long REVIEW_REWARD = 500L;

    private static ReviewRepository instance;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface ReviewsCallback {
        void onResult(List<Review> reviews);

        void onError(Exception error);
    }

    public interface ReviewCallback {
        void onResult(Review review);

        void onError(Exception error);
    }

    public interface PendingCallback {
        void onResult(List<ReviewableItem> items);

        void onError(Exception error);
    }

    public interface SaveCallback {
        void onResult(SaveResult result);

        void onError(Exception error);
    }

    public interface InteractionCallback {
        void onResult(boolean changed);

        void onError(Exception error);
    }

    private ReviewRepository() {
    }

    public static synchronized ReviewRepository getInstance() {
        if (instance == null) {
            instance = new ReviewRepository();
        }
        return instance;
    }

    public void getProductReviews(int productId, ReviewsCallback callback) {
        firestore.collection("reviews").whereEqualTo("productId", productId).limit(100).get()
                .addOnSuccessListener(snapshot -> callback.onResult(toSortedReviews(snapshot)))
                .addOnFailureListener(callback::onError);
    }

    public void getReviewById(String reviewId, ReviewCallback callback) {
        firestore.collection("reviews").document(reviewId).get()
                .addOnSuccessListener(snapshot -> callback.onResult(
                        snapshot.exists() ? Review.fromDocument(snapshot) : null))
                .addOnFailureListener(callback::onError);
    }

    public void getMyReviews(String email, ReviewsCallback callback) {
        String normalizedEmail;
        String reviewerId;
        try {
            normalizedEmail = normalizeEmail(email);
            reviewerId = authenticatedUserId(normalizedEmail);
        } catch (IllegalArgumentException | IllegalStateException error) {
            callback.onError(error);
            return;
        }
        firestore.collection("reviews").whereEqualTo("reviewerId", reviewerId).get()
                .addOnSuccessListener(idSnapshot ->
                        firestore.collection("reviews").whereEqualTo("reviewerEmail", normalizedEmail).get()
                                .addOnSuccessListener(emailSnapshot -> {
                                    Map<String, DocumentSnapshot> documents = new HashMap<>();
                                    for (DocumentSnapshot document : idSnapshot.getDocuments()) {
                                        documents.put(document.getId(), document);
                                    }
                                    for (DocumentSnapshot document : emailSnapshot.getDocuments()) {
                                        documents.put(document.getId(), document);
                                    }
                                    callback.onResult(toSortedReviews(
                                            new ArrayList<>(documents.values())));
                                })
                                .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    public void getPendingItems(String email, PendingCallback callback) {
        String normalizedEmail;
        try {
            normalizedEmail = normalizeEmail(email);
        } catch (IllegalArgumentException error) {
            callback.onError(error);
            return;
        }
        getMyReviews(normalizedEmail, new ReviewsCallback() {
            @Override
            public void onResult(List<Review> reviews) {
                Set<String> reviewedItems = new HashSet<>();
                for (Review review : reviews) {
                    reviewedItems.add(ReviewRules.documentId(review.getOrderId(), review.getProductId()));
                }
                loadDeliveredOrderItems(normalizedEmail, reviewedItems, callback);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    public void saveReview(String email, String reviewerName, int orderId, int orderItemId, int productId,
            String productName, String productIcon, int rating, String comment, boolean anonymous,
            SaveCallback callback) {
        String normalizedEmail;
        String reviewerId;
        try {
            normalizedEmail = normalizeEmail(email);
            reviewerId = authenticatedUserId(normalizedEmail);
        } catch (IllegalArgumentException | IllegalStateException error) {
            callback.onError(error);
            return;
        }
        String normalizedComment = comment == null ? "" : comment.trim();
        if (orderId <= 0 || orderItemId <= 0 || productId <= 0 || rating < 1 || rating > 5
                || normalizedComment.isEmpty() || normalizedComment.length() > 2000) {
            callback.onError(new IllegalArgumentException("Nội dung đánh giá chưa hợp lệ."));
            return;
        }

        String reviewId = ReviewRules.documentId(orderId, productId);
        DocumentReference orderRef = firestore.collection("orders").document(String.valueOf(orderId));
        DocumentReference itemRef = orderRef.collection("items").document(String.valueOf(orderItemId));
        DocumentReference productRef = firestore.collection("products").document(String.valueOf(productId));
        DocumentReference reviewRef = firestore.collection("reviews").document(reviewId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot order = transaction.get(orderRef);
            DocumentSnapshot item = transaction.get(itemRef);
            DocumentSnapshot existingReview = transaction.get(reviewRef);
            DocumentSnapshot product = transaction.get(productRef);
            if (!order.exists() || !normalizedEmail.equals(order.getString("userEmail"))
                    || !ReviewRules.isDelivered(order.getString("status"))
                    || !item.exists() || value(item, "productId") != productId
                    || !product.exists()) {
                throw new IllegalStateException("Chỉ có thể đánh giá sản phẩm trong đơn đã giao.");
            }
            if (existingReview.exists()
                    && !reviewerId.equals(existingReview.getString("reviewerId"))) {
                throw new IllegalStateException("Bài đánh giá đã tồn tại và không thuộc tài khoản này.");
            }

            boolean isNewReview = !existingReview.exists();
            int currentReviewCount = Math.max(0, value(product, "reviewCount"));
            double currentAverage = number(product, "averageRating");
            int oldRating = isNewReview ? 0 : value(existingReview, "rating");
            int updatedReviewCount = ReviewRules.reviewCountAfter(currentReviewCount, isNewReview);
            double updatedAverage = ReviewRules.averageAfter(
                    currentReviewCount, currentAverage, oldRating, rating, isNewReview);

            boolean rewardGranted = false;
            if (isNewReview) {
                rewardGranted = PointsRepository.getInstance().grantPointsInTransaction(
                        transaction, normalizedEmail, REVIEW_REWARD, "product_review", reviewId);
            }

            java.util.Map<String, Object> reviewData = new java.util.HashMap<>();
            reviewData.put("orderId", orderId);
            reviewData.put("orderItemId", orderItemId);
            reviewData.put("productId", productId);
            reviewData.put("productName", productName);
            reviewData.put("productIcon", productIcon);
            reviewData.put("reviewerId", reviewerId);
            reviewData.put("reviewerEmail", normalizedEmail);
            reviewData.put("reviewerName", anonymous ? "" : reviewerName);
            reviewData.put("anonymous", anonymous);
            reviewData.put("rating", rating);
            reviewData.put("comment", normalizedComment);
            reviewData.put("updatedAt", FieldValue.serverTimestamp());
            if (isNewReview) {
                reviewData.put("likesCount", 0L);
                reviewData.put("viewsCount", 0L);
                reviewData.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(reviewRef, reviewData);
            } else {
                transaction.set(reviewRef, reviewData, SetOptions.merge());
            }

            java.util.Map<String, Object> productUpdate = new java.util.HashMap<>();
            productUpdate.put("reviewCount", updatedReviewCount);
            productUpdate.put("averageRating", updatedReviewCount == 0 ? 0.0 : updatedAverage);
            transaction.update(productRef, productUpdate);
            return new SaveResult(isNewReview, rewardGranted);
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    public void recordLike(String reviewId, String email, InteractionCallback callback) {
        recordInteraction(reviewId, email, "likes", callback);
    }

    public void recordView(String reviewId, String email, InteractionCallback callback) {
        recordInteraction(reviewId, email, "views", callback);
    }

    private void recordInteraction(String reviewId, String email, String kind, InteractionCallback callback) {
        String reviewerId;
        try {
            reviewerId = authenticatedUserId(email);
        } catch (IllegalArgumentException | IllegalStateException error) {
            callback.onError(error);
            return;
        }
        DocumentReference reviewRef = firestore.collection("reviews").document(reviewId);
        DocumentReference markerRef = reviewRef.collection(kind)
                .document(reviewerId);
        String countField = "likes".equals(kind) ? "likesCount" : "viewsCount";

        firestore.runTransaction(transaction -> {
            DocumentSnapshot review = transaction.get(reviewRef);
            DocumentSnapshot marker = transaction.get(markerRef);
            if (!review.exists()) {
                return false;
            }
            if (reviewerId.equals(review.getString("reviewerId")) || marker.exists()) {
                return false;
            }

            long count = Math.max(0, longValue(review, countField));
            transaction.update(reviewRef, countField, Math.addExact(count, 1));
            java.util.Map<String, Object> markerData = new java.util.HashMap<>();
            markerData.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(markerRef, markerData);
            return true;
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    private void loadDeliveredOrderItems(String email, Set<String> reviewedItems, PendingCallback callback) {
        FirestoreRepository.getInstance().getUserOrders(email, new FirestoreRepository.OrdersCallback() {
            @Override
            public void onResult(List<Order> orders) {
                List<Order> deliveredOrders = new ArrayList<>();
                for (Order order : orders) {
                    if (ReviewRules.isDelivered(order.getStatus())) {
                        deliveredOrders.add(order);
                    }
                }
                if (deliveredOrders.isEmpty()) {
                    callback.onResult(Collections.emptyList());
                    return;
                }

                List<ReviewableItem> pendingItems = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger remaining = new AtomicInteger(deliveredOrders.size());
                AtomicInteger errors = new AtomicInteger();
                for (Order order : deliveredOrders) {
                    FirestoreRepository.getInstance().getOrderItems(order.getId(),
                            new FirestoreRepository.OrderItemsCallback() {
                                @Override
                                public void onResult(List<OrderItem> items) {
                                    for (OrderItem item : items) {
                                        String key = ReviewRules.documentId(order.getId(), item.getProductId());
                                        if (!reviewedItems.contains(key)) {
                                            pendingItems.add(new ReviewableItem(order, item));
                                        }
                                    }
                                    finishOrderItemsLoad(remaining, errors, pendingItems, callback);
                                }

                                @Override
                                public void onError(Exception error) {
                                    if (errors.compareAndSet(0, 1)) {
                                        callback.onError(error);
                                    }
                                    remaining.decrementAndGet();
                                }
                            });
                }
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    private void finishOrderItemsLoad(AtomicInteger remaining, AtomicInteger errors,
            List<ReviewableItem> items, PendingCallback callback) {
        if (remaining.decrementAndGet() == 0 && errors.get() == 0) {
            List<ReviewableItem> sorted = new ArrayList<>(items);
            sorted.sort((first, second) -> Integer.compare(second.getOrderId(), first.getOrderId()));
            callback.onResult(sorted);
        }
    }

    private List<Review> toSortedReviews(QuerySnapshot snapshot) {
        return toSortedReviews(snapshot.getDocuments());
    }

    private List<Review> toSortedReviews(List<DocumentSnapshot> documents) {
        List<Review> reviews = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            reviews.add(Review.fromDocument(document));
        }
        reviews.sort((first, second) -> Long.compare(
                dateMillis(second.getCreatedAt()), dateMillis(first.getCreatedAt())));
        return reviews;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Cần đăng nhập để sử dụng đánh giá.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String authenticatedUserId(String email) {
        String normalizedEmail = normalizeEmail(email);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null
                || !normalizedEmail.equals(user.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Phiên đăng nhập không hợp lệ để sử dụng đánh giá.");
        }
        return user.getUid();
    }

    private int value(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0 : value.intValue();
    }

    private long longValue(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0 : value;
    }

    private double number(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private long dateMillis(Date date) {
        return date == null ? 0 : date.getTime();
    }

    public static final class SaveResult {
        private final boolean created;
        private final boolean rewardGranted;

        private SaveResult(boolean created, boolean rewardGranted) {
            this.created = created;
            this.rewardGranted = rewardGranted;
        }

        public boolean isCreated() {
            return created;
        }

        public boolean isRewardGranted() {
            return rewardGranted;
        }
    }

    public static final class ReviewableItem {
        private final int orderId;
        private final int orderItemId;
        private final int productId;
        private final String orderDate;
        private final String productName;
        private final String productIcon;
        private final String orderStatus;

        private ReviewableItem(Order order, OrderItem item) {
            this.orderId = order.getId();
            this.orderItemId = item.getId();
            this.productId = item.getProductId();
            this.orderDate = order.getOrderDate();
            this.productName = item.getProductName();
            this.productIcon = item.getProductIcon();
            this.orderStatus = order.getStatus();
        }

        public int getOrderId() {
            return orderId;
        }

        public int getOrderItemId() {
            return orderItemId;
        }

        public int getProductId() {
            return productId;
        }

        public String getOrderDate() {
            return orderDate;
        }

        public String getProductName() {
            return productName;
        }

        public String getProductIcon() {
            return productIcon;
        }

        public String getOrderStatus() {
            return orderStatus;
        }
    }
}
