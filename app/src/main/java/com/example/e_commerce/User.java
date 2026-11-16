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

    // Hàm khởi tạo mặc định với avatar mặc định
    public User() {
        this.avatar = "👤";
    }

    // Hàm khởi tạo đầy đủ thông tin người dùng khi đọc từ SQLite
    public User(int id, String fullName, String email, String password, String avatar) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
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
}