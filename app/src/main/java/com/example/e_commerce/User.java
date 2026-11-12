package com.example.e_commerce;

/**
 * User.java
 * ─────────────────────────────────────────────────────────
 * Model đại diện cho một người dùng lưu trong bảng users (SQLite).
 * Các trường: id, fullName, email, password, avatar (emoji đại diện).
 * ─────────────────────────────────────────────────────────
 */
public class User {
    private int id;          // Khóa chính trong bảng users
    private String fullName; // Họ và tên
    private String email;    // Email (dùng để đăng nhập, duy nhất)
    private String password; // Mật khẩu
    private String avatar;   // Biểu tượng emoji đại diện (ví dụ: 👨‍💼, 👩‍💻)

    // Constructor mặc định – avatar mặc định là 👤
    public User() {
        this.avatar = "👤";
    }

    // Constructor đầy đủ (dùng khi đọc từ SQLite)
    public User(int id, String fullName, String email, String password, String avatar) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
    }

    // Constructor kèm avatar (khi tạo mới chưa có id)
    public User(String fullName, String email, String password, String avatar) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
    }

    // Constructor không có avatar → dùng mặc định 👤
    public User(int id, String fullName, String email, String password) {
        this(id, fullName, email, password, "👤");
    }

    public User(String fullName, String email, String password) {
        this(fullName, email, password, "👤");
    }

    // ─── Getters & Setters ───

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    /** Trả về avatar; mặc định 👤 nếu chưa thiết lập */
    public String getAvatar() { return avatar != null && !avatar.isEmpty() ? avatar : "👤"; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
