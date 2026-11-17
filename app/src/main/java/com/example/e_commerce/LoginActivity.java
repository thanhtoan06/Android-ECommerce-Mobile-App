// Màn hình Đăng nhập: kiểm tra phiên cũ, xác thực tài khoản qua Firebase và lưu bản sao tài khoản hợp lệ xuống SQLite.
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    // Khai báo 2 ô nhập chữ (tài khoản, mật khẩu) và 2 nút bấm (Đăng nhập, Đăng ký)
    private EditText edtEmail, edtPassword;
    private Button btnLogin, btnRegister;

    // Hàm khởi tạo màn hình Đăng nhập: kiểm tra phiên cũ, nạp giao diện và gắn sự kiện nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra bộ nhớ tạm (SharedPreferences): nếu đã đăng nhập từ lần trước thì vào thẳng trang Sản phẩm
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
            finish(); // Đóng màn hình đăng nhập để bấm Back không quay ngược lại đây
            return;
        }

        // Nạp file giao diện activity_login.xml lên màn hình
        setContentView(R.layout.activity_login);

        // Ánh xạ các ô nhập chữ và nút bấm từ file XML vào biến Java để điều khiển
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        // Bắt sự kiện khi người dùng bấm nút ĐĂNG NHẬP
        btnLogin.setOnClickListener(v -> {
            // Lấy chuỗi tài khoản và mật khẩu người dùng vừa gõ, xóa khoảng trắng thừa hai đầu
            String account = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();

            // Kiểm tra không được bỏ trống ô tài khoản hoặc ô mật khẩu
            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Khóa tạm nút Đăng nhập trong lúc chờ Firebase phản hồi để tránh bấm nhiều lần
            btnLogin.setEnabled(false);

            // Bước 1: Gửi tài khoản và mật khẩu lên Firebase kiểm tra trước
            FirestoreRepository.getInstance().checkLogin(account, password, new FirestoreRepository.UserCallback() {
                @Override
                public void onResult(User user) {
                    // Nếu Firebase không tìm thấy tài khoản khớp mật khẩu -> mở lại nút và báo lỗi
                    if (user == null) {
                        btnLogin.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Tên tài khoản hoặc mật khẩu không chính xác!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Bước 2: Firebase xác thực thành công -> lưu và cập nhật đầy đủ bản sao xuống SQLite
                    DatabaseHelper databaseHelper = DatabaseHelper.getInstance(LoginActivity.this);
                    databaseHelper.registerUser(user.getFullName(), user.getEmail(), user.getPassword(), user.getPhone());
                    databaseHelper.updateUsername(user.getEmail(), user.getFullName());
                    databaseHelper.updatePhone(user.getEmail(), user.getPhone());
                    databaseHelper.updateAddress(user.getEmail(), user.getAddress());
                    databaseHelper.updatePassword(user.getEmail(), user.getPassword());

                    // Bước 3: Lưu phiên đăng nhập vào SharedPreferences và chuyển sang trang Danh sách sản phẩm
                    prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(), user.getPhone(), user.getAddress());
                    Toast.makeText(LoginActivity.this, "Đăng nhập thành công! Xin chào " + user.getFullName(), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
                    finish();
                }

                @Override
                public void onError(Exception e) {
                    // Mở lại nút Đăng nhập và thông báo nếu mất kết nối mạng tới Firebase
                    btnLogin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Không thể kết nối Firebase.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Bắt sự kiện khi người dùng bấm nút TẠO TÀI KHOẢN MỚI -> mở chuyển sang màn hình Đăng ký
        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );
    }
}