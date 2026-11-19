// Màn hình Tài khoản: xem/sửa thông tin cá nhân, đổi mật khẩu, xem đơn mua và đăng xuất (Firebase xử lý chính, SQLite lưu bản sao).
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AccountActivity extends AppCompatActivity {

    // Khai báo bộ nhớ phiên đăng nhập, kho chính Firebase, kho bản sao SQLite và các ô hiển thị
    private SharedPrefManager prefManager;
    private FirestoreRepository repository;
    private DatabaseHelper databaseHelper;
    private TextView tvAccountName, tvDetailUsername, tvDetailPhone, tvDetailAddress;

    // Hàm khởi tạo màn hình: hiển thị thông tin người dùng và gắn sự kiện cho các nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp giao diện XML của màn hình Tài khoản
        setContentView(R.layout.activity_account);

        // Kết nối Firebase (kho chính), SQLite (kho bản sao) và SharedPreferences (phiên đăng nhập)
        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);
        prefManager = SharedPrefManager.getInstance(this);

        // Ánh xạ các thành phần hiển thị thông tin và nút thao tác từ giao diện XML
        TextView tvAvatar = findViewById(R.id.tvAvatar);
        tvAccountName = findViewById(R.id.tvAccountName);
        TextView tvAccountEmail = findViewById(R.id.tvAccountEmail);
        tvDetailUsername = findViewById(R.id.tvDetailUsername);
        TextView tvDetailEmail = findViewById(R.id.tvDetailEmail);
        tvDetailPhone = findViewById(R.id.tvDetailPhone);
        tvDetailAddress = findViewById(R.id.tvDetailAddress);
        Button btnLogout = findViewById(R.id.btnLogout);

        // Ánh xạ các dòng thông tin cá nhân và nút bấm chức năng
        View layoutUsernameRow = findViewById(R.id.layoutUsernameRow);
        View layoutPhoneRow = findViewById(R.id.layoutPhoneRow);
        View layoutAddressRow = findViewById(R.id.layoutAddressRow);
        View btnChangePassword = findViewById(R.id.btnChangePassword);

        // Lấy thông tin cơ bản của tài khoản đang đăng nhập từ SharedPreferences
        String username = prefManager.getUsername();
        String email = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        // Hiển thị tạm dữ liệu từ bản sao SQLite/SharedPreferences trong lúc chờ Firebase tải dữ liệu chính
        User localUser = databaseHelper.getUserByEmail(email);
        if (localUser != null) {
            username = localUser.getFullName();
            avatar = localUser.getAvatar();
        }
        String phone = localUser != null ? localUser.getPhone() : prefManager.getPhone();
        String address = localUser != null ? localUser.getAddress() : prefManager.getAddress();

        // Đổ dữ liệu tạm thời lên giao diện (nếu trường nào trống thì ghi "Chưa thiết lập")
        tvAvatar.setText(avatar);
        String displayName = !username.isEmpty() ? username : (!email.isEmpty() ? email : "Người dùng");
        tvAccountName.setText(displayName);
        tvAccountEmail.setText(!email.isEmpty() ? email : "Chưa cập nhật email");
        tvDetailUsername.setText(!username.isEmpty() ? username : "Chưa thiết lập");
        tvDetailEmail.setText(!email.isEmpty() ? email : "Chưa thiết lập");
        tvDetailPhone.setText(!phone.isEmpty() ? phone : "Chưa thiết lập");
        tvDetailAddress.setText(!address.isEmpty() ? address : "Chưa thiết lập");

        // Tải dữ liệu chính thức từ Firebase -> cập nhật bản sao SQLite, phiên tạm và giao diện
        repository.getUserByEmail(email, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {
                // Nếu không tìm thấy tài khoản trên Firebase thì dừng lại
                if (user == null) return;

                // Đồng bộ bản sao mới nhất xuống SQLite và SharedPreferences
                databaseHelper.registerUser(user.getFullName(), user.getEmail(), user.getPassword(), user.getPhone());
                databaseHelper.updateUsername(user.getEmail(), user.getFullName());
                databaseHelper.updatePhone(user.getEmail(), user.getPhone());
                databaseHelper.updateAddress(user.getEmail(), user.getAddress());
                prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(),
                        user.getPhone(), user.getAddress());

                // Cập nhật giao diện theo dữ liệu từ Firebase (giữ "Chưa thiết lập" nếu trống)
                tvAvatar.setText(user.getAvatar());
                tvAccountName.setText(!user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
                tvDetailUsername.setText(!user.getFullName().isEmpty() ? user.getFullName() : "Chưa thiết lập");
                tvDetailPhone.setText(!user.getPhone().isEmpty() ? user.getPhone() : "Chưa thiết lập");
                tvDetailAddress.setText(!user.getAddress().isEmpty() ? user.getAddress() : "Chưa thiết lập");
            }

            @Override
            public void onError(Exception e) {
                // Nếu lỗi mạng thì giữ nguyên dữ liệu bản sao đã hiển thị
            }
        });

        // Gắn sự kiện mở các hộp thoại chỉnh sửa trực tiếp khi chạm vào dòng tương ứng
        if (layoutUsernameRow != null) layoutUsernameRow.setOnClickListener(v -> showChangeUsernameDialog());
        if (layoutPhoneRow != null) layoutPhoneRow.setOnClickListener(v -> showChangePhoneDialog());
        if (layoutAddressRow != null) layoutAddressRow.setOnClickListener(v -> showChangeAddressDialog());

        // Gắn sự kiện mở hộp thoại Đổi mật khẩu
        if (btnChangePassword != null) btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // Bấm nút Đăng xuất -> hỏi xác nhận -> xóa phiên đăng nhập -> quay về màn hình Đăng nhập
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản không?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        // Xóa dữ liệu phiên đăng nhập trong SharedPreferences
                        prefManager.logout();
                        Toast.makeText(this, "Đã đăng xuất thành công!", Toast.LENGTH_SHORT).show();

                        // Mở màn hình Đăng nhập và xóa hết các màn hình cũ
                        Intent intent = new Intent(AccountActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Cài đặt thanh menu điều hướng dưới đáy màn hình
        setupBottomMenu();
    }

    // Hộp thoại đổi tên: Firebase kiểm tra trùng và cập nhật trước -> thành công mới lưu bản sao vào SQLite
    private void showChangeUsernameDialog() {
        String currentEmail = prefManager.getEmail();
        String currentUsername = prefManager.getUsername();

        // Nạp giao diện hộp thoại đổi tên và ánh xạ các thành phần
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_username, null);
        EditText edtNewUsername = dialogView.findViewById(R.id.edtNewUsername);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelUsername);
        Button btnSave = dialogView.findViewById(R.id.btnSaveUsername);

        // Điền sẵn tên cũ vào ô nhập và đặt con trỏ ở cuối dòng
        if (!currentUsername.isEmpty()) {
            edtNewUsername.setText(currentUsername);
            edtNewUsername.setSelection(currentUsername.length());
        }

        // Tạo hộp thoại và làm trong suốt nền mặc định để hiện đẹp bo góc
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Bấm Hủy thì đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Bấm Lưu: kiểm tra tên hợp lệ -> cập nhật lên Firebase trước -> lưu bản sao vào SQLite sau
        btnSave.setOnClickListener(v -> {
            String newUsername = edtNewUsername.getText().toString().trim();

            // Kiểm tra không được để trống và phải khác tên cũ
            if (newUsername.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên đăng nhập mới!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newUsername.equalsIgnoreCase(currentUsername.trim())) {
                Toast.makeText(this, "Tên đăng nhập mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Bước 1: Kiểm tra tên trùng trên Firebase
            repository.checkUsernameExists(newUsername, currentEmail, exists -> {
                if (exists) {
                    Toast.makeText(this, "Tên đăng nhập này đã được sử dụng bởi tài khoản khác!", Toast.LENGTH_LONG).show();
                    return;
                }
                // Bước 2: Cập nhật tên mới lên Firebase
                Map<String, Object> values = new HashMap<>();
                values.put("fullName", newUsername);
                repository.updateUser(currentEmail, values, ok -> {
                    if (!ok) {
                        Toast.makeText(this, "Đổi tên đăng nhập thất bại!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Bước 3: Firebase thành công mới lưu bản sao xuống SQLite và cập nhật màn hình
                    databaseHelper.updateUsername(currentEmail, newUsername);
                    prefManager.updateUsername(newUsername);
                    tvAccountName.setText(newUsername);
                    tvDetailUsername.setText(newUsername);
                    dialog.dismiss();
                    Toast.makeText(this, "Đổi tên đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                });
            });
        });

        // Hiển thị hộp thoại
        dialog.show();
    }

    // Hộp thoại đổi SĐT: cập nhật lên Firebase trước -> thành công mới lưu bản sao vào SQLite
    private void showChangePhoneDialog() {
        String currentEmail = prefManager.getEmail();
        String currentPhone = prefManager.getPhone();

        // Nạp giao diện hộp thoại đổi SĐT và ánh xạ các thành phần
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_phone, null);
        EditText edtNewPhone = dialogView.findViewById(R.id.edtNewPhone);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPhone);
        Button btnSave = dialogView.findViewById(R.id.btnSavePhone);

        // Điền sẵn SĐT cũ vào ô nhập nếu đã có
        if (!currentPhone.isEmpty()) {
            edtNewPhone.setText(currentPhone);
            edtNewPhone.setSelection(currentPhone.length());
        }

        // Tạo hộp thoại và làm trong suốt nền mặc định
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Bấm Hủy thì đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Bấm Lưu: kiểm tra SĐT hợp lệ -> cập nhật lên Firebase trước -> lưu bản sao vào SQLite sau
        btnSave.setOnClickListener(v -> {
            String newPhone = edtNewPhone.getText().toString().trim();

            // Kiểm tra không trống, đủ 9-11 số và khác SĐT cũ
            if (newPhone.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPhone.length() < 9 || newPhone.length() > 11) {
                Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPhone.equals(currentPhone.trim())) {
                Toast.makeText(this, "Số điện thoại mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Bước 1: Cập nhật SĐT mới lên Firebase
            repository.updateUser(currentEmail, Collections.singletonMap("phone", newPhone), ok -> {
                if (!ok) {
                    Toast.makeText(this, "Cập nhật số điện thoại thất bại!", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Bước 2: Firebase thành công mới lưu bản sao xuống SQLite và cập nhật màn hình
                databaseHelper.updatePhone(currentEmail, newPhone);
                prefManager.updatePhone(newPhone);
                tvDetailPhone.setText(newPhone);
                dialog.dismiss();
                Toast.makeText(this, "Cập nhật số điện thoại thành công!", Toast.LENGTH_SHORT).show();
            });
        });

        // Hiển thị hộp thoại
        dialog.show();
    }

    // Hộp thoại đổi địa chỉ: cập nhật lên Firebase trước -> thành công mới lưu bản sao vào SQLite
    private void showChangeAddressDialog() {
        String currentEmail = prefManager.getEmail();
        String currentAddress = prefManager.getAddress();

        // Nạp giao diện hộp thoại đổi địa chỉ và ánh xạ các thành phần
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_address, null);
        EditText edtNewAddress = dialogView.findViewById(R.id.edtNewAddress);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAddress);
        Button btnSave = dialogView.findViewById(R.id.btnSaveAddress);

        // Điền sẵn địa chỉ cũ vào ô nhập nếu đã có
        if (!currentAddress.isEmpty()) {
            edtNewAddress.setText(currentAddress);
            edtNewAddress.setSelection(currentAddress.length());
        }

        // Tạo hộp thoại và làm trong suốt nền mặc định
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Bấm Hủy thì đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Bấm Lưu: kiểm tra địa chỉ hợp lệ -> cập nhật lên Firebase trước -> lưu bản sao vào SQLite sau
        btnSave.setOnClickListener(v -> {
            String newAddress = edtNewAddress.getText().toString().trim();

            // Kiểm tra không trống và khác địa chỉ cũ
            if (newAddress.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập địa chỉ nhận hàng!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newAddress.equalsIgnoreCase(currentAddress.trim())) {
                Toast.makeText(this, "Địa chỉ mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Bước 1: Cập nhật địa chỉ mới lên Firebase
            repository.updateUser(currentEmail, Collections.singletonMap("address", newAddress), ok -> {
                if (!ok) {
                    Toast.makeText(this, "Cập nhật địa chỉ thất bại!", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Bước 2: Firebase thành công mới lưu bản sao xuống SQLite và cập nhật màn hình
                databaseHelper.updateAddress(currentEmail, newAddress);
                prefManager.updateAddress(newAddress);
                tvDetailAddress.setText(newAddress);
                dialog.dismiss();
                Toast.makeText(this, "Cập nhật địa chỉ thành công!", Toast.LENGTH_SHORT).show();
            });
        });

        // Hiển thị hộp thoại
        dialog.show();
    }

    // Hộp thoại đổi mật khẩu: Firebase kiểm tra mật khẩu cũ và cập nhật trước -> thành công mới lưu bản sao vào SQLite
    private void showChangePasswordDialog() {
        String currentEmail = prefManager.getEmail();

        // Nạp giao diện hộp thoại đổi mật khẩu và ánh xạ các thành phần
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText edtCurrentPassword = dialogView.findViewById(R.id.edtCurrentPassword);
        EditText edtNewPassword = dialogView.findViewById(R.id.edtNewPassword);
        EditText edtConfirmNewPassword = dialogView.findViewById(R.id.edtConfirmNewPassword);
        ImageView btnToggleCurrentPassword = dialogView.findViewById(R.id.btnToggleCurrentPassword);
        ImageView btnToggleNewPassword = dialogView.findViewById(R.id.btnToggleNewPassword);
        ImageView btnToggleConfirmNewPassword = dialogView.findViewById(R.id.btnToggleConfirmNewPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPassword);
        Button btnSave = dialogView.findViewById(R.id.btnSavePassword);

        // Cài đặt nút con mắt ẩn/hiện cho các ô mật khẩu
        setupPasswordToggle(edtCurrentPassword, btnToggleCurrentPassword);
        setupPasswordToggle(edtNewPassword, btnToggleNewPassword);
        setupPasswordToggle(edtConfirmNewPassword, btnToggleConfirmNewPassword);

        // Tạo hộp thoại và làm trong suốt nền mặc định
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Bấm Hủy thì đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Bấm Lưu: kiểm tra mật khẩu -> xác thực qua Firebase -> cập nhật Firebase trước, SQLite sau
        btnSave.setOnClickListener(v -> {
            String currentPass = edtCurrentPassword.getText().toString().trim();
            String newPass = edtNewPassword.getText().toString().trim();
            String confirmPass = edtConfirmNewPassword.getText().toString().trim();

            // Kiểm tra không trống, đủ 6 ký tự, khác mật khẩu cũ và khớp mật khẩu xác nhận
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

            // Bước 1: Kiểm tra mật khẩu cũ trên Firebase
            repository.checkPassword(currentEmail, currentPass, valid -> {
                if (!valid) {
                    Toast.makeText(this, "Mật khẩu hiện tại không chính xác!", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Bước 2: Cập nhật mật khẩu mới lên Firebase
                repository.updateUser(currentEmail, Collections.singletonMap("password", newPass), ok -> {
                    if (!ok) {
                        Toast.makeText(this, "Đổi mật khẩu thất bại!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Bước 3: Firebase thành công mới lưu bản sao xuống SQLite
                    databaseHelper.updatePassword(currentEmail, newPass);
                    dialog.dismiss();
                    Toast.makeText(this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                });
            });
        });

        // Hiển thị hộp thoại
        dialog.show();
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

    // Cài đặt thanh menu đáy: tô xanh mục "Tài khoản" và gắn sự kiện chuyển trang (4 mục)
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuOrders = findViewById(R.id.menuOrders);
        View menuAccount = findViewById(R.id.menuAccount);

        // Cập nhật màu icon cho thanh điều hướng đáy
        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuOrders = findViewById(R.id.ivMenuOrders);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);

        if (ivMenuHome != null) ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuCart != null) ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuOrders != null) ivMenuOrders.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuAccount != null) ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));

        // Tô màu xanh in đậm cho mục "Tài khoản" để báo hiệu đang ở trang này
        TextView tvMenuAccount = findViewById(R.id.tvMenuAccount);
        if (tvMenuAccount != null) {
            tvMenuAccount.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvMenuAccount.setTypeface(null, Typeface.BOLD);
        }

        // Đặt màu xám mặc định cho mục "Sản phẩm"
        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        if (tvMenuHome != null) {
            tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvMenuHome.setTypeface(null, Typeface.NORMAL);
        }

        // Bấm "Sản phẩm" -> quay về danh sách sản phẩm
        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Bấm "Giỏ hàng" -> mở màn hình Giỏ hàng
        menuCart.setOnClickListener(v -> {
            startActivity(new Intent(this, CartActivity.class));
            finish();
        });

        // Bấm "Đơn hàng" -> mở màn hình Lịch sử đơn mua
        if (menuOrders != null) {
            menuOrders.setOnClickListener(v -> {
                startActivity(new Intent(this, OrderHistoryActivity.class));
                finish();
            });
        }

        // Bấm "Tài khoản" -> báo đang ở sẵn trang Tài khoản
        menuAccount.setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Tài khoản", Toast.LENGTH_SHORT).show()
        );
    }
}