
package com.example.e_commerce;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

public class Order implements Serializable {

    private int id;
    private String userEmail;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String note;
    private String paymentMethod;
    private long totalAmount;
    private long itemsSubtotal = -1;
    private long productDiscount;
    private long shippingFee = -1;
    private long shippingDiscount;
    private long pointsDiscount;
    private String orderDate;
    private String status;
    private Date deliveredAt;
    private String returnReason;

    public Order() {
    }

    public Order(int id, String userEmail, String receiverName, String receiverPhone, String receiverAddress,
            String note, String paymentMethod, long totalAmount, String orderDate, String status) {
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public long getItemsSubtotal() {
        return itemsSubtotal;
    }

    public void setItemsSubtotal(long itemsSubtotal) {
        this.itemsSubtotal = itemsSubtotal;
    }

    public long getProductDiscount() {
        return productDiscount;
    }

    public void setProductDiscount(long productDiscount) {
        this.productDiscount = productDiscount;
    }

    public long getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(long shippingFee) {
        this.shippingFee = shippingFee;
    }

    public long getShippingDiscount() {
        return shippingDiscount;
    }

    public void setShippingDiscount(long shippingDiscount) {
        this.shippingDiscount = shippingDiscount;
    }

    public long getPointsDiscount() {
        return pointsDiscount;
    }

    public void setPointsDiscount(long pointsDiscount) {
        this.pointsDiscount = pointsDiscount;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(String orderDate) {
        this.orderDate = orderDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Date deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public String getFormattedTotal() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
    }
}
