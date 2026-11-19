
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private SharedPrefManager prefManager;
    private FirestoreRepository repository;
    private TextView tvAccountName, tvDetailUsername, tvDetailPhone, tvDetailAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        repository = FirestoreRepository.getInstance();
        prefManager = SharedPrefManager.getInstance(this);

        TextView tvAvatar = findViewById(R.id.tvAvatar);
        tvAccountName = findViewById(R.id.tvAccountName);
        TextView tvAccountEmail = findViewById(R.id.tvAccountEmail);
        tvDetailUsername = findViewById(R.id.tvDetailUsername);
        TextView tvDetailEmail = findViewById(R.id.tvDetailEmail);
        tvDetailPhone = findViewById(R.id.tvDetailPhone);
        tvDetailAddress = findViewById(R.id.tvDetailAddress);
        Button btnLogout = findViewById(R.id.btnLogout);

        View layoutUsernameRow = findViewById(R.id.layoutUsernameRow);
        View layoutPhoneRow = findViewById(R.id.layoutPhoneRow);
        View layoutAddressRow = findViewById(R.id.layoutAddressRow);
        View btnChangePassword = findViewById(R.id.btnChangePassword);
        View btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
        View btnBackSettings = findViewById(R.id.btnBackSettings);

        String username = prefManager.getUsername();
        String email = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        String phone = prefManager.getPhone();
        String address = prefManager.getAddress();

        tvAvatar.setText(avatar);
        String displayName = !username.isEmpty() ? username : (!email.isEmpty() ? email : "Người dùng");
        tvAccountName.setText(displayName);
        tvAccountEmail.setText(!email.isEmpty() ? email : "Chưa cập nhật email");
        tvDetailUsername.setText(!username.isEmpty() ? username : "Chưa thiết lập");
        tvDetailEmail.setText(!email.isEmpty() ? email : "Chưa thiết lập");
        tvDetailPhone.setText(!phone.isEmpty() ? phone : "Chưa thiết lập");
        tvDetailAddress.setText(!address.isEmpty() ? address : "Chưa thiết lập");

        repository.getUserByEmail(email, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {

                if (user == null)
                    return;

                prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(), user.getPhone(),
                        user.getAddress());

                tvAvatar.setText(user.getAvatar());
                tvAccountName.setText(!user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
                tvDetailUsername.setText(!user.getFullName().isEmpty() ? user.getFullName() : "Chưa thiết lập");
                tvDetailPhone.setText(!user.getPhone().isEmpty() ? user.getPhone() : "Chưa thiết lập");
                tvDetailAddress.setText(!user.getAddress().isEmpty() ? user.getAddress() : "Chưa thiết lập");
            }

            @Override
            public void onError(Exception e) {

            }
        });

        if (layoutUsernameRow != null)
            layoutUsernameRow.setOnClickListener(v -> showChangeUsernameDialog());
        if (layoutPhoneRow != null)
            layoutPhoneRow.setOnClickListener(v -> showChangePhoneDialog());
        if (layoutAddressRow != null)
            layoutAddressRow.setOnClickListener(v -> showChangeAddressDialog());

        if (btnChangePassword != null)
            btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        if (btnNotificationSettings != null)
            btnNotificationSettings.setOnClickListener(v -> openNotificationSettings());
        if (btnBackSettings != null)
            btnBackSettings.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản không?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        PushNotificationManager.removeCurrentToken(this, prefManager.getEmail(), removed -> {
                            FirebaseAuth.getInstance().signOut();
                            prefManager.logout();
                            String message = removed
                                    ? "Đã đăng xuất thành công!"
                                    : "Đã đăng xuất nhưng chưa gỡ được token thông báo đẩy.";
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            AuthGate.returnToHome(this);
                        });
                    }).setNegativeButton("Hủy", null).show();
        });

        setupBottomMenu();
    }

    private void showChangeUsernameDialog() {
        String currentEmail = prefManager.getEmail();
        String currentUsername = prefManager.getUsername();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_username, null);
        EditText edtNewUsername = dialogView.findViewById(R.id.edtNewUsername);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelUsername);
        Button btnSave = dialogView.findViewById(R.id.btnSaveUsername);

        if (!currentUsername.isEmpty()) {
            edtNewUsername.setText(currentUsername);
            edtNewUsername.setSelection(currentUsername.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newUsername = edtNewUsername.getText().toString().trim();

            if (newUsername.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên đăng nhập mới!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newUsername.equalsIgnoreCase(currentUsername.trim())) {
                Toast.makeText(this, "Tên đăng nhập mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            repository.checkUsernameExists(newUsername, currentEmail, new FirestoreRepository.UsernameCallback() {
                @Override
                public void onResult(boolean exists) {
                    if (exists) {
                        Toast.makeText(SettingsActivity.this,
                                "Tên đăng nhập này đã được sử dụng bởi tài khoản khác!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Map<String, Object> values = new HashMap<>();
                    values.put("fullName", newUsername);
                    repository.updateUser(currentEmail, values, ok -> {
                        if (!ok) {
                            Toast.makeText(SettingsActivity.this, "Đổi tên đăng nhập thất bại!", Toast.LENGTH_SHORT)
                                    .show();
                            return;
                        }

                        prefManager.updateUsername(newUsername);
                        tvAccountName.setText(newUsername);
                        tvDetailUsername.setText(newUsername);
                        dialog.dismiss();
                        Toast.makeText(SettingsActivity.this, "Đổi tên đăng nhập thành công!", Toast.LENGTH_SHORT)
                                .show();
                    });
                }

                @Override
                public void onError(Exception error) {
                    Toast.makeText(SettingsActivity.this, "Không thể kiểm tra tên đăng nhập. Vui lòng thử lại.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void showChangePhoneDialog() {
        String currentEmail = prefManager.getEmail();
        String currentPhone = prefManager.getPhone();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_phone, null);
        EditText edtNewPhone = dialogView.findViewById(R.id.edtNewPhone);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPhone);
        Button btnSave = dialogView.findViewById(R.id.btnSavePhone);

        if (!currentPhone.isEmpty()) {
            edtNewPhone.setText(currentPhone);
            edtNewPhone.setSelection(currentPhone.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newPhone = edtNewPhone.getText().toString().trim();

            if (newPhone.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!PhoneNumberRules.isValid(newPhone)) {
                Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPhone.equals(currentPhone.trim())) {
                Toast.makeText(this, "Số điện thoại mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            repository.updateUser(currentEmail, Collections.singletonMap("phone", newPhone), ok -> {
                if (!ok) {
                    Toast.makeText(this, "Cập nhật số điện thoại thất bại!", Toast.LENGTH_SHORT).show();
                    return;
                }

                prefManager.updatePhone(newPhone);
                tvDetailPhone.setText(newPhone);
                dialog.dismiss();
                Toast.makeText(this, "Cập nhật số điện thoại thành công!", Toast.LENGTH_SHORT).show();
            });
        });

        dialog.show();
    }

    private void showChangeAddressDialog() {
        String currentEmail = prefManager.getEmail();
        String currentAddress = prefManager.getAddress();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_address, null);
        EditText edtNewAddress = dialogView.findViewById(R.id.edtNewAddress);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAddress);
        Button btnSave = dialogView.findViewById(R.id.btnSaveAddress);

        if (!currentAddress.isEmpty()) {
            edtNewAddress.setText(currentAddress);
            edtNewAddress.setSelection(currentAddress.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newAddress = edtNewAddress.getText().toString().trim();

            if (newAddress.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập địa chỉ nhận hàng!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newAddress.equalsIgnoreCase(currentAddress.trim())) {
                Toast.makeText(this, "Địa chỉ mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            repository.updateUser(currentEmail, Collections.singletonMap("address", newAddress), ok -> {
                if (!ok) {
                    Toast.makeText(this, "Cập nhật địa chỉ thất bại!", Toast.LENGTH_SHORT).show();
                    return;
                }

                prefManager.updateAddress(newAddress);
                tvDetailAddress.setText(newAddress);
                dialog.dismiss();
                Toast.makeText(this, "Cập nhật địa chỉ thành công!", Toast.LENGTH_SHORT).show();
            });
        });

        dialog.show();
    }

    private void showChangePasswordDialog() {
        String currentEmail = prefManager.getEmail();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText edtCurrentPassword = dialogView.findViewById(R.id.edtCurrentPassword);
        EditText edtNewPassword = dialogView.findViewById(R.id.edtNewPassword);
        EditText edtConfirmNewPassword = dialogView.findViewById(R.id.edtConfirmNewPassword);
        ImageView btnToggleCurrentPassword = dialogView.findViewById(R.id.btnToggleCurrentPassword);
        ImageView btnToggleNewPassword = dialogView.findViewById(R.id.btnToggleNewPassword);
        ImageView btnToggleConfirmNewPassword = dialogView.findViewById(R.id.btnToggleConfirmNewPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPassword);
        Button btnSave = dialogView.findViewById(R.id.btnSavePassword);

        setupPasswordToggle(edtCurrentPassword, btnToggleCurrentPassword);
        setupPasswordToggle(edtNewPassword, btnToggleNewPassword);
        setupPasswordToggle(edtConfirmNewPassword, btnToggleConfirmNewPassword);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String currentPass = edtCurrentPassword.getText().toString().trim();
            String newPass = edtNewPassword.getText().toString().trim();
            String confirmPass = edtConfirmNewPassword.getText().toString().trim();

            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPass.length() < 6) {
                Toast.makeText(this, "Mật khẩu mới phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPass.equals(currentPass)) {
                Toast.makeText(this, "Mật khẩu mới không được trùng với mật khẩu hiện tại!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPass.equals(confirmPass)) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(currentEmail)) {
                Toast.makeText(this, "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);
            user.reauthenticate(credential).addOnCompleteListener(this, authTask -> {
                if (!authTask.isSuccessful()) {
                    Toast.makeText(this, "Mật khẩu hiện tại không chính xác!", Toast.LENGTH_SHORT).show();
                    return;
                }

                user.updatePassword(newPass).addOnCompleteListener(this, updateTask -> {
                    if (!updateTask.isSuccessful()) {
                        String message = updateTask.getException() == null ? "Đổi mật khẩu thất bại."
                                : updateTask.getException().getLocalizedMessage();
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        return;
                    }

                    dialog.dismiss();
                    Toast.makeText(this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                });
            });
        });

        dialog.show();
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

    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);

        if (ivMenuHome != null)
            ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuCart != null)
            ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuAccount != null)
            ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));

        TextView tvMenuAccount = findViewById(R.id.tvMenuAccount);
        if (tvMenuAccount != null) {
            tvMenuAccount.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvMenuAccount.setTypeface(null, Typeface.BOLD);
        }

        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        if (tvMenuHome != null) {
            tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvMenuHome.setTypeface(null, Typeface.NORMAL);
        }

        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        menuCart.setOnClickListener(v -> {
            startActivity(new Intent(this, CartActivity.class));
            finish();
        });

        menuAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountActivity.class));
            finish();
        });

        findViewById(R.id.menuNotifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
        }
        startActivity(intent);
    }
}
