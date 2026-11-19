// Màn hình Lịch sử đơn mua: tải danh sách đơn hàng chính thức từ Firebase và lưu bản sao xuống SQLite.
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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

        // Bấm vào bất kỳ dòng đơn hàng nào trên danh sách -> mở Chi tiết đơn hàng
        lvOrders.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < orderList.size()) {
                Order order = orderList.get(position);
                Intent intent = new Intent(this, OrderDetailActivity.class);
                intent.putExtra("order", order);
                intent.putExtra("order_id", order.getId());
                startActivity(intent);
            }
        });

        // Bấm nút "Mua sắm ngay" (khi danh sách đơn trống) -> chuyển về màn hình Danh sách sản phẩm
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Cài đặt thanh điều hướng đáy 4 mục
        setupBottomMenu();

        // Tải danh sách đơn hàng từ Firebase ngay khi vừa mở màn hình
        loadOrderData();
    }

    // Tự động làm mới danh sách đơn hàng mỗi khi người dùng quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadOrderData();
    }

    // Cài đặt thanh menu đáy: tô xanh mục "Đơn hàng" và xử lý chuyển đổi giữa 4 màn hình
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuOrders = findViewById(R.id.menuOrders);
        View menuAccount = findViewById(R.id.menuAccount);

        // Cập nhật màu sắc cho các icon trên thanh điều hướng đáy
        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuOrders = findViewById(R.id.ivMenuOrders);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);

        if (ivMenuHome != null) ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuCart != null) ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuOrders != null) ivMenuOrders.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));
        if (ivMenuAccount != null) ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));

        // Đổi màu và in đậm nhãn "Đơn hàng"
        TextView tvMenuOrders = findViewById(R.id.tvMenuOrders);
        if (tvMenuOrders != null) {
            tvMenuOrders.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvMenuOrders.setTypeface(null, Typeface.BOLD);
        }

        // Đặt màu xám mặc định cho mục "Sản phẩm"
        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        if (tvMenuHome != null) {
            tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvMenuHome.setTypeface(null, Typeface.NORMAL);
        }

        // Bấm "Sản phẩm" -> chuyển sang màn hình Sản phẩm
        if (menuHome != null) {
            menuHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProductListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        // Bấm "Giỏ hàng" -> chuyển sang màn hình Giỏ hàng
        if (menuCart != null) {
            menuCart.setOnClickListener(v -> {
                startActivity(new Intent(this, CartActivity.class));
                finish();
            });
        }

        // Bấm "Đơn hàng" -> đang ở sẵn màn hình này
        if (menuOrders != null) {
            menuOrders.setOnClickListener(v ->
                    Toast.makeText(this, "Bạn đang ở trang Lịch sử đơn hàng", Toast.LENGTH_SHORT).show()
            );
        }

        // Bấm "Tài khoản" -> chuyển sang màn hình Tài khoản
        if (menuAccount != null) {
            menuAccount.setOnClickListener(v -> {
                startActivity(new Intent(this, AccountActivity.class));
                finish();
            });
        }
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
