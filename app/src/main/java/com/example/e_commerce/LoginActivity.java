/**
 * LoginActivity.java: Xử lý màn hình đăng nhập (kiểm tra tài khoản trong SQLite, lưu phiên đăng nhập và chuyển trang).
 */
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private Button btnLogin, btnRegister;

    // Hàm chạy đầu tiên khi mở màn hình đăng nhập: kiểm tra phiên cũ, hiển thị giao diện và gắn sự kiện nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra bộ nhớ tạm (SharedPreferences): nếu đã đăng nhập từ trước thì vào thẳng trang sản phẩm
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
            finish(); // Đóng trang đăng nhập để bấm nút Quay lại không bị văng ngược về đây
            return;
        }

        // Gắn giao diện XML vào code Java để điều khiển
        setContentView(R.layout.activity_login);

        // Kết nối các biến Java với các ô nhập và nút bấm trên giao diện
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        // Xử lý khi người dùng bấm nút ĐĂNG NHẬP
        btnLogin.setOnClickListener(v -> {
            // Lấy chữ người dùng nhập và xóa khoảng trắng thừa 2 đầu
            String account = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();

            // Kiểm tra nếu bỏ trống ô nhập thì báo lỗi và dừng lại
            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Gọi SQLite kiểm tra tài khoản và mật khẩu có khớp trong database không
            User user = DatabaseHelper.getInstance(LoginActivity.this).checkLogin(account, password);
            if (user != null) {
                // Nếu đúng: Lưu thông tin người dùng vào bộ nhớ tạm (SharedPreferences) để dùng cho các trang sau
                prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(), user.getPhone(), user.getAddress());
                Toast.makeText(LoginActivity.this, "Đăng nhập thành công! Xin chào " + user.getFullName(), Toast.LENGTH_SHORT).show();

                // Chuyển sang màn hình danh sách sản phẩm và đóng trang đăng nhập
                startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
                finish();
            } else {
                // Nếu sai: Hiện thông báo lỗi
                Toast.makeText(LoginActivity.this, "Tên tài khoản hoặc mật khẩu không chính xác!", Toast.LENGTH_SHORT).show();
            }
        });

        // Xử lý khi người dùng bấm nút TẠO TÀI KHOẢN MỚI -> mở trang đăng ký
        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );
    }
}