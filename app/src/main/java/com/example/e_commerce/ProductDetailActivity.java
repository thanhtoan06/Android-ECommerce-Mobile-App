
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    private TextView tvHeaderCartCount;
    private int quantity = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_product_detail);

        View btnBack = findViewById(R.id.btnBack);
        TextView tvIcon = findViewById(R.id.tvIcon);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvStock = findViewById(R.id.tvStock);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvPrice = findViewById(R.id.tvPrice);
        TextView tvDescription = findViewById(R.id.tvDescription);
        TextView tvQuantity = findViewById(R.id.tvQuantity);
        View btnDecrease = findViewById(R.id.btnDecrease);
        View btnIncrease = findViewById(R.id.btnIncrease);
        Button btnAddToCart = findViewById(R.id.btnAddToCart);
        Button btnBuyNow = findViewById(R.id.btnBuyNow);
        tvHeaderCartCount = findViewById(R.id.tvHeaderCartCount);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnHeaderCart).setOnClickListener(v -> {
            if (AuthGate.requireLogin(this)) {
                startActivity(new Intent(this, CartActivity.class));
            }
        });

        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        ProductRepository.findById(productId, new ProductRepository.ProductCallback() {
            @Override
            public void onResult(Product product) {

                if (product == null) {
                    Toast.makeText(ProductDetailActivity.this, "Không tìm thấy thông tin sản phẩm!", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                    return;
                }

                tvIcon.setText(product.getIcon());
                tvCategory.setText(product.getCategory());
                tvName.setText(product.getName());
                tvPrice.setText(product.getFormattedPrice());
                tvDescription.setText(product.getDescription());

                int stock = product.getStock();
                if (tvStock != null) {
                    if (stock <= 0) {
                        tvStock.setText("Hết hàng");
                        tvStock.setTextColor(0xFFEF4444);

                    } else if (stock <= 5) {
                        tvStock.setText("Còn " + stock + " sp (sắp hết)");
                        tvStock.setTextColor(0xFFF97316);

                    } else {
                        tvStock.setText("Còn " + stock + " sp");
                        tvStock.setTextColor(0xFF64748B);

                    }
                }

                if (stock <= 0) {
                    if (tvQuantity != null)
                        tvQuantity.setText("0");
                    if (btnDecrease != null)
                        btnDecrease.setEnabled(false);
                    if (btnIncrease != null)
                        btnIncrease.setEnabled(false);
                    btnAddToCart.setEnabled(false);
                    btnAddToCart.setAlpha(0.4f);
                    btnAddToCart.setText("Hết hàng");
                    btnBuyNow.setEnabled(false);
                    btnBuyNow.setAlpha(0.4f);
                    btnBuyNow.setText("Hết hàng");
                    return;
                }

                quantity = 1;
                if (tvQuantity != null) {
                    tvQuantity.setText(String.valueOf(quantity));
                }
                if (btnDecrease != null) {
                    btnDecrease.setOnClickListener(v -> {
                        if (quantity > 1) {
                            quantity--;
                            if (tvQuantity != null)
                                tvQuantity.setText(String.valueOf(quantity));
                        }
                    });
                }
                if (btnIncrease != null) {
                    btnIncrease.setOnClickListener(v -> {
                        if (quantity < stock) {
                            quantity++;
                            if (tvQuantity != null)
                                tvQuantity.setText(String.valueOf(quantity));
                        } else {
                            Toast.makeText(ProductDetailActivity.this, "Đã đạt số lượng tồn kho tối đa (" + stock + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                btnAddToCart.setOnClickListener(v -> {
                    if (stock <= 0) {
                        Toast.makeText(ProductDetailActivity.this, "Sản phẩm hiện đã hết hàng!", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    if (!AuthGate.requireLogin(ProductDetailActivity.this)) {
                        return;
                    }

                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    btnAddToCart.setEnabled(false);

                    final int selectedQty = Math.min(quantity, stock);
                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), selectedQty, success -> {
                        btnAddToCart.setEnabled(true);
                        if (!success) {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        updateCartBadge();
                        new AlertDialog.Builder(ProductDetailActivity.this).setTitle("Đã thêm vào giỏ hàng")
                                .setMessage("Sản phẩm \"" + product.getName() + "\" (" + selectedQty
                                        + " sản phẩm) đã được thêm vào giỏ hàng!")
                                .setPositiveButton("Xem giỏ hàng",
                                        (dialog, which) -> startActivity(
                                                new Intent(ProductDetailActivity.this, CartActivity.class)))
                                .setNegativeButton("Tiếp tục xem", null).show();
                    });
                });

                btnBuyNow.setOnClickListener(v -> {
                    if (stock <= 0) {
                        Toast.makeText(ProductDetailActivity.this, "Sản phẩm hiện đã hết hàng!", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    if (!AuthGate.requireLogin(ProductDetailActivity.this)) {
                        return;
                    }

                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    btnBuyNow.setEnabled(false);

                    final int selectedQty = Math.min(quantity, stock);
                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), selectedQty, success -> {
                        btnBuyNow.setEnabled(true);
                        if (!success) {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        startActivity(new Intent(ProductDetailActivity.this, CartActivity.class));
                    });
                });
            }

            @Override
            public void onError(Exception e) {

                Toast.makeText(ProductDetailActivity.this, "Không thể tải sản phẩm từ Firebase.", Toast.LENGTH_SHORT)
                        .show();
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    private void updateCartBadge() {
        if (tvHeaderCartCount != null) {
            if (!SharedPrefManager.getInstance(this).isLoggedIn()) {
                tvHeaderCartCount.setText("0");
                return;
            }
            String userEmail = SharedPrefManager.getInstance(this).getEmail();
            FirestoreRepository.getInstance().getCartCount(userEmail,
                    count -> tvHeaderCartCount.setText(String.valueOf(count)));
        }
    }
}
