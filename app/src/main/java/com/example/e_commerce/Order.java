/**
 * Order.java: Đối tượng lưu trữ thông tin một đơn hàng đã đặt (người nhận, địa chỉ, thanh toán, tổng tiền, ngày đặt).
 */
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class Order {

    private int id;
    private String userEmail;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String note;
    private String paymentMethod;
    private long totalAmount;
    private String orderDate;
    private String status;

    public Order() {}

    // Hàm khởi tạo đầy đủ thông tin đơn hàng khi đọc từ SQLite
    public Order(int id, String userEmail, String receiverName, String receiverPhone,
                 String receiverAddress, String note, String paymentMethod,
                 long totalAmount, String orderDate, String status) {
        this.id = id;
        this.userEmail = userEmail;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddress = receiverAddress;
        this.note = note;
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.orderDate = orderDate;
        this.status = status;
    }

    // Lấy mã đơn hàng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy email người đặt
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    // Lấy họ tên người nhận
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    // Lấy số điện thoại người nhận
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    // Lấy địa chỉ giao hàng
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    // Lấy ghi chú đơn hàng
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    // Lấy phương thức thanh toán
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    // Lấy tổng tiền gốc dạng số nguyên
    public long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }

    // Lấy ngày giờ đặt hàng
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    // Lấy trạng thái đơn hàng
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Định dạng tổng tiền đơn hàng theo kiểu Việt Nam (ví dụ: 1.790.000 ₫)
    public String getFormattedTotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
    }
}