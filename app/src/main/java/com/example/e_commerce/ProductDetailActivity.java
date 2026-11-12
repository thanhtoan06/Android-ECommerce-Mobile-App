/**
 * Màn hình hiển thị chi tiết thông tin của sản phẩm được chọn và thêm vào giỏ hàng SQLite.
 */
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "extra_product_id"; // Khóa truyền ID sản phẩm qua Intent
    private TextView tvHeaderCartCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kích hoạt giao diện hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_product_detail);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        // Xử lý căn lề tránh bị che khuất bởi system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Khai báo các thành phần UI trên giao diện
        TextView tvBack = findViewById(R.id.tvBack);
        TextView tvIcon = findViewById(R.id.tvIcon);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvPrice = findViewById(R.id.tvPrice);
        TextView tvDescription = findViewById(R.id.tvDescription);
        Button btnAddToCart = findViewById(R.id.btnAddToCart);
        Button btnBuyNow = findViewById(R.id.btnBuyNow);
        tvHeaderCartCount = findViewById(R.id.tvHeaderCartCount);

        // Nút quay lại màn hình trước
        tvBack.setOnClickListener(v -> finish());

        // Nút mở nhanh giỏ hàng từ header
        findViewById(R.id.btnHeaderCart).setOnClickListener(v -> {
            Intent intent = new Intent(ProductDetailActivity.this, CartActivity.class);
            startActivity(intent);
        });

        // Nhận ID sản phẩm được truyền từ ProductListActivity và lấy dữ liệu thật từ SQLite
        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        Product product = ProductRepository.findById(this, productId);

        // Nếu tìm thấy sản phẩm, đổ dữ liệu lên giao diện
        if (product != null) {
            tvIcon.setText(product.getIcon());
            tvIcon.setBackgroundColor(product.getColor());
            tvCategory.setText(product.getCategory());
            tvName.setText(product.getName());
            tvPrice.setText(product.getFormattedPrice());
            tvDescription.setText(product.getDescription());

            // 1. Sự kiện bấm nút "THÊM VÀO GIỎ HÀNG" -> Lưu thật vào SQLite database!
            btnAddToCart.setOnClickListener(v -> {
                String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                boolean success = DatabaseHelper.getInstance(ProductDetailActivity.this).addToCart(userEmail, product.getId(), 1);
                if (success) {
                    updateCartBadge();
                    new AlertDialog.Builder(ProductDetailActivity.this)
                            .setTitle("🛒 Đã thêm vào giỏ hàng")
                            .setMessage("Sản phẩm \"" + product.getName() + "\" đã được thêm vào giỏ hàng của bạn!")
                            .setPositiveButton("Xem giỏ hàng", (dialog, which) -> {
                                Intent intent = new Intent(ProductDetailActivity.this, CartActivity.class);
                                startActivity(intent);
                            })
                            .setNegativeButton("Tiếp tục xem", null)
                            .show();
                } else {
                    Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
                }
            });

            // 2. Sự kiện bấm nút "MUA NGAY" -> Thêm vào SQLite rồi chuyển ngay sang Giỏ hàng
            btnBuyNow.setOnClickListener(v -> {
                String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                DatabaseHelper.getInstance(ProductDetailActivity.this).addToCart(userEmail, product.getId(), 1);
                Intent intent = new Intent(ProductDetailActivity.this, CartActivity.class);
                startActivity(intent);
            });
        } else {
            // Thông báo lỗi nếu không tìm thấy sản phẩm
            Toast.makeText(this, "Không tìm thấy sản phẩm trong cơ sở dữ liệu!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    private void updateCartBadge() {
        if (tvHeaderCartCount != null) {
            String userEmail = SharedPrefManager.getInstance(this).getEmail();
            int count = DatabaseHelper.getInstance(this).getCartCount(userEmail);
            tvHeaderCartCount.setText(String.valueOf(count));
        }
    }
}
