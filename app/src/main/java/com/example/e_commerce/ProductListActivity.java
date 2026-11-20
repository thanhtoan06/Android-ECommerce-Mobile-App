package com.example.e_commerce;

import android.app.AlertDialog;
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
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProductListActivity extends AppCompatActivity {

    private static final ProductSortRules.Criterion[] SORT_OPTIONS = {
            ProductSortRules.Criterion.PRICE_ASC,
            ProductSortRules.Criterion.PRICE_DESC,
            ProductSortRules.Criterion.BEST_SELLING,
            ProductSortRules.Criterion.TOP_RATED,
            ProductSortRules.Criterion.NAME_A_TO_Z,
            ProductSortRules.Criterion.NEWEST
    };

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
    private View layoutUserInfo;
    private View layoutGuestActions;

    private Product[] allProducts = new Product[0];
    private Product[] filteredProducts = new Product[0];
    private List<Voucher> savedVouchers = new ArrayList<>();
    private String selectedCategory = "Tất cả";
    private List<ProductSortRules.Criterion> currentSortCriteria = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);

        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            PushNotificationManager.requestPermissionIfNeeded(this);
            PushNotificationManager.registerCurrentToken(this, prefManager.getEmail());
        }

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
        layoutUserInfo = findViewById(R.id.layoutUserInfo);
        layoutGuestActions = findViewById(R.id.layoutGuestActions);

        findViewById(R.id.btnHomeLogin).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.btnHomeRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary));
        swipeRefresh.setOnRefreshListener(() -> loadProductsFromFirebase(true));
        updateUserHeader();
        setupSearch();
        btnSort.setOnClickListener(this::showSortMenu);
        updateSortLabel();
        btnResetFilter.setOnClickListener(v -> resetAllFilters());
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (filteredProducts != null && position < filteredProducts.length) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, filteredProducts[position].getId());
                startActivity(intent);
            }
        });
        setupNavigationMenu();
        openOrderFromPushIfPresent();
        progressBarLoading.setVisibility(View.VISIBLE);
        InitialCatalogSeeder.seedCatalogAndVouchersIfNeeded(FirebaseFirestore.getInstance())
                .addOnSuccessListener(ignored -> loadProductsFromFirebase(false))
                .addOnFailureListener(error -> {
                    progressBarLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Không thể tạo dữ liệu mẫu: "
                            + error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUserHeader();
        loadSavedVouchers();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openOrderFromPushIfPresent();
    }

    private void updateUserHeader() {
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        boolean isLoggedIn = prefManager.isLoggedIn();
        layoutUserInfo.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        layoutGuestActions.setVisibility(isLoggedIn ? View.GONE : View.VISIBLE);
        if (!isLoggedIn) {
            return;
        }

        String username = prefManager.getUsername();
        String userEmail = prefManager.getEmail();

        tvUserGreeting.setText("Xin chào, " + (!username.isEmpty() ? username : "Quý khách"));
        tvUserEmail.setText(!userEmail.isEmpty() ? userEmail : "Chưa có email");
    }

    private void loadSavedVouchers() {
        SharedPrefManager preferences = SharedPrefManager.getInstance(this);
        if (!preferences.isLoggedIn()) {
            savedVouchers = new ArrayList<>();
            if (allProducts.length > 0) {
                applyFilterAndSort();
            }
            return;
        }
        VoucherRepository.getInstance().loadWallet(preferences.getEmail(), new VoucherRepository.WalletCallback() {
            @Override
            public void onResult(VoucherRepository.VoucherWallet wallet) {
                savedVouchers = wallet.getSaved();
                if (allProducts.length > 0) {
                    applyFilterAndSort();
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(ProductListActivity.this, "Không thể tải ưu đãi đã lưu.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilterAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClearSearch.setOnClickListener(v -> edtSearch.setText(""));
    }

    private void showSortMenu(View anchor) {
        List<ProductSortRules.Criterion> pendingCriteria = new ArrayList<>(currentSortCriteria);
        CharSequence[] labels = new CharSequence[SORT_OPTIONS.length];
        boolean[] checked = new boolean[SORT_OPTIONS.length];
        for (int index = 0; index < SORT_OPTIONS.length; index++) {
            ProductSortRules.Criterion criterion = SORT_OPTIONS[index];
            labels[index] = criterion.getLabel();
            checked[index] = pendingCriteria.contains(criterion);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Sắp xếp sản phẩm")
                .setMessage(sortPriorityMessage(pendingCriteria))
                .setMultiChoiceItems(labels, checked, (shownDialog, which, isChecked) -> {
                    ProductSortRules.Criterion selected = SORT_OPTIONS[which];
                    if (isChecked) {
                        ProductSortRules.Criterion oppositePrice = oppositePriceCriterion(selected);
                        if (oppositePrice != null && pendingCriteria.remove(oppositePrice)) {
                            int oppositeIndex = indexOfSortCriterion(oppositePrice);
                            AlertDialog sortDialog = (AlertDialog) shownDialog;
                            sortDialog.getListView().setItemChecked(oppositeIndex, false);
                        }
                        pendingCriteria.remove(selected);
                        pendingCriteria.add(selected);
                    } else {
                        pendingCriteria.remove(selected);
                    }
                    ((AlertDialog) shownDialog).setMessage(sortPriorityMessage(pendingCriteria));
                })
                .setPositiveButton("Áp dụng", (shownDialog, which) -> {
                    currentSortCriteria = new ArrayList<>(pendingCriteria);
                    updateSortLabel();
                    applyFilterAndSort();
                })
                .setNeutralButton("Mặc định", (shownDialog, which) -> {
                    currentSortCriteria.clear();
                    updateSortLabel();
                    applyFilterAndSort();
                })
                .setNegativeButton("Hủy", null)
                .create();
        dialog.show();
    }

    private void updateSortLabel() {
        if (currentSortCriteria.isEmpty()) {
            tvSortLabel.setText("Sắp xếp");
            btnSort.setContentDescription("Sắp xếp sản phẩm");
            return;
        }

        ProductSortRules.Criterion primary = currentSortCriteria.get(0);
        String label = primary.getShortLabel();
        if (currentSortCriteria.size() > 1) {
            label += " +" + (currentSortCriteria.size() - 1);
        }
        tvSortLabel.setText(label);
        btnSort.setContentDescription("Sắp xếp sản phẩm: " + sortPriorityMessage(currentSortCriteria));
    }

    private String sortPriorityMessage(List<ProductSortRules.Criterion> criteria) {
        if (criteria.isEmpty()) {
            return "Tích một hoặc nhiều tiêu chí. Tiêu chí tích trước được ưu tiên cao hơn.";
        }

        StringBuilder message = new StringBuilder("Ưu tiên: ");
        for (ProductSortRules.Criterion criterion : criteria) {
            if (message.length() > "Ưu tiên: ".length()) {
                message.append(" → ");
            }
            message.append(criterion.getLabel());
        }
        return message.toString();
    }

    private ProductSortRules.Criterion oppositePriceCriterion(ProductSortRules.Criterion criterion) {
        if (criterion == ProductSortRules.Criterion.PRICE_ASC) {
            return ProductSortRules.Criterion.PRICE_DESC;
        }
        if (criterion == ProductSortRules.Criterion.PRICE_DESC) {
            return ProductSortRules.Criterion.PRICE_ASC;
        }
        return null;
    }

    private int indexOfSortCriterion(ProductSortRules.Criterion criterion) {
        for (int index = 0; index < SORT_OPTIONS.length; index++) {
            if (SORT_OPTIONS[index] == criterion) {
                return index;
            }
        }
        return -1;
    }

    private void resetAllFilters() {
        edtSearch.setText("");
        selectedCategory = "Tất cả";
        currentSortCriteria.clear();
        updateSortLabel();
        buildCategoryTabs();
        applyFilterAndSort();
    }

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
                buildCategoryTabs();
                applyFilterAndSort();
            }

            @Override
            public void onError(Exception e) {
                progressBarLoading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(ProductListActivity.this, "Không thể kết nối Firebase.", Toast.LENGTH_SHORT).show();
            }
        });
    }

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

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(marginEnd);
            tab.setLayoutParams(params);

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

    private void applyFilterAndSort() {
        String query = edtSearch.getText().toString().trim().toLowerCase();
        List<Product> matched = new ArrayList<>();

        for (Product product : allProducts) {
            boolean categoryMatch = "Tất cả".equals(selectedCategory) || (product.getCategory() != null
                    && selectedCategory.equalsIgnoreCase(product.getCategory().trim()));

            if (!categoryMatch)
                continue;

            boolean searchMatch = query.isEmpty()
                    || (product.getName() != null && product.getName().toLowerCase().contains(query))
                    || (product.getDescription() != null && product.getDescription().toLowerCase().contains(query))
                    || (product.getCategory() != null && product.getCategory().toLowerCase().contains(query));

            if (searchMatch) {
                matched.add(product);
            }
        }

        if (!currentSortCriteria.isEmpty()) {
            matched.sort(ProductSortRules.comparatorFor(currentSortCriteria));
        }

        filteredProducts = matched.toArray(new Product[0]);

        gridView.setAdapter(new ProductAdapter(this, filteredProducts, savedVouchers));

        if (filteredProducts.length == 0) {
            layoutEmpty.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
        }
    }

    private void setupNavigationMenu() {
        findViewById(R.id.menuHome)
                .setOnClickListener(v -> Toast.makeText(this, "Bạn đang ở trang Sản phẩm", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menuCart).setOnClickListener(v -> {
            if (AuthGate.requireLogin(this)) {
                startActivity(new Intent(this, CartActivity.class));
            }
        });

        findViewById(R.id.menuAccount).setOnClickListener(v -> {
            if (AuthGate.requireLogin(this)) {
                startActivity(new Intent(this, AccountActivity.class));
            }
        });

        findViewById(R.id.menuNotifications).setOnClickListener(v -> {
            if (AuthGate.requireLogin(this)) {
                startActivity(new Intent(this, NotificationActivity.class));
            }
        });
    }

    private void openOrderFromPushIfPresent() {
        Intent launchIntent = getIntent();
        String type = launchIntent.getStringExtra("type");
        if (!"order_placed".equals(type) && !"order_status".equals(type)) {
            return;
        }

        String orderIdValue = launchIntent.getStringExtra("orderId");
        launchIntent.removeExtra("type");
        launchIntent.removeExtra("orderId");
        if (orderIdValue == null || !AuthGate.requireLogin(this)) {
            return;
        }

        try {
            int orderId = Integer.parseInt(orderIdValue);
            if (orderId > 0) {
                Intent orderIntent = new Intent(this, OrderDetailActivity.class);
                orderIntent.putExtra("order_id", orderId);
                startActivity(orderIntent);
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Thông báo chứa mã đơn hàng không hợp lệ.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
