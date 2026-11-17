// Màn hình Lịch sử đơn mua: tải và hiển thị danh sách các đơn hàng đã đặt của người dùng từ Firestore
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

    // Khai báo các thành phần giao diện trên màn hình Lịch sử đơn mua
    private ListView lvOrders;
    private View layoutEmptyOrders;
    private TextView tvOrderCountBadge;

    // Khai báo danh sách đơn hàng, Adapter, FirestoreRepository và email người dùng hiện tại
    private final List<Order> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private FirestoreRepository repository;
    private String currentUserEmail;

    // Hàm chạy đầu tiên khi mở màn hình Lịch sử đơn mua: kết nối giao diện và tải danh sách đơn hàng
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        // Khởi tạo đối tượng kết nối Firestore và lấy email tài khoản đang đăng nhập
        repository = FirestoreRepository.getInstance();
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Kết nối các thành phần trên giao diện
        TextView tvBack = findViewById(R.id.tvBack);
        lvOrders = findViewById(R.id.lvOrders);
        layoutEmptyOrders = findViewById(R.id.layoutEmptyOrders);
        tvOrderCountBadge = findViewById(R.id.tvOrderCountBadge);

        // Nút mũi tên quay lại màn hình Tài khoản
        tvBack.setOnClickListener(v -> finish());

        // Khởi tạo Adapter đổ danh sách đơn hàng lên ListView
        adapter = new OrderAdapter(this, orderList);
        lvOrders.setAdapter(adapter);

        // Nút "Mua sắm ngay" khi chưa có đơn hàng nào: chuyển về danh sách sản phẩm
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Tải danh sách đơn hàng ngay khi mở màn hình
        loadOrderData();
    }

    // Tự động làm mới danh sách đơn hàng mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadOrderData();
    }

    // Truy vấn danh sách đơn hàng của tài khoản hiện tại từ Firestore
    private void loadOrderData() {
        repository.getUserOrders(currentUserEmail, new FirestoreRepository.OrdersCallback() {
            @Override
            public void onResult(List<Order> orders) {
                // Cập nhật danh sách đơn hàng mới và thông báo Adapter vẽ lại ListView
                orderList.clear();
                orderList.addAll(orders);
                adapter.notifyDataSetChanged();
                updateOrderVisibility();
            }

            @Override
            public void onError(Exception e) { }
        });
    }

    // Ẩn/hiện giao diện trống tùy theo số lượng đơn hàng
    private void updateOrderVisibility() {
        if (orderList.isEmpty()) {
            // Chưa có đơn hàng nào: hiện thông báo trống và ẩn danh sách
            layoutEmptyOrders.setVisibility(View.VISIBLE);
            lvOrders.setVisibility(View.GONE);
            tvOrderCountBadge.setText("0 đơn");
        } else {
            // Có đơn hàng: ẩn thông báo trống, hiện danh sách và cập nhật tổng số đơn
            layoutEmptyOrders.setVisibility(View.GONE);
            lvOrders.setVisibility(View.VISIBLE);
            tvOrderCountBadge.setText(orderList.size() + " đơn");
        }
    }
}