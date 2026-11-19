package com.example.e_commerce;

import com.google.firebase.Timestamp;

public class AppNotification {

    private final String id;
    private final String type;
    private final String title;
    private final String message;
    private final int orderId;
    private final Timestamp createdAt;
    private boolean read;

    public AppNotification(String id, String type, String title, String message, int orderId, Timestamp createdAt,
            boolean read) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.message = message;
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.read = read;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public int getOrderId() {
        return orderId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
