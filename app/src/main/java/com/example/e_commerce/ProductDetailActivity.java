// Màn hình Chi tiết sản phẩm: hiển thị thông tin đầy đủ của một sản phẩm và xử lý thao tác thêm vào giỏ hoặc mua ngay
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProductDetailActivity extends AppCompatActivity {

    // Khai báo khóa truyền mã sản phẩm qua Intent và TextView hiển thị số lượng trên biểu tượng giỏ hàng
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    private TextView tvHeaderCartCount;
    private DatabaseHelper databaseHelper;

    // Hàm chạy đầu tiên khi mở màn hình chi tiết: tải thông tin sản phẩm và gắn sự kiện nút mua
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);
        databaseHelper = DatabaseHelper.getInstance(this);

        // Kết nối các biến Java với các thành phần trên giao diện
        TextView tvBack = findViewById(R.id.tvBack);
        TextView tvIcon = findViewById(R.id.tvIcon);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvPrice = findViewById(R.id.tvPrice);
        TextView tvDescription = findViewById(R.id.tvDescription);
        Button btnAddToCart = findViewById(R.id.btnAddToCart);
        Button btnBuyNow = findViewById(R.id.btnBuyNow);
        tvHeaderCartCount = findViewById(R.id.tvHeaderCartCount);

        // Nút quay lại danh sách sản phẩm
        tvBack.setOnClickListener(v -> finish());

        // Nút mở nhanh màn hình Giỏ hàng trên thanh tiêu đề
        findViewById(R.id.btnHeaderCart).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class))
        );

        // Lấy ID sản phẩm được truyền sang và tải dữ liệu từ Firestore
        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        ProductRepository.findById(productId, new ProductRepository.ProductCallback() {
            @Override
            public void onResult(Product product) {
                // Nếu không tìm thấy sản phẩm tương ứng thì thông báo và đóng màn hình
                if (product == null) {
                    Toast.makeText(ProductDetailActivity.this, "Không tìm thấy thông tin sản phẩm!", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // Hiển thị thông tin sản phẩm lên giao diện
                tvIcon.setText(product.getIcon());
                tvCategory.setText(product.getCategory());
                tvName.setText(product.getName());
                tvPrice.setText(product.getFormattedPrice());
                tvDescription.setText(product.getDescription());

                // 1. Nút "Thêm vào giỏ": lưu sản phẩm vào collection carts trên Firestore
                btnAddToCart.setOnClickListener(v -> {
                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    databaseHelper.addToCart(userEmail, product.getId(), 1);
                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), 1, success -> {
                        if (success) {
                            updateCartBadge();
                            new AlertDialog.Builder(ProductDetailActivity.this)
                                    .setTitle("🛒 Đã thêm vào giỏ hàng")
                                    .setMessage("Sản phẩm \"" + product.getName() + "\" đã được thêm vào giỏ hàng!")
                                    .setPositiveButton("Xem giỏ hàng", (dialog, which) ->
                                            startActivity(new Intent(ProductDetailActivity.this, CartActivity.class))
                                    )
                                    .setNegativeButton("Tiếp tục xem", null)
                                    .show();
                        } else {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
                        }
                    });
                });

                // 2. Nút "Mua ngay": thêm vào giỏ trên Firestore rồi mở màn hình Giỏ hàng
                btnBuyNow.setOnClickListener(v -> {
                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    databaseHelper.addToCart(userEmail, product.getId(), 1);
                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), 1,
                            success -> startActivity(new Intent(ProductDetailActivity.this, CartActivity.class)));
                });
            }

            @Override
            public void onError(Exception e) {
                // Thông báo lỗi và thoát màn hình nếu không tải được dữ liệu từ Firestore
                Toast.makeText(ProductDetailActivity.this, "Không thể tải sản phẩm.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // Cập nhật lại số lượng giỏ hàng trên góc phải mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    // Lấy tổng số sản phẩm trong giỏ hàng từ Firestore
    private void updateCartBadge() {
        if (tvHeaderCartCount != null) {
            String userEmail = SharedPrefManager.getInstance(this).getEmail();
            FirestoreRepository.getInstance().getCartCount(userEmail,
                    count -> tvHeaderCartCount.setText(String.valueOf(count)));
        }
    }
}