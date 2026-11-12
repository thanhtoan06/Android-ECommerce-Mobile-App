/**
 * Product.java
 * ─────────────────────────────────────────────────────────
 * Model đại diện cho một sản phẩm trong bảng products (SQLite).
 * Các trường: id, tên, danh mục, giá (VNĐ), mô tả, icon (emoji), màu nền (ARGB).
 * Tất cả trường là final (bất biến) sau khi đợc khởi tạo.
 * ─────────────────────────────────────────────────────────
 */
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class Product {

    private final int id;          // Mã định danh sản phẩm
    private final String name;     // Tên sản phẩm
    private final String category; // Danh mục sản phẩm
    private final long price;      // Giá sản phẩm (VND)
    private final String description;// Mô tả chi tiết sản phẩm
    private final String icon;     // Biểu tượng (emoji) đại diện cho sản phẩm
    private final int color;       // Màu nền đại diện cho sản phẩm

    // Hàm khởi tạo đối tượng Product
    public Product(int id, String name, String category, long price, String description, String icon, int color) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.icon = icon;
        this.color = color;
    }

    // Lấy mã sản phẩm
    public int getId() {
        return id;
    }

    // Lấy tên sản phẩm
    public String getName() {
        return name;
    }

    // Lấy danh mục sản phẩm
    public String getCategory() {
        return category;
    }

    // Lấy giá sản phẩm
    public long getPrice() {
        return price;
    }

    // Lấy giá sản phẩm đã định dạng tiền tệ VN (VD: 189.000 ₫)
    public String getFormattedPrice() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
    }

    // Lấy mô tả sản phẩm
    public String getDescription() {
        return description;
    }

    // Lấy icon sản phẩm
    public String getIcon() {
        return icon;
    }

    // Lấy màu sắc đại diện sản phẩm
    public int getColor() {
        return color;
    }
}
