// Đối tượng lưu trữ thông tin một đơn hàng đã đặt (mã đơn, người nhận, địa chỉ, phương thức thanh toán, tổng tiền, ngày đặt và trạng thái)
package com.example.e_commerce;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

public class Order implements Serializable {

    // Khai báo các thuộc tính lưu trữ thông tin của một đơn hàng
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

    // Hàm khởi tạo rỗng phục vụ việc ánh xạ dữ liệu từ Firestore
    public Order() {}

    // Hàm khởi tạo đầy đủ thông tin đơn hàng khi đọc từ Firestore
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

    // Lấy và cập nhật mã đơn hàng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy và cập nhật email người đặt
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    // Lấy và cập nhật họ tên người nhận
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    // Lấy và cập nhật số điện thoại người nhận
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    // Lấy và cập nhật địa chỉ giao hàng
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    // Lấy và cập nhật ghi chú đơn hàng
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    // Lấy và cập nhật phương thức thanh toán
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    // Lấy và cập nhật tổng tiền gốc dạng số nguyên
    public long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }

    // Lấy và cập nhật ngày giờ đặt hàng
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    // Lấy và cập nhật trạng thái đơn hàng
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Định dạng tổng tiền đơn hàng theo kiểu Việt Nam (ví dụ: 1.790.000 ₫)
    public String getFormattedTotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
    }
}