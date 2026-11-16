/**
 * CartActivity.java: Màn hình Giỏ hàng hiển thị sản phẩm đã chọn từ SQLite, cho phép tăng/giảm số lượng, xóa và chuyển sang thanh toán.
 */
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity implements CartAdapter.OnCartItemListener {

    private ListView lvCartItems;
    private View layoutEmptyCart;
    private View layoutCheckoutBar;
    private TextView tvCartTotalPrice;
    private TextView tvCartHeaderTitle;
    private TextView btnClearAllCart;
    private Button btnCheckout;

    private final List<CartItem> cartItemList = new ArrayList<>();
    private CartAdapter adapter;
    private DatabaseHelper databaseHelper;
    private String currentUserEmail;

    // Hàm chạy đầu tiên khi mở màn hình Giỏ hàng: kết nối giao diện và gắn sự kiện các nút bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        databaseHelper = DatabaseHelper.getInstance(this);
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Kết nối các biến Java với các thành phần trên giao diện
        lvCartItems = findViewById(R.id.lvCartItems);
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart);
        layoutCheckoutBar = findViewById(R.id.layoutCheckoutBar);
        tvCartTotalPrice = findViewById(R.id.tvCartTotalPrice);
        tvCartHeaderTitle = findViewById(R.id.tvCartHeaderTitle);
        btnClearAllCart = findViewById(R.id.btnClearAllCart);
        btnCheckout = findViewById(R.id.btnCheckout);

        // Khởi tạo Adapter đổ danh sách sản phẩm trong giỏ lên ListView
        adapter = new CartAdapter(this, cartItemList, this);
        lvCartItems.setAdapter(adapter);

        // Khi bấm vào một sản phẩm trong giỏ -> mở lại trang Chi tiết sản phẩm đó
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

        // Nút "Khám phá sản phẩm" khi giỏ hàng đang trống
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Nút "Xóa tất cả": hỏi xác nhận rồi xóa sạch giỏ hàng trong SQLite
        btnClearAllCart.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Xóa giỏ hàng")
                    .setMessage("Bạn có chắc chắn muốn xóa toàn bộ sản phẩm khỏi giỏ hàng?")
                    .setPositiveButton("Xóa tất cả", (dialog, which) -> {
                        databaseHelper.clearCart(currentUserEmail);
                        loadCartData();
                        Toast.makeText(this, "Đã xóa toàn bộ giỏ hàng!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Nút "Thanh toán": chuyển sang màn hình nhập thông tin đặt hàng (CheckoutActivity)
        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(this, "Giỏ hàng đang trống!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, CheckoutActivity.class));
        });

        // Cài đặt thanh menu điều hướng dưới cùng
        setupBottomMenu();
    }

    // Tự động tải lại dữ liệu giỏ hàng mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadCartData();
    }

    // Đọc danh sách giỏ hàng từ SQLite, tính tổng số lượng + tổng tiền và cập nhật giao diện
    private void loadCartData() {
        cartItemList.clear();
        cartItemList.addAll(databaseHelper.getCartItems(currentUserEmail));
        adapter.notifyDataSetChanged();

        if (cartItemList.isEmpty()) {
            // Hiển thị màn hình thông báo giỏ hàng trống
            layoutEmptyCart.setVisibility(View.VISIBLE);
            lvCartItems.setVisibility(View.GONE);
            layoutCheckoutBar.setVisibility(View.GONE);
            btnClearAllCart.setVisibility(View.GONE);
            tvCartHeaderTitle.setText("Giỏ hàng của tôi");
        } else {
            // Hiển thị danh sách món hàng và thanh tổng thanh toán
            layoutEmptyCart.setVisibility(View.GONE);
            lvCartItems.setVisibility(View.VISIBLE);
            layoutCheckoutBar.setVisibility(View.VISIBLE);
            btnClearAllCart.setVisibility(View.VISIBLE);

            long total = 0;
            int totalQuantity = 0;
            for (CartItem item : cartItemList) {
                total += item.getSubtotal();
                totalQuantity += item.getQuantity();
            }

            tvCartHeaderTitle.setText("Giỏ hàng (" + totalQuantity + ")");
            tvCartTotalPrice.setText(NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(total) + " ₫");
            btnCheckout.setText("THANH TOÁN (" + totalQuantity + ")");
        }
    }

    // Xử lý khi người dùng bấm nút (+) hoặc (−) để thay đổi số lượng sản phẩm trong giỏ
    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        if (newQuantity <= 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Xóa sản phẩm")
                    .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        databaseHelper.removeCartItem(item.getId());
                        loadCartData();
                    })
                    .setNegativeButton("Giữ lại", null)
                    .show();
        } else {
            databaseHelper.updateCartQuantity(item.getId(), newQuantity);
            loadCartData();
        }
    }

    // Xử lý khi người dùng bấm nút thùng rác xóa một sản phẩm khỏi giỏ
    @Override
    public void onItemDeleted(CartItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa sản phẩm")
                .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    databaseHelper.removeCartItem(item.getId());
                    loadCartData();
                    Toast.makeText(this, "Đã xóa sản phẩm khỏi giỏ hàng!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Đánh dấu tab Giỏ hàng đang chọn và gắn sự kiện chuyển trang cho thanh menu dưới cùng
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        // Tô đậm tab "Giỏ hàng"
        if (menuCart instanceof ViewGroup) {
            ViewGroup cartGroup = (ViewGroup) menuCart;
            for (int i = 0; i < cartGroup.getChildCount(); i++) {
                View child = cartGroup.getChildAt(i);
                if (child instanceof TextView && "Giỏ hàng".contentEquals(((TextView) child).getText())) {
                    ((TextView) child).setTextColor(Color.parseColor("#1976D2"));
                    ((TextView) child).setTypeface(null, Typeface.BOLD);
                }
            }
        }

        // Đặt lại màu chữ bình thường cho tab "Sản phẩm"
        if (menuHome instanceof ViewGroup) {
            ViewGroup homeGroup = (ViewGroup) menuHome;
            for (int i = 0; i < homeGroup.getChildCount(); i++) {
                View child = homeGroup.getChildAt(i);
                if (child instanceof TextView && "Sản phẩm".contentEquals(((TextView) child).getText())) {
                    ((TextView) child).setTextColor(Color.parseColor("#757575"));
                    ((TextView) child).setTypeface(null, Typeface.NORMAL);
                }
            }
        }

        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        menuCart.setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Giỏ hàng", Toast.LENGTH_SHORT).show()
        );

        menuAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountActivity.class));
            finish();
        });
    }
}