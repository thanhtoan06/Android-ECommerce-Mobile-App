// Đối tượng lưu trữ thông tin chi tiết từng sản phẩm thuộc một đơn hàng (mã đơn, tên sản phẩm, biểu tượng, đơn giá, số lượng và thành tiền)
package com.example.e_commerce;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

public class OrderItem implements Serializable {

    // Khai báo các thuộc tính của một dòng sản phẩm trong đơn hàng
    private int id;
    private int orderId;
    private int productId;
    private String productName;
    private String productIcon;
    private long price;
    private int quantity;
    private long subtotal;

    // Hàm khởi tạo rỗng phục vụ việc ánh xạ dữ liệu từ Firestore
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

    // Lấy và cập nhật mã dòng chi tiết đơn hàng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy và cập nhật mã đơn hàng chứa sản phẩm này
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    // Lấy và cập nhật mã sản phẩm
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    // Lấy và cập nhật tên sản phẩm lúc đặt hàng
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    // Lấy và cập nhật biểu tượng icon sản phẩm
    public String getProductIcon() { return productIcon; }
    public void setProductIcon(String productIcon) { this.productIcon = productIcon; }

    // Lấy và cập nhật đơn giá lúc đặt hàng
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    // Lấy và cập nhật số lượng mua
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    // Lấy và cập nhật thành tiền gốc dạng số nguyên
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