// Màn hình Thanh toán: hiển thị tóm tắt đơn hàng, nhập thông tin giao hàng và đặt mua (Firebase tạo đơn chính, SQLite lưu bản sao sau khi thành công).
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {

    // Khai báo các ô nhập thông tin người nhận, phương thức thanh toán và vùng hiển thị tổng tiền
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

    // Khai báo kho chính Firebase, kho bản sao SQLite, phiên đăng nhập, danh sách hàng và tổng tiền đơn
    private FirestoreRepository repository;
    private DatabaseHelper databaseHelper;
    private SharedPrefManager prefManager;
    private String currentUserEmail;
    private List<CartItem> cartItems;
    private long totalAmount = 0;

    // Hàm khởi tạo màn hình Thanh toán: ánh xạ giao diện, điền sẵn thông tin cũ và tải danh sách hàng
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Nạp giao diện XML của màn hình Thanh toán
        setContentView(R.layout.activity_checkout);

        // Kết nối Firebase (kho chính), SQLite (kho bản sao) và lấy email người đang đăng nhập
        repository = FirestoreRepository.getInstance();
        databaseHelper = DatabaseHelper.getInstance(this);
        prefManager = SharedPrefManager.getInstance(this);
        currentUserEmail = prefManager.getEmail();

        // Ánh xạ các ô nhập chữ, nút chọn và nhãn hiển thị từ file giao diện XML vào biến Java
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

        // Bấm mũi tên quay lại thì đóng màn hình hiện tại để về lại Giỏ hàng
        tvCheckoutBack.setOnClickListener(v -> finish());

        // Đảm bảo dòng xem trước mẫu ẩn khi chạy ứng dụng thật
        View samplePreview = findViewById(R.id.sampleCheckoutItemPreview);
        if (samplePreview != null) {
            samplePreview.setVisibility(View.GONE);
        }

        // Tự động điền sẵn Tên người nhận nếu tài khoản đã lưu tên trước đó
        String savedUsername = prefManager.getUsername();
        if (savedUsername != null && !savedUsername.isEmpty() && !"Người dùng".equals(savedUsername)) {
            etReceiverName.setText(savedUsername);
        }

        // Tự động điền sẵn Số điện thoại nếu tài khoản đã lưu SĐT trước đó
        String savedPhone = prefManager.getPhone();
        if (savedPhone != null && !savedPhone.trim().isEmpty()) {
            etReceiverPhone.setText(savedPhone.trim());
        }

        // Tự động điền sẵn Địa chỉ nhận hàng nếu tài khoản đã lưu địa chỉ trước đó
        String savedAddress = prefManager.getAddress();
        if (savedAddress != null && !savedAddress.trim().isEmpty()) {
            etReceiverAddress.setText(savedAddress.trim());
        }

        // Kiểm tra bổ sung thông tin từ kho SQLite nếu SharedPreferences chưa có đủ
        User localUser = databaseHelper.getUserByEmail(currentUserEmail);
        if (localUser != null) {
            if (etReceiverName.getText().toString().trim().isEmpty()
                    && localUser.getFullName() != null && !localUser.getFullName().trim().isEmpty()
                    && !"Người dùng".equals(localUser.getFullName())) {
                etReceiverName.setText(localUser.getFullName().trim());
            }
            if (etReceiverPhone.getText().toString().trim().isEmpty()
                    && localUser.getPhone() != null && !localUser.getPhone().trim().isEmpty()) {
                etReceiverPhone.setText(localUser.getPhone().trim());
            }
            if (etReceiverAddress.getText().toString().trim().isEmpty()
                    && localUser.getAddress() != null && !localUser.getAddress().trim().isEmpty()) {
                etReceiverAddress.setText(localUser.getAddress().trim());
            }
        }

        // Đồng bộ dữ liệu mới nhất từ Firebase về ô nhập thông tin người nhận
        repository.getUserByEmail(currentUserEmail, new FirestoreRepository.UserCallback() {
            @Override
            public void onResult(User user) {
                if (user == null) return;
                if (etReceiverName.getText().toString().trim().isEmpty()
                        && user.getFullName() != null && !user.getFullName().trim().isEmpty()
                        && !"Người dùng".equals(user.getFullName())) {
                    etReceiverName.setText(user.getFullName().trim());
                }
                if (etReceiverPhone.getText().toString().trim().isEmpty()
                        && user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
                    etReceiverPhone.setText(user.getPhone().trim());
                }
                if (etReceiverAddress.getText().toString().trim().isEmpty()
                        && user.getAddress() != null && !user.getAddress().trim().isEmpty()) {
                    etReceiverAddress.setText(user.getAddress().trim());
                }
            }

            @Override
            public void onError(Exception e) {}
        });

        // Bắt sự kiện đổi phương thức thanh toán: hiện thông tin số tài khoản nếu chọn Chuyển khoản ngân hàng
        rgPaymentMethods.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbBankTransfer) {
                layoutBankInfo.setVisibility(View.VISIBLE);
            } else {
                layoutBankInfo.setVisibility(View.GONE);
            }
        });

        // Tải danh sách sản phẩm trong giỏ từ Firebase (kho chính) và lưu bản sao vào SQLite
        loadCartSummary();

        // Bấm nút Đặt hàng thì chạy hàm kiểm tra dữ liệu và tạo đơn hàng
        btnConfirmOrder.setOnClickListener(v -> handlePlaceOrder());
    }

    // Đọc giỏ hàng chính thức từ Firebase -> lưu bản sao xuống SQLite -> lọc các món đã chọn -> vẽ danh sách tóm tắt
    private void loadCartSummary() {
        ArrayList<Integer> selectedProductIds = getIntent().getIntegerArrayListExtra("SELECTED_PRODUCT_IDS");
        repository.getCartItems(currentUserEmail, new FirestoreRepository.CartCallback() {
            @Override
            public void onResult(List<CartItem> items) {
                // Cập nhật bản sao toàn bộ giỏ hàng dưới SQLite theo đúng dữ liệu từ Firebase
                databaseHelper.clearCart(currentUserEmail);
                for (CartItem item : items) {
                    databaseHelper.addToCart(currentUserEmail, item.getProduct().getId(), item.getQuantity());
                }

                // Lọc chỉ giữ lại những sản phẩm được người dùng tích chọn thanh toán
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

                // Hiển thị danh sách tóm tắt lên màn hình
                renderCartSummary();
            }

            @Override
            public void onError(Exception e) {
                // Thử đọc bản sao từ SQLite nếu không kết nối được Firebase
                List<CartItem> localItems = databaseHelper.getCartItems(currentUserEmail);
                if (localItems != null && !localItems.isEmpty()) {
                    if (selectedProductIds != null && !selectedProductIds.isEmpty()) {
                        List<CartItem> filtered = new ArrayList<>();
                        for (CartItem item : localItems) {
                            if (item.getProduct() != null && selectedProductIds.contains(item.getProduct().getId())) {
                                filtered.add(item);
                            }
                        }
                        cartItems = filtered;
                    } else {
                        cartItems = localItems;
                    }
                    renderCartSummary();
                } else {
                    Toast.makeText(CheckoutActivity.this, "Không thể tải giỏ hàng từ Firebase.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
    }

    // Hiển thị từng món hàng trong giỏ lên khung tóm tắt và tính tổng tiền thanh toán
    private void renderCartSummary() {
        // Nếu giỏ hàng trống thì thông báo và quay về màn hình trước
        if (cartItems == null || cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng rỗng!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Xóa sạch các dòng giao diện cũ trước khi vẽ danh sách mới
        llOrderItemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        totalAmount = 0;

        // Duyệt qua từng món hàng để tạo dòng hiển thị và cộng dồn tổng tiền
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            totalAmount += item.getSubtotal();

            // Nạp giao diện một dòng tóm tắt sản phẩm và ánh xạ các ô chữ bên trong
            View itemView = inflater.inflate(R.layout.item_checkout_summary, llOrderItemsContainer, false);
            ImageView ivIcon = itemView.findViewById(R.id.ivSummaryIcon);
            TextView tvName = itemView.findViewById(R.id.tvSummaryName);
            TextView tvQuantity = itemView.findViewById(R.id.tvSummaryQuantity);
            TextView tvSubtotal = itemView.findViewById(R.id.tvSummarySubtotal);

            // Gán tên sản phẩm, số lượng mua và thành tiền của món đó
            if (item.getProduct() != null) {
                tvName.setText(item.getProduct().getName());
            }
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_orders);
            }
            tvQuantity.setText("Số lượng: x" + item.getQuantity());
            tvSubtotal.setText(item.getFormattedSubtotal());

            // Thêm dòng sản phẩm vào khung danh sách tóm tắt
            llOrderItemsContainer.addView(itemView);

            // Thêm đường kẻ ngang mỏng ngăn cách giữa các sản phẩm
            if (i < cartItems.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFEEEEEE);
                llOrderItemsContainer.addView(divider);
            }
        }

        // Định dạng tổng tiền theo kiểu VNĐ và hiển thị lên các dòng tổng cùng nút Đặt hàng
        String formattedTotal = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";
        tvCheckoutItemsSubtotal.setText(formattedTotal);
        tvCheckoutGrandTotal.setText(formattedTotal);
        btnConfirmOrder.setText("ĐẶT HÀNG • " + formattedTotal);
    }

    // Kiểm tra thông tin giao hàng, tạo đơn chính trên Firebase trước -> thành công mới lưu bản sao vào SQLite
    private void handlePlaceOrder() {
        // Lấy dữ liệu tên, SĐT, địa chỉ và ghi chú người dùng vừa nhập
        String name = etReceiverName.getText().toString().trim();
        String phone = etReceiverPhone.getText().toString().trim();
        String address = etReceiverAddress.getText().toString().trim();
        String note = etOrderNote.getText().toString().trim();

        // Kiểm tra không để trống họ tên người nhận
        if (name.isEmpty()) {
            etReceiverName.setError("Vui lòng nhập họ và tên người nhận!");
            etReceiverName.requestFocus();
            return;
        }

        // Kiểm tra không để trống số điện thoại và phải dài từ 9 đến 11 chữ số
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

        // Kiểm tra không để trống địa chỉ nhận hàng
        if (address.isEmpty()) {
            etReceiverAddress.setError("Vui lòng nhập địa chỉ nhận hàng chi tiết!");
            etReceiverAddress.requestFocus();
            return;
        }

        // Xác định phương thức thanh toán người dùng đang chọn
        String paymentMethod;
        int checkedId = rgPaymentMethods.getCheckedRadioButtonId();
        if (checkedId == R.id.rbBankTransfer) {
            paymentMethod = "Chuyển khoản ngân hàng";
        } else if (checkedId == R.id.rbEWallet) {
            paymentMethod = "Ví điện tử (MoMo / ZaloPay)";
        } else {
            paymentMethod = "Thanh toán khi nhận hàng (COD)";
        }

        // Khóa nút Đặt hàng tạm thời để tránh bấm nhiều lần tạo trùng đơn
        btnConfirmOrder.setEnabled(false);

        // Bước 1: Gửi lệnh tạo đơn hàng chính thức và xóa giỏ hàng trên Firebase trước
        repository.createOrder(currentUserEmail, name, phone, address, note, paymentMethod, cartItems,
                new FirestoreRepository.OrderCallback() {
                    @Override
                    public void onResult(long orderId) {
                        // Bước 2: Firebase tạo đơn thành công -> chuyển đổi danh sách hàng để lưu bản sao xuống SQLite
                        long total = 0;
                        List<OrderItem> items = new ArrayList<>();
                        int itemId = 1;
                        for (CartItem cartItem : cartItems) {
                            Product product = cartItem.getProduct();
                            long subtotal = cartItem.getSubtotal();
                            total += subtotal;
                            items.add(new OrderItem(itemId++, (int) orderId,
                                    product == null ? 0 : product.getId(),
                                    product == null ? "Sản phẩm" : product.getName(),
                                    product == null ? "📦" : product.getIcon(),
                                    product == null ? 0 : product.getPrice(),
                                    cartItem.getQuantity(), subtotal));
                        }

                        // Tạo đối tượng đơn hàng bản sao kèm ngày giờ đặt và lưu vào SQLite
                        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
                        Order order = new Order((int) orderId, currentUserEmail, name, phone, address, note,
                                paymentMethod, total, date, "Đặt hàng thành công");
                        if (!databaseHelper.saveRemoteOrder(order, items)) {
                            btnConfirmOrder.setEnabled(true);
                            Toast.makeText(CheckoutActivity.this, "Không thể lưu bản sao SQLite.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Xóa các món đã đặt mua khỏi giỏ hàng SQLite (giữ lại các món chưa chọn mua)
                        for (CartItem cartItem : cartItems) {
                            if (cartItem.getProduct() != null) {
                                databaseHelper.removeCartItem(currentUserEmail, cartItem.getProduct().getId());
                            }
                        }

                        // Bước 3: Lưu SĐT và địa chỉ làm mặc định cho các lần mua sau rồi hiện thông báo thành công
                        prefManager.updatePhone(phone);
                        prefManager.updateAddress(address);
                        databaseHelper.updatePhone(currentUserEmail, phone);
                        databaseHelper.updateAddress(currentUserEmail, address);
                        showOrderSuccessDialog(orderId, name, phone, address, paymentMethod);
                    }

                    @Override
                    public void onError(Exception e) {
                        // Mở lại nút Đặt hàng và báo lỗi nếu Firebase không tạo được đơn
                        btnConfirmOrder.setEnabled(true);
                        Toast.makeText(CheckoutActivity.this, "Không thể tạo đơn hàng trên Firebase.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Hiển thị hộp thoại thông báo Đặt hàng thành công kèm mã đơn và 2 nút điều hướng
    private void showOrderSuccessDialog(long orderId, String name, String phone, String address, String paymentMethod) {
        // Định dạng tổng tiền theo tiền Việt Nam
        String formattedTotal = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(totalAmount) + " ₫";

        // Ghép chuỗi thông tin chi tiết đơn hàng vừa đặt
        String message = "🎉 Cảm ơn quý khách đã mua sắm!\n\n"
                + "• Mã đơn hàng: #DH" + String.format(Locale.getDefault(), "%05d", orderId) + "\n"
                + "• Người nhận: " + name + " (" + phone + ")\n"
                + "• Địa chỉ: " + address + "\n"
                + "• Phương thức: " + paymentMethod + "\n"
                + "• Tổng thanh toán: " + formattedTotal;

        // Tạo hộp thoại thông báo (khóa chạm ra ngoài để bắt buộc chọn 1 trong 2 nút)
        new AlertDialog.Builder(this)
                .setTitle("✅ ĐẶT HÀNG THÀNH CÔNG")
                .setMessage(message)
                .setCancelable(false)
                // Bấm "Về trang chủ" -> quay về màn hình Danh sách sản phẩm
                .setPositiveButton("Về trang chủ", (d, which) -> {
                    Intent intent = new Intent(this, ProductListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                // Bấm "Xem đơn mua" -> chuyển sang màn hình Lịch sử đơn hàng
                .setNegativeButton("Xem đơn mua", (d, which) -> {
                    Intent intent = new Intent(this, OrderHistoryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
    }
}