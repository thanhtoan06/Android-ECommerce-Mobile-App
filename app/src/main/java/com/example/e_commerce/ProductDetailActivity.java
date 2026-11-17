// Màn hình Chi tiết sản phẩm: tải thông tin sản phẩm từ Firebase, lưu bản sao SQLite và xử lý Thêm vào giỏ / Mua ngay.
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProductDetailActivity extends AppCompatActivity {

    // Khai báo tên khóa (Key) nhận mã ID sản phẩm truyền từ trang danh sách sang, nhãn đếm giỏ hàng và kho SQLite
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    private TextView tvHeaderCartCount;
    private DatabaseHelper databaseHelper;

    // Hàm khởi tạo màn hình Chi tiết sản phẩm: tải thông tin từ Firebase, lưu bản sao SQLite và gắn sự kiện nút mua
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp giao diện XML của màn hình Chi tiết sản phẩm
        setContentView(R.layout.activity_product_detail);
        // Kết nối kho bản sao dữ liệu SQLite dưới máy
        databaseHelper = DatabaseHelper.getInstance(this);

        // Ánh xạ các nhãn hiển thị (biểu tượng, danh mục, tên, giá, mô tả) và các nút bấm từ file XML
        TextView tvBack = findViewById(R.id.tvBack);
        TextView tvIcon = findViewById(R.id.tvIcon);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvName = findViewById(R.id.tvName);
        TextView tvPrice = findViewById(R.id.tvPrice);
        TextView tvDescription = findViewById(R.id.tvDescription);
        Button btnAddToCart = findViewById(R.id.btnAddToCart);
        Button btnBuyNow = findViewById(R.id.btnBuyNow);
        tvHeaderCartCount = findViewById(R.id.tvHeaderCartCount);

        // Bấm mũi tên quay lại thì đóng màn hình hiện tại để trở về trang Danh sách sản phẩm
        tvBack.setOnClickListener(v -> finish());

        // Bấm biểu tượng giỏ hàng ở góc trên bên phải thì mở sang màn hình Giỏ hàng
        findViewById(R.id.btnHeaderCart).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class))
        );

        // Bước 1: Đọc thông tin chi tiết sản phẩm chính thức từ Firebase theo productId truyền sang
        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        ProductRepository.findById(productId, new ProductRepository.ProductCallback() {
            @Override
            public void onResult(Product product) {
                // Nếu không tìm thấy sản phẩm trên Firebase thì báo lỗi và đóng màn hình
                if (product == null) {
                    Toast.makeText(ProductDetailActivity.this, "Không tìm thấy thông tin sản phẩm!", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // Lưu bản sao thông tin sản phẩm vừa tải về xuống SQLite
                databaseHelper.saveProduct(product);

                // Đổ toàn bộ thông tin sản phẩm (biểu tượng, danh mục, tên, giá tiền, mô tả) lên màn hình
                tvIcon.setText(product.getIcon());
                tvCategory.setText(product.getCategory());
                tvName.setText(product.getName());
                tvPrice.setText(product.getFormattedPrice());
                tvDescription.setText(product.getDescription());

                // Gắn sự kiện nút "Thêm vào giỏ": ghi lên Firebase trước -> thành công mới lưu bản sao SQLite
                btnAddToCart.setOnClickListener(v -> {
                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    btnAddToCart.setEnabled(false);

                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), 1, success -> {
                        btnAddToCart.setEnabled(true);
                        if (!success) {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Cập nhật bản sao giỏ hàng dưới SQLite, làm mới số đỏ trên giỏ và hiện hộp thoại xác nhận
                        databaseHelper.addToCart(userEmail, product.getId(), 1);
                        updateCartBadge();
                        new AlertDialog.Builder(ProductDetailActivity.this)
                                .setTitle("🛒 Đã thêm vào giỏ hàng")
                                .setMessage("Sản phẩm \"" + product.getName() + "\" đã được thêm vào giỏ hàng!")
                                .setPositiveButton("Xem giỏ hàng", (dialog, which) ->
                                        startActivity(new Intent(ProductDetailActivity.this, CartActivity.class))
                                )
                                .setNegativeButton("Tiếp tục xem", null)
                                .show();
                    });
                });

                // Gắn sự kiện nút "Mua ngay": thêm lên Firebase trước -> thành công mới lưu SQLite và mở Giỏ hàng
                btnBuyNow.setOnClickListener(v -> {
                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    btnBuyNow.setEnabled(false);

                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), 1, success -> {
                        btnBuyNow.setEnabled(true);
                        if (!success) {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Lưu bản sao vào SQLite và chuyển thẳng sang màn hình Giỏ hàng
                        databaseHelper.addToCart(userEmail, product.getId(), 1);
                        startActivity(new Intent(ProductDetailActivity.this, CartActivity.class));
                    });
                });
            }

            @Override
            public void onError(Exception e) {
                // Thông báo lỗi nếu mất kết nối mạng khi tải chi tiết sản phẩm từ Firebase
                Toast.makeText(ProductDetailActivity.this, "Không thể tải sản phẩm từ Firebase.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // Tự động cập nhật lại con số hiển thị trên biểu tượng giỏ hàng mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    // Đọc tổng số lượng sản phẩm trong giỏ từ Firebase và hiển thị lên huy hiệu góc trên bên phải
    private void updateCartBadge() {
        if (tvHeaderCartCount != null) {
            String userEmail = SharedPrefManager.getInstance(this).getEmail();
            FirestoreRepository.getInstance().getCartCount(userEmail,
                    count -> tvHeaderCartCount.setText(String.valueOf(count)));
        }
    }
}