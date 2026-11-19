// Màn hình Danh sách sản phẩm: tìm kiếm thời gian thực, sắp xếp giá, lọc danh mục, bố cục lưới 2 cột và vuốt để làm mới.
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProductListActivity extends AppCompatActivity {

    // Khai báo các chế độ sắp xếp theo giá
    private static final int SORT_DEFAULT = 0;
    private static final int SORT_PRICE_ASC = 1;
    private static final int SORT_PRICE_DESC = 2;

    // Các thành phần giao diện
    private TextView tvUserGreeting;
    private TextView tvUserEmail;
    private GridView gridView;
    private LinearLayout layoutCategoryTabs;
    private EditText edtSearch;
    private ImageView btnClearSearch;
    private LinearLayout btnSort;
    private TextView tvSortLabel;
    private SwipeRefreshLayout swipeRefresh;
    private View layoutEmpty;
    private Button btnResetFilter;
    private ProgressBar progressBarLoading;

    // Dữ liệu sản phẩm và trạng thái lọc/sắp xếp
    private Product[] allProducts = new Product[0];
    private Product[] filteredProducts = new Product[0];
    private String selectedCategory = "Tất cả";
    private int currentSortMode = SORT_DEFAULT;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        // Khởi tạo SQLite
        databaseHelper = DatabaseHelper.getInstance(this);

        // Ánh xạ các thành phần giao diện
        tvUserGreeting = findViewById(R.id.tvUserGreeting);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        gridView = findViewById(R.id.listView);
        layoutCategoryTabs = findViewById(R.id.layoutCategoryTabs);
        edtSearch = findViewById(R.id.edtSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnSort = findViewById(R.id.btnSort);
        tvSortLabel = findViewById(R.id.tvSortLabel);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        btnResetFilter = findViewById(R.id.btnResetFilter);
        progressBarLoading = findViewById(R.id.progressBarLoading);

        // Cài đặt màu chủ đạo cho vòng xoay vuốt làm mới
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary));
        swipeRefresh.setOnRefreshListener(() -> loadProductsFromFirebase(true));

        // Cập nhật thông tin tài khoản người dùng trên đầu trang
        updateUserHeader();

        // Cài đặt bộ lắng nghe tìm kiếm sản phẩm theo tên thời gian thực
        setupSearch();

        // Cài đặt nút bấm sắp xếp sản phẩm theo giá
        btnSort.setOnClickListener(this::showSortMenu);

        // Nút đặt lại bộ lọc khi không tìm thấy sản phẩm
        btnResetFilter.setOnClickListener(v -> resetAllFilters());

        // Bắt sự kiện khi bấm vào 1 thẻ sản phẩm trên lưới 2 cột -> mở màn hình Chi tiết sản phẩm
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (filteredProducts != null && position < filteredProducts.length) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, filteredProducts[position].getId());
                startActivity(intent);
            }
        });

        // Cài đặt các nút trên thanh menu điều hướng đáy
        setupNavigationMenu();

        // Tải danh sách sản phẩm lần đầu từ Firebase
        loadProductsFromFirebase(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUserHeader();
    }

    // Hiển thị lời chào và Email của tài khoản đang đăng nhập
    private void updateUserHeader() {
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        String username = prefManager.getUsername();
        String userEmail = prefManager.getEmail();

        tvUserGreeting.setText("Xin chào, " + (!username.isEmpty() ? username : "Quý khách"));
        tvUserEmail.setText(!userEmail.isEmpty() ? userEmail : "Chưa có email");
    }

    // Cài đặt sự kiện tìm kiếm và nút xóa nhanh từ khóa
    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Hiển thị nút xóa nhanh nếu có ký tự nhập, ngược lại thì ẩn
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                // Lọc lại danh sách sản phẩm ngay lập tức
                applyFilterAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Bấm nút xóa nhanh -> làm sạch ô tìm kiếm
        btnClearSearch.setOnClickListener(v -> edtSearch.setText(""));
    }

    // Hiển thị PopupMenu để chọn kiểu sắp xếp theo giá
    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, SORT_DEFAULT, 0, "Mặc định");
        popup.getMenu().add(0, SORT_PRICE_ASC, 1, "Giá tăng dần (Thấp → Cao)");
        popup.getMenu().add(0, SORT_PRICE_DESC, 2, "Giá giảm dần (Cao → Thấp)");

        popup.setOnMenuItemClickListener(item -> {
            currentSortMode = item.getItemId();
            updateSortLabel();
            applyFilterAndSort();
            return true;
        });
        popup.show();
    }

    // Cập nhật nhãn hiển thị trạng thái sắp xếp giá
    private void updateSortLabel() {
        switch (currentSortMode) {
            case SORT_PRICE_ASC:
                tvSortLabel.setText("Giá ↑");
                break;
            case SORT_PRICE_DESC:
                tvSortLabel.setText("Giá ↓");
                break;
            default:
                tvSortLabel.setText("Giá");
                break;
        }
    }

    // Đặt lại toàn bộ từ khóa tìm kiếm, danh mục và sắp xếp về mặc định
    private void resetAllFilters() {
        edtSearch.setText("");
        selectedCategory = "Tất cả";
        currentSortMode = SORT_DEFAULT;
        updateSortLabel();
        buildCategoryTabs();
        applyFilterAndSort();
    }

    // Đọc danh sách sản phẩm từ Firebase (hỗ trợ hiển thị vòng xoay hoặc kéo làm mới)
    private void loadProductsFromFirebase(boolean isSwipeRefresh) {
        if (!isSwipeRefresh) {
            progressBarLoading.setVisibility(View.VISIBLE);
        }

        ProductRepository.getAll(new ProductRepository.ProductsCallback() {
            @Override
            public void onResult(Product[] products) {
                progressBarLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                allProducts = products != null ? products : new Product[0];
                // Cập nhật bản sao sản phẩm vào SQLite
                for (Product product : allProducts) {
                    databaseHelper.saveProduct(product);
                }

                buildCategoryTabs();
                applyFilterAndSort();
            }

            @Override
            public void onError(Exception e) {
                progressBarLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(ProductListActivity.this, "Không thể kết nối Firebase, đang nạp từ bộ nhớ máy.", Toast.LENGTH_SHORT).show();

                // Nạp bản sao dự phòng từ SQLite cục bộ nếu mất mạng
                List<Product> localList = databaseHelper.getAllProducts();
                if (localList != null && !localList.isEmpty() && allProducts.length == 0) {
                    allProducts = localList.toArray(new Product[0]);
                    buildCategoryTabs();
                    applyFilterAndSort();
                }
            }
        });
    }

    // Tự động gom tên các danh mục từ danh sách sản phẩm để tạo thành các tab bấm lọc trên nền Header tím
    private void buildCategoryTabs() {
        layoutCategoryTabs.removeAllViews();

        Set<String> categories = new LinkedHashSet<>();
        categories.add("Tất cả");
        for (Product product : allProducts) {
            if (product.getCategory() != null && !product.getCategory().trim().isEmpty()) {
                categories.add(product.getCategory().trim());
            }
        }

        if (!categories.contains(selectedCategory)) {
            selectedCategory = "Tất cả";
        }

        int padH = dpToPx(14);
        int padV = dpToPx(6);
        int marginEnd = dpToPx(8);

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

            // Tab đang chọn: nền trắng chữ tím nổi bật; Tab chưa chọn: nền trắng mờ chữ trắng
            if (category.equals(selectedCategory)) {
                tab.setBackgroundResource(R.drawable.bg_tab_active_white);
                tab.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tab.setTypeface(null, Typeface.BOLD);
            } else {
                tab.setBackgroundResource(R.drawable.bg_tab_inactive_translucent);
                tab.setTextColor(ContextCompat.getColor(this, R.color.white));
                tab.setTypeface(null, Typeface.NORMAL);
            }

            tab.setOnClickListener(v -> {
                selectedCategory = category;
                buildCategoryTabs();
                applyFilterAndSort();
            });

            layoutCategoryTabs.addView(tab);
        }
    }

    // Bộ xử lý lọc tổng hợp: kết hợp tìm kiếm từ khóa + lọc danh mục + sắp xếp giá
    private void applyFilterAndSort() {
        String query = edtSearch.getText().toString().trim().toLowerCase();
        List<Product> matched = new ArrayList<>();

        // Bước 1: Lọc theo danh mục và từ khóa
        for (Product product : allProducts) {
            boolean categoryMatch = "Tất cả".equals(selectedCategory)
                    || (product.getCategory() != null && selectedCategory.equalsIgnoreCase(product.getCategory().trim()));

            if (!categoryMatch) continue;

            boolean searchMatch = query.isEmpty()
                    || (product.getName() != null && product.getName().toLowerCase().contains(query))
                    || (product.getDescription() != null && product.getDescription().toLowerCase().contains(query))
                    || (product.getCategory() != null && product.getCategory().toLowerCase().contains(query));

            if (searchMatch) {
                matched.add(product);
            }
        }

        // Bước 2: Sắp xếp theo giá
        if (currentSortMode == SORT_PRICE_ASC) {
            matched.sort(Comparator.comparingLong(Product::getPrice));
        } else if (currentSortMode == SORT_PRICE_DESC) {
            matched.sort((p1, p2) -> Long.compare(p2.getPrice(), p1.getPrice()));
        }

        filteredProducts = matched.toArray(new Product[0]);

        // Bước 3: Đưa dữ liệu lên GridView 2 cột hoặc hiển thị trạng thái trống
        gridView.setAdapter(new ProductAdapter(this, filteredProducts));

        if (filteredProducts.length == 0) {
            layoutEmpty.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
        }
    }

    // Gắn sự kiện các nút điều hướng đáy (4 mục: Sản phẩm, Giỏ hàng, Đơn hàng, Tài khoản)
    private void setupNavigationMenu() {
        findViewById(R.id.menuHome).setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở trang Sản phẩm", Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.menuCart).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class))
        );

        findViewById(R.id.menuOrders).setOnClickListener(v ->
                startActivity(new Intent(this, OrderHistoryActivity.class))
        );

        findViewById(R.id.menuAccount).setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class))
        );
    }

    // Chuyển đổi dp sang pixel
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
