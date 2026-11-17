// Màn hình Đăng ký: kiểm tra tính hợp lệ của thông tin, kiểm tra trùng email và tạo tài khoản mới trên Firestore
package com.example.e_commerce;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    // Khai báo các ô nhập liệu và nút bấm trên màn hình Đăng ký
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
            // Lấy chuỗi người dùng nhập vào và loại bỏ khoảng trắng thừa ở hai đầu
            String name = edtFullName.getText().toString().trim();
            String email = edtRegisterEmail.getText().toString().trim();
            String phone = edtRegisterPhone.getText().toString().trim();
            String pass = edtRegisterPassword.getText().toString().trim();
            String confirmPass = edtConfirmPassword.getText().toString().trim();

            // Kiểm tra dữ liệu hợp lệ trước khi lưu lên Firestore
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show();
            } else if (phone.length() < 9 || phone.length() > 11) {
                Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
            } else if (pass.length() < 6) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
            } else if (!pass.equals(confirmPass)) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            } else {
                // Kiểm tra trùng email và lưu tài khoản mới lên Firestore
                FirestoreRepository repository = FirestoreRepository.getInstance();
                DatabaseHelper databaseHelper = DatabaseHelper.getInstance(this);
                repository.checkEmailExists(email, exists -> {
                    if (exists) {
                        Toast.makeText(this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG).show();
                    } else {
                        // Tiến hành tạo tài khoản mới trên Firestore và quay về màn hình Đăng nhập nếu thành công
                        databaseHelper.registerUser(name, email, pass, phone);
                        repository.registerUser(name, email, pass, phone, success -> {
                            Toast.makeText(this, success ? "Đăng ký thành công! Vui lòng đăng nhập." : "Đăng ký thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                            if (success) finish();
                        });
                    }
                });
            }
        });

        // Xử lý khi người dùng bấm nút QUAY LẠI ĐĂNG NHẬP
        btnBackToLogin.setOnClickListener(v -> finish());
    }
}