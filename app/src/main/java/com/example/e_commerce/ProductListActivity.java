// Màn hình Danh sách sản phẩm: tải danh sách sản phẩm chính thức từ Firebase, lưu bản sao SQLite và lọc theo danh mục.
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

    // Khai báo các thành phần giao diện: thông tin người dùng trên đầu trang, ListView sản phẩm và thanh danh mục
    private TextView tvHeaderAvatar;
    private TextView tvUserGreeting;
    private TextView tvUserEmail;
    private ListView listView;
    private LinearLayout layoutCategoryTabs;

    // Khai báo mảng toàn bộ sản phẩm, mảng sản phẩm sau khi lọc, danh mục đang chọn và kho bản sao SQLite
    private Product[] allProducts = new Product[0];
    private Product[] filteredProducts = new Product[0];
    private String selectedCategory = "Tất cả";
    private DatabaseHelper databaseHelper;

    // Hàm khởi tạo màn hình Danh sách sản phẩm: hiển thị thông tin người dùng, tải sản phẩm và gắn sự kiện bấm
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp giao diện XML của màn hình Danh sách sản phẩm
        setContentView(R.layout.activity_product_list);

        // Kết nối kho bản sao dữ liệu SQLite dưới máy
        databaseHelper = DatabaseHelper.getInstance(this);
        databaseHelper.getReadableDatabase();

        // Ánh xạ các ô chữ trên thanh đầu trang, danh sách ListView và thanh chứa nút danh mục
        tvHeaderAvatar = findViewById(R.id.tvHeaderAvatar);
        tvUserGreeting = findViewById(R.id.tvUserGreeting);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        listView = findViewById(R.id.listView);
        layoutCategoryTabs = findViewById(R.id.layoutCategoryTabs);

        // Hiển thị Avatar, lời chào và Email của tài khoản đang đăng nhập
        updateUserHeader();

        // Tải danh sách sản phẩm chính thức từ Firebase, lưu bản sao vào SQLite và hiển thị lên giao diện
        loadProductsFromFirebase();

        // Bắt sự kiện khi bấm vào 1 dòng sản phẩm -> mở màn hình Chi tiết sản phẩm kèm mã ID của món đó
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (filteredProducts != null && position < filteredProducts.length) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, filteredProducts[position].getId());
                startActivity(intent);
            }
        });

        // Bấm mục "Sản phẩm" trên thanh menu đáy (đang ở sẵn trang này nên hiện thông báo nhẹ)
        findViewById(R.id.menuHome).setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Sản phẩm", Toast.LENGTH_SHORT).show()
        );

        // Bấm mục "Giỏ hàng" trên thanh menu đáy -> chuyển sang màn hình Giỏ hàng
        findViewById(R.id.menuCart).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class))
        );

        // Bấm mục "Tài khoản" trên thanh menu đáy -> chuyển sang màn hình Tài khoản
        findViewById(R.id.menuAccount).setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class))
        );
    }

    // Tự động làm mới lời chào người dùng và danh sách sản phẩm mỗi khi quay lại màn hình này
    @Override
    protected void onResume() {
        super.onResume();
        updateUserHeader();
        loadProductsFromFirebase();
    }

    // Đọc thông tin tài khoản từ SharedPreferences để hiển thị Avatar, lời chào và Email trên đầu trang
    private void updateUserHeader() {
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        String username = prefManager.getUsername();
        String userEmail = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        tvHeaderAvatar.setText(avatar);
        tvUserGreeting.setText("Xin chào, " + (!username.isEmpty() ? username : "Quý khách"));
        tvUserEmail.setText(!userEmail.isEmpty() ? userEmail : "Chưa có email");
    }

    // Đọc danh sách sản phẩm chính thức từ Firebase -> lưu bản sao vào SQLite -> vẽ danh mục và danh sách
    private void loadProductsFromFirebase() {
        ProductRepository.getAll(new ProductRepository.ProductsCallback() {
            @Override
            public void onResult(Product[] products) {
                // Lưu mảng sản phẩm tải về từ Firebase và cập nhật bản sao xuống SQLite
                allProducts = products;
                for (Product product : products) {
                    databaseHelper.saveProduct(product);
                }

                // Tạo các nút lọc danh mục và hiển thị danh sách sản phẩm lên ListView
                buildCategoryTabs();
                filterProductsByCategory();
            }

            @Override
            public void onError(Exception e) {
                // Thông báo nếu không kết nối được với Firebase để tải danh sách sản phẩm
                Toast.makeText(ProductListActivity.this, "Không thể tải sản phẩm từ Firebase.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Tự động gom tên các danh mục từ danh sách sản phẩm để tạo thành các nút bấm lọc ở phía trên
    private void buildCategoryTabs() {
        // Xóa sạch các nút danh mục cũ trước khi vẽ lại thanh danh mục mới
        layoutCategoryTabs.removeAllViews();

        // Dùng LinkedHashSet để lọc lấy tên các danh mục không trùng lặp và giữ nguyên thứ tự xuất hiện
        Set<String> categories = new LinkedHashSet<>();
        categories.add("Tất cả");
        for (Product product : allProducts) {
            if (product.getCategory() != null && !product.getCategory().trim().isEmpty()) {
                categories.add(product.getCategory().trim());
            }
        }

        // Nếu danh mục đang chọn trước đó không còn tồn tại nữa thì tự động đưa về "Tất cả"
        if (!categories.contains(selectedCategory)) {
            selectedCategory = "Tất cả";
        }

        // Quy đổi kích thước khoảng đệm trong nút và khoảng cách giữa các nút từ đơn vị dp sang pixel
        int padH = dpToPx(14);
        int padV = dpToPx(6);
        int marginEnd = dpToPx(8);

        // Duyệt qua từng tên danh mục để tạo ra một nút bấm TextView tương ứng
        for (String category : categories) {
            TextView tab = new TextView(this);
            tab.setText(category);
            tab.setTextSize(13f);
            tab.setPadding(padH, padV, padH, padV);

            // Cài đặt kích thước vừa khít chữ và khoảng cách lề bên phải giữa các nút
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(marginEnd);
            tab.setLayoutParams(params);

            // Nút đang chọn thì tô nền xanh chữ trắng in đậm; nút chưa chọn thì để nền nhạt chữ xanh thường
            if (category.equals(selectedCategory)) {
                tab.setBackgroundResource(R.drawable.bg_primary_button);
                tab.setTextColor(ContextCompat.getColor(this, R.color.white));
                tab.setTypeface(null, Typeface.BOLD);
            } else {
                tab.setBackgroundResource(R.drawable.bg_secondary_button);
                tab.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tab.setTypeface(null, Typeface.NORMAL);
            }

            // Bắt sự kiện khi bấm vào nút danh mục: lưu danh mục mới, vẽ lại màu nút và lọc lại sản phẩm
            tab.setOnClickListener(v -> {
                selectedCategory = category;
                buildCategoryTabs();
                filterProductsByCategory();
            });

            // Thêm nút danh mục vừa tạo vào thanh ngang trên màn hình
            layoutCategoryTabs.addView(tab);
        }
    }

    // Lọc danh sách sản phẩm theo danh mục đang chọn và hiển thị lên ListView
    private void filterProductsByCategory() {
        if ("Tất cả".equals(selectedCategory)) {
            // Chọn "Tất cả" thì hiển thị toàn bộ danh sách sản phẩm
            filteredProducts = allProducts;
        } else {
            // Chọn danh mục riêng thì chỉ lọc ra các sản phẩm khớp với tên danh mục đó
            List<Product> result = new ArrayList<>();
            for (Product product : allProducts) {
                if (selectedCategory.equalsIgnoreCase(product.getCategory())) {
                    result.add(product);
                }
            }
            filteredProducts = result.toArray(new Product[0]);
        }

        // Gắn ProductAdapter để hiển thị mảng sản phẩm sau khi lọc lên ListView
        listView.setAdapter(new ProductAdapter(this, filteredProducts));
    }

    // Quy đổi đơn vị kích thước từ dp sang pixel chuẩn theo mật độ điểm ảnh của từng màn hình điện thoại
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}