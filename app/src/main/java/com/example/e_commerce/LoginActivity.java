/**
 * Màn hình Đăng nhập (Login), cho phép người dùng nhập tài khoản hoặc chuyển sang đăng ký.
 */
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword; // Ô nhập email và mật khẩu
    private Button btnLogin, btnRegister;     // Nút đăng nhập và chuyển sang đăng ký

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra nếu người dùng đã đăng nhập trước đó qua SharedPreferences
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            Intent intent = new Intent(LoginActivity.this, ProductListActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Ánh xạ các thành phần giao diện
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        // Xử lý sự kiện bấm nút Đăng nhập
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account = edtEmail.getText().toString().trim();
                String password = edtPassword.getText().toString().trim();

                // Kiểm tra dữ liệu nhập vào có trống không
                if (account.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                } else {
                    // Xác thực tài khoản thật từ cơ sở dữ liệu SQLite (hỗ trợ cả Tên hoặc Email)
                    DatabaseHelper dbHelper = DatabaseHelper.getInstance(LoginActivity.this);
                    User user = dbHelper.checkLogin(account, password);

                    if (user != null) {
                        // Đăng nhập hợp lệ: Lưu thông tin (kèm avatar) vào SharedPreferences
                        SharedPrefManager.getInstance(LoginActivity.this).saveUser(user.getEmail(), user.getFullName(), user.getAvatar());

                        Toast.makeText(LoginActivity.this, "Đăng nhập thành công! Xin chào " + user.getFullName(), Toast.LENGTH_SHORT).show();
                        // Chuyển sang màn hình danh sách sản phẩm
                        Intent intent = new Intent(LoginActivity.this, ProductListActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // Báo lỗi nếu tài khoản hoặc mật khẩu không khớp
                        Toast.makeText(LoginActivity.this, "Tên tài khoản hoặc mật khẩu không chính xác!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Xử lý sự kiện bấm nút chuyển sang màn hình Đăng ký
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }
}
