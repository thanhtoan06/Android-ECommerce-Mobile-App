
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {

    private EditText etReceiverName;
    private EditText etReceiverPhone;
    private EditText etReceiverAddress;
    private EditText etOrderNote;
    private LinearLayout llOrderItemsContainer;
    private TextView tvCheckoutItemsSubtotal;
    private TextView tvCheckoutProductDiscount;
    private TextView tvCheckoutShippingFee;
    private TextView tvCheckoutShippingDiscount;
    private TextView tvCheckoutGrandTotal;
    private Button btnConfirmOrder;
    private View btnSelectProductVoucher;
    private View btnSelectShippingVoucher;
    private TextView tvSelectProductVoucherLabel;
    private TextView tvSelectShippingVoucherLabel;

    private FirestoreRepository repository;
    private VoucherRepository voucherRepository;
    private SharedPrefManager prefManager;
    private String currentUserEmail;
    private List<CartItem> cartItems;
    private VoucherRepository.VoucherWallet voucherWallet;
    private Voucher selectedProductVoucher;
    private Voucher selectedShippingVoucher;
    private VoucherCalculator.ProductDiscount currentProductDiscount;
    private long currentItemsSubtotal;
    private long currentShippingDiscount;
    private String pendingProductVoucherId;
    private String pendingShippingVoucherId;
    private boolean cartReady;
    private boolean voucherWalletReady;
    private long totalAmount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_checkout);

        repository = FirestoreRepository.getInstance();
        voucherRepository = VoucherRepository.getInstance();
        prefManager = SharedPrefManager.getInstance(this);
        currentUserEmail = prefManager.getEmail();
        pendingProductVoucherId = getIntent().getStringExtra(VoucherActivity.EXTRA_PRODUCT_VOUCHER_ID);
        pendingShippingVoucherId = getIntent().getStringExtra(VoucherActivity.EXTRA_SHIPPING_VOUCHER_ID);

        TextView tvCheckoutBack = findViewById(R.id.tvCheckoutBack);
        etReceiverName = findViewById(R.id.etReceiverName);
        etReceiverPhone = findViewById(R.id.etReceiverPhone);
        etReceiverAddress = findViewById(R.id.etReceiverAddress);
        etOrderNote = findViewById(R.id.etOrderNote);
        llOrderItemsContainer = findViewById(R.id.llOrderItemsContainer);
        tvCheckoutItemsSubtotal = findViewById(R.id.tvCheckoutItemsSubtotal);
        tvCheckoutProductDiscount = findViewById(R.id.tvCheckoutProductDiscount);
        tvCheckoutShippingFee = findViewById(R.id.tvCheckoutShippingFee);
        tvCheckoutShippingDiscount = findViewById(R.id.tvCheckoutShippingDiscount);
        tvCheckoutGrandTotal = findViewById(R.id.tvCheckoutGrandTotal);
        btnConfirmOrder = findViewById(R.id.btnConfirmOrder);
        btnSelectProductVoucher = findViewById(R.id.btnSelectProductVoucher);
        btnSelectShippingVoucher = findViewById(R.id.btnSelectShippingVoucher);
        tvSelectProductVoucherLabel = findViewById(R.id.tvSelectProductVoucherLabel);
        tvSelectShippingVoucherLabel = findViewById(R.id.tvSelectShippingVoucherLabel);
        btnConfirmOrder.setEnabled(false);

        tvCheckoutBack.setOnClickListener(v -> finish());
        btnSelectProductVoucher.setOnClickListener(v -> selectVoucher(true));
        btnSelectShippingVoucher.setOnClickListener(v -> selectVoucher(false));

        View samplePreview = findViewById(R.id.sampleCheckoutItemPreview);
        if (samplePreview != null) {
            samplePreview.setVisibility(View.GONE);
        }

        String savedUsername = prefManager.getUsername();
        if (savedUsername != null && !savedUsername.isEmpty() && !"Người dùng".equals(savedUsername)) {
            etReceiverName.setText(savedUsername);
        }

        String savedPhone = prefManager.getPhone();
        if (savedPhone != null && !savedPhone.trim().isEmpty()) {
            etReceiverPhone.setText(savedPhone.trim());
        }

        String savedAddress = prefManager.getAddress();
        if (savedAddress != null && !savedAddress.trim().isEmpty()) {
            etReceiverAddress.setText(savedAddress.trim());
        }

        repository.getUserByEmail(currentUserEmail, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {
                if (user == null)
                    return;
                if (etReceiverName.getText().toString().trim().isEmpty() && user.getFullName() != null
                        && !user.getFullName().trim().isEmpty() && !"Người dùng".equals(user.getFullName())) {
                    etReceiverName.setText(user.getFullName().trim());
                }
                if (etReceiverPhone.getText().toString().trim().isEmpty() && user.getPhone() != null
                        && !user.getPhone().trim().isEmpty()) {
                    etReceiverPhone.setText(user.getPhone().trim());
                }
                if (etReceiverAddress.getText().toString().trim().isEmpty() && user.getAddress() != null
                        && !user.getAddress().trim().isEmpty()) {
                    etReceiverAddress.setText(user.getAddress().trim());
                }
            }

            @Override
            public void onError(Exception e) {
            }
        });

        loadCartSummary();

        btnConfirmOrder.setOnClickListener(v -> handlePlaceOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVoucherWallet();
    }

    private void loadVoucherWallet() {
        if (currentUserEmail == null || currentUserEmail.trim().isEmpty()) {
            voucherWalletReady = true;
            pendingProductVoucherId = null;
            pendingShippingVoucherId = null;
            updatePlaceOrderEnabled();
            return;
        }
        voucherWalletReady = false;
        updatePlaceOrderEnabled();
        voucherRepository.loadWallet(currentUserEmail, new VoucherRepository.WalletCallback() {
            @Override
            public void onResult(VoucherRepository.VoucherWallet wallet) {
                voucherWallet = wallet;
                voucherWalletReady = true;
                resolvePendingVoucherSelections();
                if (cartItems != null) {
                    renderCartSummary();
                }
                updatePlaceOrderEnabled();
            }

            @Override
            public void onError(Exception error) {
                voucherWallet = null;
                voucherWalletReady = true;
                pendingProductVoucherId = null;
                pendingShippingVoucherId = null;
                Toast.makeText(CheckoutActivity.this, "Không thể tải voucher đã lưu.", Toast.LENGTH_LONG).show();
                if (cartItems != null) {
                    renderCartSummary();
                }
                updatePlaceOrderEnabled();
            }
        });
    }

    private void resolvePendingVoucherSelections() {
        selectedProductVoucher = findSavedVoucher(pendingProductVoucherId, true);
        selectedShippingVoucher = findSavedVoucher(pendingShippingVoucherId, false);
        if (pendingProductVoucherId != null && selectedProductVoucher == null) {
            Toast.makeText(this, "Voucher giảm giá sản phẩm không còn hiệu lực.", Toast.LENGTH_LONG).show();
        }
        if (pendingShippingVoucherId != null && selectedShippingVoucher == null) {
            Toast.makeText(this, "Voucher vận chuyển không còn hiệu lực.", Toast.LENGTH_LONG).show();
        }
        pendingProductVoucherId = null;
        pendingShippingVoucherId = null;
    }

    private Voucher findSavedVoucher(String voucherId, boolean productVoucher) {
        if (voucherId == null || voucherWallet == null) {
            return null;
        }
        for (Voucher voucher : voucherWallet.getSaved()) {
            boolean matchingType = productVoucher ? voucher.isProductVoucher() : voucher.isShippingVoucher();
            if (matchingType && voucherId.equals(voucher.getId()) && !voucher.isExpired(new Date())) {
                return voucher;
            }
        }
        return null;
    }

    private void loadCartSummary() {
        ArrayList<Integer> selectedProductIds = getIntent().getIntegerArrayListExtra("SELECTED_PRODUCT_IDS");
        repository.getCartItems(currentUserEmail, new FirestoreRepository.CartCallback() {
            @Override
            public void onResult(List<CartItem> items) {

                if (selectedProductIds != null && !selectedProductIds.isEmpty()) {
                    List<CartItem> filtered = new ArrayList<>();
                    for (CartItem item : items) {
                        if (item.getProduct() != null && selectedProductIds.contains(item.getProduct().getId())) {
                            filtered.add(item);
                        }
                    }
                    cartItems = filtered;
                } else {
                    cartItems = items;
                }

                cartReady = true;
                renderCartSummary();
                updatePlaceOrderEnabled();
            }

            @Override
            public void onError(Exception e) {

                Toast.makeText(CheckoutActivity.this, "Không thể tải giỏ hàng từ Firebase.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void renderCartSummary() {

        if (cartItems == null || cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng rỗng!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        llOrderItemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        totalAmount = 0;
        currentItemsSubtotal = 0;
        for (CartItem item : cartItems) {
            currentItemsSubtotal += item.getSubtotal();
        }
        currentProductDiscount = VoucherCalculator.calculateProductDiscount(cartItems, selectedProductVoucher);
        if (!currentProductDiscount.isApplicable()) {
            if (selectedProductVoucher != null) {
                Toast.makeText(this, "Voucher giảm giá không áp dụng cho giỏ hàng hiện tại.",
                        Toast.LENGTH_LONG).show();
            }
            selectedProductVoucher = null;
            currentProductDiscount = VoucherCalculator.calculateProductDiscount(cartItems, null);
        }
        currentShippingDiscount = VoucherCalculator.calculateShippingDiscount(
                cartItems, selectedShippingVoucher, VoucherCalculator.BASE_SHIPPING_FEE);
        if (currentShippingDiscount == 0) {
            if (selectedShippingVoucher != null) {
                Toast.makeText(this, "Voucher vận chuyển không áp dụng cho giỏ hàng hiện tại.",
                        Toast.LENGTH_LONG).show();
            }
            selectedShippingVoucher = null;
        }

        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);

            View itemView = inflater.inflate(R.layout.item_checkout_summary, llOrderItemsContainer, false);
            ImageView ivIcon = itemView.findViewById(R.id.ivSummaryIcon);
            TextView tvName = itemView.findViewById(R.id.tvSummaryName);
            TextView tvQuantity = itemView.findViewById(R.id.tvSummaryQuantity);
            TextView tvSubtotal = itemView.findViewById(R.id.tvSummarySubtotal);
            TextView tvOriginalSubtotal = itemView.findViewById(R.id.tvSummaryOriginalSubtotal);

            if (item.getProduct() != null) {
                tvName.setText(item.getProduct().getName());
            }
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_orders);
            }
            tvQuantity.setText("Số lượng: x" + item.getQuantity());
            long originalSubtotal = item.getSubtotal();
            long lineDiscount = item.getProduct() == null
                    ? 0
                    : currentProductDiscount.getDiscountForProduct(item.getProduct().getId());
            long discountedSubtotal = Math.max(0, originalSubtotal - lineDiscount);
            tvSubtotal.setText(formatMoney(discountedSubtotal));
            if (lineDiscount > 0) {
                tvOriginalSubtotal.setText(formatMoney(originalSubtotal));
                tvOriginalSubtotal.setPaintFlags(
                        tvOriginalSubtotal.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                tvOriginalSubtotal.setVisibility(View.VISIBLE);
            } else {
                tvOriginalSubtotal.setVisibility(View.GONE);
            }

            llOrderItemsContainer.addView(itemView);

            if (i < cartItems.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFEEEEEE);
                llOrderItemsContainer.addView(divider);
            }
        }

        long discountedItemsSubtotal = Math.max(0,
                currentItemsSubtotal - currentProductDiscount.getTotalDiscount());
        long discountedShipping = Math.max(0,
                VoucherCalculator.BASE_SHIPPING_FEE - currentShippingDiscount);
        totalAmount = discountedItemsSubtotal + discountedShipping;
        tvCheckoutItemsSubtotal.setText(formatMoney(currentItemsSubtotal));
        tvCheckoutProductDiscount.setText(currentProductDiscount.getTotalDiscount() > 0
                ? "-" + formatMoney(currentProductDiscount.getTotalDiscount()) : formatMoney(0));
        tvCheckoutShippingFee.setText(formatMoney(VoucherCalculator.BASE_SHIPPING_FEE));
        tvCheckoutShippingDiscount.setText(currentShippingDiscount > 0
                ? "-" + formatMoney(currentShippingDiscount) : formatMoney(0));
        updateVoucherButtonLabels();

        String formattedTotal = formatMoney(totalAmount);
        tvCheckoutGrandTotal.setText(formattedTotal);
        btnConfirmOrder.setText("ĐẶT HÀNG • " + formattedTotal);
    }

    private void selectVoucher(boolean productVoucher) {
        if (cartItems == null || voucherWallet == null) {
            Toast.makeText(this, "Đang tải giỏ hàng và voucher.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Voucher> eligible = new ArrayList<>();
        for (Voucher voucher : voucherWallet.getSaved()) {
            if (voucher.isExpired(new Date())) {
                continue;
            }
            if (productVoucher && voucher.isProductVoucher()
                    && VoucherCalculator.calculateProductDiscount(cartItems, voucher).isApplicable()) {
                eligible.add(voucher);
            } else if (!productVoucher && voucher.isShippingVoucher()
                    && VoucherCalculator.calculateShippingDiscount(
                            cartItems, voucher, VoucherCalculator.BASE_SHIPPING_FEE) > 0) {
                eligible.add(voucher);
            }
        }
        if (eligible.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Chưa có voucher phù hợp")
                    .setMessage("Lưu voucher phù hợp trong Kho voucher rồi quay lại thanh toán.")
                    .setPositiveButton("Mở kho voucher", (dialog, which) ->
                            startActivity(new Intent(this, VoucherActivity.class)))
                    .setNegativeButton("Đóng", null)
                    .show();
            return;
        }

        String[] options = new String[eligible.size() + 1];
        options[0] = "Không sử dụng voucher";
        for (int index = 0; index < eligible.size(); index++) {
            Voucher voucher = eligible.get(index);
            options[index + 1] = voucher.getName() + " · " + voucher.getCode();
        }
        new AlertDialog.Builder(this)
                .setTitle(productVoucher ? "Voucher giảm giá sản phẩm" : "Voucher vận chuyển")
                .setItems(options, (dialog, which) -> {
                    Voucher selected = which == 0 ? null : eligible.get(which - 1);
                    if (productVoucher) {
                        selectedProductVoucher = selected;
                    } else {
                        selectedShippingVoucher = selected;
                    }
                    renderCartSummary();
                })
                .show();
    }

    private void updateVoucherButtonLabels() {
        tvSelectProductVoucherLabel.setText(selectedProductVoucher == null
                ? "Chọn voucher giảm giá sản phẩm"
                : selectedProductVoucher.getCode() + " · " + selectedProductVoucher.getName());
        tvSelectShippingVoucherLabel.setText(selectedShippingVoucher == null
                ? "Chọn voucher giảm phí vận chuyển"
                : selectedShippingVoucher.getCode() + " · " + selectedShippingVoucher.getName());
    }

    private void updatePlaceOrderEnabled() {
        btnConfirmOrder.setEnabled(cartReady && voucherWalletReady);
    }

    private String formatMoney(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private void handlePlaceOrder() {

        String name = etReceiverName.getText().toString().trim();
        String phone = etReceiverPhone.getText().toString().trim();
        String address = etReceiverAddress.getText().toString().trim();
        String note = etOrderNote.getText().toString().trim();

        if (name.isEmpty()) {
            etReceiverName.setError("Vui lòng nhập họ và tên người nhận!");
            etReceiverName.requestFocus();
            return;
        }

        if (phone.isEmpty()) {
            etReceiverPhone.setError("Vui lòng nhập số điện thoại nhận hàng!");
            etReceiverPhone.requestFocus();
            return;
        }
        if (phone.length() < 9 || phone.length() > 11) {
            etReceiverPhone.setError("Số điện thoại không hợp lệ (9 - 11 chữ số)!");
            etReceiverPhone.requestFocus();
            return;
        }

        if (address.isEmpty()) {
            etReceiverAddress.setError("Vui lòng nhập địa chỉ nhận hàng chi tiết!");
            etReceiverAddress.requestFocus();
            return;
        }

        String paymentMethod = "Thanh toán khi nhận hàng (COD)";

        btnConfirmOrder.setEnabled(false);

        repository.createOrder(currentUserEmail, name, phone, address, note, paymentMethod, cartItems,
                selectedProductVoucher == null ? null : selectedProductVoucher.getId(),
                selectedShippingVoucher == null ? null : selectedShippingVoucher.getId(),
                new FirestoreRepository.OrderCallback() {
                    @Override
                    public void onResult(long orderId) {
                        if (orderId < 0) {
                            btnConfirmOrder.setEnabled(true);
                            Toast.makeText(CheckoutActivity.this, "Không thể tạo đơn hàng trên Firebase.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        prefManager.updatePhone(phone);
                        prefManager.updateAddress(address);
                        showOrderSuccessDialog(orderId, name, phone, address, paymentMethod);
                    }

                    @Override
                    public void onError(Exception e) {

                        btnConfirmOrder.setEnabled(true);
                        String detail = e.getMessage();
                        Toast.makeText(CheckoutActivity.this,
                                detail == null || detail.trim().isEmpty()
                                        ? "Không thể tạo đơn hàng trên Firebase."
                                        : "Không thể đặt hàng: " + detail,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showOrderSuccessDialog(long orderId, String name, String phone, String address, String paymentMethod) {

        String formattedTotal = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";

        String message = "Cảm ơn quý khách đã mua sắm!\n\n" + "• Mã đơn hàng: #DH"
                + String.format(Locale.getDefault(), "%05d", orderId) + "\n" + "• Người nhận: " + name + " (" + phone
                + ")\n" + "• Địa chỉ: " + address + "\n" + "• Phương thức: " + paymentMethod + "\n"
                + "• Tổng thanh toán: " + formattedTotal;

        new AlertDialog.Builder(this).setTitle("ĐẶT HÀNG THÀNH CÔNG").setMessage(message).setCancelable(false)

                .setPositiveButton("Về trang chủ", (d, which) -> {
                    Intent intent = new Intent(this, ProductListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })

                .setNegativeButton("Xem đơn mua", (d, which) -> {
                    Intent intent = new Intent(this, OrderHistoryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }).show();
    }
}
