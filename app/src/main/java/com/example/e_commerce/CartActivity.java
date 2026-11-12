package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình Giỏ hàng (CartActivity) hiển thị các sản phẩm người dùng đã chọn mua,
 * cho phép tăng giảm số lượng, xóa và tiến hành thanh toán trực tiếp từ SQLite.
 */
public class CartActivity extends AppCompatActivity implements CartAdapter.OnCartItemListener {

    private ListView lvCartItems;
    private View layoutEmptyCart;
    private View layoutCheckoutBar;
    private TextView tvCartTotalPrice;
    private TextView tvCartHeaderTitle;
    private TextView btnClearAllCart;
    private Button btnCheckout;

    private List<CartItem> cartItemList = new ArrayList<>();
    private CartAdapter adapter;
    private DatabaseHelper databaseHelper;
    private String currentUserEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kích hoạt giao diện hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_cart);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        // Căn lề tự động tránh che khuất bởi system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        databaseHelper = DatabaseHelper.getInstance(this);
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        // Ánh xạ View
        lvCartItems = findViewById(R.id.lvCartItems);
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart);
        layoutCheckoutBar = findViewById(R.id.layoutCheckoutBar);
        tvCartTotalPrice = findViewById(R.id.tvCartTotalPrice);
        tvCartHeaderTitle = findViewById(R.id.tvCartHeaderTitle);
        btnClearAllCart = findViewById(R.id.btnClearAllCart);
        btnCheckout = findViewById(R.id.btnCheckout);

        // Adapter cho ListView
        adapter = new CartAdapter(this, cartItemList, this);
        lvCartItems.setAdapter(adapter);

        // Sự kiện bấm vào sản phẩm trong giỏ hàng để xem lại chi tiết
        lvCartItems.setOnItemClickListener((parent, view, position, id) -> {
            if (position < cartItemList.size()) {
                CartItem item = cartItemList.get(position);
                if (item.getProduct() != null) {
                    Intent intent = new Intent(CartActivity.this, ProductDetailActivity.class);
                    intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, item.getProduct().getId());
                    startActivity(intent);
                }
            }
        });

        // Nút khám phá sản phẩm khi giỏ hàng trống
        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(CartActivity.this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Nút xóa toàn bộ giỏ hàng
        btnClearAllCart.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) return;
            new AlertDialog.Builder(CartActivity.this)
                    .setTitle("Xóa giỏ hàng")
                    .setMessage("Bạn có chắc chắn muốn xóa toàn bộ sản phẩm khỏi giỏ hàng?")
                    .setPositiveButton("Xóa tất cả", (dialog, which) -> {
                        databaseHelper.clearCart(currentUserEmail);
                        loadCartData();
                        Toast.makeText(CartActivity.this, "Đã xóa toàn bộ giỏ hàng!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Nút Tiến hành thanh toán
        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(CartActivity.this, "Giỏ hàng đang trống!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(CartActivity.this, CheckoutActivity.class);
            startActivity(intent);
        });

        setupBottomMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCartData();
    }

    /**
     * Tải lại toàn bộ dữ liệu giỏ hàng từ database SQLite
     */
    private void loadCartData() {
        cartItemList.clear();
        List<CartItem> items = databaseHelper.getCartItems(currentUserEmail);
        cartItemList.addAll(items);
        adapter.notifyDataSetChanged();

        if (cartItemList.isEmpty()) {
            layoutEmptyCart.setVisibility(View.VISIBLE);
            lvCartItems.setVisibility(View.GONE);
            layoutCheckoutBar.setVisibility(View.GONE);
            btnClearAllCart.setVisibility(View.GONE);
            tvCartHeaderTitle.setText("Giỏ hàng của tôi");
        } else {
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

    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        if (newQuantity <= 0) {
            // Xác nhận xóa khi giảm về 0
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

    @Override
    public void onItemDeleted(CartItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa sản phẩm")
                .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    databaseHelper.removeCartItem(item.getId());
                    loadCartData();
                    Toast.makeText(CartActivity.this, "Đã xóa sản phẩm khỏi giỏ hàng!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Cài đặt thanh Menu dưới cùng và tô màu nổi bật tab Giỏ hàng
     */
    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        // Highlight tab Giỏ hàng
        if (menuCart instanceof android.view.ViewGroup) {
            android.view.ViewGroup cartGroup = (android.view.ViewGroup) menuCart;
            for (int i = 0; i < cartGroup.getChildCount(); i++) {
                View child = cartGroup.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if ("Giỏ hàng".contentEquals(tv.getText())) {
                        tv.setTextColor(Color.parseColor("#1976D2"));
                        tv.setTypeface(null, Typeface.BOLD);
                    }
                }
            }
        }

        // Bỏ highlight tab Sản phẩm
        if (menuHome instanceof android.view.ViewGroup) {
            android.view.ViewGroup homeGroup = (android.view.ViewGroup) menuHome;
            for (int i = 0; i < homeGroup.getChildCount(); i++) {
                View child = homeGroup.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if ("Sản phẩm".contentEquals(tv.getText())) {
                        tv.setTextColor(Color.parseColor("#757575"));
                        tv.setTypeface(null, Typeface.NORMAL);
                    }
                }
            }
        }

        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(CartActivity.this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        menuCart.setOnClickListener(v ->
                Toast.makeText(CartActivity.this, "Bạn đang ở trang Giỏ hàng", Toast.LENGTH_SHORT).show()
        );

        menuAccount.setOnClickListener(v -> {
            Intent intent = new Intent(CartActivity.this, AccountActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
