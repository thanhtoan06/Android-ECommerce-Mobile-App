// Màn hình Đăng nhập: hỗ trợ ẩn/hiện mật khẩu, quên mật khẩu, vòng xoay tải và xác thực tài khoản qua Firebase/SQLite.
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Collections;

public class LoginActivity extends AppCompatActivity {

    // Khai báo các view giao diện
    private EditText edtEmail, edtPassword;
    private Button btnLogin, btnRegister;
    private ImageView btnTogglePassword;
    private TextView tvForgotPassword;
    private ProgressBar progressBarLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra phiên đăng nhập cũ trong SharedPreferences: nếu đã đăng nhập thì vào thẳng trang chính
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
            finish();
            return;
        }

        // Nạp file giao diện activity_login.xml
        setContentView(R.layout.activity_login);

        // Ánh xạ các thành phần giao diện
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBarLogin = findViewById(R.id.progressBarLogin);

        // Cài đặt nút con mắt ẩn/hiện cho ô mật khẩu đăng nhập
        setupPasswordToggle(edtPassword, btnTogglePassword);

        // Thiết lập sự kiện bấm Quên mật khẩu
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        // Thiết lập sự kiện khi bấm nút ĐĂNG NHẬP
        btnLogin.setOnClickListener(v -> handleLogin(prefManager));

        // Thiết lập sự kiện chuyển sang màn hình Đăng ký
        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );
    }

    // Hàm tiện ích dùng chung: Bật/tắt ẩn hiện mật khẩu và tự động đổi icon con mắt
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

    // Chức năng 2: Bật/tắt trạng thái tải (vòng xoay xoay tròn và khóa nút)
    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            btnLogin.setEnabled(false);
            btnLogin.setText(""); // Xóa tạm chữ trên nút để hiện vòng xoay
            progressBarLogin.setVisibility(View.VISIBLE);
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("ĐĂNG NHẬP");
            progressBarLogin.setVisibility(View.GONE);
        }
    }

    // Xử lý đăng nhập tài khoản qua Firebase và đồng bộ vào SQLite
    private void handleLogin(SharedPrefManager prefManager) {
        String account = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        // Kiểm tra không để trống thông tin
        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bật vòng xoay tải và vô hiệu hóa nút đăng nhập
        setLoadingState(true);

        // Bước 1: Gửi yêu cầu xác thực tài khoản lên Firebase Firestore
        FirestoreRepository.getInstance().checkLogin(account, password, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {
                // Nếu không tìm thấy hoặc mật khẩu sai -> tắt xoay tải và thông báo lỗi
                if (user == null) {
                    setLoadingState(false);
                    Toast.makeText(LoginActivity.this, "Tên tài khoản hoặc mật khẩu không chính xác!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Bước 2: Xác thực thành công -> đồng bộ dữ liệu vào SQLite cục bộ
                DatabaseHelper databaseHelper = DatabaseHelper.getInstance(LoginActivity.this);
                databaseHelper.registerUser(user.getFullName(), user.getEmail(), user.getPassword(), user.getPhone());
                databaseHelper.updateUsername(user.getEmail(), user.getFullName());
                databaseHelper.updatePhone(user.getEmail(), user.getPhone());
                databaseHelper.updateAddress(user.getEmail(), user.getAddress());
                databaseHelper.updatePassword(user.getEmail(), user.getPassword());

                // Bước 3: Lưu phiên đăng nhập vào SharedPreferences và mở trang Sản phẩm
                prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(), user.getPhone(), user.getAddress());
                Toast.makeText(LoginActivity.this, "Đăng nhập thành công! Xin chào " + user.getFullName(), Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
                finish();
            }

            @Override
            public void onError(Exception e) {
                // Tắt vòng xoay tải và báo lỗi kết nối mạng
                setLoadingState(false);
                Toast.makeText(LoginActivity.this, "Không thể kết nối Firebase: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Chức năng 3: Hiển thị hộp thoại khôi phục mật khẩu (Quên mật khẩu)
    private void showForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        EditText edtForgotEmail = dialogView.findViewById(R.id.edtForgotEmail);
        EditText edtForgotNewPassword = dialogView.findViewById(R.id.edtForgotNewPassword);
        EditText edtForgotConfirmPassword = dialogView.findViewById(R.id.edtForgotConfirmPassword);
        ImageView btnToggleForgotNewPassword = dialogView.findViewById(R.id.btnToggleForgotNewPassword);
        ImageView btnToggleForgotConfirmPassword = dialogView.findViewById(R.id.btnToggleForgotConfirmPassword);
        ProgressBar progressBarForgot = dialogView.findViewById(R.id.progressBarForgot);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelForgot);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitForgot);

        // Cài đặt nút con mắt ẩn/hiện cho 2 ô mật khẩu mới
        setupPasswordToggle(edtForgotNewPassword, btnToggleForgotNewPassword);
        setupPasswordToggle(edtForgotConfirmPassword, btnToggleForgotConfirmPassword);

        // Tự động điền email nếu người dùng đã nhập ở ô đăng nhập bên ngoài
        String currentInput = edtEmail.getText().toString().trim();
        if (!currentInput.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(currentInput).matches()) {
            edtForgotEmail.setText(currentInput);
        }

        // Tạo dialog dạng khung bo góc trong suốt
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Bấm Hủy -> đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Bấm Đặt lại mật khẩu -> kiểm tra và cập nhật lên Firebase + SQLite
        btnSubmit.setOnClickListener(v -> {
            String email = edtForgotEmail.getText().toString().trim();
            String newPassword = edtForgotNewPassword.getText().toString().trim();
            String confirmPassword = edtForgotConfirmPassword.getText().toString().trim();

            // Kiểm tra tính hợp lệ của dữ liệu nhập
            if (email.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đầy đủ các thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Địa chỉ email không đúng định dạng!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPassword.length() < 6) {
                Toast.makeText(this, "Mật khẩu mới phải có tối thiểu 6 ký tự!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Hiện vòng xoay tải trong dialog và vô hiệu hóa các nút
            progressBarForgot.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);
            btnCancel.setEnabled(false);

            // Kiểm tra xem email có tồn tại trên hệ thống Firebase không
            FirestoreRepository.getInstance().getUserByEmail(email, new FirestoreRepository.UserCallback() {
                @Override
                public void onResult(User user) {
                    if (user == null) {
                        progressBarForgot.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        btnCancel.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Không tìm thấy tài khoản với email này!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Cập nhật mật khẩu mới lên Firebase
                    FirestoreRepository.getInstance().updateUser(email, Collections.singletonMap("password", newPassword), success -> {
                        progressBarForgot.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        btnCancel.setEnabled(true);

                        if (success) {
                            // Cập nhật mật khẩu mới xuống SQLite cục bộ
                            DatabaseHelper.getInstance(LoginActivity.this).updatePassword(email, newPassword);

                            // Điền sẵn thông tin mới vào màn hình đăng nhập
                            edtEmail.setText(email);
                            edtPassword.setText(newPassword);

                            Toast.makeText(LoginActivity.this, "Đặt lại mật khẩu thành công! Vui lòng bấm Đăng nhập.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(LoginActivity.this, "Đặt lại mật khẩu thất bại, vui lòng thử lại sau!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    progressBarForgot.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    btnCancel.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Lỗi kết nối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }
}
