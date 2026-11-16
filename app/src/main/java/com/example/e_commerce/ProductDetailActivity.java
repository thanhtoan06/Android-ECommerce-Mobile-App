/**
 * ProductDetailActivity.java: Hiển thị thông tin chi tiết một sản phẩm từ SQLite và xử lý thêm vào giỏ hàng / mua ngay.
 */
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    private TextView tvHeaderCartCount;

    // Hàm chạy đầu tiên khi mở màn hình chi tiết: đọc thông tin sản phẩm theo ID và gắn sự kiện nút mua
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

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

        // Lấy ID sản phẩm được truyền sang từ ProductListActivity và tìm trong SQLite
        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        Product product = ProductRepository.findById(this, productId);

        if (product != null) {
            // Đổ thông tin thật của sản phẩm lên giao diện
            tvIcon.setText(product.getIcon());
            tvCategory.setText(product.getCategory());
            tvName.setText(product.getName());
            tvPrice.setText(product.getFormattedPrice());
            tvDescription.setText(product.getDescription());

            // 1. Nút "Thêm vào giỏ": lưu sản phẩm vào bảng cart trong SQLite
            btnAddToCart.setOnClickListener(v -> {
                String userEmail = SharedPrefManager.getInstance(this).getEmail();
                boolean success = DatabaseHelper.getInstance(this).addToCart(userEmail, product.getId(), 1);
                if (success) {
                    updateCartBadge();
                    new AlertDialog.Builder(this)
                            .setTitle("🛒 Đã thêm vào giỏ hàng")
                            .setMessage("Sản phẩm \"" + product.getName() + "\" đã được thêm vào giỏ hàng!")
                            .setPositiveButton("Xem giỏ hàng", (dialog, which) ->
                                    startActivity(new Intent(this, CartActivity.class))
                            )
                            .setNegativeButton("Tiếp tục xem", null)
                            .show();
                } else {
                    Toast.makeText(this, "Không thể thêm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
                }
            });

            // 2. Nút "Mua ngay": thêm vào giỏ hàng SQLite và chuyển thẳng sang màn hình Giỏ hàng
            btnBuyNow.setOnClickListener(v -> {
                String userEmail = SharedPrefManager.getInstance(this).getEmail();
                DatabaseHelper.getInstance(this).addToCart(userEmail, product.getId(), 1);
                startActivity(new Intent(this, CartActivity.class));
            });
        } else {
            Toast.makeText(this, "Không tìm thấy thông tin sản phẩm!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Cập nhật lại số lượng giỏ hàng trên góc phải mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    // Lấy tổng số sản phẩm đang có trong giỏ hàng từ SQLite để hiển thị lên icon giỏ hàng
    private void updateCartBadge() {
        if (tvHeaderCartCount != null) {
            String userEmail = SharedPrefManager.getInstance(this).getEmail();
            int count = DatabaseHelper.getInstance(this).getCartCount(userEmail);
            tvHeaderCartCount.setText(String.valueOf(count));
        }
    }
}