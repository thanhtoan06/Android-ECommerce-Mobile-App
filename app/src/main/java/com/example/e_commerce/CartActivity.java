
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartActivity extends AppCompatActivity implements CartAdapter.OnCartItemListener {

    private ListView lvCartItems;
    private View layoutEmptyCart;
    private View layoutCheckoutBar;
    private TextView tvCartTotalPrice;
    private TextView tvCartHeaderTitle;
    private TextView btnClearAllCart;
    private Button btnCheckout;
    private CheckBox cbSelectAll;
    private boolean isUpdatingSelectAll = false;

    private final List<CartItem> cartItemList = new ArrayList<>();
    private CartAdapter adapter;
    private FirestoreRepository repository;
    private String currentUserEmail;
    private String selectedProductVoucherId;
    private String selectedShippingVoucherId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cart);

        repository = FirestoreRepository.getInstance();
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();
        selectedProductVoucherId = getIntent().getStringExtra(VoucherActivity.EXTRA_PRODUCT_VOUCHER_ID);
        selectedShippingVoucherId = getIntent().getStringExtra(VoucherActivity.EXTRA_SHIPPING_VOUCHER_ID);

        lvCartItems = findViewById(R.id.lvCartItems);
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart);
        layoutCheckoutBar = findViewById(R.id.layoutCheckoutBar);
        tvCartTotalPrice = findViewById(R.id.tvCartTotalPrice);
        tvCartHeaderTitle = findViewById(R.id.tvCartHeaderTitle);
        btnClearAllCart = findViewById(R.id.btnClearAllCart);
        btnCheckout = findViewById(R.id.btnCheckout);
        cbSelectAll = findViewById(R.id.cbSelectAll);

        if (cbSelectAll != null) {
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingSelectAll)
                    return;
                for (CartItem item : cartItemList) {
                    item.setSelected(isChecked);
                }
                adapter.notifyDataSetChanged();
                updateCartVisibility();
            });
        }

        adapter = new CartAdapter(this, cartItemList, this);
        lvCartItems.setAdapter(adapter);

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

        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnClearAllCart.setOnClickListener(v -> {

            if (cartItemList.isEmpty())
                return;

            new AlertDialog.Builder(this).setTitle("Xóa giỏ hàng")
                    .setMessage("Bạn có chắc chắn muốn xóa toàn bộ sản phẩm khỏi giỏ hàng?")
                    .setPositiveButton("Xóa tất cả", (dialog, which) -> {

                        repository.clearCart(currentUserEmail, success -> {
                            if (success) {

                                Toast.makeText(this, "Đã xóa toàn bộ giỏ hàng!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Xóa giỏ hàng thất bại!", Toast.LENGTH_SHORT).show();
                            }

                            loadCartData();
                        });
                    }).setNegativeButton("Hủy", null).show();
        });

        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(this, "Giỏ hàng đang trống!", Toast.LENGTH_SHORT).show();
                return;
            }

            List<CartItem> selectedItems = new ArrayList<>();
            ArrayList<Integer> selectedProductIds = new ArrayList<>();
            for (CartItem item : cartItemList) {
                if (item.isSelected()) {
                    selectedItems.add(item);
                    if (item.getProduct() != null) {
                        selectedProductIds.add(item.getProduct().getId());
                    }
                }
            }

            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất một sản phẩm để thanh toán!", Toast.LENGTH_SHORT).show();
                return;
            }

            for (CartItem item : selectedItems) {
                Product p = item.getProduct();
                if (p != null) {
                    if (p.getStock() <= 0) {
                        new AlertDialog.Builder(this).setTitle("Sản phẩm hết hàng").setMessage("Sản phẩm \""
                                + p.getName()
                                + "\" hiện đã hết hàng trong kho. Vui lòng bỏ chọn hoặc xóa món này khỏi giỏ trước khi thanh toán!")
                                .setPositiveButton("Đã hiểu", null).show();
                        return;
                    }
                    if (item.getQuantity() > p.getStock()) {
                        new AlertDialog.Builder(this).setTitle("Vượt quá số lượng tồn kho")
                                .setMessage("Sản phẩm \"" + p.getName() + "\" chỉ còn " + p.getStock()
                                        + " sản phẩm trong kho. Vui lòng giảm số lượng đặt mua trước khi thanh toán!")
                                .setPositiveButton("Đã hiểu", null).show();
                        return;
                    }
                }
            }

            Intent intent = new Intent(this, CheckoutActivity.class);
            intent.putIntegerArrayListExtra("SELECTED_PRODUCT_IDS", selectedProductIds);
            intent.putExtra(VoucherActivity.EXTRA_PRODUCT_VOUCHER_ID, selectedProductVoucherId);
            intent.putExtra(VoucherActivity.EXTRA_SHIPPING_VOUCHER_ID, selectedShippingVoucherId);
            startActivity(intent);
        });

        setupBottomMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCartData();
    }

    private void loadCartData() {
        repository.getCartItems(currentUserEmail, new FirestoreRepository.CartCallback() {
            @Override
            public void onResult(List<CartItem> items) {

                Map<Integer, Boolean> prevSelection = new HashMap<>();
                for (CartItem ci : cartItemList) {
                    if (ci.getProduct() != null) {
                        prevSelection.put(ci.getProduct().getId(), ci.isSelected());
                    }
                }

                cartItemList.clear();
                for (CartItem ci : items) {
                    if (ci.getProduct() != null && prevSelection.containsKey(ci.getProduct().getId())) {
                        ci.setSelected(Boolean.TRUE.equals(prevSelection.get(ci.getProduct().getId())));
                    }
                    cartItemList.add(ci);
                }

                adapter.notifyDataSetChanged();
                updateCartVisibility();
            }

            @Override
            public void onError(Exception e) {

                Toast.makeText(CartActivity.this, "Không thể tải giỏ hàng từ Firebase.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCartVisibility() {
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
            int selectedCount = 0;
            boolean allSelected = !cartItemList.isEmpty();

            for (CartItem item : cartItemList) {
                if (item.isSelected()) {
                    total += item.getSubtotal();
                    selectedCount++;
                } else {
                    allSelected = false;
                }
            }

            if (cbSelectAll != null) {
                isUpdatingSelectAll = true;
                cbSelectAll.setChecked(allSelected);
                isUpdatingSelectAll = false;
            }

            tvCartHeaderTitle.setText("Giỏ hàng (" + cartItemList.size() + ")");
            tvCartTotalPrice.setText(NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(total) + " ₫");
            btnCheckout.setText("THANH TOÁN (" + selectedCount + ")");
        }
    }

    @Override
    public void onItemSelectionChanged(CartItem item, boolean isSelected) {
        updateCartVisibility();
    }

    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        if (newQuantity <= 0) {

            new AlertDialog.Builder(this).setTitle("Xóa sản phẩm")
                    .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                    .setPositiveButton("Xóa", (dialog, which) -> {

                        repository.removeCartItem(currentUserEmail, item.getProduct().getId(), success -> {

                            loadCartData();
                        });
                    }).setNegativeButton("Giữ lại", null).show();
        } else {

            repository.updateCartQuantity(currentUserEmail, item.getProduct().getId(), newQuantity, success -> {

                loadCartData();
            });
        }
    }

    @Override
    public void onItemDeleted(CartItem item) {
        new AlertDialog.Builder(this).setTitle("Xóa sản phẩm")
                .setMessage("Bạn có muốn xóa \"" + item.getProduct().getName() + "\" khỏi giỏ hàng?")
                .setPositiveButton("Xóa", (dialog, which) -> {

                    repository.removeCartItem(currentUserEmail, item.getProduct().getId(), success -> {
                        if (success) {

                            Toast.makeText(this, "Đã xóa sản phẩm khỏi giỏ hàng!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Xóa sản phẩm thất bại!", Toast.LENGTH_SHORT).show();
                        }
                        loadCartData();
                    });
                }).setNegativeButton("Hủy", null).show();
    }

    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);

        if (ivMenuHome != null)
            ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuCart != null)
            ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));
        if (ivMenuAccount != null)
            ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));

        TextView tvMenuCart = findViewById(R.id.tvMenuCart);
        if (tvMenuCart != null) {
            tvMenuCart.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvMenuCart.setTypeface(null, Typeface.BOLD);
        }

        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        if (tvMenuHome != null) {
            tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvMenuHome.setTypeface(null, Typeface.NORMAL);
        }

        menuHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        menuCart.setOnClickListener(v -> Toast.makeText(this, "Bạn đang ở trang Giỏ hàng", Toast.LENGTH_SHORT).show());

        menuAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, AccountActivity.class));
            finish();
        });

        findViewById(R.id.menuNotifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
    }
}
