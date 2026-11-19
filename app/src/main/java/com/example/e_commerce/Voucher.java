package com.example.e_commerce;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.io.Serializable;
import java.util.Date;

public final class Voucher implements Serializable {

    private final String id;
    private final String code;
    private final String name;
    private final String description;
    private final String type;
    private final String discountType;
    private final long value;
    private final String appliesTo;
    private final String category;
    private final long minimumOrderAmount;
    private final long maximumDiscountAmount;
    private final int validityAfterSaveDays;
    private final Date campaignEndsAt;
    private final Date savedAt;
    private final Date expiresAt;
    private final Date usedAt;
    private final String state;
    private final int sortOrder;
    private final boolean active;

    Voucher(String id, String code, String name, String description, String type, String discountType,
            long value, String appliesTo, String category, long minimumOrderAmount, long maximumDiscountAmount,
            int validityAfterSaveDays, Date campaignEndsAt, Date savedAt, Date expiresAt, Date usedAt, String state,
            int sortOrder, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.type = type;
        this.discountType = discountType;
        this.value = value;
        this.appliesTo = appliesTo;
        this.category = category;
        this.minimumOrderAmount = minimumOrderAmount;
        this.maximumDiscountAmount = maximumDiscountAmount;
        this.validityAfterSaveDays = validityAfterSaveDays;
        this.campaignEndsAt = campaignEndsAt;
        this.savedAt = savedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.state = state;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public static Voucher fromDocument(DocumentSnapshot document) {
        return new Voucher(
                stringOrDefault(document.getString("id"), document.getId()),
                stringOrEmpty(document.getString("code")),
                stringOrEmpty(document.getString("name")),
                stringOrEmpty(document.getString("description")),
                stringOrEmpty(document.getString("type")),
                stringOrEmpty(document.getString("discountType")),
                longOrZero(document.getLong("value")),
                stringOrDefault(document.getString("appliesTo"), "all"),
                stringOrEmpty(document.getString("category")),
                longOrZero(document.getLong("minimumOrderAmount")),
                longOrZero(document.getLong("maximumDiscountAmount")),
                (int) longOrZero(document.getLong("validityAfterSaveDays")),
                dateOrNull(document.getTimestamp("campaignEndsAt")),
                dateOrNull(document.getTimestamp("savedAt")),
                dateOrNull(document.getTimestamp("expiresAt")),
                dateOrNull(document.getTimestamp("usedAt")),
                stringOrDefault(document.getString("state"), "available"),
                (int) longOrZero(document.getLong("sortOrder")),
                Boolean.TRUE.equals(document.getBoolean("isActive")));
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static long longOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private static Date dateOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toDate();
    }

    public boolean isProductVoucher() {
        return "product".equals(type);
    }

    public boolean isShippingVoucher() {
        return "shipping".equals(type);
    }

    public boolean isUsed() {
        return "used".equals(state);
    }

    public boolean isSaved() {
        return "saved".equals(state);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isApplicableToAll() {
        return "all".equals(appliesTo);
    }

    public boolean isApplicableToCategory() {
        return "category".equals(appliesTo) && !category.isEmpty();
    }

    public boolean isExpired(Date now) {
        Date expiry = expiresAt != null ? expiresAt : campaignEndsAt;
        return expiry != null && !expiry.after(now);
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public String getDiscountType() {
        return discountType;
    }

    public long getValue() {
        return value;
    }

    public String getAppliesTo() {
        return appliesTo;
    }

    public String getCategory() {
        return category;
    }

    public long getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public long getMaximumDiscountAmount() {
        return maximumDiscountAmount;
    }

    public int getValidityAfterSaveDays() {
        return validityAfterSaveDays;
    }

    public Date getCampaignEndsAt() {
        return campaignEndsAt;
    }

    public Date getSavedAt() {
        return savedAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public Date getUsedAt() {
        return usedAt;
    }

    public String getState() {
        return state;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
