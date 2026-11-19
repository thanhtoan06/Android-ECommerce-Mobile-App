// Đối tượng lưu trữ thông tin một sản phẩm (mã, tên, danh mục, giá bán, mô tả, biểu tượng và tồn kho thực tế)
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class Product {

    // Khai báo các thuộc tính cố định của một sản phẩm
    private final int id;
    private final String name;
    private final String category;
    private final long price;
    private final String description;
    private final String icon;
    private final int stock; // Số lượng tồn kho thực tế lưu trong cơ sở dữ liệu

    // Hàm khởi tạo đầy đủ có số lượng tồn kho thực tế
    public Product(int id, String name, String category, long price, String description, String icon, int stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.icon = icon;
        this.stock = stock;
    }

    // Hàm khởi tạo tương thích (nếu không truyền stock thì mặc định 0)
    public Product(int id, String name, String category, long price, String description, String icon) {
        this(id, name, category, price, description, icon, 0);
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

    // Lấy giá gốc dạng số nguyên
    public long getPrice() {
        return price;
    }

    // Định dạng giá tiền theo kiểu Việt Nam (ví dụ: 189.000 ₫)
    public String getFormattedPrice() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
    }

    // Lấy mô tả chi tiết sản phẩm
    public String getDescription() {
        return description;
    }

    // Lấy biểu tượng icon sản phẩm
    public String getIcon() {
        return icon;
    }

    // Lấy số lượng hàng còn trong kho thực tế (đọc trực tiếp từ CSDL, không tính giả định)
    public int getStock() {
        return stock;
    }

    // Kiểm tra sản phẩm còn hàng hay đã hết
    public boolean isInStock() {
        return stock > 0;
    }
}
