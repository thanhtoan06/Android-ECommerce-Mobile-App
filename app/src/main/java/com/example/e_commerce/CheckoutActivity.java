// Màn hình Thanh toán: hiển thị tóm tắt đơn hàng, nhập thông tin người nhận, chọn phương thức thanh toán và lưu đơn hàng lên Firestore
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {

    // Khai báo các thành phần nhập liệu và hiển thị trên màn hình Thanh toán
    private EditText etReceiverName;
    private EditText etReceiverPhone;
    private EditText etReceiverAddress;
    private EditText etOrderNote;
    private RadioGroup rgPaymentMethods;
    private View layoutBankInfo;
    private LinearLayout llOrderItemsContainer;
    private TextView tvCheckoutItemsSubtotal;
    private TextView tvCheckoutGrandTotal;
    private Button btnConfirmOrder;

    // Khai báo đối tượng truy xuất dữ liệu, quản lý phiên và danh sách sản phẩm thanh toán
    private FirestoreRepository repository;
    private SharedPrefManager prefManager;
    private String currentUserEmail;
    private List<CartItem> cartItems;
    private long totalAmount = 0;
    private DatabaseHelper databaseHelper;

    // Hàm chạy đầu tiên khi mở màn hình thanh toán: kết nối giao diện, điền sẵn thông tin và tải tóm tắt giỏ hàng
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        // Khởi tạo đối tượng kết nối Firestore, SharedPreferences và lấy email người dùng
        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);
        prefManager = SharedPrefManager.getInstance(this);
        currentUserEmail = prefManager.getEmail();

        // Kết nối các biến Java với các thành phần trên giao diện
        TextView tvCheckoutBack = findViewById(R.id.tvCheckoutBack);
        etReceiverName = findViewById(R.id.etReceiverName);
        etReceiverPhone = findViewById(R.id.etReceiverPhone);
        etReceiverAddress = findViewById(R.id.etReceiverAddress);
        etOrderNote = findViewById(R.id.etOrderNote);
        rgPaymentMethods = findViewById(R.id.rgPaymentMethods);
        layoutBankInfo = findViewById(R.id.layoutBankInfo);
        llOrderItemsContainer = findViewById(R.id.llOrderItemsContainer);
        tvCheckoutItemsSubtotal = findViewById(R.id.tvCheckoutItemsSubtotal);
        tvCheckoutGrandTotal = findViewById(R.id.tvCheckoutGrandTotal);
        btnConfirmOrder = findViewById(R.id.btnConfirmOrder);

        // Nút quay lại màn hình Giỏ hàng
        tvCheckoutBack.setOnClickListener(v -> finish());

        // Tự động điền sẵn tên, số điện thoại và địa chỉ nếu tài khoản đã lưu trước đó
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

        // Ẩn/hiện thông tin chuyển khoản ngân hàng khi người dùng đổi phương thức thanh toán
        rgPaymentMethods.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbBankTransfer) {
                layoutBankInfo.setVisibility(View.VISIBLE);
            } else {
                layoutBankInfo.setVisibility(View.GONE);
            }
        });

        // Tải danh sách sản phẩm từ giỏ hàng Firestore và tính tổng tiền
        loadCartSummary();

        // Gắn sự kiện cho nút Xác nhận đặt hàng
        btnConfirmOrder.setOnClickListener(v -> handlePlaceOrder());
    }

    // Đọc danh sách sản phẩm trong giỏ hàng Firestore
    private void loadCartSummary() {
        repository.getCartItems(currentUserEmail, new FirestoreRepository.CartCallback() {
            @Override
            public void onResult(List<CartItem> items) {
                // Lưu danh sách giỏ hàng lấy được và vẽ lên phần tóm tắt đơn hàng
                cartItems = items;
                renderCartSummary();
            }

            @Override
            public void onError(Exception e) {
                // Báo lỗi và đóng màn hình thanh toán nếu không đọc được giỏ hàng
                Toast.makeText(CheckoutActivity.this, "Không thể tải giỏ hàng.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // Hiển thị danh sách từng món hàng tóm tắt lên giao diện và tính tổng tiền thanh toán
    private void renderCartSummary() {
        if (cartItems == null || cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng rỗng!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Xóa các dòng cũ (nếu có) trước khi nạp danh sách sản phẩm mới
        llOrderItemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        totalAmount = 0;

        // Duyệt từng sản phẩm trong giỏ, nạp layout item_checkout_summary và cộng dồn tổng tiền
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            totalAmount += item.getSubtotal();

            View itemView = inflater.inflate(R.layout.item_checkout_summary, llOrderItemsContainer, false);
            TextView tvIcon = itemView.findViewById(R.id.tvSummaryIcon);
            TextView tvName = itemView.findViewById(R.id.tvSummaryName);
            TextView tvQuantity = itemView.findViewById(R.id.tvSummaryQuantity);
            TextView tvSubtotal = itemView.findViewById(R.id.tvSummarySubtotal);

            if (item.getProduct() != null) {
                tvIcon.setText(item.getProduct().getIcon());
                tvName.setText(item.getProduct().getName());
            }
            tvQuantity.setText("Số lượng: x" + item.getQuantity());
            tvSubtotal.setText(item.getFormattedSubtotal());

            llOrderItemsContainer.addView(itemView);

            // Thêm đường kẻ phân cách giữa các sản phẩm
            if (i < cartItems.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFEEEEEE);
                llOrderItemsContainer.addView(divider);
            }
        }

        // Định dạng tổng tiền theo tiền tệ Việt Nam và hiển thị lên các nhãn tổng thanh toán
        String formattedTotal = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
        tvCheckoutItemsSubtotal.setText(formattedTotal);
        tvCheckoutGrandTotal.setText(formattedTotal);
        btnConfirmOrder.setText("ĐẶT HÀNG • " + formattedTotal);
    }

    // Kiểm tra thông tin giao hàng người dùng nhập và lưu đơn hàng mới vào Firestore
    private void handlePlaceOrder() {
        String name = etReceiverName.getText().toString().trim();
        String phone = etReceiverPhone.getText().toString().trim();
        String address = etReceiverAddress.getText().toString().trim();
        String note = etOrderNote.getText().toString().trim();

        // Kiểm tra tính hợp lệ của họ tên, số điện thoại và địa chỉ
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

        // Xác định phương thức thanh toán được chọn từ RadioGroup
        String paymentMethod;
        int checkedId = rgPaymentMethods.getCheckedRadioButtonId();
        if (checkedId == R.id.rbBankTransfer) {
            paymentMethod = "Chuyển khoản ngân hàng";
        } else if (checkedId == R.id.rbEWallet) {
            paymentMethod = "Ví điện tử (MoMo / ZaloPay)";
        } else {
            paymentMethod = "Thanh toán khi nhận hàng (COD)";
        }

        // Gửi yêu cầu tạo đơn hàng lên Firestore và cập nhật lại SĐT, địa chỉ cho tài khoản
        databaseHelper.createOrder(currentUserEmail, name, phone, address, note, paymentMethod, cartItems);
        repository.createOrder(currentUserEmail, name, phone, address, note, paymentMethod, cartItems,
                new FirestoreRepository.OrderCallback() {
                    @Override
                    public void onResult(long orderId) {
                        if (orderId == -1) {
                            Toast.makeText(CheckoutActivity.this, "Có lỗi xảy ra khi tạo đơn hàng.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        // Lưu lại số điện thoại và địa chỉ mới nhất vào hồ sơ tài khoản để tiện cho lần mua sau
                        repository.updateUser(currentUserEmail, java.util.Collections.singletonMap("phone", phone),
                                ok -> prefManager.updatePhone(phone));
                        databaseHelper.updatePhone(currentUserEmail, phone);
                        repository.updateUser(currentUserEmail, java.util.Collections.singletonMap("address", address),
                                ok -> prefManager.updateAddress(address));
                        databaseHelper.updateAddress(currentUserEmail, address);
                        showOrderSuccessDialog(orderId, name, phone, address, paymentMethod);
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(CheckoutActivity.this, "Có lỗi xảy ra khi tạo đơn hàng.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Hiển thị hộp thoại thông báo đặt hàng thành công kèm mã đơn và điều hướng tiếp theo
    private void showOrderSuccessDialog(long orderId, String name, String phone, String address, String paymentMethod) {
        String formattedTotal = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";

        // Ghép nội dung thông báo chi tiết đơn hàng vừa đặt
        String message = "🎉 Cảm ơn quý khách đã mua sắm!\n\n"
                + "• Mã đơn hàng: #DH" + String.format(Locale.getDefault(), "%05d", orderId) + "\n"
                + "• Người nhận: " + name + " (" + phone + ")\n"
                + "• Địa chỉ: " + address + "\n"
                + "• Phương thức: " + paymentMethod + "\n"
                + "• Tổng thanh toán: " + formattedTotal;

        // Hiện hộp thoại không cho bấm ra ngoài để người dùng chọn hướng đi tiếp
        new AlertDialog.Builder(this)
                .setTitle("✅ ĐẶT HÀNG THÀNH CÔNG")
                .setMessage(message)
                .setCancelable(false)
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
                })
                .show();
    }
}