/**
 * RegisterActivity.java: Xử lý màn hình đăng ký tài khoản mới (kiểm tra dữ liệu nhập và lưu vào SQLite).
 */
package com.example.e_commerce;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtFullName, edtRegisterEmail, edtRegisterPhone, edtRegisterPassword, edtConfirmPassword;
    private Button btnSubmitRegister, btnBackToLogin;

    // Hàm chạy đầu tiên khi mở màn hình đăng ký: hiển thị giao diện và gắn sự kiện nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Kết nối các biến Java với các ô nhập và nút bấm trên giao diện
        edtFullName = findViewById(R.id.edtFullName);
        edtRegisterEmail = findViewById(R.id.edtRegisterEmail);
        edtRegisterPhone = findViewById(R.id.edtRegisterPhone);
        edtRegisterPassword = findViewById(R.id.edtRegisterPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        // Xử lý khi người dùng bấm nút HOÀN TẤT ĐĂNG KÝ
        btnSubmitRegister.setOnClickListener(v -> {
            String name = edtFullName.getText().toString().trim();
            String email = edtRegisterEmail.getText().toString().trim();
            String phone = edtRegisterPhone.getText().toString().trim();
            String pass = edtRegisterPassword.getText().toString().trim();
            String confirmPass = edtConfirmPassword.getText().toString().trim();

            // Kiểm tra dữ liệu hợp lệ trước khi lưu vào database
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show();
            } else if (phone.length() < 9 || phone.length() > 11) {
                Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
            } else if (pass.length() < 6) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
            } else if (!pass.equals(confirmPass)) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            } else {
                // Kiểm tra trùng email và lưu tài khoản mới (avatar mặc định 👤) vào SQLite
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
                if (dbHelper.checkEmailExists(email)) {
                    Toast.makeText(this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG).show();
                } else {
                    boolean isRegistered = dbHelper.registerUser(name, email, pass, phone);
                    if (isRegistered) {
                        Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
                        finish(); // Đóng trang đăng ký để quay về màn hình đăng nhập
                    } else {
                        Toast.makeText(this, "Đăng ký thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Xử lý khi người dùng bấm nút QUAY LẠI ĐĂNG NHẬP
        btnBackToLogin.setOnClickListener(v -> finish());
    }
}