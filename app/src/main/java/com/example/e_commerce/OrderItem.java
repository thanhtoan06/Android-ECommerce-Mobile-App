/**
 * OrderItem.java: Đối tượng lưu trữ chi tiết từng sản phẩm bên trong một đơn hàng (tên, icon, giá, số lượng, thành tiền).
 */
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class OrderItem {

    private int id;
    private int orderId;
    private int productId;
    private String productName;
    private String productIcon;
    private long price;
    private int quantity;
    private long subtotal;

    public OrderItem() {}

    // Hàm khởi tạo thông tin một món hàng trong đơn hàng
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

    // Lấy mã dòng chi tiết đơn hàng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy mã đơn hàng chứa sản phẩm này
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    // Lấy mã sản phẩm
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    // Lấy tên sản phẩm lúc đặt hàng
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    // Lấy biểu tượng icon sản phẩm
    public String getProductIcon() { return productIcon; }
    public void setProductIcon(String productIcon) { this.productIcon = productIcon; }

    // Lấy đơn giá lúc đặt hàng
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    // Lấy số lượng mua
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    // Lấy thành tiền gốc dạng số nguyên
    public long getSubtotal() { return subtotal; }
    public void setSubtotal(long subtotal) { this.subtotal = subtotal; }

    // Định dạng đơn giá theo kiểu Việt Nam
    public String getFormattedPrice() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
    }

    // Định dạng thành tiền theo kiểu Việt Nam
    public String getFormattedSubtotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(subtotal) + " ₫";
    }
}