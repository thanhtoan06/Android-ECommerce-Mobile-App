package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * CartItem.java
 * ─────────────────────────────────────────────────────────
 * Model đại diện cho một dòng sản phẩm trong giỏ hàng (bảng cart SQLite).
 * Mỗi CartItem gồm: id (khóa bảng cart), sản phẩm đầy đủ, và số lượng.
 * ─────────────────────────────────────────────────────────
 */
public class CartItem {
    private int id;          // Khóa chính bảng cart (dùng để update/delete)
    private Product product; // Thông tin sản phẩm đầy đủ (JOIN từ bảng products)
    private int quantity;    // Số lượng người dùng đặt mua

    public CartItem(int id, Product product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }

    // ─── Getters & Setters ───

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** Thành tiền = đơn giá × số lượng */
    public long getSubtotal() {
        return product != null ? product.getPrice() * quantity : 0;
    }

    /** Thành tiền định dạng tiền tệ Việt Nam (vd: 189.000 ₫) */
    public String getFormattedSubtotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(getSubtotal()) + " ₫";
    }
}
