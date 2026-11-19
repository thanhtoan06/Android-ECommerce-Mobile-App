// Đối tượng đại diện cho một dòng sản phẩm trong giỏ hàng (lưu thông tin sản phẩm, số lượng và tính thành tiền).
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class CartItem {

    // Khai báo các thuộc tính của một dòng trong giỏ hàng: mã dòng, đối tượng sản phẩm, số lượng và trạng thái tích chọn
    private int id;
    private Product product;
    private int quantity;
    private boolean selected = true;

    // Hàm khởi tạo một dòng sản phẩm trong giỏ hàng
    public CartItem(int id, Product product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }

    // Lấy và cập nhật mã dòng giỏ hàng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy và cập nhật đối tượng sản phẩm
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    // Lấy và cập nhật số lượng đặt mua
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    // Lấy và cập nhật trạng thái tích chọn mua của sản phẩm
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    // Tính thành tiền của món hàng (đơn giá × số lượng)
    public long getSubtotal() {
        return product != null ? product.getPrice() * quantity : 0;
    }

    // Định dạng thành tiền theo chuẩn tiền Việt Nam (ví dụ: 189.000 ₫)
    public String getFormattedSubtotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(getSubtotal()) + " ₫";
    }
}