package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "review_order_id";
    public static final String EXTRA_ORDER_ITEM_ID = "review_order_item_id";
    public static final String EXTRA_PRODUCT_ID = "review_product_id";

    private static final int TAB_PENDING = 0;
    private static final int TAB_WRITTEN = 1;

    private final List<Review> writtenReviews = new ArrayList<>();
    private final List<ReviewRepository.ReviewableItem> pendingItems = new ArrayList<>();
    private ReviewRepository repository;
    private String email;
    private int selectedTab = TAB_PENDING;
    private int loadVersion;
    private int completedLoads;
    private boolean loadFailed;
    private boolean initialItemHandled;
    private MaterialButton btnPending;
    private MaterialButton btnWritten;
    private LinearLayout layoutItems;
    private TextView tvEmpty;
    private TextView tvReviewCount;
    private TextView tvLikes;
    private TextView tvViews;
    private ProgressBar progress;
    private View layoutStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGate.requireLogin(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_reviews);
        repository = ReviewRepository.getInstance();
        email = SharedPrefManager.getInstance(this).getEmail();
        btnPending = findViewById(R.id.btnPendingReviews);
        btnWritten = findViewById(R.id.btnWrittenReviews);
        layoutItems = findViewById(R.id.layoutReviewItems);
        tvEmpty = findViewById(R.id.tvReviewsEmpty);
        tvReviewCount = findViewById(R.id.tvMyReviewCount);
        tvLikes = findViewById(R.id.tvMyReviewLikes);
        tvViews = findViewById(R.id.tvMyReviewViews);
        progress = findViewById(R.id.progressReviews);
        layoutStats = findViewById(R.id.layoutReviewStats);

        findViewById(R.id.btnBackReviews).setOnClickListener(v -> finish());
        btnPending.setOnClickListener(v -> selectTab(TAB_PENDING));
        btnWritten.setOnClickListener(v -> selectTab(TAB_WRITTEN));
        updateTabButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReviews();
    }

    private void loadReviews() {
        int requestVersion = ++loadVersion;
        completedLoads = 0;
        loadFailed = false;
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        layoutItems.removeAllViews();

        repository.getMyReviews(email, new ReviewRepository.ReviewsCallback() {
            @Override
            public void onResult(List<Review> reviews) {
                if (requestVersion != loadVersion) {
                    return;
                }
                writtenReviews.clear();
                writtenReviews.addAll(reviews);
                updateReviewTotals();
                finishLoad(requestVersion);
            }

            @Override
            public void onError(Exception error) {
                handleLoadError(requestVersion, error);
            }
        });
        repository.getPendingItems(email, new ReviewRepository.PendingCallback() {
            @Override
            public void onResult(List<ReviewRepository.ReviewableItem> items) {
                if (requestVersion != loadVersion) {
                    return;
                }
                pendingItems.clear();
                pendingItems.addAll(items);
                finishLoad(requestVersion);
            }

            @Override
            public void onError(Exception error) {
                handleLoadError(requestVersion, error);
            }
        });
    }

    private void handleLoadError(int requestVersion, Exception error) {
        if (requestVersion != loadVersion || loadFailed) {
            return;
        }
        loadFailed = true;
        progress.setVisibility(View.GONE);
        Toast.makeText(this, "Không thể tải dữ liệu đánh giá.", Toast.LENGTH_SHORT).show();
    }

    private void finishLoad(int requestVersion) {
        if (requestVersion != loadVersion || loadFailed) {
            return;
        }
        completedLoads++;
        if (completedLoads == 2) {
            progress.setVisibility(View.GONE);
            renderCurrentTab();
            openRequestedItem();
        }
    }

    private void openRequestedItem() {
        if (initialItemHandled || !getIntent().hasExtra(EXTRA_ORDER_ID)) {
            return;
        }
        initialItemHandled = true;
        int orderId = getIntent().getIntExtra(EXTRA_ORDER_ID, -1);
        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        for (Review review : writtenReviews) {
            if (review.getOrderId() == orderId && review.getProductId() == productId) {
                showReviewDialog(review.getOrderId(), review.getOrderItemId(), review.getProductId(),
                        review.getProductName(), review.getProductIcon(), review);
                return;
            }
        }
        int itemId = getIntent().getIntExtra(EXTRA_ORDER_ITEM_ID, -1);
        for (ReviewRepository.ReviewableItem item : pendingItems) {
            if (item.getOrderId() == orderId && item.getProductId() == productId
                    && item.getOrderItemId() == itemId) {
                showReviewDialog(item.getOrderId(), item.getOrderItemId(), item.getProductId(),
                        item.getProductName(), item.getProductIcon(), null);
                return;
            }
        }
        Toast.makeText(this, "Chỉ có thể đánh giá sản phẩm trong đơn đã giao.", Toast.LENGTH_SHORT).show();
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        updateTabButtons();
        renderCurrentTab();
    }

    private void updateTabButtons() {
        boolean pendingSelected = selectedTab == TAB_PENDING;
        layoutStats.setVisibility(pendingSelected ? View.GONE : View.VISIBLE);
        btnPending.setBackgroundTintList(ContextCompat.getColorStateList(
                this, pendingSelected ? R.color.primary : android.R.color.transparent));
        btnPending.setTextColor(ContextCompat.getColor(this,
                pendingSelected ? R.color.white : R.color.text_secondary));
        btnWritten.setBackgroundTintList(ContextCompat.getColorStateList(
                this, pendingSelected ? android.R.color.transparent : R.color.primary));
        btnWritten.setTextColor(ContextCompat.getColor(this,
                pendingSelected ? R.color.text_secondary : R.color.white));
    }

    private void updateReviewTotals() {
        long likes = 0;
        long views = 0;
        for (Review review : writtenReviews) {
            likes += review.getLikesCount();
            views += review.getViewsCount();
        }
        tvReviewCount.setText(String.valueOf(writtenReviews.size()));
        tvLikes.setText(String.valueOf(likes));
        tvViews.setText(String.valueOf(views));
    }

    private void renderCurrentTab() {
        layoutItems.removeAllViews();
        if (selectedTab == TAB_PENDING) {
            tvEmpty.setText("Chưa có sản phẩm nào trong đơn đã giao cần đánh giá.");
            tvEmpty.setVisibility(pendingItems.isEmpty() ? View.VISIBLE : View.GONE);
            for (ReviewRepository.ReviewableItem item : pendingItems) {
                layoutItems.addView(createPendingCard(item));
            }
            return;
        }

        tvEmpty.setText("Bạn chưa có bài đánh giá nào.");
        tvEmpty.setVisibility(writtenReviews.isEmpty() ? View.VISIBLE : View.GONE);
        for (Review review : writtenReviews) {
            layoutItems.addView(createReviewCard(review));
        }
    }

    private View createPendingCard(ReviewRepository.ReviewableItem item) {
        LinearLayout card = createCard();
        TextView title = createText(item.getProductIcon() + "  " + item.getProductName(),
                R.color.text_primary, 15, true);
        card.addView(title);
        TextView details = createText("Đơn #DH" + String.format(Locale.getDefault(), "%05d", item.getOrderId())
                + " · " + item.getOrderDate() + " · Đã giao", R.color.text_secondary, 12, false);
        details.setPadding(0, dp(6), 0, dp(10));
        card.addView(details);
        Button action = createActionButton("Viết đánh giá");
        card.addView(action);
        card.setOnClickListener(v -> showReviewDialog(item.getOrderId(), item.getOrderItemId(), item.getProductId(),
                item.getProductName(), item.getProductIcon(), null));
        action.setOnClickListener(v -> showReviewDialog(item.getOrderId(), item.getOrderItemId(),
                item.getProductId(), item.getProductName(), item.getProductIcon(), null));
        return card;
    }

    private View createReviewCard(Review review) {
        LinearLayout card = createCard();
        TextView title = createText(review.getProductIcon() + "  " + review.getProductName(),
                R.color.text_primary, 15, true);
        card.addView(title);
        TextView rating = createText(stars(review.getRating()) + "  ·  Đơn #DH"
                + String.format(Locale.getDefault(), "%05d", review.getOrderId()),
                R.color.text_secondary, 12, false);
        rating.setPadding(0, dp(6), 0, dp(4));
        card.addView(rating);
        TextView comment = createText(review.getComment(), R.color.text_primary, 13, false);
        comment.setMaxLines(4);
        comment.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(comment);
        TextView totals = createText("♥ " + review.getLikesCount() + " lượt thích  ·  "
                + review.getViewsCount() + " lượt xem", R.color.text_secondary, 12, false);
        totals.setPadding(0, dp(8), 0, dp(10));
        card.addView(totals);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = createActionButton("Chỉnh sửa");
        Button open = createActionButton("Xem sản phẩm");
        actions.addView(edit);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.setMarginStart(dp(8));
        actions.addView(open, openParams);
        card.addView(actions);
        card.setOnClickListener(v -> openProductReview(review));
        edit.setOnClickListener(v -> showReviewDialog(review.getOrderId(), review.getOrderItemId(),
                review.getProductId(), review.getProductName(), review.getProductIcon(), review));
        open.setOnClickListener(v -> openProductReview(review));
        return card;
    }

    private void showReviewDialog(int orderId, int orderItemId, int productId, String productName,
            String productIcon, Review existingReview) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_write_review, null, false);
        RatingBar rating = content.findViewById(R.id.ratingReview);
        EditText comment = content.findViewById(R.id.etReviewComment);
        CheckBox anonymous = content.findViewById(R.id.cbReviewAnonymous);
        if (existingReview != null) {
            rating.setRating(existingReview.getRating());
            comment.setText(existingReview.getComment());
            anonymous.setChecked(existingReview.isAnonymous());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existingReview == null ? "Đánh giá sản phẩm" : "Sửa đánh giá")
                .setMessage(productIcon + "  " + productName)
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setPositiveButton(existingReview == null ? "Gửi đánh giá" : "Lưu thay đổi", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = comment.getText().toString().trim();
            if (rating.getRating() < 1 || text.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn số sao và nhập nội dung đánh giá.", Toast.LENGTH_SHORT).show();
                return;
            }
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setEnabled(false);
            String reviewerName = SharedPrefManager.getInstance(this).getUsername();
            repository.saveReview(email, reviewerName, orderId, orderItemId, productId, productName, productIcon,
                    Math.round(rating.getRating()), text, anonymous.isChecked(), new ReviewRepository.SaveCallback() {
                        @Override
                        public void onResult(ReviewRepository.SaveResult result) {
                            dialog.dismiss();
                            if (result.isCreated() && result.isRewardGranted()) {
                                Toast.makeText(ReviewActivity.this,
                                        "Đánh giá hợp lệ, 500 Xu đã được ghi nhận và bù trừ nếu có khoản thu hồi.",
                                        Toast.LENGTH_LONG).show();
                            } else if (result.isCreated()) {
                                Toast.makeText(ReviewActivity.this, "Đánh giá đã được ghi nhận.", Toast.LENGTH_SHORT)
                                        .show();
                            } else {
                                Toast.makeText(ReviewActivity.this, "Đã cập nhật đánh giá, không nhận thêm Xu.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            loadReviews();
                        }

                        @Override
                        public void onError(Exception error) {
                            saveButton.setEnabled(true);
                            Toast.makeText(ReviewActivity.this,
                                    error.getMessage() == null ? "Không thể lưu đánh giá." : error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }));
        dialog.show();
    }

    private void openProductReview(Review review) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, review.getProductId());
        intent.putExtra(ProductDetailActivity.EXTRA_REVIEW_ID, review.getId());
        startActivity(intent);
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(R.drawable.bg_card_white);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private TextView createText(String value, int colorResource, float size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(ContextCompat.getColor(this, colorResource));
        text.setTextSize(size);
        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }
        return text;
    }

    private Button createActionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTransformationMethod(null);
        button.setTextSize(12);
        button.setTextColor(ContextCompat.getColor(this, R.color.primary));
        button.setBackgroundResource(R.drawable.bg_counter_btn);
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private String stars(int rating) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            text.append(i < rating ? "★" : "☆");
        }
        return text.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
