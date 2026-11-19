
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

    private EditText edtEmail, edtPassword;
    private Button btnLogin, btnRegister;
    private ImageView btnTogglePassword;
    private TextView tvForgotPassword;
    private ProgressBar progressBarLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBarLogin = findViewById(R.id.progressBarLogin);

        setupPasswordToggle(edtPassword, btnTogglePassword);

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        btnLogin.setOnClickListener(v -> handleLogin(prefManager));

        btnRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void setupPasswordToggle(EditText editText, ImageView toggleView) {
        if (editText == null || toggleView == null)
            return;
        toggleView.setOnClickListener(v -> {
            boolean isCurrentlyPassword = editText.getTransformationMethod() instanceof PasswordTransformationMethod;
            if (isCurrentlyPassword) {

                editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                toggleView.setImageResource(R.drawable.ic_visibility);
            } else {

                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                toggleView.setImageResource(R.drawable.ic_visibility_off);
            }

            editText.setSelection(editText.getText().length());
        });
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("");

            progressBarLogin.setVisibility(View.VISIBLE);
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("ĐĂNG NHẬP");
            progressBarLogin.setVisibility(View.GONE);
        }
    }

    private void handleLogin(SharedPrefManager prefManager) {
        String account = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true);

        FirestoreRepository.getInstance().checkLogin(account, password, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {

                if (user == null) {
                    setLoadingState(false);
                    Toast.makeText(LoginActivity.this, "Tên tài khoản hoặc mật khẩu không chính xác!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(), user.getPhone(),
                        user.getAddress());
                Toast.makeText(LoginActivity.this, "Đăng nhập thành công! Xin chào " + user.getFullName(),
                        Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, ProductListActivity.class));
                finish();
            }

            @Override
            public void onError(Exception e) {

                setLoadingState(false);
                Toast.makeText(LoginActivity.this, "Không thể kết nối Firebase: " + e.getMessage(), Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

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

        setupPasswordToggle(edtForgotNewPassword, btnToggleForgotNewPassword);
        setupPasswordToggle(edtForgotConfirmPassword, btnToggleForgotConfirmPassword);

        String currentInput = edtEmail.getText().toString().trim();
        if (!currentInput.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(currentInput).matches()) {
            edtForgotEmail.setText(currentInput);
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String email = edtForgotEmail.getText().toString().trim();
            String newPassword = edtForgotNewPassword.getText().toString().trim();
            String confirmPassword = edtForgotConfirmPassword.getText().toString().trim();

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

            progressBarForgot.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);
            btnCancel.setEnabled(false);

            FirestoreRepository.getInstance().getUserByEmail(email, new FirestoreRepository.UserCallback() {
                @Override
                public void onResult(User user) {
                    if (user == null) {
                        progressBarForgot.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        btnCancel.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Không tìm thấy tài khoản với email này!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirestoreRepository.getInstance().updateUser(email,
                            Collections.singletonMap("password", newPassword), success -> {
                                progressBarForgot.setVisibility(View.GONE);
                                btnSubmit.setEnabled(true);
                                btnCancel.setEnabled(true);

                                if (success) {

                                    edtEmail.setText(email);
                                    edtPassword.setText(newPassword);

                                    Toast.makeText(LoginActivity.this,
                                            "Đặt lại mật khẩu thành công! Vui lòng bấm Đăng nhập.", Toast.LENGTH_LONG)
                                            .show();
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(LoginActivity.this,
                                            "Đặt lại mật khẩu thất bại, vui lòng thử lại sau!", Toast.LENGTH_SHORT)
                                            .show();
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
