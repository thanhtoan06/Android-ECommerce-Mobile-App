package com.example.e_commerce;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VoucherActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_VOUCHER_ID = "com.example.e_commerce.PRODUCT_VOUCHER_ID";
    public static final String EXTRA_SHIPPING_VOUCHER_ID = "com.example.e_commerce.SHIPPING_VOUCHER_ID";
    private static final int TAB_AVAILABLE = 0;
    private static final int TAB_SAVED = 1;
    private static final int TAB_USED = 2;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_PRODUCT = 1;
    private static final int FILTER_SHIPPING = 2;

    private VoucherRepository repository;
    private String userEmail;
    private int selectedTab = TAB_AVAILABLE;
    private int selectedTypeFilter = FILTER_ALL;
    private VoucherRepository.VoucherWallet wallet;
    private LinearLayout layoutVoucherList;
    private TextView tvSectionTitle;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private MaterialButton btnAvailable;
    private MaterialButton btnSaved;
    private MaterialButton btnUsed;
    private MaterialButton btnFilterAll;
    private MaterialButton btnFilterProduct;
    private MaterialButton btnFilterShipping;
    private final NumberFormat currencyFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGate.requireLogin(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_vouchers);
        repository = VoucherRepository.getInstance();
        userEmail = SharedPrefManager.getInstance(this).getEmail();
        layoutVoucherList = findViewById(R.id.layoutVoucherList);
        tvSectionTitle = findViewById(R.id.tvVoucherSectionTitle);
        tvEmpty = findViewById(R.id.tvVoucherEmpty);
        progressBar = findViewById(R.id.progressVouchers);
        btnAvailable = findViewById(R.id.btnTabAvailable);
        btnSaved = findViewById(R.id.btnTabSaved);
        btnUsed = findViewById(R.id.btnTabUsed);
        btnFilterAll = findViewById(R.id.btnFilterAllVouchers);
        btnFilterProduct = findViewById(R.id.btnFilterProductVouchers);
        btnFilterShipping = findViewById(R.id.btnFilterShippingVouchers);

        findViewById(R.id.btnBackVouchers).setOnClickListener(v -> finish());
        btnAvailable.setOnClickListener(v -> selectTab(TAB_AVAILABLE));
        btnSaved.setOnClickListener(v -> selectTab(TAB_SAVED));
        btnUsed.setOnClickListener(v -> selectTab(TAB_USED));
        btnFilterAll.setOnClickListener(v -> selectTypeFilter(FILTER_ALL));
        btnFilterProduct.setOnClickListener(v -> selectTypeFilter(FILTER_PRODUCT));
        btnFilterShipping.setOnClickListener(v -> selectTypeFilter(FILTER_SHIPPING));
        updateTypeFilterButtons();
        setupBottomMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWallet();
    }

    private void loadWallet() {
        progressBar.setVisibility(View.VISIBLE);
        repository.loadWallet(userEmail, new VoucherRepository.WalletCallback() {
            @Override
            public void onResult(VoucherRepository.VoucherWallet result) {
                progressBar.setVisibility(View.GONE);
                wallet = result;
                updateTabLabels();
                renderVouchers();
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                tvSectionTitle.setText("Không thể tải kho voucher");
                Toast.makeText(VoucherActivity.this, "Không thể tải voucher từ Firebase.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        updateTabLabels();
        renderVouchers();
    }

    private void selectTypeFilter(int filter) {
        selectedTypeFilter = filter;
        updateTypeFilterButtons();
        renderVouchers();
    }

    private void updateTabLabels() {
        int availableCount = wallet == null ? 0 : wallet.getAvailable().size();
        int savedCount = wallet == null ? 0 : wallet.getSaved().size();
        int usedCount = wallet == null ? 0 : wallet.getUsed().size();
        btnAvailable.setText("Có thể lưu (" + availableCount + ")");
        btnSaved.setText("Của tôi (" + savedCount + ")");
        btnUsed.setText("Đã dùng (" + usedCount + ")");
        styleTab(btnAvailable, selectedTab == TAB_AVAILABLE);
        styleTab(btnSaved, selectedTab == TAB_SAVED);
        styleTab(btnUsed, selectedTab == TAB_USED);
    }

    private void styleTab(MaterialButton button, boolean selected) {
        button.setBackgroundTintList(ContextCompat.getColorStateList(
                this, selected ? R.color.primary : R.color.surface));
        button.setTextColor(ContextCompat.getColor(this, selected ? R.color.white : R.color.text_secondary));
        button.setStrokeColor(ContextCompat.getColorStateList(
                this, selected ? R.color.primary : R.color.card_stroke));
    }

    private void updateTypeFilterButtons() {
        styleTypeFilter(btnFilterAll, selectedTypeFilter == FILTER_ALL, R.color.primary);
        styleTypeFilter(btnFilterProduct, selectedTypeFilter == FILTER_PRODUCT, R.color.primary);
        styleTypeFilter(btnFilterShipping, selectedTypeFilter == FILTER_SHIPPING, R.color.success_dark);
    }

    private void styleTypeFilter(MaterialButton button, boolean selected, int accentColor) {
        int backgroundColor = ContextCompat.getColor(this, selected ? accentColor : R.color.surface);
        int textColor = ContextCompat.getColor(this, selected ? R.color.white : accentColor);
        int strokeColor = ContextCompat.getColor(this, selected ? accentColor : R.color.card_stroke);
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setTextColor(textColor);
        button.setStrokeColor(ColorStateList.valueOf(strokeColor));
    }

    private void renderVouchers() {
        layoutVoucherList.removeAllViews();
        if (wallet == null) {
            tvSectionTitle.setText("Voucher của bạn");
            tvEmpty.setText("Đang tải voucher...");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        List<Voucher> vouchers = vouchersForSelectedTab();
        String title = selectedTab == TAB_AVAILABLE
                ? "Voucher có thể lưu"
                : selectedTab == TAB_SAVED ? "Voucher của tôi" : "Lịch sử đã sử dụng";
        if (selectedTypeFilter == FILTER_PRODUCT) {
            title = "Voucher sản phẩm · " + title;
        } else if (selectedTypeFilter == FILTER_SHIPPING) {
            title = "Voucher vận chuyển · " + title;
        }
        tvSectionTitle.setText(title + " (" + vouchers.size() + ")");
        tvEmpty.setText(emptyMessage());
        tvEmpty.setVisibility(vouchers.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Voucher voucher : vouchers) {
            View card = inflater.inflate(R.layout.item_voucher, layoutVoucherList, false);
            bindVoucher(card, voucher);
            layoutVoucherList.addView(card);
        }
    }

    private List<Voucher> vouchersForSelectedTab() {
        if (wallet == null) {
            return new ArrayList<>();
        }
        List<Voucher> source;
        switch (selectedTab) {
            case TAB_SAVED:
                source = wallet.getSaved();
                break;
            case TAB_USED:
                source = wallet.getUsed();
                break;
            case TAB_AVAILABLE:
            default:
                source = wallet.getAvailable();
                break;
        }
        if (selectedTypeFilter == FILTER_ALL) {
            return source;
        }

        List<Voucher> filtered = new ArrayList<>();
        for (Voucher voucher : source) {
            if ((selectedTypeFilter == FILTER_PRODUCT && voucher.isProductVoucher())
                    || (selectedTypeFilter == FILTER_SHIPPING && voucher.isShippingVoucher())) {
                filtered.add(voucher);
            }
        }
        return filtered;
    }

    private String emptyMessage() {
        if (selectedTypeFilter == FILTER_PRODUCT) {
            return "Không có voucher giảm giá sản phẩm trong mục này.";
        }
        if (selectedTypeFilter == FILTER_SHIPPING) {
            return "Không có voucher giảm phí vận chuyển trong mục này.";
        }
        return selectedTab == TAB_USED
                ? "Bạn chưa sử dụng voucher nào."
                : selectedTab == TAB_SAVED ? "Bạn chưa lưu voucher nào." : "Hiện không có voucher mới.";
    }

    private void bindVoucher(View card, Voucher voucher) {
        TextView type = card.findViewById(R.id.tvVoucherType);
        TextView code = card.findViewById(R.id.tvVoucherCode);
        TextView name = card.findViewById(R.id.tvVoucherName);
        TextView description = card.findViewById(R.id.tvVoucherDescription);
        TextView conditions = card.findViewById(R.id.tvVoucherConditions);
        TextView expiry = card.findViewById(R.id.tvVoucherExpiry);
        View badge = card.findViewById(R.id.layoutVoucherBadge);
        MaterialButton details = card.findViewById(R.id.btnVoucherDetails);
        MaterialButton action = card.findViewById(R.id.btnVoucherAction);

        boolean shippingVoucher = voucher.isShippingVoucher();
        int accentColor = ContextCompat.getColor(this,
                shippingVoucher ? R.color.success_dark : R.color.primary);
        badge.setBackgroundResource(shippingVoucher
                ? R.drawable.bg_voucher_badge_sawtooth_green
                : R.drawable.bg_voucher_badge_sawtooth);
        code.setTextColor(shippingVoucher ? 0xFFD1FAE5 : 0xFFE0E7FF);
        expiry.setTextColor(accentColor);
        details.setTextColor(accentColor);
        action.setBackgroundTintList(ColorStateList.valueOf(accentColor));
        type.setText(shippingVoucher ? "GIẢM PHÍ VẬN CHUYỂN" : "GIẢM GIÁ SẢN PHẨM");
        code.setText(voucher.getCode());
        name.setText(voucher.getName());
        description.setText(voucher.getDescription());
        conditions.setText(conditionSummary(voucher));
        expiry.setText(expirySummary(voucher));
        details.setOnClickListener(v -> showDetails(voucher));

        if (selectedTab == TAB_AVAILABLE) {
            action.setVisibility(View.VISIBLE);
            action.setText("Lưu voucher");
            action.setOnClickListener(v -> saveVoucher(voucher));
        } else if (selectedTab == TAB_SAVED) {
            action.setVisibility(View.VISIBLE);
            action.setText(voucher.isExpired(new Date()) ? "Đã hết hạn" : "Dùng ngay");
            action.setEnabled(!voucher.isExpired(new Date()));
            action.setOnClickListener(v -> useVoucher(voucher));
        } else {
            action.setVisibility(View.GONE);
            expiry.setText(voucher.getUsedAt() == null
                    ? "Đã sử dụng"
                    : "Đã sử dụng ngày " + dateFormat.format(voucher.getUsedAt()));
        }
    }

    private void saveVoucher(Voucher voucher) {
        repository.saveVoucher(userEmail, voucher.getId(), new VoucherRepository.SaveCallback() {
            @Override
            public void onResult(VoucherRepository.SaveResult result) {
                Toast.makeText(VoucherActivity.this, result.getMessage(), Toast.LENGTH_SHORT).show();
                if (result.isSuccessful()) {
                    loadWallet();
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(VoucherActivity.this, "Không thể lưu voucher. Vui lòng thử lại.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void useVoucher(Voucher voucher) {
        Intent intent = new Intent(this, CartActivity.class);
        if (voucher.isProductVoucher()) {
            intent.putExtra(EXTRA_PRODUCT_VOUCHER_ID, voucher.getId());
        } else {
            intent.putExtra(EXTRA_SHIPPING_VOUCHER_ID, voucher.getId());
        }
        startActivity(intent);
    }

    private void showDetails(Voucher voucher) {
        StringBuilder message = new StringBuilder(voucher.getDescription());
        message.append("\n\nPhạm vi: ").append(scopeText(voucher));
        message.append("\nMức giảm: ").append(discountSummary(voucher));
        if (voucher.getMinimumOrderAmount() > 0) {
            message.append("\nĐơn tối thiểu: ").append(money(voucher.getMinimumOrderAmount()));
        }
        if (voucher.getMaximumDiscountAmount() > 0) {
            message.append("\nMức giảm tối đa: ").append(money(voucher.getMaximumDiscountAmount()));
        }
        if (voucher.getExpiresAt() != null) {
            message.append("\nHạn sử dụng thực tế: ").append(dateFormat.format(voucher.getExpiresAt()));
        } else if (voucher.getCampaignEndsAt() != null) {
            message.append("\nChương trình kết thúc: ").append(dateFormat.format(voucher.getCampaignEndsAt()));
        }
        if (!voucher.isSaved() && !voucher.isUsed() && voucher.getValidityAfterSaveDays() > 0) {
            message.append("\nThời hạn sau khi lưu: tối đa ")
                    .append(voucher.getValidityAfterSaveDays()).append(" ngày");
            if (voucher.getCampaignEndsAt() != null) {
                message.append(", nhưng không muộn hơn ngày kết thúc chương trình");
            }
        }
        if (voucher.getUsedAt() != null) {
            message.append("\nĐã sử dụng ngày ").append(dateFormat.format(voucher.getUsedAt()));
        }
        message.append("\n\nMã voucher: ").append(voucher.getCode());
        new AlertDialog.Builder(this)
                .setTitle(voucher.getName())
                .setMessage(message.toString())
                .setPositiveButton("Đóng", null)
                .show();
    }

    private String conditionSummary(Voucher voucher) {
        StringBuilder summary = new StringBuilder(scopeText(voucher));
        if (voucher.getMinimumOrderAmount() > 0) {
            summary.append(" · Đơn tối thiểu ").append(money(voucher.getMinimumOrderAmount()));
        }
        if (voucher.getMaximumDiscountAmount() > 0) {
            summary.append(" · Tối đa ").append(money(voucher.getMaximumDiscountAmount()));
        }
        return summary.toString();
    }

    private String scopeText(Voucher voucher) {
        if ("category".equals(voucher.getAppliesTo())) {
            return voucher.getCategory().isEmpty()
                    ? "Danh mục chưa được cấu hình"
                    : "Danh mục " + voucher.getCategory();
        }
        if (voucher.isSpecificProductScope()) {
            if (!voucher.getProductNames().isEmpty()) {
                StringBuilder productNames = new StringBuilder();
                for (String productName : voucher.getProductNames()) {
                    if (productNames.length() > 0) {
                        productNames.append(", ");
                    }
                    productNames.append(productName);
                }
                return "Sản phẩm cụ thể: " + productNames;
            }
            StringBuilder productIds = new StringBuilder();
            for (Integer productId : voucher.getProductIds()) {
                if (productIds.length() > 0) {
                    productIds.append(", ");
                }
                productIds.append("#").append(productId);
            }
            return productIds.length() == 0
                    ? "Sản phẩm cụ thể chưa được cấu hình"
                    : "Sản phẩm cụ thể: " + productIds;
        }
        return voucher.isApplicableToAll() ? "Toàn bộ sản phẩm" : "Phạm vi chưa được cấu hình";
    }

    private String discountSummary(Voucher voucher) {
        switch (voucher.getDiscountType()) {
            case "fixed_per_item":
                return "Giảm " + money(voucher.getValue()) + " mỗi sản phẩm đủ điều kiện";
            case "percentage_per_item":
                return "Giảm " + voucher.getValue() + "% trên mỗi sản phẩm đủ điều kiện";
            case "free_shipping":
                return "Miễn phí vận chuyển";
            case "shipping_percentage":
                return "Giảm " + voucher.getValue() + "% phí vận chuyển";
            case "shipping_fixed":
                return "Giảm " + money(voucher.getValue()) + " phí vận chuyển";
            default:
                return money(voucher.getValue());
        }
    }

    private String expirySummary(Voucher voucher) {
        if (voucher.isUsed()) {
            return voucher.getUsedAt() == null
                    ? "Đã sử dụng"
                    : "Đã sử dụng ngày " + dateFormat.format(voucher.getUsedAt());
        }
        if (voucher.getExpiresAt() != null) {
            if (voucher.isExpired(new Date())) {
                return "Đã hết hạn ngày " + dateFormat.format(voucher.getExpiresAt());
            }
            return "Có hiệu lực đến " + dateFormat.format(voucher.getExpiresAt());
        }
        return voucher.getCampaignEndsAt() == null
                ? "Lưu voucher để kích hoạt thời hạn"
                : "Lưu trước " + dateFormat.format(voucher.getCampaignEndsAt());
    }

    private String money(long amount) {
        return currencyFormat.format(amount) + "đ";
    }

    private void setupBottomMenu() {
        findViewById(R.id.menuHome).setOnClickListener(v -> navigateTo(ProductListActivity.class));
        findViewById(R.id.menuCart).setOnClickListener(v -> navigateTo(CartActivity.class));
        findViewById(R.id.menuNotifications).setOnClickListener(v -> navigateTo(NotificationActivity.class));
        findViewById(R.id.menuAccount).setOnClickListener(v -> navigateTo(AccountActivity.class));

        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        int activeColor = ContextCompat.getColor(this, R.color.primary);
        ((ImageView) findViewById(R.id.ivMenuHome)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuCart)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuNotifications)).setColorFilter(inactiveColor);
        ((ImageView) findViewById(R.id.ivMenuAccount)).setColorFilter(activeColor);

        TextView[] inactiveLabels = {
                findViewById(R.id.tvMenuHome),
                findViewById(R.id.tvMenuCart),
                findViewById(R.id.tvMenuNotifications)
        };
        for (TextView label : inactiveLabels) {
            label.setTextColor(inactiveColor);
        }
        TextView accountLabel = findViewById(R.id.tvMenuAccount);
        accountLabel.setTextColor(activeColor);
    }

    private void navigateTo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
        finish();
    }
}
