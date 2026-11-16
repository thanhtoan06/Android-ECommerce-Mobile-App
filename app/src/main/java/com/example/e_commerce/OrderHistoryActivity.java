/**
 * OrderHistoryActivity.java: Màn hình hiển thị lịch sử các đơn hàng đã đặt của người dùng từ SQLite.
 */
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class OrderHistoryActivity extends AppCompatActivity {

    private ListView lvOrders;
    private View layoutEmptyOrders;
    private TextView tvOrderCountBadge;

    private final List<Order> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private DatabaseHelper dbHelper;
    private String currentUserEmail;

    // Hàm chạy đầu tiên khi mở màn hình Lịch sử đơn mua: kết nối giao diện và tải danh sách đơn hàng
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        dbHelper = DatabaseHelper.getInstance(this);
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Kết nối các thành phần trên giao diện
        TextView tvBack = findViewById(R.id.tvBack);
        lvOrders = findViewById(R.id.lvOrders);
        layoutEmptyOrders = findViewById(R.id.layoutEmptyOrders);
        tvOrderCountBadge = findViewById(R.id.tvOrderCountBadge);

        tvBack.setOnClickListener(v -> finish());

        // Khởi tạo Adapter đổ danh sách đơn hàng lên ListView
        adapter = new OrderAdapter(this, orderList);
        lvOrders.setAdapter(adapter);

        // Nút "Mua sắm ngay" khi chưa có đơn hàng nào
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        loadOrderData();
    }

    // Tự động làm mới danh sách đơn hàng mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadOrderData();
    }

    // Truy vấn danh sách đơn hàng của tài khoản hiện tại từ SQLite và cập nhật giao diện
    private void loadOrderData() {
        orderList.clear();
        orderList.addAll(dbHelper.getUserOrders(currentUserEmail));
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