package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * OrderItem.java
 * ─────────────────────────────────────────────────────────
 * Model đại diện cho một dòng sản phẩm trong đơn hàng (bảng order_items SQLite).
 * Lưu snapshot tên/giá tại thời điểm đặt hàng để bảo toàn lịch sử
 * kể cả khi sản phẩm gốc bị xóa hoặc thay đổi giá sau này.
 * ─────────────────────────────────────────────────────────
 */
public class OrderItem {
    private int id;             // Khóa chính bảng order_items
    private int orderId;        // Khóa ngoại trỏ tới bảng orders
    private int productId;      // ID gốc của sản phẩm (tham khảo)
    private String productName; // Tên sản phẩm tại thời điểm đặt hàng (snapshot)
    private String productIcon; // Emoji icon sản phẩm tại thời điểm đặt hàng
    private long price;         // Đơn giá tại thời điểm đặt hàng (VNĐ)
    private int quantity;       // Số lượng đặt mua
    private long subtotal;      // Thành tiền = price × quantity

    public OrderItem() {}

    // Constructor đầy đủ (dùng khi đọc từ SQLite)
    public OrderItem(int id, int orderId, int productId, String productName,
                     String productIcon, long price, int quantity, long subtotal) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productIcon = productIcon;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    // ─── Getters & Setters ───

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductIcon() { return productIcon; }
    public void setProductIcon(String productIcon) { this.productIcon = productIcon; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public long getSubtotal() { return subtotal; }
    public void setSubtotal(long subtotal) { this.subtotal = subtotal; }

    /** Đơn giá định dạng tiền tệ Việt Nam */
    public String getFormattedPrice() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
    }

    /** Thành tiền định dạng tiền tệ Việt Nam */
    public String getFormattedSubtotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(subtotal) + " ₫";
    }
}
