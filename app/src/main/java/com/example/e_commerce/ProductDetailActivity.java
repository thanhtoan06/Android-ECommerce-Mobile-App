
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    public static final String EXTRA_REVIEW_ID = "extra_review_id";
    private TextView tvHeaderCartCount;
    private LinearLayout layoutProductReviewItems;
    private LinearLayout layoutProductReviews;
    private TextView tvProductReviewListTitle;
    private TextView tvProductReviewListEmpty;
    private TextView tvProductReviewSummary;
    private android.widget.ScrollView scrollProductDetail;
    private int productReviewCount;
    private int quantity = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_product_detail);

        View btnBack = findViewById(R.id.btnBack);
        ImageView ivProductImage = findViewById(R.id.ivProductImage);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvStock = findViewById(R.id.tvStock);
        TextView tvName = findViewById(R.id.tvName);
        tvProductReviewSummary = findViewById(R.id.tvProductReviewSummary);
        tvProductReviewListTitle = findViewById(R.id.tvProductReviewListTitle);
        tvProductReviewListEmpty = findViewById(R.id.tvProductReviewListEmpty);
        layoutProductReviewItems = findViewById(R.id.layoutProductReviewItems);
        layoutProductReviews = findViewById(R.id.layoutProductReviews);
        scrollProductDetail = findViewById(R.id.scrollProductDetail);
        TextView tvPrice = findViewById(R.id.tvPrice);
        TextView tvOriginalPrice = findViewById(R.id.tvOriginalPrice);
        TextView tvVoucherOffer = findViewById(R.id.tvVoucherOffer);
        View layoutVoucherOffer = findViewById(R.id.layoutVoucherOffer);
        TextView tvFreeShipping = findViewById(R.id.tvFreeShipping);
        TextView tvDescription = findViewById(R.id.tvDescription);
        TextView tvQuantity = findViewById(R.id.tvQuantity);
        View btnDecrease = findViewById(R.id.btnDecrease);
        View btnIncrease = findViewById(R.id.btnIncrease);
        Button btnAddToCart = findViewById(R.id.btnAddToCart);
        Button btnBuyNow = findViewById(R.id.btnBuyNow);
        tvHeaderCartCount = findViewById(R.id.tvHeaderCartCount);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnHeaderCart).setOnClickListener(v -> {
            if (AuthGate.requireLogin(this)) {
                startActivity(new Intent(this, CartActivity.class));
            }
        });

        int productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, -1);
        ProductRepository.findById(productId, new ProductRepository.ProductCallback() {
            @Override
            public void onResult(Product product) {

                if (product == null) {
                    Toast.makeText(ProductDetailActivity.this, "Không tìm thấy thông tin sản phẩm!", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                    return;
                }

                ImageBlobLoader.bind(ivProductImage, product.getImageBytes(),
                        "product-" + product.getId(), R.drawable.ic_product_image_placeholder);
                tvCategory.setText(product.getCategory());
                tvName.setText(product.getName());
                productReviewCount = product.getReviewCount();
                updateProductReviewSummary(product.getAverageRating(), product.getReviewCount(),
                        product.getSoldCount());
                loadProductReviews(product.getId());
                tvPrice.setText(product.getFormattedPrice());
                tvOriginalPrice.setVisibility(View.GONE);
                layoutVoucherOffer.setVisibility(View.GONE);
                tvFreeShipping.setVisibility(View.GONE);
                loadVoucherOffers(product, tvPrice, tvOriginalPrice,
                        layoutVoucherOffer, tvVoucherOffer, tvFreeShipping);
                tvDescription.setText(product.getDescription());

                int stock = product.getStock();
                if (tvStock != null) {
                    if (stock <= 0) {
                        tvStock.setText("Hết hàng");
                        tvStock.setTextColor(0xFFEF4444);

                    } else if (stock <= 5) {
                        tvStock.setText("Còn " + stock + " sp (sắp hết)");
                        tvStock.setTextColor(0xFFF97316);

                    } else {
                        tvStock.setText("Còn " + stock + " sp");
                        tvStock.setTextColor(0xFF64748B);

                    }
                }

                if (stock <= 0) {
                    if (tvQuantity != null)
                        tvQuantity.setText("0");
                    if (btnDecrease != null)
                        btnDecrease.setEnabled(false);
                    if (btnIncrease != null)
                        btnIncrease.setEnabled(false);
                    btnAddToCart.setEnabled(false);
                    btnAddToCart.setAlpha(0.4f);
                    btnAddToCart.setText("Hết hàng");
                    btnBuyNow.setEnabled(false);
                    btnBuyNow.setAlpha(0.4f);
                    btnBuyNow.setText("Hết hàng");
                    return;
                }

                quantity = 1;
                if (tvQuantity != null) {
                    tvQuantity.setText(String.valueOf(quantity));
                }
                if (btnDecrease != null) {
                    btnDecrease.setOnClickListener(v -> {
                        if (quantity > 1) {
                            quantity--;
                            if (tvQuantity != null)
                                tvQuantity.setText(String.valueOf(quantity));
                        }
                    });
                }
                if (btnIncrease != null) {
                    btnIncrease.setOnClickListener(v -> {
                        if (quantity < stock) {
                            quantity++;
                            if (tvQuantity != null)
                                tvQuantity.setText(String.valueOf(quantity));
                        } else {
                            Toast.makeText(ProductDetailActivity.this, "Đã đạt số lượng tồn kho tối đa (" + stock + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                btnAddToCart.setOnClickListener(v -> {
                    if (stock <= 0) {
                        Toast.makeText(ProductDetailActivity.this, "Sản phẩm hiện đã hết hàng!", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    if (!AuthGate.requireLogin(ProductDetailActivity.this)) {
                        return;
                    }

                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    btnAddToCart.setEnabled(false);

                    final int selectedQty = Math.min(quantity, stock);
                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), selectedQty, success -> {
                        btnAddToCart.setEnabled(true);
                        if (!success) {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        updateCartBadge();
                        new AlertDialog.Builder(ProductDetailActivity.this).setTitle("Đã thêm vào giỏ hàng")
                                .setMessage("Sản phẩm \"" + product.getName() + "\" (" + selectedQty
                                        + " sản phẩm) đã được thêm vào giỏ hàng!")
                                .setPositiveButton("Xem giỏ hàng",
                                        (dialog, which) -> startActivity(
                                                new Intent(ProductDetailActivity.this, CartActivity.class)))
                                .setNegativeButton("Tiếp tục xem", null).show();
                    });
                });

                btnBuyNow.setOnClickListener(v -> {
                    if (stock <= 0) {
                        Toast.makeText(ProductDetailActivity.this, "Sản phẩm hiện đã hết hàng!", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    if (!AuthGate.requireLogin(ProductDetailActivity.this)) {
                        return;
                    }

                    String userEmail = SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail();
                    btnBuyNow.setEnabled(false);

                    final int selectedQty = Math.min(quantity, stock);
                    FirestoreRepository.getInstance().addToCart(userEmail, product.getId(), selectedQty, success -> {
                        btnBuyNow.setEnabled(true);
                        if (!success) {
                            Toast.makeText(ProductDetailActivity.this, "Không thể thêm vào giỏ hàng!",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        startActivity(new Intent(ProductDetailActivity.this, CartActivity.class));
                    });
                });
            }

            @Override
            public void onError(Exception e) {

                Toast.makeText(ProductDetailActivity.this, "Không thể tải sản phẩm từ Firebase.", Toast.LENGTH_SHORT)
                        .show();
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    private void updateCartBadge() {
        if (tvHeaderCartCount != null) {
            if (!SharedPrefManager.getInstance(this).isLoggedIn()) {
                tvHeaderCartCount.setText("0");
                return;
            }
            String userEmail = SharedPrefManager.getInstance(this).getEmail();
            FirestoreRepository.getInstance().getCartCount(userEmail,
                    count -> tvHeaderCartCount.setText(String.valueOf(count)));
        }
    }

    private void loadVoucherOffers(Product product, TextView price, TextView originalPrice,
            View voucherOfferLayout, TextView voucherOffer, TextView freeShipping) {
        SharedPrefManager preferences = SharedPrefManager.getInstance(this);
        if (!preferences.isLoggedIn()) {
            return;
        }

        VoucherRepository.getInstance().loadWallet(preferences.getEmail(), new VoucherRepository.WalletCallback() {
            @Override
            public void onResult(VoucherRepository.VoucherWallet wallet) {
                CartItem item = new CartItem(product.getId(), product, 1);
                Voucher bestProductVoucher = null;
                long bestDiscount = 0;
                long bestShippingDiscount = 0;
                for (Voucher voucher : wallet.getSaved()) {
                    if (voucher.isExpired(new java.util.Date())) {
                        continue;
                    }
                    if (voucher.isProductVoucher()) {
                        VoucherCalculator.ProductDiscount discount =
                                VoucherCalculator.calculateProductDiscount(
                                        java.util.Collections.singletonList(item), voucher);
                        if (discount.getTotalDiscount() > bestDiscount) {
                            bestDiscount = discount.getTotalDiscount();
                            bestProductVoucher = voucher;
                        }
                    } else if (voucher.isShippingVoucher()) {
                        long shippingDiscount = VoucherCalculator.calculateShippingDiscount(
                                java.util.Collections.singletonList(item),
                                voucher,
                                VoucherCalculator.BASE_SHIPPING_FEE);
                        bestShippingDiscount = Math.max(bestShippingDiscount, shippingDiscount);
                    }
                }

                if (bestProductVoucher != null) {
                    originalPrice.setText(product.getFormattedPrice());
                    originalPrice.setPaintFlags(
                            originalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    originalPrice.setVisibility(View.VISIBLE);
                    price.setText(formatMoney(product.getPrice() - bestDiscount));
                    voucherOffer.setText("Ưu đãi " + bestProductVoucher.getCode()
                            + " · -" + formatMoney(bestDiscount));
                    voucherOfferLayout.setVisibility(View.VISIBLE);
                }
                if (bestShippingDiscount > 0) {
                    freeShipping.setText(bestShippingDiscount >= VoucherCalculator.BASE_SHIPPING_FEE
                            ? "Miễn phí vận chuyển"
                            : "Giảm phí ship " + formatMoney(bestShippingDiscount));
                    freeShipping.setVisibility(View.VISIBLE);
                } else {
                    freeShipping.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(ProductDetailActivity.this, "Không thể tải ưu đãi đã lưu.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatMoney(long amount) {
        return java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private void updateProductReviewSummary(double averageRating, int reviewCount, int soldCount) {
        tvProductReviewSummary.setText(String.format(Locale.forLanguageTag("vi-VN"),
                "★ %.1f (%d đánh giá) · Đã bán %d", averageRating, reviewCount, soldCount));
    }

    private void loadProductReviews(int productId) {
        layoutProductReviewItems.removeAllViews();
        tvProductReviewListEmpty.setVisibility(View.GONE);
        ReviewRepository.getInstance().getProductReviews(productId, new ReviewRepository.ReviewsCallback() {
            @Override
            public void onResult(List<Review> reviews) {
                String requestedReviewId = getIntent().getStringExtra(EXTRA_REVIEW_ID);
                boolean targetIncluded = requestedReviewId == null;
                for (Review review : reviews) {
                    if (requestedReviewId != null && requestedReviewId.equals(review.getId())) {
                        targetIncluded = true;
                        break;
                    }
                }
                if (!targetIncluded) {
                    ReviewRepository.getInstance().getReviewById(requestedReviewId,
                            new ReviewRepository.ReviewCallback() {
                                @Override
                                public void onResult(Review requestedReview) {
                                    if (requestedReview != null && requestedReview.getProductId() == productId) {
                                        reviews.add(0, requestedReview);
                                    } else {
                                        Toast.makeText(ProductDetailActivity.this,
                                                "Không tìm thấy bài đánh giá cần mở.", Toast.LENGTH_SHORT).show();
                                    }
                                    renderProductReviews(reviews);
                                }

                                @Override
                                public void onError(Exception error) {
                                    tvProductReviewListEmpty.setText("Không thể tải bài đánh giá được chọn.");
                                    tvProductReviewListEmpty.setVisibility(View.VISIBLE);
                                }
                            });
                    return;
                }
                renderProductReviews(reviews);
            }

            @Override
            public void onError(Exception error) {
                tvProductReviewListEmpty.setText("Không thể tải đánh giá. Vui lòng thử lại.");
                tvProductReviewListEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderProductReviews(List<Review> reviews) {
        tvProductReviewListTitle.setText("Đánh giá của người mua (" + productReviewCount + ")");
        tvProductReviewListEmpty.setVisibility(reviews.isEmpty() ? View.VISIBLE : View.GONE);
        for (Review review : reviews) {
            layoutProductReviewItems.addView(createProductReviewCard(review));
            if (SharedPrefManager.getInstance(ProductDetailActivity.this).isLoggedIn()) {
                ReviewRepository.getInstance().recordView(review.getId(),
                        SharedPrefManager.getInstance(ProductDetailActivity.this).getEmail(),
                        new ReviewRepository.InteractionCallback() {
                            @Override
                            public void onResult(boolean changed) {
                                if (changed) {
                                    View card = layoutProductReviewItems.findViewWithTag(review.getId());
                                    if (card != null) {
                                        TextView totals = (TextView) card.findViewWithTag("review_totals");
                                        if (totals != null) {
                                            totals.setText(interactionSummary(
                                                    review.getLikesCount(), review.getViewsCount() + 1));
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                Toast.makeText(ProductDetailActivity.this,
                                        "Không thể ghi nhận lượt xem đánh giá.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }
        scrollToRequestedReview(reviews);
    }

    private View createProductReviewCard(Review review) {
        LinearLayout card = new LinearLayout(this);
        card.setTag(review.getId());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(12), dp(10), dp(12));
        card.setBackgroundResource(R.drawable.bg_chip_blue);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);

        TextView author = createReviewText(review.getDisplayName(), R.color.text_primary, 13, true);
        card.addView(author);
        TextView rating = createReviewText(reviewStars(review.getRating()) + "  ·  "
                + formatReviewDate(review.getCreatedAt()), R.color.text_secondary, 11, false);
        rating.setPadding(0, dp(4), 0, dp(6));
        card.addView(rating);
        TextView comment = createReviewText(review.getComment(), R.color.text_primary, 13, false);
        comment.setMaxLines(8);
        comment.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(comment);

        LinearLayout interactions = new LinearLayout(this);
        interactions.setGravity(Gravity.CENTER_VERTICAL);
        interactions.setOrientation(LinearLayout.HORIZONTAL);
        interactions.setPadding(0, dp(6), 0, 0);
        TextView totals = createReviewText(interactionSummary(
                review.getLikesCount(), review.getViewsCount()), R.color.text_secondary, 11, false);
        totals.setTag("review_totals");
        LinearLayout.LayoutParams totalsParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        totals.setLayoutParams(totalsParams);
        interactions.addView(totals);

        Button like = new Button(this);
        like.setText("Thích");
        like.setTransformationMethod(null);
        like.setTextSize(11);
        like.setPadding(dp(8), 0, dp(8), 0);
        like.setMinHeight(0);
        like.setMinimumHeight(0);
        interactions.addView(like);
        like.setOnClickListener(v -> {
            if (!AuthGate.requireLogin(this)) {
                return;
            }
            String email = SharedPrefManager.getInstance(this).getEmail();
            like.setEnabled(false);
            ReviewRepository.getInstance().recordLike(review.getId(), email,
                    new ReviewRepository.InteractionCallback() {
                        @Override
                        public void onResult(boolean changed) {
                            like.setEnabled(true);
                            if (changed) {
                                like.setText("Đã thích");
                                totals.setText(interactionSummary(
                                        review.getLikesCount() + 1, review.getViewsCount()));
                            } else {
                                Toast.makeText(ProductDetailActivity.this,
                                        "Bạn đã thích bài này hoặc không thể tự thích đánh giá của mình.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            like.setEnabled(true);
                            Toast.makeText(ProductDetailActivity.this,
                                    "Không thể ghi nhận lượt thích.", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
        card.addView(interactions);
        return card;
    }

    private TextView createReviewText(String text, int colorResource, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(this, colorResource));
        view.setTextSize(size);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private String interactionSummary(long likes, long views) {
        return "♥ " + likes + " lượt thích  ·  " + views + " lượt xem";
    }

    private String reviewStars(int rating) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            text.append(i < rating ? "★" : "☆");
        }
        return text.toString();
    }

    private String formatReviewDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(date);
    }

    private void scrollToRequestedReview(List<Review> reviews) {
        String requestedReviewId = getIntent().getStringExtra(EXTRA_REVIEW_ID);
        if (requestedReviewId == null) {
            return;
        }
        for (Review review : reviews) {
            if (requestedReviewId.equals(review.getId())) {
                View target = layoutProductReviewItems.findViewWithTag(review.getId());
                if (target != null) {
                    target.setBackgroundResource(R.drawable.bg_chip_green);
                    scrollProductDetail.post(() -> scrollProductDetail.smoothScrollTo(
                            0, layoutProductReviews.getTop() + target.getTop()));
                }
                return;
            }
        }
        Toast.makeText(this, "Không tìm thấy bài đánh giá cần mở.", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
