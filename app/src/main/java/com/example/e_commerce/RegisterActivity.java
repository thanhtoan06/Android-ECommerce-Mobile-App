
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

    private EditText edtFullName, edtRegisterEmail, edtRegisterPhone, edtRegisterPassword, edtConfirmPassword;
    private Button btnSubmitRegister, btnBackToLogin;
    private ImageView btnToggleRegisterPassword, btnToggleConfirmPassword;
    private ProgressBar progressBarRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_register);

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

        setupPasswordToggle(edtRegisterPassword, btnToggleRegisterPassword);
        setupPasswordToggle(edtConfirmPassword, btnToggleConfirmPassword);

        btnSubmitRegister.setOnClickListener(v -> handleRegister());

        btnBackToLogin.setOnClickListener(v -> finish());
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
            btnSubmitRegister.setEnabled(false);
            btnSubmitRegister.setText("");

            progressBarRegister.setVisibility(View.VISIBLE);
        } else {
            btnSubmitRegister.setEnabled(true);
            btnSubmitRegister.setText("HOÀN TẤT ĐĂNG KÝ");
            progressBarRegister.setVisibility(View.GONE);
        }
    }

    private void handleRegister() {

        String name = edtFullName.getText().toString().trim();
        String email = edtRegisterEmail.getText().toString().trim().toLowerCase();
        String phone = edtRegisterPhone.getText().toString().trim();
        String pass = edtRegisterPassword.getText().toString().trim();
        String confirmPass = edtConfirmPassword.getText().toString().trim();

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

        setLoadingState(true);

        FirestoreRepository.getInstance().checkEmailExists(email, exists -> {
            if (exists) {
                setLoadingState(false);
                Toast.makeText(this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG).show();
                return;
            }

            FirestoreRepository.getInstance().registerUser(name, email, pass, phone, success -> {
                if (!success) {
                    setLoadingState(false);
                    Toast.makeText(this, "Đăng ký Firebase thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
