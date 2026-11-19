// Màn hình Giỏ hàng: hiển thị, tăng/giảm số lượng, xóa sản phẩm và chuyển thanh toán (Firebase xử lý chính, SQLite lưu bản sao sau khi thành công).
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity implements CartAdapter.OnCartItemListener {

    // Khai báo các thành phần giao diện: danh sách hàng, khung trống, thanh thanh toán, tổng tiền và các nút
    private ListView lvCartItems;
    private View layoutEmptyCart;
    private View layoutCheckoutBar;
    private TextView tvCartTotalPrice;
    private TextView tvCartHeaderTitle;
    private TextView btnClearAllCart;
    private Button btnCheckout;

    // Khai báo danh sách món hàng, bộ nạp giao diện Adapter, kho chính Firebase, kho bản sao SQLite và email người dùng
    private final List<CartItem> cartItemList = new ArrayList<>();
    private CartAdapter adapter;
    private FirestoreRepository repository;
    private DatabaseHelper databaseHelper;
    private String currentUserEmail;

    // Hàm khởi tạo màn hình Giỏ hàng: kết nối dữ liệu, ánh xạ giao diện và gắn sự kiện nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp giao diện XML của màn hình Giỏ hàng
        setContentView(R.layout.activity_cart);

        // Kết nối Firebase (kho chính), SQLite (kho bản sao) và lấy email tài khoản đang đăng nhập
        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Ánh xạ các thành phần giao diện từ file XML vào biến Java
        lvCartItems = findViewById(R.id.lvCartItems);
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart);
        layoutCheckoutBar = findViewById(R.id.layoutCheckoutBar);
        tvCartTotalPrice = findViewById(R.id.tvCartTotalPrice);
        tvCartHeaderTitle = findViewById(R.id.tvCartHeaderTitle);
        btnClearAllCart = findViewById(R.id.btnClearAllCart);
        btnCheckout = findViewById(R.id.btnCheckout);

        // Khởi tạo CartAdapter và gắn vào ListView để hiển thị từng món trong giỏ
        adapter = new CartAdapter(this, cartItemList, this);
        lvCartItems.setAdapter(adapter);

        // Bấm vào một sản phẩm trong giỏ -> mở màn hình Chi tiết của sản phẩm đó
        lvCartItems.setOnItemClickListener((parent, view, position, id) -> {
            if (position < cartItemList.size()) {
                CartItem item = cartItemList.get(position);
                if (item.getProduct() != null) {
                    Intent intent = new Intent(this, ProductDetailActivity.class);
                    intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, item.getProduct().getId());
                    startActivity(intent);
                }
            }
        });

        // Bấm nút "Khám phá sản phẩm" (khi giỏ trống) -> quay về trang Danh sách sản phẩm
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Bấm nút "Xóa tất cả": Firebase xóa dữ liệu chính trước -> thành công mới xóa bản sao SQLite
        btnClearAllCart.setOnClickListener(v -> {
            // Nếu giỏ hàng đang trống thì không làm gì
            if (cartItemList.isEmpty()) return;

            // Hiện hộp thoại hỏi xác nhận trước khi xóa sạch giỏ hàng
            new AlertDialog.Builder(this)
                    .setTitle("Xóa giỏ hàng")
                    .setMessage("Bạn có chắc chắn muốn xóa toàn bộ sản phẩm khỏi giỏ hàng?")
                    .setPositiveButton("Xóa tất cả", (dialog, which) -> {
                        // Bước 1: Gửi lệnh xóa toàn bộ giỏ hàng lên Firebase trước
                        repository.clearCart(currentUserEmail, success -> {
                            if (success) {
                                // Bước 2: Firebase xóa thành công mới xóa bản sao trong SQLite và báo cho người dùng
                                databaseHelper.clearCart(currentUserEmail);
                                Toast.makeText(this, "Đã xóa toàn bộ giỏ hàng!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Xóa giỏ hàng thất bại!", Toast.LENGTH_SHORT).show();
                            }
                            // Bước 3: Tải lại dữ liệu từ Firebase để cập nhật giao diện
                            loadCartData();
                        });
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Bấm nút "Thanh toán": kiểm tra giỏ có hàng rồi chuyển sang màn hình Đặt hàng (CheckoutActivity)
        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(this, "Giỏ hàng đang trống!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, CheckoutActivity.class));
        });

        // Cài đặt thanh menu điều hướng dưới đáy màn hình
        setupBottomMenu();
    }

    // Tự động tải lại dữ liệu giỏ hàng mới nhất mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadCartData();
    }

    // Đọc danh sách giỏ hàng chính thức từ Firebase -> lưu bản sao vào SQLite -> vẽ lại giao diện
    private void loadCartData() {
        repository.getCartItems(currentUserEmail, new FirestoreRepository.CartCallback() {
            @Override
            public void onResult(List<CartItem> items) {
                // Cập nhật danh sách hiển thị trên màn hình
                cartItemList.clear();
                cartItemList.addAll(items);

                // Làm mới bản sao giỏ hàng dưới SQLite theo đúng dữ liệu từ Firebase
                databaseHelper.clearCart(currentUserEmail);
                for (CartItem item : items) {
                    databaseHelper.addToCart(currentUserEmail, item.getProduct().getId(), item.getQuantity());
                }

                // Vẽ lại danh sách và cập nhật tổng tiền
                adapter.notifyDataSetChanged();
                updateCartVisibility();
            }

            @Override
            public void onError(Exception e) {
                // Thông báo nếu lỗi mạng không tải được giỏ hàng từ Firebase
                Toast.makeText(CartActivity.this, "Không thể tải giỏ hàng từ Firebase.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Ẩn/hiện khung giỏ trống hoặc danh sách hàng, đồng thời cộng tổng tiền và tổng số lượng
    private void updateCartVisibility() {
        if (cartItemList.isEmpty()) {
            // Nếu giỏ trống: hiện khung thông báo trống, ẩn danh sách và thanh thanh toán
            layoutEmptyCart.setVisibility(View.VISIBLE);
            lvCartItems.setVisibility(View.GONE);
            layoutCheckoutBar.setVisibility(View.GONE);
            btnClearAllCart.setVisibility(View.GONE);
            tvCartHeaderTitle.setText("Giỏ hàng của tôi");
        } else {
            // Nếu giỏ có hàng: ẩn khung trống, hiện danh sách, thanh thanh toán và nút xóa tất cả
            layoutEmptyCart.setVisibility(View.GONE);
            lvCartItems.setVisibility(View.VISIBLE);
            layoutCheckoutBar.setVisibility(View.VISIBLE);
            btnClearAllCart.setVisibility(View.VISIBLE);

            // Cộng dồn tổng tiền và tổng số lượng của tất cả món trong giỏ
            long total = 0;
            int totalQuantity = 0;
            for (CartItem item : cartItemList) {
                total += item.getSubtotal();
                totalQuantity += item.getQuantity();
            }

            // Hiển thị tổng số lượng và định dạng tổng tiền theo kiểu tiền Việt Nam (VNĐ)
            tvCartHeaderTitle.setText("Giỏ hàng (" + totalQuantity + ")");
            tvCartTotalPrice.setText(NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(total) + " ₫");
            btnCheckout.setText("THANH TOÁN (" + totalQuantity + ")");
        }
    }

    // Xử lý khi bấm (+) hoặc (−) đổi số lượng: cập nhật Firebase trước -> thành công mới lưu bản sao SQLite
    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        if (newQuantity <= 0) {
            // Nếu giảm số lượng về 0 -> hỏi xác nhận xóa món hàng khỏi giỏ
            new AlertDialog.Builder(this)
                    .setTitle("Xóa sản phẩm")
                    .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        // Bước 1: Xóa trên Firebase trước
                        repository.removeCartItem(currentUserEmail, item.getProduct().getId(), success -> {
                            // Bước 2: Firebase thành công mới xóa bản sao trong SQLite
                            if (success) databaseHelper.removeCartItem(currentUserEmail, item.getProduct().getId());
                            loadCartData();
                        });
                    })
                    .setNegativeButton("Giữ lại", null)
                    .show();
        } else {
            // Bước 1: Cập nhật số lượng mới lên Firebase trước
            repository.updateCartQuantity(currentUserEmail, item.getProduct().getId(), newQuantity, success -> {
                // Bước 2: Firebase thành công mới cập nhật bản sao trong SQLite
                if (success) databaseHelper.updateCartQuantity(currentUserEmail, item.getProduct().getId(), newQuantity);
                loadCartData();
            });
        }
    }

    // Xử lý khi bấm biểu tượng thùng rác xóa 1 món: xóa trên Firebase trước -> thành công mới xóa bản sao SQLite
    @Override
    public void onItemDeleted(CartItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa sản phẩm")
                .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    // Bước 1: Xóa món hàng trên Firebase trước
                    repository.removeCartItem(currentUserEmail, item.getProduct().getId(), success -> {
                        if (success) {
                            // Bước 2: Firebase thành công mới xóa bản sao trong SQLite và báo kết quả
                            databaseHelper.removeCartItem(currentUserEmail, item.getProduct().getId());
                            Toast.makeText(this, "Đã xóa sản phẩm khỏi giỏ hàng!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Xóa sản phẩm thất bại!", Toast.LENGTH_SHORT).show();
                        }
                        loadCartData();
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Cài đặt thanh menu đáy: tô xanh mục "Giỏ hàng" và gắn sự kiện chuyển trang (4 mục)
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuOrders = findViewById(R.id.menuOrders);
        View menuAccount = findViewById(R.id.menuAccount);

        // Cập nhật màu icon cho thanh điều hướng đáy
        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuOrders = findViewById(R.id.ivMenuOrders);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);

        if (ivMenuHome != null) ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuCart != null) ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));
        if (ivMenuOrders != null) ivMenuOrders.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuAccount != null) ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));

        // Tô màu xanh in đậm cho mục "Giỏ hàng" để báo hiệu đang ở trang này
        TextView tvMenuCart = findViewById(R.id.tvMenuCart);
        if (tvMenuCart != null) {
            tvMenuCart.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvMenuCart.setTypeface(null, Typeface.BOLD);
        }

        // Đặt màu xám mặc định cho các mục còn lại
        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        if (tvMenuHome != null) {
            tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvMenuHome.setTypeface(null, Typeface.NORMAL);
        }

        // Bấm "Sản phẩm" -> quay về danh sách sản phẩm
        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Bấm "Giỏ hàng" -> báo đang ở sẵn trang Giỏ hàng
        menuCart.setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Giỏ hàng", Toast.LENGTH_SHORT).show()
        );

        // Bấm "Đơn hàng" -> mở màn hình Lịch sử đơn mua
        if (menuOrders != null) {
            menuOrders.setOnClickListener(v ->
                    startActivity(new Intent(this, OrderHistoryActivity.class))
            );
        }

        // Bấm "Tài khoản" -> mở màn hình Tài khoản
        menuAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountActivity.class));
            finish();
        });
    }
}