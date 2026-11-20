
package com.example.e_commerce;

import android.content.Intent;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtFullName, edtRegisterEmail, edtRegisterPhone, edtRegisterPassword, edtConfirmPassword;
    private Button btnSubmitRegister, btnBackToLogin;
    private ImageView btnToggleRegisterPassword, btnToggleConfirmPassword;
    private ProgressBar progressBarRegister;
    private ImageView ivRegisterAvatar;
    private ActivityResultLauncher<String> avatarPicker;
    private byte[] selectedAvatarBytes;
    private boolean isCompressingAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        avatarPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            isCompressingAvatar = true;
            btnSubmitRegister.setEnabled(false);
            ImageDataUtils.compressUriAsync(this, uri, true, new ImageDataUtils.ImageCallback() {
                @Override
                public void onSuccess(byte[] imageBytes) {
                    isCompressingAvatar = false;
                    btnSubmitRegister.setEnabled(true);
                    selectedAvatarBytes = imageBytes;
                    ImageBlobLoader.bind(ivRegisterAvatar, imageBytes, "register-avatar",
                            R.drawable.ic_person);
                }

                @Override
                public void onError(Exception error) {
                    isCompressingAvatar = false;
                    btnSubmitRegister.setEnabled(true);
                    String message = error.getLocalizedMessage();
                    Toast.makeText(RegisterActivity.this,
                            message == null ? "Không thể xử lý ảnh đại diện." : message,
                            Toast.LENGTH_LONG).show();
                }
            });
        });

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
        ivRegisterAvatar = findViewById(R.id.ivRegisterAvatar);
        View btnChooseAvatar = findViewById(R.id.btnChooseAvatar);
        findViewById(R.id.btnBackToHome).setOnClickListener(v -> AuthGate.returnToHome(this));

        ivRegisterAvatar.setOnClickListener(v -> avatarPicker.launch("image/*"));
        btnChooseAvatar.setOnClickListener(v -> avatarPicker.launch("image/*"));
        setupPasswordToggle(edtRegisterPassword, btnToggleRegisterPassword);
        setupPasswordToggle(edtConfirmPassword, btnToggleConfirmPassword);

        btnSubmitRegister.setOnClickListener(v -> handleRegister());

        btnBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
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
        if (isCompressingAvatar) {
            Toast.makeText(this, "Vui lòng đợi ảnh đại diện xử lý xong.", Toast.LENGTH_SHORT).show();
            return;
        }

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
        if (!PhoneNumberRules.isValid(phone)) {
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

        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                setLoadingState(false);
                if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                    Toast.makeText(this, "Email này đã được đăng ký! Vui lòng chọn email khác.", Toast.LENGTH_LONG)
                            .show();
                } else {
                    String message = task.getException() == null
                            || task.getException().getLocalizedMessage() == null
                                    ? "Đăng ký thất bại."
                                    : task.getException().getLocalizedMessage();
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
                return;
            }

            FirestoreRepository.getInstance().registerUser(name, email, phone, selectedAvatarBytes, success -> {
                if (!success) {
                    if (auth.getCurrentUser() != null) {
                        auth.getCurrentUser().delete().addOnCompleteListener(this, deleteTask -> {
                            auth.signOut();
                            setLoadingState(false);
                            String message = deleteTask.isSuccessful()
                                    ? "Không thể lưu hồ sơ tài khoản. Vui lòng thử lại."
                                    : "Không thể lưu hồ sơ hoặc hủy tài khoản vừa tạo. Vui lòng liên hệ hỗ trợ.";
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                    } else {
                        setLoadingState(false);
                        Toast.makeText(this, "Không thể lưu hồ sơ tài khoản. Vui lòng thử lại.", Toast.LENGTH_LONG)
                                .show();
                    }
                    return;
                }

                auth.signOut();
                setLoadingState(false);
                Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
