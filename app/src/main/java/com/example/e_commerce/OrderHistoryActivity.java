package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * OrderHistoryActivity.java
 * ─────────────────────────────────────────────────────────
 * Màn hình danh sách lịch sử đơn mua của người dùng từ SQLite database.
 * Hiển thị chi tiết từng đơn hàng, mã đơn, trạng thái, danh sách sản phẩm,
 * tổng tiền và thông tin giao nhận.
 * ─────────────────────────────────────────────────────────
 */
public class OrderHistoryActivity extends AppCompatActivity {

    private ListView lvOrders;
    private View layoutEmptyOrders;
    private TextView tvOrderCountBadge;

    private List<Order> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private DatabaseHelper dbHelper;
    private String currentUserEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kích hoạt giao diện hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_order_history);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        // Căn lề tự động tránh che khuất bởi thanh hệ thống
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = DatabaseHelper.getInstance(this);
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Ánh xạ View
        TextView tvBack = findViewById(R.id.tvBack);
        lvOrders = findViewById(R.id.lvOrders);
        layoutEmptyOrders = findViewById(R.id.layoutEmptyOrders);
        tvOrderCountBadge = findViewById(R.id.tvOrderCountBadge);

        tvBack.setOnClickListener(v -> finish());

        // Khởi tạo Adapter
        adapter = new OrderAdapter(this, orderList);
        lvOrders.setAdapter(adapter);

        // Nút mua sắm ngay khi danh sách trống
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(OrderHistoryActivity.this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        loadOrderData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrderData();
    }

    /**
     * Tải lại danh sách đơn hàng của người dùng từ SQLite
     */
    private void loadOrderData() {
        orderList.clear();
        List<Order> orders = dbHelper.getUserOrders(currentUserEmail);
        orderList.addAll(orders);
        adapter.notifyDataSetChanged();

        if (orderList.isEmpty()) {
            layoutEmptyOrders.setVisibility(View.VISIBLE);
            lvOrders.setVisibility(View.GONE);
            tvOrderCountBadge.setText("0 đơn");
        } else {
            layoutEmptyOrders.setVisibility(View.GONE);
            lvOrders.setVisibility(View.VISIBLE);
            tvOrderCountBadge.setText(orderList.size() + " đơn");
        }
    }
}
