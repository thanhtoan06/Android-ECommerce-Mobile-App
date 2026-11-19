// Màn hình Chi tiết đơn hàng: hiển thị đầy đủ thông tin sản phẩm, người nhận, địa chỉ và thanh toán từ Firebase/SQLite.
package com.example.e_commerce;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

public class OrderDetailActivity extends AppCompatActivity {

    // Khai báo các thành phần hiển thị trên giao diện chi tiết đơn hàng
    private TextView tvOrderDetailCode;
    private TextView tvOrderDetailStatus;
    private TextView tvOrderDetailDate;
    private TextView tvReceiverNamePhone;
    private TextView tvReceiverAddressDetail;
    private TextView tvOrderNote;
    private TextView tvPaymentMethodDetail;
    private TextView tvSubtotalLabel;
    private TextView tvSubtotalAmount;
    private TextView tvTotalAmountDetail;
    private LinearLayout llOrderDetailProducts;
    private ProgressBar pbLoadingProducts;

    // Kho dữ liệu Firebase và SQLite
    private FirestoreRepository repository;
    private DatabaseHelper databaseHelper;
    private Order currentOrder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);

        // Ánh xạ các thành phần giao diện
        initViews();

        // Đảm bảo dòng xem trước mẫu luôn ẩn khi chạy ứng dụng thật
        View samplePreview = findViewById(R.id.layoutSampleProductPreview);
        if (samplePreview != null) {
            samplePreview.setVisibility(View.GONE);
        }

        // Nút quay lại tròn tối giản
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Lấy dữ liệu đơn hàng từ Intent
        currentOrder = (Order) getIntent().getSerializableExtra("order");
        if (currentOrder == null) {
            int orderId = getIntent().getIntExtra("order_id", -1);
            if (orderId != -1) {
                // Thử tìm trong SQLite nếu truyền ID
                String email = SharedPrefManager.getInstance(this).getEmail();
                List<Order> orders = databaseHelper.getUserOrders(email);
                for (Order o : orders) {
                    if (o.getId() == orderId) {
                        currentOrder = o;
                        break;
                    }
                }
            }
        }

        if (currentOrder == null) {
            Toast.makeText(this, "Không tìm thấy thông tin đơn hàng.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Đổ dữ liệu chung của đơn hàng lên giao diện
        bindOrderDetails(currentOrder);

        // Tải danh sách chi tiết các món hàng trong đơn
        loadOrderProducts(currentOrder.getId());
    }

    // Ánh xạ các view trên layout
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
        tvTotalAmountDetail = findViewById(R.id.tvTotalAmountDetail);
        llOrderDetailProducts = findViewById(R.id.llOrderDetailProducts);
        pbLoadingProducts = findViewById(R.id.pbLoadingProducts);
    }

    // Hiển thị thông tin chung đơn hàng
    private void bindOrderDetails(Order order) {
        tvOrderDetailCode.setText(String.format(Locale.getDefault(), "#DH%05d", order.getId()));
        tvOrderDetailStatus.setText(order.getStatus());
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
        tvSubtotalAmount.setText(order.getFormattedTotal());
        tvTotalAmountDetail.setText(order.getFormattedTotal());
    }

    // Tải danh sách chi tiết sản phẩm từ Firebase, fallback sang SQLite nếu ngoại tuyến
    private void loadOrderProducts(int orderId) {
        pbLoadingProducts.setVisibility(View.VISIBLE);
        llOrderDetailProducts.removeAllViews();

        repository.getOrderItems(orderId, new FirestoreRepository.OrderItemsCallback() {
            @Override
            public void onResult(List<OrderItem> items) {
                pbLoadingProducts.setVisibility(View.GONE);
                if (items != null && !items.isEmpty()) {
                    databaseHelper.saveRemoteOrder(currentOrder, items);
                    renderProductItems(items);
                } else {
                    loadProductsFromLocal(orderId);
                }
            }

            @Override
            public void onError(Exception e) {
                pbLoadingProducts.setVisibility(View.GONE);
                loadProductsFromLocal(orderId);
            }
        });
    }

    // Đọc chi tiết sản phẩm từ kho SQLite nội bộ
    private void loadProductsFromLocal(int orderId) {
        List<OrderItem> localItems = databaseHelper.getOrderItems(orderId);
        if (localItems != null && !localItems.isEmpty()) {
            renderProductItems(localItems);
        } else {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Không có chi tiết sản phẩm.");
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvEmpty.setTextSize(13f);
            tvEmpty.setPadding(0, 16, 0, 16);
            llOrderDetailProducts.addView(tvEmpty);
        }
    }

    // Vẽ từng dòng sản phẩm vào layout từ dữ liệu đơn hàng thật
    private void renderProductItems(List<OrderItem> items) {
        llOrderDetailProducts.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int iconSize = (int) (40 * density);
        int marginStart = (int) (12 * density);
        int marginEnd = (int) (8 * density);
        int paddingVertical = (int) (6 * density);

        int totalQty = 0;

        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            totalQty += item.getQuantity();

            // Khung chứa 1 sản phẩm
            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, paddingVertical, 0, paddingVertical);

            // Khung biểu tượng sản phẩm tròn nhẹ
            TextView tvIcon = new TextView(this);
            tvIcon.setText(item.getProductIcon());
            tvIcon.setTextSize(20f);
            tvIcon.setGravity(Gravity.CENTER);
            tvIcon.setBackgroundResource(R.drawable.bg_counter_btn);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            tvIcon.setLayoutParams(iconParams);
            itemRow.addView(tvIcon);

            // Cột thông tin tên, đơn giá và số lượng
            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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

            // Cột hiển thị thành tiền bên phải
            TextView tvSubtotal = new TextView(this);
            tvSubtotal.setText(item.getFormattedSubtotal());
            tvSubtotal.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvSubtotal.setTextSize(14f);
            tvSubtotal.setTypeface(null, Typeface.BOLD);
            itemRow.addView(tvSubtotal);

            llOrderDetailProducts.addView(itemRow);

            // Đường kẻ giữa các món nếu chưa phải món cuối
            if (i < items.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
                divParams.setMargins(0, (int) (4 * density), 0, (int) (4 * density));
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_stroke));
                llOrderDetailProducts.addView(divider);
            }
        }

        // Cập nhật nhãn tổng số món tiền hàng
        tvSubtotalLabel.setText("Tiền hàng (" + totalQty + " món)");
    }
}
