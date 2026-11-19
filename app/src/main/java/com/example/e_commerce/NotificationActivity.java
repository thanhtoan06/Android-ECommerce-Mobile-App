package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private ListView lvNotifications;
    private View layoutEmptyNotifications;
    private ProgressBar progressBar;
    private TextView btnMarkAllRead;
    private TextView tvEmptyNotifications;
    private NotificationAdapter adapter;
    private FirestoreRepository repository;
    private String currentUserEmail;
    private final List<AppNotification> notificationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGate.requireLogin(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_notifications);
        repository = FirestoreRepository.getInstance();
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        lvNotifications = findViewById(R.id.lvNotifications);
        layoutEmptyNotifications = findViewById(R.id.layoutEmptyNotifications);
        progressBar = findViewById(R.id.progressBarNotifications);
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        tvEmptyNotifications = findViewById(R.id.tvEmptyNotifications);
        adapter = new NotificationAdapter(this, notificationList);
        lvNotifications.setAdapter(adapter);

        btnMarkAllRead.setOnClickListener(v -> markAllRead());
        lvNotifications.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < notificationList.size()) {
                openNotification(notificationList.get(position));
            }
        });

        setupBottomMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        progressBar.setVisibility(View.VISIBLE);
        btnMarkAllRead.setEnabled(false);
        lvNotifications.setVisibility(View.GONE);
        layoutEmptyNotifications.setVisibility(View.GONE);

        repository.syncOrderStatusNotifications(currentUserEmail, synced -> {
            if (!synced) {
                Toast.makeText(this, "Không thể đồng bộ cập nhật đơn hàng.", Toast.LENGTH_SHORT).show();
            }
            repository.getUserNotifications(currentUserEmail, new FirestoreRepository.NotificationsCallback() {
                @Override
                public void onResult(List<AppNotification> notifications) {
                    progressBar.setVisibility(View.GONE);
                    notificationList.clear();
                    notificationList.addAll(notifications);
                    adapter.notifyDataSetChanged();
                    boolean hasUnread = false;
                    for (AppNotification notification : notifications) {
                        if (!notification.isRead()) {
                            hasUnread = true;
                            break;
                        }
                    }
                    btnMarkAllRead.setEnabled(hasUnread);

                    boolean hasNotifications = !notifications.isEmpty();
                    lvNotifications.setVisibility(hasNotifications ? View.VISIBLE : View.GONE);
                    layoutEmptyNotifications.setVisibility(hasNotifications ? View.GONE : View.VISIBLE);
                    tvEmptyNotifications.setText("Bạn chưa có thông báo");
                }

                @Override
                public void onError(Exception e) {
                    progressBar.setVisibility(View.GONE);
                    btnMarkAllRead.setEnabled(false);
                    lvNotifications.setVisibility(View.GONE);
                    layoutEmptyNotifications.setVisibility(View.VISIBLE);
                    tvEmptyNotifications.setText("Không thể tải thông báo. Vui lòng thử lại.");
                    Toast.makeText(NotificationActivity.this, "Không thể tải thông báo từ Firebase.",
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void markAllRead() {
        btnMarkAllRead.setEnabled(false);
        repository.markAllNotificationsRead(currentUserEmail, success -> {
            if (!success) {
                btnMarkAllRead.setEnabled(true);
                Toast.makeText(this, "Không thể đánh dấu thông báo đã đọc.", Toast.LENGTH_SHORT).show();
                return;
            }
            loadNotifications();
        });
    }

    private void openNotification(AppNotification notification) {
        if (!notification.isRead()) {
            repository.markNotificationRead(notification.getId(), success -> {
                if (!success) {
                    Toast.makeText(this, "Không thể cập nhật trạng thái thông báo.", Toast.LENGTH_SHORT).show();
                    return;
                }
                notification.setRead(true);
                adapter.notifyDataSetChanged();
                openRelatedOrder(notification);
            });
            return;
        }
        openRelatedOrder(notification);
    }

    private void openRelatedOrder(AppNotification notification) {
        if (notification.getOrderId() <= 0) {
            return;
        }
        Intent intent = new Intent(this, OrderDetailActivity.class);
        intent.putExtra("order_id", notification.getOrderId());
        startActivity(intent);
    }

    private void setupBottomMenu() {
        findViewById(R.id.menuHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.menuCart).setOnClickListener(v -> navigateTo(CartActivity.class));
        findViewById(R.id.menuAccount).setOnClickListener(v -> navigateTo(AccountActivity.class));
        findViewById(R.id.menuNotifications).setOnClickListener(
                v -> Toast.makeText(this, "Bạn đang ở trang Thông báo", Toast.LENGTH_SHORT).show());

        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        int activeColor = ContextCompat.getColor(this, R.color.primary);
        ((ImageView) findViewById(R.id.ivMenuHome)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuCart)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuAccount)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuNotifications)).setColorFilter(activeColor);

        TextView[] inactiveLabels = {
                findViewById(R.id.tvMenuHome),
                findViewById(R.id.tvMenuCart),
                findViewById(R.id.tvMenuAccount)
        };
        for (TextView label : inactiveLabels) {
            label.setTextColor(inactiveColor);
            label.setTypeface(null, Typeface.NORMAL);
        }
        TextView activeLabel = findViewById(R.id.tvMenuNotifications);
        activeLabel.setTextColor(activeColor);
        activeLabel.setTypeface(null, Typeface.BOLD);
    }

    private void navigateTo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
        finish();
    }
}
