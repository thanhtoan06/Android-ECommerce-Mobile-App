package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

/**
 * Màn hình Tài khoản (AccountActivity) hiển thị thông tin người dùng từ SharedPreferences,
 * cung cấp chức năng đổi avatar, xem lịch sử đơn hàng từ SQLite và đăng xuất.
 */
public class AccountActivity extends AppCompatActivity {

    private TextView tvAvatar;
    private SharedPrefManager prefManager;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kích hoạt giao diện hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_account);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        // Xử lý căn lề tự động tránh che khuất bởi system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ánh xạ các thành phần giao diện
        TextView tvBack = findViewById(R.id.tvBack);
        tvAvatar = findViewById(R.id.tvAvatar);
        TextView tvAccountName = findViewById(R.id.tvAccountName);
        TextView tvAccountEmail = findViewById(R.id.tvAccountEmail);
        TextView tvDetailUsername = findViewById(R.id.tvDetailUsername);
        TextView tvDetailEmail = findViewById(R.id.tvDetailEmail);
        Button btnLogout = findViewById(R.id.btnLogout);

        // Lấy thông tin người dùng từ SharedPreferences qua SharedPrefManager
        prefManager = SharedPrefManager.getInstance(this);
        String username = prefManager.getUsername();
        String email = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        // Hiển thị Avatar, tên người dùng và email lên giao diện
        tvAvatar.setText(avatar);
        String displayName = !username.isEmpty() ? username : (!email.isEmpty() ? email : "Người dùng");
        tvAccountName.setText(displayName);
        tvAccountEmail.setText(!email.isEmpty() ? email : "Chưa cập nhật email");
        tvDetailUsername.setText(!username.isEmpty() ? username : "Chưa thiết lập");
        tvDetailEmail.setText(!email.isEmpty() ? email : "Chưa thiết lập");

        // Sự kiện chạm vào Avatar để đổi ảnh đại diện
        tvAvatar.setOnClickListener(v -> showChangeAvatarDialog(email));

        // Sự kiện nút Quay lại
        tvBack.setOnClickListener(v -> finish());

        // Tiện ích: Đơn mua và Địa chỉ
        findViewById(R.id.btnOrderHistory).setOnClickListener(v -> {
            Intent intent = new Intent(AccountActivity.this, OrderHistoryActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.btnAddressBook).setOnClickListener(v ->
                Toast.makeText(AccountActivity.this, "Địa chỉ nhận hàng (Đã cập nhật theo đơn hàng gần nhất)", Toast.LENGTH_SHORT).show()
        );

        // Xử lý sự kiện bấm nút ĐĂNG XUẤT với hộp thoại xác nhận
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(AccountActivity.this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản không?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        // 1. Xóa thông tin người dùng đã lưu trong SharedPreferences
                        prefManager.logout();

                        Toast.makeText(AccountActivity.this, "Đã đăng xuất thành công!", Toast.LENGTH_SHORT).show();

                        // 2. Chuyển về LoginActivity và xóa sạch lịch sử BackStack
                        Intent intent = new Intent(AccountActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Thiết lập trạng thái thanh menu điều hướng dưới cùng (Bottom Navigation)
        setupBottomMenu();
    }

    /**
     * Hiển thị danh sách lịch sử đơn hàng đã lưu trong cơ sở dữ liệu SQLite
     */
    private void showOrderHistoryDialog(String email) {
        List<Order> orders = DatabaseHelper.getInstance(this).getUserOrders(email);
        if (orders.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("📦 Đơn mua của tôi")
                    .setMessage("Bạn chưa có đơn hàng nào trong hệ thống cơ sở dữ liệu.")
                    .setPositiveButton("Mua sắm ngay", (dialog, which) -> {
                        Intent intent = new Intent(AccountActivity.this, ProductListActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Đóng", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Order order : orders) {
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
              .append("🔖 Mã ĐH: #DH").append(String.format("%05d", order.getId())).append("\n")
              .append("📅 Ngày đặt: ").append(order.getOrderDate()).append("\n")
              .append("👤 Người nhận: ").append(order.getReceiverName()).append(" (").append(order.getReceiverPhone()).append(")\n")
              .append("📍 Địa chỉ: ").append(order.getReceiverAddress()).append("\n")
              .append("💳 Thanh toán: ").append(order.getPaymentMethod()).append("\n")
              .append("💰 Tổng tiền: ").append(order.getFormattedTotal()).append("\n")
              .append("📌 Trạng thái: ").append(order.getStatus()).append("\n");

            List<OrderItem> items = DatabaseHelper.getInstance(this).getOrderItems(order.getId());
            if (!items.isEmpty()) {
                sb.append("📦 Sản phẩm:\n");
                for (OrderItem item : items) {
                    sb.append("   • ").append(item.getProductIcon()).append(" ")
                      .append(item.getProductName()).append(" (x").append(item.getQuantity())
                      .append(") = ").append(item.getFormattedSubtotal()).append("\n");
                }
            }
            sb.append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("📦 Lịch sử đơn hàng (" + orders.size() + ")")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Đóng", null)
                .show();
    }

    /**
     * Hộp thoại đổi Avatar và cập nhật đồng thời vào SQLite và SharedPreferences
     */
    private void showChangeAvatarDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Chọn Avatar mới")
                .setItems(avatarLabels, (dialog, which) -> {
                    String newAvatar = avatarList[which];

                    // Cập nhật trong cơ sở dữ liệu SQLite
                    DatabaseHelper.getInstance(AccountActivity.this).updateAvatar(email, newAvatar);

                    // Cập nhật trong phiên đăng nhập SharedPreferences
                    prefManager.updateAvatar(newAvatar);

                    // Cập nhật giao diện
                    tvAvatar.setText(newAvatar);
                    Toast.makeText(AccountActivity.this, "Đã đổi ảnh đại diện thành công!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Cấu hình và xử lý các sự kiện click cho Bottom Navigation
     */
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        // Highlight tab Tài khoản
        if (menuAccount instanceof android.view.ViewGroup) {
            android.view.ViewGroup accountGroup = (android.view.ViewGroup) menuAccount;
            for (int i = 0; i < accountGroup.getChildCount(); i++) {
                View child = accountGroup.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if ("Tài khoản".contentEquals(tv.getText())) {
                        tv.setTextColor(Color.parseColor("#1976D2"));
                        tv.setTypeface(null, Typeface.BOLD);
                    }
                }
            }
        }

        // Bỏ highlight tab Sản phẩm
        if (menuHome instanceof android.view.ViewGroup) {
            android.view.ViewGroup homeGroup = (android.view.ViewGroup) menuHome;
            for (int i = 0; i < homeGroup.getChildCount(); i++) {
                View child = homeGroup.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if ("Sản phẩm".contentEquals(tv.getText())) {
                        tv.setTextColor(Color.parseColor("#757575"));
                        tv.setTypeface(null, Typeface.NORMAL);
                    }
                }
            }
        }

        // Sự kiện chuyển tab
        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(AccountActivity.this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        menuCart.setOnClickListener(v -> {
            Intent intent = new Intent(AccountActivity.this, CartActivity.class);
            startActivity(intent);
            finish();
        });

        menuAccount.setOnClickListener(v ->
                Toast.makeText(AccountActivity.this, "Bạn đang ở trang Tài khoản", Toast.LENGTH_SHORT).show()
        );
    }
}
