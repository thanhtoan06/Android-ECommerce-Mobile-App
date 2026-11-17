// Lớp quản lý phiên đăng nhập bằng SharedPreferences: lưu trữ, cập nhật và xóa thông tin người dùng đang đăng nhập
package com.example.e_commerce;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefManager {

    // Tên file SharedPreferences và các khóa (key) dùng để lưu thông tin tài khoản
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_ADDRESS = "address";

    // Khai báo đối tượng Singleton và công cụ đọc/ghi SharedPreferences
    private static SharedPrefManager instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    // Hàm khởi tạo private (chỉ mở file SharedPreferences một lần duy nhất)
    private SharedPrefManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    // Lấy đối tượng SharedPrefManager dùng chung cho toàn bộ ứng dụng (Singleton)
    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    // Lưu đầy đủ thông tin tài khoản vào bộ nhớ tạm sau khi đăng nhập thành công
    public void saveUser(String email, String username, String avatar, String phone, String address) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_AVATAR, (avatar != null && !avatar.isEmpty()) ? avatar : "👤");
        editor.putString(KEY_PHONE, phone != null ? phone : "");
        editor.putString(KEY_ADDRESS, address != null ? address : "");
        editor.apply();
    }

    // Hàm nạp chồng: lưu thông tin cơ bản khi chưa có số điện thoại và địa chỉ
    public void saveUser(String email, String username, String avatar) {
        saveUser(email, username, avatar, "", "");
    }

    // Cập nhật lại tên tài khoản / tên đăng nhập trong bộ nhớ tạm
    public void updateUsername(String username) {
        editor.putString(KEY_USERNAME, username);
        editor.apply();
    }

    // Cập nhật lại số điện thoại trong bộ nhớ tạm
    public void updatePhone(String phone) {
        editor.putString(KEY_PHONE, phone != null ? phone : "");
        editor.apply();
    }

    // Cập nhật lại địa chỉ trong bộ nhớ tạm
    public void updateAddress(String address) {
        editor.putString(KEY_ADDRESS, address != null ? address : "");
        editor.apply();
    }

    // Kiểm tra xem người dùng đã đăng nhập trước đó chưa (trả về true/false)
    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // Lấy email của người dùng đang đăng nhập
    public String getEmail() {
        return sharedPreferences.getString(KEY_EMAIL, "");
    }

    // Lấy tên hiển thị của người dùng đang đăng nhập
    public String getUsername() {
        return sharedPreferences.getString(KEY_USERNAME, "");
    }

    // Lấy biểu tượng avatar mặc định của người dùng đang đăng nhập
    public String getAvatar() {
        return sharedPreferences.getString(KEY_AVATAR, "👤");
    }

    // Lấy số điện thoại của người dùng đang đăng nhập
    public String getPhone() {
        return sharedPreferences.getString(KEY_PHONE, "");
    }

    // Lấy địa chỉ của người dùng đang đăng nhập
    public String getAddress() {
        return sharedPreferences.getString(KEY_ADDRESS, "");
    }

    // Xóa toàn bộ thông tin phiên đăng nhập khi người dùng bấm Đăng xuất
    public void logout() {
        editor.clear();
        editor.apply();
    }
}