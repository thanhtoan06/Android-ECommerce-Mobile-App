package com.example.e_commerce;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PointsActivity extends AppCompatActivity {

    private PointsRepository repository;
    private String userEmail;
    private TextView tvBalance;
    private TextView tvPointsExpiry;
    private TextView tvCheckinStatus;
    private TextView tvEmptyHistory;
    private MaterialButton btnCheckin;
    private ProgressBar progressBar;
    private LinearLayout layoutCheckinDays;
    private LinearLayout layoutHistory;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat expiryDateFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGate.requireLogin(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_points);
        repository = PointsRepository.getInstance();
        userEmail = SharedPrefManager.getInstance(this).getEmail();
        tvBalance = findViewById(R.id.tvPointsBalance);
        tvPointsExpiry = findViewById(R.id.tvPointsExpiry);
        tvCheckinStatus = findViewById(R.id.tvCheckinStatus);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        btnCheckin = findViewById(R.id.btnCheckin);
        progressBar = findViewById(R.id.progressPoints);
        layoutCheckinDays = findViewById(R.id.layoutCheckinDays);
        layoutHistory = findViewById(R.id.layoutPointHistory);

        findViewById(R.id.btnBackPoints).setOnClickListener(v -> finish());
        btnCheckin.setOnClickListener(v -> checkIn());
        setupBottomMenu();
        loadDashboard();
    }

    private void loadDashboard() {
        progressBar.setVisibility(View.VISIBLE);
        btnCheckin.setEnabled(false);
        repository.getDashboard(userEmail, new PointsRepository.DashboardCallback() {
            @Override
            public void onResult(PointsRepository.PointsDashboard dashboard) {
                progressBar.setVisibility(View.GONE);
                renderDashboard(dashboard);
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                btnCheckin.setText("Thử tải lại");
                btnCheckin.setEnabled(true);
                btnCheckin.setOnClickListener(v -> loadDashboard());
                Toast.makeText(PointsActivity.this, "Không thể tải dữ liệu Xu từ Firebase.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderDashboard(PointsRepository.PointsDashboard dashboard) {
        btnCheckin.setOnClickListener(v -> checkIn());
        tvBalance.setText(formatNumber(dashboard.getBalance()) + " Xu");
        if (dashboard.getExpiringPoints() > 0 && dashboard.getNextExpiryDate() != null) {
            tvPointsExpiry.setText(formatNumber(dashboard.getExpiringPoints())
                    + " Xu sẽ hết hạn ngày " + expiryDateFormat.format(dashboard.getNextExpiryDate()));
        } else {
            tvPointsExpiry.setText("Chưa có Xu sắp hết hạn");
        }
        PointsRules.CheckInState state = dashboard.getCheckInState();
        if (state.isDeviceDateInvalid()) {
            tvCheckinStatus.setText("Ngày trên thiết bị không hợp lệ. Vui lòng kiểm tra ngày giờ.");
            btnCheckin.setText("Kiểm tra ngày trên thiết bị");
            btnCheckin.setEnabled(false);
        } else if (state.isCheckedInToday()) {
            tvCheckinStatus.setText("Bạn đã điểm danh hôm nay. Ngày kế tiếp mở vào ngày mai.");
            btnCheckin.setText("Đã điểm danh hôm nay");
            btnCheckin.setEnabled(false);
        } else {
            long reward = PointsRules.rewardForDay(state.getDay());
            tvCheckinStatus.setText("Hôm nay: Ngày " + state.getDay() + " · Nhận "
                    + formatNumber(reward) + " Xu");
            btnCheckin.setText("Điểm danh nhận " + formatNumber(reward) + " Xu");
            btnCheckin.setEnabled(true);
        }

        renderCheckInDays(state);
        renderHistory(dashboard.getHistory());
    }

    private void renderCheckInDays(PointsRules.CheckInState state) {
        for (int index = 0; index < layoutCheckinDays.getChildCount(); index++) {
            View card = layoutCheckinDays.getChildAt(index);
            int day = index + 1;
            boolean completed = day <= state.getCompletedDays();
            boolean current = day == state.getDay() && !state.isCheckedInToday()
                    && !state.isDeviceDateInvalid();

            TextView title = card.findViewById(R.id.tvDayTitle);
            TextView reward = card.findViewById(R.id.tvDayReward);
            ImageView check = card.findViewById(R.id.ivDayStatus);
            title.setText("N" + day);
            reward.setText("+" + formatNumber(PointsRules.rewardForDay(day)));
            check.setContentDescription("Phần thưởng ngày " + day);

            int backgroundColor = completed ? R.color.primary_soft : R.color.surface;
            int strokeColor = current ? R.color.primary : R.color.card_stroke;
            GradientDrawable background = new GradientDrawable();
            background.setColor(ContextCompat.getColor(this, backgroundColor));
            background.setCornerRadius(dpToPx(12));
            background.setStroke(dpToPx(1), ContextCompat.getColor(this, strokeColor));
            card.setBackground(background);

            int labelColor = completed || current ? R.color.primary : R.color.text_secondary;
            title.setTextColor(ContextCompat.getColor(this, labelColor));
            reward.setTextColor(ContextCompat.getColor(this, completed ? R.color.primary : R.color.text_primary));
        }
    }

    private void renderHistory(List<PointsRepository.PointsEntry> entries) {
        layoutHistory.removeAllViews();
        tvEmptyHistory.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        PointsTransactionAdapter adapter = new PointsTransactionAdapter(this, entries);
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)));
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
                layoutHistory.addView(divider);
            }
            layoutHistory.addView(adapter.getView(index, null, layoutHistory));
        }
    }

    private void checkIn() {
        btnCheckin.setEnabled(false);
        repository.checkInToday(userEmail, new PointsRepository.ActionCallback() {
            @Override
            public void onResult(PointsRepository.ActionResult result) {
                Toast.makeText(PointsActivity.this, result.getMessage(), Toast.LENGTH_LONG).show();
                loadDashboard();
            }

            @Override
            public void onError(Exception e) {
                btnCheckin.setEnabled(true);
                Toast.makeText(PointsActivity.this, "Không thể điểm danh. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupBottomMenu() {
        findViewById(R.id.menuHome).setOnClickListener(v -> navigateTo(ProductListActivity.class));
        findViewById(R.id.menuCart).setOnClickListener(v -> navigateTo(CartActivity.class));
        findViewById(R.id.menuNotifications).setOnClickListener(v -> navigateTo(NotificationActivity.class));
        findViewById(R.id.menuAccount).setOnClickListener(v -> navigateTo(AccountActivity.class));

        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        int activeColor = ContextCompat.getColor(this, R.color.primary);
        ((ImageView) findViewById(R.id.ivMenuHome)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuCart)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuNotifications)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuAccount)).setColorFilter(activeColor);

        TextView[] inactiveLabels = {
                findViewById(R.id.tvMenuHome),
                findViewById(R.id.tvMenuCart),
                findViewById(R.id.tvMenuNotifications)
        };
        for (TextView label : inactiveLabels) {
            label.setTextColor(inactiveColor);
        }
        TextView accountLabel = findViewById(R.id.tvMenuAccount);
        accountLabel.setTextColor(activeColor);
        accountLabel.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void navigateTo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
        finish();
    }

    private String formatNumber(long value) {
        return numberFormat.format(value);
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
