
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
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {

    private EditText etReceiverName;
    private EditText etReceiverPhone;
    private EditText etReceiverAddress;
    private EditText etOrderNote;
    private LinearLayout llOrderItemsContainer;
    private TextView tvCheckoutItemsSubtotal;
    private TextView tvCheckoutGrandTotal;
    private Button btnConfirmOrder;

    private FirestoreRepository repository;
    private SharedPrefManager prefManager;
    private String currentUserEmail;
    private List<CartItem> cartItems;
    private long totalAmount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_checkout);

        repository = FirestoreRepository.getInstance();
        prefManager = SharedPrefManager.getInstance(this);
        currentUserEmail = prefManager.getEmail();

        TextView tvCheckoutBack = findViewById(R.id.tvCheckoutBack);
        etReceiverName = findViewById(R.id.etReceiverName);
        etReceiverPhone = findViewById(R.id.etReceiverPhone);
        etReceiverAddress = findViewById(R.id.etReceiverAddress);
        etOrderNote = findViewById(R.id.etOrderNote);
        llOrderItemsContainer = findViewById(R.id.llOrderItemsContainer);
        tvCheckoutItemsSubtotal = findViewById(R.id.tvCheckoutItemsSubtotal);
        tvCheckoutGrandTotal = findViewById(R.id.tvCheckoutGrandTotal);
        btnConfirmOrder = findViewById(R.id.btnConfirmOrder);

        tvCheckoutBack.setOnClickListener(v -> finish());

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

                renderCartSummary();
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

        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            totalAmount += item.getSubtotal();

            View itemView = inflater.inflate(R.layout.item_checkout_summary, llOrderItemsContainer, false);
            ImageView ivIcon = itemView.findViewById(R.id.ivSummaryIcon);
            TextView tvName = itemView.findViewById(R.id.tvSummaryName);
            TextView tvQuantity = itemView.findViewById(R.id.tvSummaryQuantity);
            TextView tvSubtotal = itemView.findViewById(R.id.tvSummarySubtotal);

            if (item.getProduct() != null) {
                tvName.setText(item.getProduct().getName());
            }
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_orders);
            }
            tvQuantity.setText("Số lượng: x" + item.getQuantity());
            tvSubtotal.setText(item.getFormattedSubtotal());

            llOrderItemsContainer.addView(itemView);

            if (i < cartItems.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFEEEEEE);
                llOrderItemsContainer.addView(divider);
            }
        }

        String formattedTotal = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
        tvCheckoutItemsSubtotal.setText(formattedTotal);
        tvCheckoutGrandTotal.setText(formattedTotal);
        btnConfirmOrder.setText("ĐẶT HÀNG • " + formattedTotal);
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
                        Toast.makeText(CheckoutActivity.this, "Không thể tạo đơn hàng trên Firebase.",
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
