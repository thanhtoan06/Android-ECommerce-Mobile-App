package com.example.e_commerce;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPrefManager.java
 * ─────────────────────────────────────────────────────────
 * Quản lý phiên đăng nhập (session) của người dùng bằng SharedPreferences.
 * Lưu trữ: trạng thái đăng nhập, email, tên hiển thị và avatar.
 * Dùng mẫu Singleton để toàn app dùng chung 1 instance.
 *
 * Lưu ý: SharedPreferences chỉ lưu SESSION hiện tại, không phải
 * toàn bộ danh sách tài khoản (việc đó thuộc về DatabaseHelper).
 * ─────────────────────────────────────────────────────────
 */
public class SharedPrefManager {

    private static final String PREF_NAME = "user_prefs";       // Tên file SharedPreferences
    private static final String KEY_IS_LOGGED_IN = "is_logged_in"; // Key trạng thái đăng nhập
    private static final String KEY_EMAIL = "email";             // Key email tài khoản
    private static final String KEY_USERNAME = "username";       // Key tên hiển thị
    private static final String KEY_AVATAR = "avatar";           // Key avatar emoji

    private static SharedPrefManager instance; // Instance duy nhất (Singleton)
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    // Constructor private – chỉ tạo 1 lần qua getInstance()
    private SharedPrefManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    /** Trả về instance duy nhất của SharedPrefManager (thread-safe Singleton) */
    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    /**
     * Lưu thông tin phiên đăng nhập đầy đủ: email, họ tên và avatar.
     * Gọi sau khi xác thực đăng nhập thành công từ DatabaseHelper.
     */
    public void saveUser(String email, String username, String avatar) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_AVATAR, (avatar != null && !avatar.isEmpty()) ? avatar : "👤");
        editor.apply();
    }

    /** Lưu phiên đăng nhập với avatar mặc định 👤 */
    public void saveUser(String email, String username) {
        saveUser(email, username, "👤");
    }

    /** Lưu phiên đăng nhập chỉ với email (tự trích username từ phần trước @) */
    public void saveUser(String email) {
        String username = email;
        if (email != null && email.contains("@")) {
            username = email.substring(0, email.indexOf("@"));
        }
        saveUser(email, username, "👤");
    }

    /** Cập nhật avatar trong phiên đăng nhập hiện tại (không cần đăng nhập lại) */
    public void updateAvatar(String newAvatar) {
        editor.putString(KEY_AVATAR, newAvatar);
        editor.apply();
    }

    /** Kiểm tra người dùng đã đăng nhập hay chưa (dùng khi khởi động app) */
    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /** Lấy email của tài khoản đang đăng nhập */
    public String getEmail() {
        return sharedPreferences.getString(KEY_EMAIL, "");
    }

    /** Lấy tên hiển thị (họ tên) của người dùng đang đăng nhập */
    public String getUsername() {
        return sharedPreferences.getString(KEY_USERNAME, "");
    }

    /** Lấy avatar emoji của người dùng đang đăng nhập */
    public String getAvatar() {
        return sharedPreferences.getString(KEY_AVATAR, "👤");
    }

    /** Xóa toàn bộ dữ liệu phiên đăng nhập (gọi khi người dùng đăng xuất) */
    public void logout() {
        editor.clear();
        editor.apply();
    }
}
