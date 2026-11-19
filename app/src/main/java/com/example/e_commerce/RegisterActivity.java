// Màn hình Đăng ký: kiểm tra hợp lệ dữ liệu, xác thực trùng email trên Firebase, tạo tài khoản chính và lưu bản sao SQLite.
package com.example.e_commerce;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    // Khai báo 5 ô nhập liệu, 2 nút bấm, 2 nút ẩn/hiện mật khẩu và vòng xoay tải
    private EditText edtFullName, edtRegisterEmail, edtRegisterPhone, edtRegisterPassword, edtConfirmPassword;
    private Button btnSubmitRegister, btnBackToLogin;
    private ImageView btnToggleRegisterPassword, btnToggleConfirmPassword;
    private ProgressBar progressBarRegister;

    // Hàm khởi tạo màn hình Đăng ký: nạp giao diện và gắn sự kiện xử lý cho các nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp file giao diện activity_register.xml lên màn hình
        setContentView(R.layout.activity_register);

        // Ánh xạ các ô nhập chữ, nút bấm, nút con mắt và vòng xoay tải từ file XML
        edtFullName = findViewById(R.id.edtFullName);
        edtRegisterEmail = findViewById(R.id.edtRegisterEmail);
        edtRegisterPhone = findViewById(R.id.edtRegisterPhone);
        edtRegisterPassword = findViewById(R.id.edtRegisterPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        btnToggleRegisterPassword = findViewById(R.id.btnToggleRegisterPassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);
        progressBarRegister = findViewById(R.id.progressBarRegister);

        // Cài đặt nút con mắt ẩn/hiện cho cả 2 ô mật khẩu
        setupPasswordToggle(edtRegisterPassword, btnToggleRegisterPassword);
        setupPasswordToggle(edtConfirmPassword, btnToggleConfirmPassword);

        // Bắt sự kiện khi người dùng bấm nút HOÀN TẤT ĐĂNG KÝ
        btnSubmitRegister.setOnClickListener(v -> handleRegister());

        // Bắt sự kiện khi người dùng bấm nút QUAY LẠI ĐĂNG NHẬP -> đóng màn hình đăng ký để trở về
        btnBackToLogin.setOnClickListener(v -> finish());
    }

    // Hàm tiện ích: Bật/tắt ẩn hiện mật khẩu và tự động đổi icon con mắt
    private void setupPasswordToggle(EditText editText, ImageView toggleView) {
        if (editText == null || toggleView == null) return;
        toggleView.setOnClickListener(v -> {
            boolean isCurrentlyPassword = editText.getTransformationMethod() instanceof PasswordTransformationMethod;
            if (isCurrentlyPassword) {
                // Đang ẩn -> chuyển sang hiện mật khẩu
                editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                toggleView.setImageResource(R.drawable.ic_visibility);
            } else {
                // Đang hiện -> chuyển sang ẩn mật khẩu
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                toggleView.setImageResource(R.drawable.ic_visibility_off);
            }
            // Luôn giữ con trỏ chuột ở cuối văn bản sau khi chuyển đổi
            editText.setSelection(editText.getText().length());
        });
    }

    // Bật/tắt trạng thái tải: hiện/ẩn vòng xoay và khóa/mở nút đăng ký
    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            btnSubmitRegister.setEnabled(false);
            btnSubmitRegister.setText(""); // Ẩn chữ để hiển thị vòng xoay
            progressBarRegister.setVisibility(View.VISIBLE);
        } else {
            btnSubmitRegister.setEnabled(true);
            btnSubmitRegister.setText("HOÀN TẤT ĐĂNG KÝ");
            progressBarRegister.setVisibility(View.GONE);
        }
    }

    // Xử lý kiểm tra dữ liệu và gửi yêu cầu tạo tài khoản mới lên Firebase
    private void handleRegister() {
        // Lấy nội dung người dùng gõ trong 5 ô nhập và xóa khoảng trắng thừa hai đầu
        String name = edtFullName.getText().toString().trim();
        String email = edtRegisterEmail.getText().toString().trim().toLowerCase();
        String phone = edtRegisterPhone.getText().toString().trim();
        String pass = edtRegisterPassword.getText().toString().trim();
        String confirmPass = edtConfirmPassword.getText().toString().trim();

        // Kiểm tra điều kiện hợp lệ của từng trường thông tin trước khi gửi lên Firebase
        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Địa chỉ email không hợp lệ!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phone.length() < 9 || phone.length() > 11) {
            Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pass.length() < 6) {
            Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pass.equals(confirmPass)) {
            Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bật vòng xoay tải và vô hiệu hóa nút bấm
        setLoadingState(true);

        // Bước 1: Kiểm tra xem địa chỉ email này đã tồn tại trên Firebase chưa
        FirestoreRepository.getInstance().checkEmailExists(email, exists -> {
            if (exists) {
                setLoadingState(false);
                Toast.makeText(this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG).show();
                return;
            }

            // Bước 2: Email chưa tồn tại -> tạo tài khoản chính thức trên Firebase
            FirestoreRepository.getInstance().registerUser(name, email, pass, phone, success -> {
                if (!success) {
                    setLoadingState(false);
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
}
