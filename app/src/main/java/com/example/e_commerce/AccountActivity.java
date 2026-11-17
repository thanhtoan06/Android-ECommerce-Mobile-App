// Màn hình quản lý tài khoản cá nhân: xem thông tin, đổi tên, số điện thoại, địa chỉ, mật khẩu, lịch sử đơn hàng và đăng xuất
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class AccountActivity extends AppCompatActivity {

    // Khai báo biến quản lý phiên đăng nhập, kho dữ liệu Firestore và các TextView hiển thị thông tin
    private SharedPrefManager prefManager;
    private FirestoreRepository repository;
    private DatabaseHelper databaseHelper;
    private TextView tvAccountName, tvDetailUsername, tvDetailPhone, tvDetailAddress;

    // Hàm chạy đầu tiên khi mở màn hình Tài khoản: hiển thị thông tin người dùng và gắn sự kiện các nút
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        // Khởi tạo đối tượng kết nối Firestore và SharedPreferences
        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);
        prefManager = SharedPrefManager.getInstance(this);

        // Kết nối các biến Java với các thành phần trên giao diện
        TextView tvBack = findViewById(R.id.tvBack);
        TextView tvAvatar = findViewById(R.id.tvAvatar);
        tvAccountName = findViewById(R.id.tvAccountName);
        TextView tvAccountEmail = findViewById(R.id.tvAccountEmail);
        tvDetailUsername = findViewById(R.id.tvDetailUsername);
        TextView tvDetailEmail = findViewById(R.id.tvDetailEmail);
        tvDetailPhone = findViewById(R.id.tvDetailPhone);
        tvDetailAddress = findViewById(R.id.tvDetailAddress);
        Button btnLogout = findViewById(R.id.btnLogout);

        // Ánh xạ các dòng thông tin có thể bấm để chỉnh sửa nhanh
        View layoutUsernameRow = findViewById(R.id.layoutUsernameRow);
        View layoutPhoneRow = findViewById(R.id.layoutPhoneRow);
        View layoutAddressRow = findViewById(R.id.layoutAddressRow);

        // Ánh xạ các nút chức năng cài đặt tài khoản
        View btnChangeUsername = findViewById(R.id.btnChangeUsername);
        View btnChangePhone = findViewById(R.id.btnChangePhone);
        View btnChangeAddress = findViewById(R.id.btnChangeAddress);
        View btnChangePassword = findViewById(R.id.btnChangePassword);

        // Đọc thông tin người dùng đang đăng nhập từ SharedPreferences
        String username = prefManager.getUsername();
        String email = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        String phone = "";
        String address = "";
        phone = prefManager.getPhone();
        address = prefManager.getAddress();

        // Hiển thị avatar mặc định, tên, email, số điện thoại và địa chỉ lên giao diện
        tvAvatar.setText(avatar);
        String displayName = !username.isEmpty() ? username : (!email.isEmpty() ? email : "Người dùng");
        tvAccountName.setText(displayName);
        tvAccountEmail.setText(!email.isEmpty() ? email : "Chưa cập nhật email");
        tvDetailUsername.setText(!username.isEmpty() ? username : "Chưa thiết lập");
        tvDetailEmail.setText(!email.isEmpty() ? email : "Chưa thiết lập");
        tvDetailPhone.setText(!phone.isEmpty() ? phone : "Chưa thiết lập");
        tvDetailAddress.setText(!address.isEmpty() ? address : "Chưa thiết lập");

        // Nút mũi tên quay lại màn hình trước
        tvBack.setOnClickListener(v -> finish());

        // Mở dialog đổi tên đăng nhập khi bấm nút hoặc dòng tên tài khoản
        if (btnChangeUsername != null) {
            btnChangeUsername.setOnClickListener(v -> showChangeUsernameDialog());
        }
        if (layoutUsernameRow != null) {
            layoutUsernameRow.setOnClickListener(v -> showChangeUsernameDialog());
        }

        // Mở dialog cập nhật số điện thoại khi bấm nút hoặc dòng số điện thoại
        if (btnChangePhone != null) {
            btnChangePhone.setOnClickListener(v -> showChangePhoneDialog());
        }
        if (layoutPhoneRow != null) {
            layoutPhoneRow.setOnClickListener(v -> showChangePhoneDialog());
        }

        // Mở dialog cập nhật địa chỉ nhận hàng khi bấm nút hoặc dòng địa chỉ
        if (btnChangeAddress != null) {
            btnChangeAddress.setOnClickListener(v -> showChangeAddressDialog());
        }
        if (layoutAddressRow != null) {
            layoutAddressRow.setOnClickListener(v -> showChangeAddressDialog());
        }

        // Mở dialog đổi mật khẩu
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        }

        // Mở màn hình Lịch sử đơn mua hàng
        findViewById(R.id.btnOrderHistory).setOnClickListener(v ->
                startActivity(new Intent(AccountActivity.this, OrderHistoryActivity.class))
        );

        // Xử lý bấm nút ĐĂNG XUẤT: hiện hộp thoại xác nhận, xóa phiên đăng nhập và quay về LoginActivity
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản không?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        prefManager.logout(); // Xóa dữ liệu phiên trong SharedPreferences
                        Toast.makeText(this, "Đã đăng xuất thành công!", Toast.LENGTH_SHORT).show();

                        // Chuyển về màn hình đăng nhập và xóa sạch lịch sử các màn hình trước đó
                        Intent intent = new Intent(AccountActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Cài đặt thanh menu điều hướng phía dưới cùng
        setupBottomMenu();
    }

    // Hiển thị hộp thoại thay đổi tên đăng nhập / họ tên người dùng
    private void showChangeUsernameDialog() {
        String currentEmail = prefManager.getEmail();
        String currentUsername = prefManager.getUsername();

        // Nạp giao diện hộp thoại đổi tên và ánh xạ các nút bấm
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_username, null);
        EditText edtNewUsername = dialogView.findViewById(R.id.edtNewUsername);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelUsername);
        Button btnSave = dialogView.findViewById(R.id.btnSaveUsername);

        // Điền sẵn tên hiện tại vào ô nhập liệu và đưa con trỏ về cuối
        if (!currentUsername.isEmpty()) {
            edtNewUsername.setText(currentUsername);
            edtNewUsername.setSelection(currentUsername.length());
        }

        // Tạo hộp thoại AlertDialog và làm trong suốt nền mặc định để hiện bo góc
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Nút Hủy đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Nút Lưu kiểm tra hợp lệ và cập nhật tên mới lên Firestore
        btnSave.setOnClickListener(v -> {
            String newUsername = edtNewUsername.getText().toString().trim();

            // Kiểm tra tên mới không được để trống
            if (newUsername.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên đăng nhập mới!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra tên mới có khác tên cũ không
            if (newUsername.equalsIgnoreCase(currentUsername.trim())) {
                Toast.makeText(this, "Tên đăng nhập mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra tên đăng nhập đã tồn tại trên Firestore chưa trước khi lưu
            repository.checkUsernameExists(newUsername, currentEmail, exists -> {
                if (exists) { Toast.makeText(this, "Tên đăng nhập này đã được sử dụng bởi tài khoản khác!", Toast.LENGTH_LONG).show(); return; }
                java.util.Map<String, Object> values = new java.util.HashMap<>();
                values.put("fullName", newUsername);
                databaseHelper.updateUsername(currentEmail, newUsername);
                repository.updateUser(currentEmail, values, ok -> {
                    if (ok) { prefManager.updateUsername(newUsername); tvAccountName.setText(newUsername); tvDetailUsername.setText(newUsername); dialog.dismiss(); }
                    Toast.makeText(this, ok ? "Đổi tên đăng nhập thành công!" : "Đổi tên đăng nhập thất bại!", Toast.LENGTH_SHORT).show();
                });
            });
        });

        dialog.show();
    }

    // Hiển thị hộp thoại cập nhật số điện thoại
    private void showChangePhoneDialog() {
        String currentEmail = prefManager.getEmail();
        String currentPhone = prefManager.getPhone();

        // Nạp giao diện hộp thoại đổi số điện thoại và ánh xạ các thành phần
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_phone, null);
        EditText edtNewPhone = dialogView.findViewById(R.id.edtNewPhone);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPhone);
        Button btnSave = dialogView.findViewById(R.id.btnSavePhone);

        // Điền sẵn số điện thoại hiện tại nếu đã có
        if (!currentPhone.isEmpty()) {
            edtNewPhone.setText(currentPhone);
            edtNewPhone.setSelection(currentPhone.length());
        }

        // Tạo và cấu hình nền trong suốt cho hộp thoại
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Nút Hủy đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Nút Lưu kiểm tra định dạng số điện thoại và cập nhật lên Firestore
        btnSave.setOnClickListener(v -> {
            String newPhone = edtNewPhone.getText().toString().trim();

            // Kiểm tra không để trống số điện thoại
            if (newPhone.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập số điện thoại!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra độ dài số điện thoại từ 9 đến 11 chữ số
            if (newPhone.length() < 9 || newPhone.length() > 11) {
                Toast.makeText(this, "Số điện thoại không hợp lệ (9 - 11 chữ số)!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra số mới có thay đổi so với số cũ không
            if (newPhone.equals(currentPhone.trim())) {
                Toast.makeText(this, "Số điện thoại mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Cập nhật số điện thoại mới lên Firestore và lưu lại vào SharedPreferences
            repository.updateUser(currentEmail, java.util.Collections.singletonMap("phone", newPhone), ok -> {
                if (ok) { prefManager.updatePhone(newPhone); tvDetailPhone.setText(newPhone); dialog.dismiss(); }
                Toast.makeText(this, ok ? "Cập nhật số điện thoại thành công!" : "Cập nhật số điện thoại thất bại!", Toast.LENGTH_SHORT).show();
            });
            databaseHelper.updatePhone(currentEmail, newPhone);
        });

        dialog.show();
    }

    // Hiển thị hộp thoại cập nhật địa chỉ giao hàng
    private void showChangeAddressDialog() {
        String currentEmail = prefManager.getEmail();
        String currentAddress = prefManager.getAddress();

        // Nạp giao diện hộp thoại đổi địa chỉ và ánh xạ các thành phần
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_address, null);
        EditText edtNewAddress = dialogView.findViewById(R.id.edtNewAddress);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAddress);
        Button btnSave = dialogView.findViewById(R.id.btnSaveAddress);

        // Điền sẵn địa chỉ hiện tại nếu đã có
        if (!currentAddress.isEmpty()) {
            edtNewAddress.setText(currentAddress);
            edtNewAddress.setSelection(currentAddress.length());
        }

        // Tạo và cấu hình nền trong suốt cho hộp thoại
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Nút Hủy đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Nút Lưu kiểm tra địa chỉ hợp lệ và cập nhật lên Firestore
        btnSave.setOnClickListener(v -> {
            String newAddress = edtNewAddress.getText().toString().trim();

            // Kiểm tra không để trống địa chỉ
            if (newAddress.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập địa chỉ nhận hàng!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra địa chỉ mới có khác địa chỉ cũ không
            if (newAddress.equalsIgnoreCase(currentAddress.trim())) {
                Toast.makeText(this, "Địa chỉ mới không có thay đổi!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Cập nhật địa chỉ mới lên Firestore và đồng bộ vào SharedPreferences
            repository.updateUser(currentEmail, java.util.Collections.singletonMap("address", newAddress), ok -> {
                if (ok) { prefManager.updateAddress(newAddress); tvDetailAddress.setText(newAddress); dialog.dismiss(); }
                Toast.makeText(this, ok ? "Cập nhật địa chỉ thành công!" : "Cập nhật địa chỉ thất bại!", Toast.LENGTH_SHORT).show();
            });
            databaseHelper.updateAddress(currentEmail, newAddress);
        });

        dialog.show();
    }

    // Hiển thị hộp thoại thay đổi mật khẩu tài khoản
    private void showChangePasswordDialog() {
        String currentEmail = prefManager.getEmail();

        // Nạp giao diện hộp thoại đổi mật khẩu và ánh xạ các ô nhập liệu
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText edtCurrentPassword = dialogView.findViewById(R.id.edtCurrentPassword);
        EditText edtNewPassword = dialogView.findViewById(R.id.edtNewPassword);
        EditText edtConfirmNewPassword = dialogView.findViewById(R.id.edtConfirmNewPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPassword);
        Button btnSave = dialogView.findViewById(R.id.btnSavePassword);

        // Tạo và cấu hình nền trong suốt cho hộp thoại
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Nút Hủy đóng hộp thoại
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Nút Đổi mật khẩu kiểm tra điều kiện và cập nhật mật khẩu mới lên Firestore
        btnSave.setOnClickListener(v -> {
            String currentPass = edtCurrentPassword.getText().toString().trim();
            String newPass = edtNewPassword.getText().toString().trim();
            String confirmPass = edtConfirmNewPassword.getText().toString().trim();

            // Kiểm tra nhập đầy đủ cả 3 ô mật khẩu
            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra độ dài mật khẩu mới tối thiểu 6 ký tự
            if (newPass.length() < 6) {
                Toast.makeText(this, "Mật khẩu mới phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra mật khẩu mới phải khác mật khẩu hiện tại
            if (newPass.equals(currentPass)) {
                Toast.makeText(this, "Mật khẩu mới không được trùng với mật khẩu hiện tại!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra mật khẩu xác nhận có trùng khớp không
            if (!newPass.equals(confirmPass)) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Đối chiếu mật khẩu hiện tại trên Firestore, nếu đúng thì lưu mật khẩu mới
            repository.checkPassword(currentEmail, currentPass, valid -> {
                if (!valid) { Toast.makeText(this, "Mật khẩu hiện tại không chính xác!", Toast.LENGTH_SHORT).show(); return; }
                repository.updateUser(currentEmail, java.util.Collections.singletonMap("password", newPass), ok -> {
                    Toast.makeText(this, ok ? "Đổi mật khẩu thành công!" : "Đổi mật khẩu thất bại!", Toast.LENGTH_SHORT).show();
                    if (ok) dialog.dismiss();
                });
                databaseHelper.updatePassword(currentEmail, newPass);
            });
        });

        dialog.show();
    }

    // Đánh dấu tab Tài khoản đang chọn và gắn sự kiện chuyển trang cho thanh menu dưới cùng
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        // Tô đậm chữ tab "Tài khoản"
        if (menuAccount instanceof ViewGroup) {
            ViewGroup accountGroup = (ViewGroup) menuAccount;
            for (int i = 0; i < accountGroup.getChildCount(); i++) {
                View child = accountGroup.getChildAt(i);
                if (child instanceof TextView && "Tài khoản".contentEquals(((TextView) child).getText())) {
                    ((TextView) child).setTextColor(Color.parseColor("#1976D2"));
                    ((TextView) child).setTypeface(null, Typeface.BOLD);
                }
            }
        }

        // Đặt lại màu chữ bình thường cho tab "Sản phẩm"
        if (menuHome instanceof ViewGroup) {
            ViewGroup homeGroup = (ViewGroup) menuHome;
            for (int i = 0; i < homeGroup.getChildCount(); i++) {
                View child = homeGroup.getChildAt(i);
                if (child instanceof TextView && "Sản phẩm".contentEquals(((TextView) child).getText())) {
                    ((TextView) child).setTextColor(Color.parseColor("#757575"));
                    ((TextView) child).setTypeface(null, Typeface.NORMAL);
                }
            }
        }

        // Chuyển sang màn hình Danh sách sản phẩm
        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Chuyển sang màn hình Giỏ hàng
        menuCart.setOnClickListener(v -> {
            startActivity(new Intent(this, CartActivity.class));
            finish();
        });

        // Đang ở trang Tài khoản nên chỉ hiện thông báo nhẹ
        menuAccount.setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Tài khoản", Toast.LENGTH_SHORT).show()
        );
    }
}