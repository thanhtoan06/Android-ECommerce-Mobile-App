package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Collections;
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
    private View layoutSortPanel;
    private LinearLayout layoutSortOptions;
    private final List<LinearLayout> sortOptionRows = new ArrayList<>();
    private boolean isSortPanelExpanded;
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
    private ProductSortRules.Criterion currentSortCriterion;

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
        layoutSortPanel = findViewById(R.id.layoutSortPanel);
        layoutSortOptions = findViewById(R.id.layoutSortOptions);
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
        setupSortPanel();
        btnSort.setOnClickListener(v -> setSortPanelExpanded(!isSortPanelExpanded));
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

    private void setupSortPanel() {
        for (int rowIndex = 0; rowIndex < SORT_OPTIONS.length; rowIndex += 2) {
            LinearLayout optionRow = new LinearLayout(this);
            optionRow.setOrientation(LinearLayout.HORIZONTAL);

            for (int columnIndex = 0; columnIndex < 2; columnIndex++) {
                int optionIndex = rowIndex + columnIndex;
                if (optionIndex >= SORT_OPTIONS.length) {
                    break;
                }

                ProductSortRules.Criterion criterion = SORT_OPTIONS[optionIndex];
                LinearLayout option = new LinearLayout(this);
                option.setOrientation(LinearLayout.HORIZONTAL);
                option.setGravity(Gravity.CENTER_VERTICAL);
                option.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
                option.setClickable(true);
                option.setFocusable(true);

                TextView label = new TextView(this);
                label.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                label.setMaxLines(2);
                label.setText(criterion.getLabel().replace(" (", "\n("));
                label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                label.setTextSize(12.5f);

                TextView checkmark = new TextView(this);
                checkmark.setGravity(Gravity.CENTER);
                checkmark.setText("✓");
                checkmark.setTextColor(ContextCompat.getColor(this, R.color.primary));
                checkmark.setTextSize(17f);
                checkmark.setTypeface(null, Typeface.BOLD);
                checkmark.setVisibility(View.GONE);
                option.addView(label);
                option.addView(checkmark, new LinearLayout.LayoutParams(dpToPx(20),
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                option.setOnClickListener(v -> selectSortCriterion(criterion));

                LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                        0, dpToPx(68), 1f);
                if (columnIndex == 1) {
                    optionParams.setMarginStart(dpToPx(8));
                }
                optionRow.addView(option, optionParams);
                sortOptionRows.add(option);
            }

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dpToPx(6);
            layoutSortOptions.addView(optionRow, rowParams);
        }

        findViewById(R.id.btnSortDefault).setOnClickListener(v -> {
            currentSortCriterion = null;
            updateSortLabel();
            updateSortOptionSelection();
            setSortPanelExpanded(false);
            applyFilterAndSort();
        });
        updateSortOptionSelection();
    }

    private void selectSortCriterion(ProductSortRules.Criterion criterion) {
        currentSortCriterion = criterion;
        updateSortLabel();
        updateSortOptionSelection();
        setSortPanelExpanded(false);
        applyFilterAndSort();
    }

    private void setSortPanelExpanded(boolean expanded) {
        if (isSortPanelExpanded == expanded) {
            return;
        }

        ViewGroup parent = (ViewGroup) layoutSortPanel.getParent();
        TransitionManager.beginDelayedTransition(parent, new AutoTransition().setDuration(220));
        layoutSortPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        isSortPanelExpanded = expanded;
        updateSortButtonAppearance();
    }

    private void updateSortOptionSelection() {
        for (int index = 0; index < sortOptionRows.size(); index++) {
            LinearLayout option = sortOptionRows.get(index);
            ProductSortRules.Criterion criterion = SORT_OPTIONS[index];
            boolean selected = criterion == currentSortCriterion;

            option.setBackgroundResource(selected
                    ? R.drawable.bg_sort_option_selected
                    : R.drawable.bg_sort_option);
            option.setContentDescription(criterion.getLabel()
                    + (selected ? ", đang được áp dụng" : ""));
            option.setSelected(selected);
            TextView label = (TextView) option.getChildAt(0);
            label.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.primary : R.color.text_primary));
            label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            option.getChildAt(1).setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSortLabel() {
        if (currentSortCriterion == null) {
            tvSortLabel.setText("Sắp xếp");
            btnSort.setContentDescription("Sắp xếp sản phẩm");
            updateSortButtonAppearance();
            return;
        }

        tvSortLabel.setText(currentSortCriterion.getShortLabel());
        btnSort.setContentDescription("Sắp xếp sản phẩm: " + currentSortCriterion.getLabel());
        updateSortButtonAppearance();
    }

    private void updateSortButtonAppearance() {
        btnSort.setBackgroundResource(currentSortCriterion != null || isSortPanelExpanded
                ? R.drawable.bg_sort_active
                : R.drawable.bg_search_white);
    }

    private void resetAllFilters() {
        edtSearch.setText("");
        selectedCategory = "Tất cả";
        currentSortCriterion = null;
        updateSortLabel();
        updateSortOptionSelection();
        setSortPanelExpanded(false);
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

        if (currentSortCriterion != null) {
            matched.sort(ProductSortRules.comparatorFor(
                    Collections.singletonList(currentSortCriterion)));
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
