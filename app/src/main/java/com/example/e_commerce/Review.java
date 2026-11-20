package com.example.e_commerce;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.io.Serializable;
import java.util.Date;

public final class Review implements Serializable {

    private final String id;
    private final String reviewerId;
    private final String reviewerName;
    private final String productName;
    private final String comment;
    private final int orderId;
    private final int orderItemId;
    private final int productId;
    private final int rating;
    private final long likesCount;
    private final long viewsCount;
    private final boolean anonymous;
    private final Date createdAt;
    private final Date updatedAt;

    private Review(String id, String reviewerId, String reviewerName, String productName,
            String comment, int orderId, int orderItemId, int productId, int rating, long likesCount, long viewsCount,
            boolean anonymous, Date createdAt, Date updatedAt) {
        this.id = id;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.productName = productName;
        this.comment = comment;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.rating = rating;
        this.likesCount = likesCount;
        this.viewsCount = viewsCount;
        this.anonymous = anonymous;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static Review fromDocument(DocumentSnapshot document) {
        return new Review(
                document.getId(),
                stringValue(document, "reviewerId"),
                stringValue(document, "reviewerName"),
                stringValue(document, "productName"),
                stringValue(document, "comment"),
                intValue(document, "orderId"),
                intValue(document, "orderItemId"),
                intValue(document, "productId"),
                intValue(document, "rating"),
                longValue(document, "likesCount"),
                longValue(document, "viewsCount"),
                Boolean.TRUE.equals(document.getBoolean("anonymous")),
                dateValue(document, "createdAt"),
                dateValue(document, "updatedAt"));
    }

    private static String stringValue(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        return value == null ? "" : value;
    }

    private static int intValue(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0 : value.intValue();
    }

    private static long longValue(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0 : value;
    }

    private static Date dateValue(DocumentSnapshot document, String field) {
        Timestamp value = document.getTimestamp(field);
        return value == null ? null : value.toDate();
    }

    public String getId() {
        return id;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public String getDisplayName() {
        return anonymous || reviewerName.isEmpty() ? "Người mua ẩn danh" : reviewerName;
    }

    public String getProductName() {
        return productName;
    }

    public String getComment() {
        return comment;
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

    public int getRating() {
        return rating;
    }

    public long getLikesCount() {
        return likesCount;
    }

    public long getViewsCount() {
        return viewsCount;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
}
