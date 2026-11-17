// Màn hình Lịch sử đơn mua: tải danh sách đơn hàng chính thức từ Firebase và lưu bản sao xuống SQLite.
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class OrderHistoryActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện: danh sách đơn, khung thông báo trống và nhãn đếm số đơn
    private ListView lvOrders;
    private View layoutEmptyOrders;
    private TextView tvOrderCountBadge;

    // Khai báo danh sách đơn hàng, Adapter hiển thị, kho chính Firebase, kho bản sao SQLite và email hiện tại
    private final List<Order> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private FirestoreRepository repository;
    private DatabaseHelper databaseHelper;
    private String currentUserEmail;

    // Hàm khởi tạo màn hình Lịch sử đơn mua: kết nối dữ liệu, ánh xạ giao diện và tải danh sách đơn
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp giao diện XML của màn hình Lịch sử đơn mua
        setContentView(R.layout.activity_order_history);

        // Kết nối Firebase (kho chính), SQLite (kho bản sao) và lấy email tài khoản đang đăng nhập
        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Ánh xạ nút quay lại, danh sách đơn, khung thông báo trống và nhãn đếm số đơn từ file XML
        TextView tvBack = findViewById(R.id.tvBack);
        lvOrders = findViewById(R.id.lvOrders);
        layoutEmptyOrders = findViewById(R.id.layoutEmptyOrders);
        tvOrderCountBadge = findViewById(R.id.tvOrderCountBadge);

        // Bấm mũi tên quay lại thì đóng màn hình hiện tại để trở về trang trước
        tvBack.setOnClickListener(v -> finish());

        // Tạo OrderAdapter và gắn vào danh sách ListView
        adapter = new OrderAdapter(this, orderList);
        lvOrders.setAdapter(adapter);

        // Bấm nút "Mua sắm ngay" (khi danh sách đơn trống) -> chuyển về màn hình Danh sách sản phẩm
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Tải danh sách đơn hàng từ Firebase ngay khi vừa mở màn hình
        loadOrderData();
    }

    // Tự động làm mới danh sách đơn hàng mỗi khi người dùng quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadOrderData();
    }

    // Đọc danh sách đơn hàng chính thức từ Firebase -> hiển thị lên giao diện -> lưu bản sao vào SQLite
    private void loadOrderData() {
        repository.getUserOrders(currentUserEmail, new FirestoreRepository.OrdersCallback() {
            @Override
            public void onResult(List<Order> orders) {
                // Cập nhật danh sách đơn hàng mới nhất lên ListView
                orderList.clear();
                orderList.addAll(orders);
                adapter.notifyDataSetChanged();
                updateOrderVisibility();

                // Tải chi tiết từng đơn hàng từ Firebase và lưu bản sao đầy đủ xuống SQLite
                for (Order order : orders) {
                    repository.getOrderItems(order.getId(), new FirestoreRepository.OrderItemsCallback() {
                        @Override
                        public void onResult(List<OrderItem> items) {
                            databaseHelper.saveRemoteOrder(order, items);
                        }

                        @Override
                        public void onError(Exception e) { }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                // Thông báo nếu không kết nối được với Firebase để tải đơn hàng
                Toast.makeText(OrderHistoryActivity.this,
                        "Không thể tải đơn hàng từ Firebase.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Kiểm tra số lượng đơn hàng để ẩn/hiện khung danh sách tương ứng và cập nhật huy hiệu đếm đơn
    private void updateOrderVisibility() {
        if (orderList.isEmpty()) {
            // Chưa có đơn hàng nào: hiện khung thông báo trống, ẩn danh sách và hiển thị "0 đơn"
            layoutEmptyOrders.setVisibility(View.VISIBLE);
            lvOrders.setVisibility(View.GONE);
            tvOrderCountBadge.setText("0 đơn");
        } else {
            // Đã có đơn hàng: ẩn khung trống, hiện danh sách đơn và hiển thị tổng số đơn đã mua
            layoutEmptyOrders.setVisibility(View.GONE);
            lvOrders.setVisibility(View.VISIBLE);
            tvOrderCountBadge.setText(orderList.size() + " đơn");
        }
    }
}