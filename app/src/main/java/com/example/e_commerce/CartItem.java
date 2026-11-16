/**
 * CartItem.java: Đối tượng lưu trữ một món hàng trong giỏ (mã giỏ hàng, thông tin sản phẩm và số lượng).
 */
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class CartItem {

    private int id;
    private Product product;
    private int quantity;

    // Hàm khởi tạo một dòng sản phẩm trong giỏ hàng
    public CartItem(int id, Product product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }

    // Lấy mã dòng giỏ hàng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy thông tin sản phẩm
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    // Lấy số lượng đặt mua
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    // Tính thành tiền của món hàng (đơn giá × số lượng)
    public long getSubtotal() {
        return product != null ? product.getPrice() * quantity : 0;
    }

    // Định dạng thành tiền theo kiểu Việt Nam (ví dụ: 189.000 ₫)
    public String getFormattedSubtotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(getSubtotal()) + " ₫";
    }
}