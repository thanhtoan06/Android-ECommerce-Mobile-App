package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class AccountActivity extends AppCompatActivity {

    private SharedPrefManager prefManager;
    private FirestoreRepository repository;
    private TextView tvAccountName;
    private TextView tvAccountEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        prefManager = SharedPrefManager.getInstance(this);
        repository = FirestoreRepository.getInstance();
        tvAccountName = findViewById(R.id.tvAccountName);
        tvAccountEmail = findViewById(R.id.tvAccountEmail);

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.layoutOrderShortcut).setOnClickListener(v -> {
            if (AuthGate.requireLogin(this)) {
                startActivity(new Intent(this, OrderHistoryActivity.class));
            }
        });
        findViewById(R.id.layoutPointsShortcut).setOnClickListener(v -> showUpcomingFeature());
        findViewById(R.id.layoutVoucherShortcut).setOnClickListener(v -> showUpcomingFeature());
        findViewById(R.id.layoutReviewsShortcut).setOnClickListener(v -> showUpcomingFeature());

        setupBottomMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        String email = prefManager.getEmail();
        String username = prefManager.getUsername();
        tvAccountName.setText(!username.isEmpty() ? username : (!email.isEmpty() ? email : "Người dùng"));
        tvAccountEmail.setText(!email.isEmpty() ? email : "Chưa cập nhật email");

        repository.getUserByEmail(email, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {
                if (user == null) {
                    return;
                }
                prefManager.saveUser(user.getEmail(), user.getFullName(), user.getAvatar(), user.getPhone(),
                        user.getAddress());
                tvAccountName.setText(!user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
                tvAccountEmail.setText(user.getEmail());
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AccountActivity.this, "Không thể tải thông tin tài khoản.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUpcomingFeature() {
        Toast.makeText(this, "Tính năng sẽ được triển khai ở giai đoạn tiếp theo.", Toast.LENGTH_SHORT).show();
    }

    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuNotifications = findViewById(R.id.menuNotifications);
        View menuAccount = findViewById(R.id.menuAccount);

        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuNotifications = findViewById(R.id.ivMenuNotifications);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);
        ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        ivMenuNotifications.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));

        TextView tvMenuAccount = findViewById(R.id.tvMenuAccount);
        tvMenuAccount.setTextColor(ContextCompat.getColor(this, R.color.primary));
        tvMenuAccount.setTypeface(null, Typeface.BOLD);

        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvMenuHome.setTypeface(null, Typeface.NORMAL);

        menuHome.setOnClickListener(v -> navigateTo(ProductListActivity.class));
        menuCart.setOnClickListener(v -> navigateTo(CartActivity.class));
        menuNotifications.setOnClickListener(v -> navigateTo(NotificationActivity.class));
        menuAccount.setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Tôi.", Toast.LENGTH_SHORT).show());
    }

    private void navigateTo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
        finish();
    }
}
