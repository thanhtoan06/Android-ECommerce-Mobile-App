package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Order.java
 * ─────────────────────────────────────────────────────────
 * Model đại diện cho một đơn hàng đã được đặt (bảng orders SQLite).
 * Lưu thông tin: id đơn hàng, email user, thông tin người nhận,
 * phương thức thanh toán, tổng tiền, ngày đặt và trạng thái.
 * ─────────────────────────────────────────────────────────
 */
public class Order {
    private int id;                 // Mã đơn hàng (khóa chính)
    private String userEmail;       // Email user đã đặt đơn hàng
    private String receiverName;    // Họ tên người nhận hàng
    private String receiverPhone;   // Số điện thoại nhận hàng
    private String receiverAddress; // Địa chỉ giao hàng chi tiết
    private String note;            // Ghi chú thêm (tùy chọn)
    private String paymentMethod;   // Phương thức thanh toán (COD, chuyển khoản, ví điện tử)
    private long totalAmount;       // Tổng tiền đơn hàng (VNĐ)
    private String orderDate;       // Ngày giờ đặt hàng (định dạng dd/MM/yyyy HH:mm)
    private String status;          // Trạng thái đơn hàng (vd: "Đặt hàng thành công")

    public Order() {}

    // Constructor đầy đủ (dùng khi đọc từ SQLite)
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

    // ─── Getters & Setters ───

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** Tổng tiền định dạng tiền tệ Việt Nam (vd: 1.790.000 ₫) */
    public String getFormattedTotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
    }
}
