// Màn hình Danh sách sản phẩm: hiển thị thông tin người dùng, tạo bộ lọc theo danh mục và đổ danh sách sản phẩm từ Firestore lên ListView
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProductListActivity extends AppCompatActivity {

    // Khai báo ListView hiển thị danh sách sản phẩm và thanh chứa các tab danh mục
    private ListView listView;
    private LinearLayout layoutCategoryTabs;

    private Product[] allProducts = new Product[0];      // Toàn bộ sản phẩm lấy từ Firestore
    private Product[] filteredProducts = new Product[0]; // Danh sách sản phẩm sau khi lọc theo danh mục
    private String selectedCategory = "Tất cả";          // Danh mục đang được chọn
    private DatabaseHelper databaseHelper;

    // Hàm chạy đầu tiên khi mở màn hình chính: hiển thị tên + email, tải sản phẩm và gắn sự kiện bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);
        // Khởi tạo SQLite song song; Firestore vẫn là nguồn dữ liệu chính để hiển thị.
        databaseHelper = DatabaseHelper.getInstance(this);
        databaseHelper.getReadableDatabase();

        // Lấy thông tin người dùng đang đăng nhập từ SharedPreferences
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        String username = prefManager.getUsername();
        String userEmail = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        // Hiển thị Avatar, Tên và Email lên phần đầu trang
        TextView tvHeaderAvatar = findViewById(R.id.tvHeaderAvatar);
        TextView tvUserGreeting = findViewById(R.id.tvUserGreeting);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);

        tvHeaderAvatar.setText(avatar);
        tvUserGreeting.setText("Xin chào, " + (!username.isEmpty() ? username : "Quý khách"));
        tvUserEmail.setText(!userEmail.isEmpty() ? userEmail : "Chưa có email");

        // Kết nối ListView và thanh tab danh mục
        listView = findViewById(R.id.listView);
        layoutCategoryTabs = findViewById(R.id.layoutCategoryTabs);

        // Tải danh sách sản phẩm từ Firestore và tạo các tab danh mục
        loadProductsFromDatabase();

        // Khi bấm vào một sản phẩm -> mở màn hình Chi tiết sản phẩm và truyền kèm ID
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (filteredProducts != null && position < filteredProducts.length) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, filteredProducts[position].getId());
                startActivity(intent);
            }
        });

        // Xử lý bấm các nút trên thanh menu điều hướng phía dưới cùng
        findViewById(R.id.menuHome).setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Sản phẩm", Toast.LENGTH_SHORT).show()
        );

        // Chuyển sang màn hình Giỏ hàng
        findViewById(R.id.menuCart).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class))
        );

        // Chuyển sang màn hình Tài khoản
        findViewById(R.id.menuAccount).setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class))
        );
    }

    // Tự động tải lại sản phẩm mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        loadProductsFromDatabase();
    }

    // Lấy toàn bộ sản phẩm từ Firestore, tạo tab danh mục và hiển thị danh sách tương ứng
    private void loadProductsFromDatabase() {
        ProductRepository.getAll(new ProductRepository.ProductsCallback() {
            @Override
            public void onResult(Product[] products) {
                // Lưu toàn bộ sản phẩm, dựng lại các nút lọc danh mục và lọc sản phẩm
                allProducts = products;
                buildCategoryTabs();
                filterProductsByCategory();
            }

            @Override
            public void onError(Exception e) {
                // Thông báo nếu tải danh sách sản phẩm từ Firestore thất bại
                Toast.makeText(ProductListActivity.this, "Không thể tải sản phẩm từ Firestore.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Tự động lấy các danh mục từ danh sách sản phẩm để tạo thành các nút tab lọc
    private void buildCategoryTabs() {
        // Xóa các tab cũ trước khi tạo lại thanh danh mục mới
        layoutCategoryTabs.removeAllViews();

        // Dùng LinkedHashSet để gom các tên danh mục không trùng lặp và giữ nguyên thứ tự
        Set<String> categories = new LinkedHashSet<>();
        categories.add("Tất cả");
        for (Product product : allProducts) {
            if (product.getCategory() != null && !product.getCategory().trim().isEmpty()) {
                categories.add(product.getCategory().trim());
            }
        }

        // Nếu danh mục đang chọn không còn tồn tại thì đặt lại về "Tất cả"
        if (!categories.contains(selectedCategory)) {
            selectedCategory = "Tất cả";
        }

        // Quy đổi khoảng đệm và khoảng cách giữa các tab từ dp sang pixel
        int padH = dpToPx(14);
        int padV = dpToPx(6);
        int marginEnd = dpToPx(8);

        // Duyệt từng danh mục để tạo nút bấm TextView tương ứng
        for (String category : categories) {
            TextView tab = new TextView(this);
            tab.setText(category);
            tab.setTextSize(13f);
            tab.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(marginEnd);
            tab.setLayoutParams(params);

            // Làm nổi bật tab đang được chọn
            if (category.equals(selectedCategory)) {
                tab.setBackgroundResource(R.drawable.bg_primary_button);
                tab.setTextColor(ContextCompat.getColor(this, R.color.white));
                tab.setTypeface(null, Typeface.BOLD);
            } else {
                tab.setBackgroundResource(R.drawable.bg_secondary_button);
                tab.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tab.setTypeface(null, Typeface.NORMAL);
            }

            // Khi bấm vào tab danh mục -> cập nhật trạng thái tab và lọc lại danh sách sản phẩm
            tab.setOnClickListener(v -> {
                selectedCategory = category;
                buildCategoryTabs();
                filterProductsByCategory();
            });

            layoutCategoryTabs.addView(tab);
        }
    }

    // Lọc danh sách sản phẩm theo danh mục đang chọn và hiển thị lên ListView
    private void filterProductsByCategory() {
        if ("Tất cả".equals(selectedCategory)) {
            // Hiển thị toàn bộ sản phẩm nếu đang chọn mục "Tất cả"
            filteredProducts = allProducts;
        } else {
            // Lọc các sản phẩm có danh mục trùng khớp với tab đang chọn
            List<Product> result = new ArrayList<>();
            for (Product product : allProducts) {
                if (selectedCategory.equalsIgnoreCase(product.getCategory())) {
                    result.add(product);
                }
            }
            filteredProducts = result.toArray(new Product[0]);
        }

        // Đổ danh sách sản phẩm sau khi lọc lên ListView qua ProductAdapter
        listView.setAdapter(new ProductAdapter(this, filteredProducts));
    }

    // Đổi đơn vị dp sang pixel
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}