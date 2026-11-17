// Màn hình Đăng ký: kiểm tra hợp lệ dữ liệu, xác thực trùng email trên Firebase, tạo tài khoản chính và lưu bản sao SQLite.
package com.example.e_commerce;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    // Khai báo 5 ô nhập liệu (họ tên, email, SĐT, mật khẩu, nhập lại mật khẩu) và 2 nút bấm (Đăng ký, Quay lại)
    private EditText edtFullName, edtRegisterEmail, edtRegisterPhone, edtRegisterPassword, edtConfirmPassword;
    private Button btnSubmitRegister, btnBackToLogin;

    // Hàm khởi tạo màn hình Đăng ký: nạp giao diện và gắn sự kiện xử lý cho các nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp file giao diện activity_register.xml lên màn hình
        setContentView(R.layout.activity_register);

        // Ánh xạ các ô nhập chữ và nút bấm từ file giao diện XML vào biến Java
        edtFullName = findViewById(R.id.edtFullName);
        edtRegisterEmail = findViewById(R.id.edtRegisterEmail);
        edtRegisterPhone = findViewById(R.id.edtRegisterPhone);
        edtRegisterPassword = findViewById(R.id.edtRegisterPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        // Bắt sự kiện khi người dùng bấm nút HOÀN TẤT ĐĂNG KÝ
        btnSubmitRegister.setOnClickListener(v -> {
            // Lấy nội dung người dùng gõ trong 5 ô nhập và xóa khoảng trắng thừa hai đầu
            String name = edtFullName.getText().toString().trim();
            String email = edtRegisterEmail.getText().toString().trim().toLowerCase();
            String phone = edtRegisterPhone.getText().toString().trim();
            String pass = edtRegisterPassword.getText().toString().trim();
            String confirmPass = edtConfirmPassword.getText().toString().trim();

            // Kiểm tra điều kiện hợp lệ của từng trường thông tin trước khi gửi lên Firebase
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                // Không được bỏ trống bất kỳ ô nhập nào
                Toast.makeText(this, "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show();
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                // Email phải đúng định dạng chuẩn (có @ và tên miền)
                Toast.makeText(this, "Địa chỉ email không hợp lệ!", Toast.LENGTH_SHORT).show();
            } else if (phone.length() < 9 || phone.length() > 11) {
                // Số điện thoại phải dài từ 9 đến 11 chữ số
                Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
            } else if (pass.length() < 6) {
                // Mật khẩu phải từ 6 ký tự trở lên để đảm bảo an toàn
                Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
            } else if (!pass.equals(confirmPass)) {
                // Mật khẩu nhập lại lần 2 phải khớp hoàn toàn với mật khẩu lần 1
                Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            } else {
                // Khóa tạm nút Đăng ký trong lúc chờ Firebase xử lý để tránh bấm nhiều lần
                btnSubmitRegister.setEnabled(false);

                // Bước 1: Kiểm tra xem địa chỉ email này đã tồn tại trên Firebase chưa
                FirestoreRepository.getInstance().checkEmailExists(email, exists -> {
                    if (exists) {
                        btnSubmitRegister.setEnabled(true);
                        Toast.makeText(this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Bước 2: Email chưa tồn tại -> tạo tài khoản chính thức trên Firebase
                    FirestoreRepository.getInstance().registerUser(name, email, pass, phone, success -> {
                        if (!success) {
                            btnSubmitRegister.setEnabled(true);
                            Toast.makeText(this, "Đăng ký Firebase thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Bước 3: Firebase tạo tài khoản thành công -> lưu bản sao xuống SQLite và quay về Đăng nhập
                        DatabaseHelper.getInstance(this).registerUser(name, email, pass, phone);
                        Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                });
            }
        });

        // Bắt sự kiện khi người dùng bấm nút QUAY LẠI ĐĂNG NHẬP -> đóng màn hình đăng ký để trở về
        btnBackToLogin.setOnClickListener(v -> finish());
    }
}