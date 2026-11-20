
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrderDetailActivity extends AppCompatActivity {

    private TextView tvOrderDetailCode;
    private TextView tvOrderDetailStatus;
    private TextView tvOrderDetailDate;
    private TextView tvReceiverNamePhone;
    private TextView tvReceiverAddressDetail;
    private TextView tvOrderNote;
    private TextView tvPaymentMethodDetail;
    private TextView tvSubtotalLabel;
    private TextView tvSubtotalAmount;
    private View layoutProductDiscountRow;
    private TextView tvProductDiscountAmount;
    private TextView tvShippingFeeAmount;
    private View layoutShippingDiscountRow;
    private TextView tvShippingDiscountAmount;
    private View layoutPointsDiscountRow;
    private TextView tvPointsDiscountAmount;
    private TextView tvTotalAmountDetail;
    private TextView tvOrderReturnInfo;
    private TextView tvOrderCancellationInfo;
    private LinearLayout llOrderDetailProducts;
    private ProgressBar pbLoadingProducts;
    private com.google.android.material.button.MaterialButton btnReturnOrder;
    private com.google.android.material.button.MaterialButton btnCancelOrder;

    private FirestoreRepository repository;
    private Order currentOrder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        repository = FirestoreRepository.getInstance();

        initViews();
        btnReturnOrder.setOnClickListener(v -> showReturnReasonOptions());
        btnCancelOrder.setOnClickListener(v -> confirmOrderCancellation());

        View samplePreview = findViewById(R.id.layoutSampleProductPreview);
        if (samplePreview != null) {
            samplePreview.setVisibility(View.GONE);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        currentOrder = (Order) getIntent().getSerializableExtra("order");
        if (currentOrder == null) {
            int orderId = getIntent().getIntExtra("order_id", -1);
            if (orderId == -1) {
                Toast.makeText(this, "Không tìm thấy thông tin đơn hàng.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            loadOrderById(orderId);
            return;
        }

        displayOrder(currentOrder);
    }

    private void loadOrderById(int orderId) {
        String email = SharedPrefManager.getInstance(this).getEmail();
        repository.getUserOrders(email, new FirestoreRepository.OrdersCallback() {
            @Override
            public void onResult(List<Order> orders) {
                for (Order order : orders) {
                    if (order.getId() == orderId) {
                        currentOrder = order;
                        displayOrder(order);
                        return;
                    }
                }
                Toast.makeText(OrderDetailActivity.this, "Không tìm thấy thông tin đơn hàng.", Toast.LENGTH_SHORT)
                        .show();
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(OrderDetailActivity.this, "Không thể tải đơn hàng từ Firebase.", Toast.LENGTH_SHORT)
                        .show();
                finish();
            }
        });
    }

    private void displayOrder(Order order) {
        bindOrderDetails(order);
        loadOrderProducts(order.getId());
    }

    private void initViews() {
        tvOrderDetailCode = findViewById(R.id.tvOrderDetailCode);
        tvOrderDetailStatus = findViewById(R.id.tvOrderDetailStatus);
        tvOrderDetailDate = findViewById(R.id.tvOrderDetailDate);
        tvReceiverNamePhone = findViewById(R.id.tvReceiverNamePhone);
        tvReceiverAddressDetail = findViewById(R.id.tvReceiverAddressDetail);
        tvOrderNote = findViewById(R.id.tvOrderNote);
        tvPaymentMethodDetail = findViewById(R.id.tvPaymentMethodDetail);
        tvSubtotalLabel = findViewById(R.id.tvSubtotalLabel);
        tvSubtotalAmount = findViewById(R.id.tvSubtotalAmount);
        layoutProductDiscountRow = findViewById(R.id.layoutProductDiscountRow);
        tvProductDiscountAmount = findViewById(R.id.tvProductDiscountAmount);
        tvShippingFeeAmount = findViewById(R.id.tvShippingFeeAmount);
        layoutShippingDiscountRow = findViewById(R.id.layoutShippingDiscountRow);
        tvShippingDiscountAmount = findViewById(R.id.tvShippingDiscountAmount);
        layoutPointsDiscountRow = findViewById(R.id.layoutPointsDiscountRow);
        tvPointsDiscountAmount = findViewById(R.id.tvPointsDiscountAmount);
        tvTotalAmountDetail = findViewById(R.id.tvTotalAmountDetail);
        tvOrderReturnInfo = findViewById(R.id.tvOrderReturnInfo);
        tvOrderCancellationInfo = findViewById(R.id.tvOrderCancellationInfo);
        llOrderDetailProducts = findViewById(R.id.llOrderDetailProducts);
        pbLoadingProducts = findViewById(R.id.pbLoadingProducts);
        btnReturnOrder = findViewById(R.id.btnReturnOrder);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
    }

    private void bindOrderDetails(Order order) {
        tvOrderDetailCode.setText(String.format(Locale.getDefault(), "#DH%05d", order.getId()));
        tvOrderDetailStatus.setText(order.getStatus());
        updateReturnControls(order);
        tvOrderDetailDate.setText(order.getOrderDate());

        tvReceiverNamePhone.setText(order.getReceiverName() + " (" + order.getReceiverPhone() + ")");
        tvReceiverAddressDetail.setText(order.getReceiverAddress());

        if (order.getNote() != null && !order.getNote().trim().isEmpty()) {
            tvOrderNote.setVisibility(View.VISIBLE);
            tvOrderNote.setText("Ghi chú: " + order.getNote());
        } else {
            tvOrderNote.setVisibility(View.GONE);
        }

        tvPaymentMethodDetail.setText(order.getPaymentMethod());
        tvSubtotalAmount.setText(order.getItemsSubtotal() < 0
                ? "—"
                : formatMoney(order.getItemsSubtotal()));
        layoutProductDiscountRow.setVisibility(order.getProductDiscount() > 0 ? View.VISIBLE : View.GONE);
        tvProductDiscountAmount.setText("-" + formatMoney(order.getProductDiscount()));
        tvShippingFeeAmount.setText(order.getShippingFee() < 0
                ? "Chưa có dữ liệu"
                : formatMoney(order.getShippingFee()));
        layoutShippingDiscountRow.setVisibility(order.getShippingDiscount() > 0 ? View.VISIBLE : View.GONE);
        tvShippingDiscountAmount.setText("-" + formatMoney(order.getShippingDiscount()));
        layoutPointsDiscountRow.setVisibility(order.getPointsDiscount() > 0 ? View.VISIBLE : View.GONE);
        tvPointsDiscountAmount.setText("-" + formatMoney(order.getPointsDiscount()));
        tvTotalAmountDetail.setText(order.getFormattedTotal());
    }

    private void loadOrderProducts(int orderId) {
        pbLoadingProducts.setVisibility(View.VISIBLE);
        llOrderDetailProducts.removeAllViews();

        repository.getOrderItems(orderId, new FirestoreRepository.OrderItemsCallback() {
            @Override
            public void onResult(List<OrderItem> items) {
                pbLoadingProducts.setVisibility(View.GONE);
                if (items != null && !items.isEmpty()) {
                    renderProductItems(items);
                } else {
                    showEmptyProducts();
                }
            }

            @Override
            public void onError(Exception e) {
                pbLoadingProducts.setVisibility(View.GONE);
                Toast.makeText(OrderDetailActivity.this, "Không thể tải chi tiết đơn hàng từ Firebase.",
                        Toast.LENGTH_SHORT).show();
                showEmptyProducts();
            }
        });
    }

    private void showEmptyProducts() {
        TextView tvEmpty = new TextView(this);
        tvEmpty.setText("Không có chi tiết sản phẩm.");
        tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvEmpty.setTextSize(13f);
        tvEmpty.setPadding(0, 16, 0, 16);
        llOrderDetailProducts.addView(tvEmpty);
    }

    private void renderProductItems(List<OrderItem> items) {
        llOrderDetailProducts.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int iconSize = (int) (40 * density);
        int marginStart = (int) (12 * density);
        int marginEnd = (int) (8 * density);
        int paddingVertical = (int) (6 * density);

        int totalQty = 0;
        long displayedItemsSubtotal = 0;

        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            totalQty += item.getQuantity();
            displayedItemsSubtotal += item.getSubtotal();

            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, paddingVertical, 0, paddingVertical);

            TextView tvIcon = new TextView(this);
            tvIcon.setText(item.getProductIcon());
            tvIcon.setTextSize(20f);
            tvIcon.setGravity(Gravity.CENTER);
            tvIcon.setBackgroundResource(R.drawable.bg_counter_btn);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            tvIcon.setLayoutParams(iconParams);
            itemRow.addView(tvIcon);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f);
            infoParams.setMarginStart(marginStart);
            infoParams.setMarginEnd(marginEnd);
            infoCol.setLayoutParams(infoParams);

            TextView tvName = new TextView(this);
            tvName.setText(item.getProductName());
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setMaxLines(2);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            infoCol.addView(tvName);

            TextView tvPriceQty = new TextView(this);
            tvPriceQty.setText(item.getFormattedPrice() + "  ×" + item.getQuantity());
            tvPriceQty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvPriceQty.setTextSize(12f);
            tvPriceQty.setPadding(0, (int) (3 * density), 0, 0);
            infoCol.addView(tvPriceQty);

            itemRow.addView(infoCol);

            TextView tvSubtotal = new TextView(this);
            tvSubtotal.setText(item.getFormattedSubtotal());
            tvSubtotal.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvSubtotal.setTextSize(14f);
            tvSubtotal.setTypeface(null, Typeface.BOLD);
            itemRow.addView(tvSubtotal);

            llOrderDetailProducts.addView(itemRow);

            if (ReviewRules.isDelivered(currentOrder.getStatus())) {
                LinearLayout reviewActionRow = new LinearLayout(this);
                reviewActionRow.setOrientation(LinearLayout.HORIZONTAL);
                reviewActionRow.setGravity(Gravity.END);
                Button reviewButton = new Button(this);
                reviewButton.setText("Viết / sửa đánh giá");
                reviewButton.setTransformationMethod(null);
                reviewButton.setTextSize(12f);
                reviewButton.setOnClickListener(v -> {
                    if (!AuthGate.requireLogin(this)) {
                        return;
                    }
                    Intent intent = new Intent(this, ReviewActivity.class);
                    intent.putExtra(ReviewActivity.EXTRA_ORDER_ID, currentOrder.getId());
                    intent.putExtra(ReviewActivity.EXTRA_ORDER_ITEM_ID, item.getId());
                    intent.putExtra(ReviewActivity.EXTRA_PRODUCT_ID, item.getProductId());
                    startActivity(intent);
                });
                LinearLayout.LayoutParams reviewParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                reviewActionRow.addView(reviewButton, reviewParams);
                llOrderDetailProducts.addView(reviewActionRow);
            }

            if (i < items.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (1 * density));
                divParams.setMargins(0, (int) (4 * density), 0, (int) (4 * density));
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_stroke));
                llOrderDetailProducts.addView(divider);
            }
        }

        tvSubtotalLabel.setText("Tiền hàng (" + totalQty + " món)");
        if (currentOrder.getItemsSubtotal() < 0) {
            tvSubtotalAmount.setText(formatMoney(displayedItemsSubtotal));
        }
    }

    private void updateReturnControls(Order order) {
        boolean returned = OrderReturnRules.STATUS_RETURNED.equals(order.getStatus());
        boolean cancelled = OrderCancellationRules.STATUS_CANCELLED.equals(order.getStatus());
        boolean eligible = OrderReturnRules.isEligible(
                order.getStatus(), order.getDeliveredAt(), order.getOrderDate(), new Date());
        btnReturnOrder.setVisibility(eligible ? View.VISIBLE : View.GONE);
        btnCancelOrder.setVisibility(
                OrderCancellationRules.isEligible(order.getStatus()) ? View.VISIBLE : View.GONE);
        tvOrderReturnInfo.setVisibility(View.GONE);
        tvOrderCancellationInfo.setVisibility(cancelled ? View.VISIBLE : View.GONE);
        if (cancelled) {
            tvOrderCancellationInfo.setText(
                    "Đơn hàng đã hủy. Tồn kho và Xu đã dùng được hoàn lại; voucher còn hạn được khôi phục.");
        }
        if (returned) {
            tvOrderReturnInfo.setText("Đã hoàn trả · Lý do: "
                    + (order.getReturnReason() == null ? "" : order.getReturnReason()));
            tvOrderReturnInfo.setVisibility(View.VISIBLE);
        } else if (ReviewRules.isDelivered(order.getStatus()) && !eligible) {
            tvOrderReturnInfo.setText("Đã quá thời hạn yêu cầu hoàn trả 7 ngày.");
            tvOrderReturnInfo.setVisibility(View.VISIBLE);
        }
    }

    private void confirmOrderCancellation() {
        if (currentOrder == null || !AuthGate.requireLogin(this)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hủy đơn hàng")
                .setMessage("Tồn kho và Xu đã dùng sẽ được hoàn lại. Voucher chỉ được khôi phục nếu còn hạn.")
                .setPositiveButton("Hủy đơn", (dialog, which) -> submitOrderCancellation())
                .setNegativeButton("Giữ đơn", null)
                .show();
    }

    private void submitOrderCancellation() {
        btnCancelOrder.setEnabled(false);
        String email = SharedPrefManager.getInstance(this).getEmail();
        repository.cancelOrder(currentOrder.getId(), email,
                new FirestoreRepository.OrderCancellationCallback() {
                    @Override
                    public void onSuccess() {
                        currentOrder.setStatus(OrderCancellationRules.STATUS_CANCELLED);
                        displayOrder(currentOrder);
                        Toast.makeText(OrderDetailActivity.this,
                                "Đơn hàng đã được hủy.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Exception error) {
                        btnCancelOrder.setEnabled(true);
                        Toast.makeText(OrderDetailActivity.this,
                                error.getMessage() == null
                                        ? "Không thể hủy đơn hàng. Vui lòng thử lại."
                                        : error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showReturnReasonOptions() {
        if (currentOrder == null || !AuthGate.requireLogin(this)) {
            return;
        }
        String[] reasons = {"Không đúng mẫu", "Thiếu sản phẩm", "Lý do khác"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn lý do hoàn trả")
                .setItems(reasons, (dialog, selected) -> {
                    if (selected == 0) {
                        confirmReturn(OrderReturnRules.REASON_WRONG_MODEL, "");
                    } else if (selected == 1) {
                        confirmReturn(OrderReturnRules.REASON_MISSING_ITEM, "");
                    } else {
                        showOtherReturnReason();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showOtherReturnReason() {
        EditText reasonInput = new EditText(this);
        reasonInput.setHint("Nhập lý do hoàn trả");
        reasonInput.setMinLines(3);
        reasonInput.setGravity(Gravity.TOP | Gravity.START);
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        reasonInput.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Lý do khác")
                .setView(reasonInput)
                .setPositiveButton("Tiếp tục", null)
                .setNegativeButton("Hủy", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (!OrderReturnRules.isValidReason(OrderReturnRules.REASON_OTHER, reason)) {
                        Toast.makeText(this, "Vui lòng nhập lý do tối đa 500 ký tự.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    confirmReturn(OrderReturnRules.REASON_OTHER, reason);
                }));
        dialog.show();
    }

    private void confirmReturn(String reasonType, String reason) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận hoàn trả")
                .setMessage("Yêu cầu hoàn trả toàn bộ đơn hàng sẽ được tự động chấp nhận.")
                .setPositiveButton("Gửi yêu cầu", (dialog, which) -> submitReturn(reasonType, reason))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void submitReturn(String reasonType, String reason) {
        btnReturnOrder.setEnabled(false);
        String email = SharedPrefManager.getInstance(this).getEmail();
        repository.requestOrderReturn(currentOrder.getId(), email, reasonType, reason,
                new FirestoreRepository.OrderReturnCallback() {
                    @Override
                    public void onSuccess() {
                        currentOrder.setStatus(OrderReturnRules.STATUS_RETURNED);
                        currentOrder.setReturnReason(displayReturnReason(reasonType, reason));
                        displayOrder(currentOrder);
                        Toast.makeText(OrderDetailActivity.this,
                                "Đơn hàng đã được hoàn trả.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Exception error) {
                        btnReturnOrder.setEnabled(true);
                        Toast.makeText(OrderDetailActivity.this,
                                error.getMessage() == null
                                        ? "Không thể hoàn trả đơn hàng. Vui lòng thử lại."
                                        : error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String displayReturnReason(String reasonType, String reason) {
        if (OrderReturnRules.REASON_WRONG_MODEL.equals(reasonType)) {
            return "Không đúng mẫu";
        }
        if (OrderReturnRules.REASON_MISSING_ITEM.equals(reasonType)) {
            return "Thiếu sản phẩm";
        }
        return reason.trim();
    }

    private String formatMoney(long amount) {
        return java.text.NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

}
