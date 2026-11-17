/**
 * User.java: Đối tượng lưu trữ thông tin của một người dùng (id, họ tên, email, mật khẩu, avatar).
 */
package com.example.e_commerce;

public class User {

    private int id;
    private String fullName;
    private String email;
    private String password;
    private String avatar;
    private String phone;
    private String address;

    // Hàm khởi tạo mặc định với avatar mặc định
    public User() {
        this.avatar = "👤";
        this.phone = "";
        this.address = "";
    }

    // Hàm khởi tạo thông tin người dùng cơ bản
    public User(int id, String fullName, String email, String password, String avatar) {
        this(id, fullName, email, password, avatar, "", "");
    }

    // Hàm khởi tạo đầy đủ thông tin người dùng khi đọc từ SQLite
    public User(int id, String fullName, String email, String password, String avatar, String phone, String address) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
        this.phone = (phone != null) ? phone : "";
        this.address = (address != null) ? address : "";
    }

    // Lấy mã ID người dùng
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Lấy họ và tên người dùng
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    // Lấy email đăng nhập
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // Lấy mật khẩu
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // Lấy biểu tượng avatar (mặc định là 👤 nếu trống)
    public String getAvatar() { return avatar != null && !avatar.isEmpty() ? avatar : "👤"; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    // Lấy số điện thoại
    public String getPhone() { return phone != null ? phone : ""; }
    public void setPhone(String phone) { this.phone = (phone != null) ? phone : ""; }

    // Lấy địa chỉ
    public String getAddress() { return address != null ? address : ""; }
    public void setAddress(String address) { this.address = (address != null) ? address : ""; }
}