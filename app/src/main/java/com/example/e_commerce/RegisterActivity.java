/**
 * Màn hình Đăng ký tài khoản mới (Register) cho người dùng.
 */
package com.example.e_commerce;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtFullName, edtRegisterEmail, edtRegisterPassword, edtConfirmPassword; // Các ô nhập thông tin đăng ký
    private TextView tvRegisterAvatar; // Biểu tượng ảnh đại diện được chọn
    private Button btnSubmitRegister, btnBackToLogin; // Nút xác nhận đăng ký và quay lại đăng nhập

    // Danh sách avatar mẫu để người dùng lựa chọn
    private final String[] avatarList = {"👨‍💼", "👩‍💼", "👨‍💻", "👩‍💻", "🧑‍🎨", "🧑‍🚀", "🦁", "🐱", "👤"};
    private final String[] avatarLabels = {
            "👨‍💼 Doanh nhân",
            "👩‍💼 Nữ doanh nhân",
            "👨‍💻 Nam lập trình",
            "👩‍💻 Nữ lập trình",
            "🧑‍🎨 Họa sĩ",
            "🧑‍🚀 Phi hành gia",
            "🦁 Sư tử",
            "🐱 Mèo con",
            "👤 Mặc định"
    };
    private String selectedAvatar = "👨‍💼"; // Avatar mặc định

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Ánh xạ các thành phần giao diện
        tvRegisterAvatar = findViewById(R.id.tvRegisterAvatar);
        edtFullName = findViewById(R.id.edtFullName);
        edtRegisterEmail = findViewById(R.id.edtRegisterEmail);
        edtRegisterPassword = findViewById(R.id.edtRegisterPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        // Sự kiện click chọn Avatar
        tvRegisterAvatar.setOnClickListener(v -> showAvatarPickerDialog());

        // Xử lý sự kiện bấm nút Xác nhận Đăng ký
        btnSubmitRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = edtFullName.getText().toString().trim();
                String email = edtRegisterEmail.getText().toString().trim();
                String pass = edtRegisterPassword.getText().toString().trim();
                String confirmPass = edtConfirmPassword.getText().toString().trim();

                // Kiểm tra các trường dữ liệu bắt buộc
                if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(RegisterActivity.this, "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show();
                } else if (pass.length() < 6) {
                    // Yêu cầu mật khẩu tối thiểu 6 ký tự
                    Toast.makeText(RegisterActivity.this, "Mật khẩu phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
                } else if (!pass.equals(confirmPass)) {
                    // Kiểm tra khớp mật khẩu xác nhận
                    Toast.makeText(RegisterActivity.this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
                } else {
                    // Thực hiện lưu tài khoản thật kèm avatar vào cơ sở dữ liệu SQLite
                    DatabaseHelper dbHelper = DatabaseHelper.getInstance(RegisterActivity.this);
                    if (dbHelper.checkEmailExists(email)) {
                        Toast.makeText(RegisterActivity.this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG).show();
                    } else {
                        boolean isRegistered = dbHelper.registerUser(name, email, pass, selectedAvatar);
                        if (isRegistered) {
                            Toast.makeText(RegisterActivity.this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
                            finish(); // Quay lại màn hình đăng nhập
                        } else {
                            Toast.makeText(RegisterActivity.this, "Đăng ký thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });

        // Xử lý sự kiện bấm nút quay về màn hình đăng nhập
        btnBackToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * Hộp thoại cho phép người dùng chọn Avatar
     */
    private void showAvatarPickerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Chọn ảnh đại diện của bạn")
                .setItems(avatarLabels, (dialog, which) -> {
                    selectedAvatar = avatarList[which];
                    tvRegisterAvatar.setText(selectedAvatar);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }
}
