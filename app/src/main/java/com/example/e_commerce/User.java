// Đối tượng lưu trữ thông tin tài khoản người dùng (mã, họ tên, email, mật khẩu, avatar, số điện thoại và địa chỉ)
package com.example.e_commerce;

public class User {

    // Khai báo các thuộc tính của một tài khoản người dùng
    private int id;
    private String fullName;
    private String email;
    private String password;
    private String avatar;
    private String phone;
    private String address;

    // Hàm khởi tạo mặc định với avatar mặc định (phục vụ ánh xạ dữ liệu từ Firestore)
    public User() {
        this.avatar = "👤";
        this.phone = "";
        this.address = "";
    }

    // Hàm khởi tạo thông tin người dùng cơ bản (chưa có số điện thoại và địa chỉ)
    public User(int id, String fullName, String email, String password, String avatar) {
        this(id, fullName, email, password, avatar, "", "");
    }

    // Hàm khởi tạo đầy đủ thông tin người dùng khi đọc từ Firestore
    public User(int id, String fullName, String email, String password, String avatar, String phone, String address) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
        this.phone = (phone != null) ? phone : "";
        this.address = (address != null) ? address : "";
    }

    // Lấy và cập nhật mã ID người dùng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy và cập nhật họ và tên người dùng
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    // Lấy và cập nhật email đăng nhập
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // Lấy và cập nhật mật khẩu
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // Lấy và cập nhật biểu tượng avatar (mặc định là 👤 nếu trống)
    public String getAvatar() { return avatar != null && !avatar.isEmpty() ? avatar : "👤"; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    // Lấy và cập nhật số điện thoại
    public String getPhone() { return phone != null ? phone : ""; }
    public void setPhone(String phone) { this.phone = (phone != null) ? phone : ""; }

    // Lấy và cập nhật địa chỉ
    public String getAddress() { return address != null ? address : ""; }
    public void setAddress(String address) { this.address = (address != null) ? address : ""; }
}