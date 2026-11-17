// Kho lưu trữ dữ liệu trung tâm: giao tiếp trực tiếp với Firebase Cloud Firestore cho các nghiệp vụ sản phẩm, giỏ hàng, đơn hàng và tài khoản
package com.example.e_commerce;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class FirestoreRepository {

    // Khai báo email mặc định cho khách vãng lai, đối tượng Singleton và kết nối FirebaseFirestore
    private static final String GUEST_EMAIL = "guest@cart.local";
    private static FirestoreRepository instance;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    // Các callback nhận kết quả trả về bất đồng bộ từ Firestore
    public interface ProductsCallback { void onResult(List<Product> products); void onError(Exception e); }
    public interface ProductCallback { void onResult(Product product); void onError(Exception e); }
    public interface CartCallback { void onResult(List<CartItem> items); void onError(Exception e); }
    public interface BooleanCallback { void onResult(boolean success); }
    public interface UserCallback { void onResult(User user); void onError(Exception e); }
    public interface OrdersCallback { void onResult(List<Order> orders); void onError(Exception e); }
    public interface OrderItemsCallback { void onResult(List<OrderItem> items); void onError(Exception e); }
    public interface OrderCallback { void onResult(long id); void onError(Exception e); }

    // Hàm khởi tạo private để đảm bảo chỉ dùng 1 đối tượng FirestoreRepository duy nhất (Singleton)
    private FirestoreRepository() {}

    // Lấy thể hiện duy nhất của FirestoreRepository
    public static synchronized FirestoreRepository getInstance() {
        if (instance == null) {
            instance = new FirestoreRepository();
        }
        return instance;
    }

    // Chuẩn hóa email về chữ thường và cắt khoảng trắng để dùng làm khóa định danh trên Firestore
    private String normalizeEmail(String email) {
        return (email == null || email.trim().isEmpty()) ? GUEST_EMAIL : email.trim().toLowerCase();
    }

    // --- SẢN PHẨM ---

    // Nạp 12 sản phẩm mẫu nếu trên Firestore chưa có dữ liệu
    public void seedProductsIfNeeded() {
        firestore.collection("products").limit(1).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.isEmpty()) return;
            writeDefaultProducts(null);
        });
    }

    // Lấy toàn bộ sản phẩm sắp xếp theo id tăng dần
    public void getAllProducts(ProductsCallback callback) {
        firestore.collection("products")
                .orderBy("id", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Nếu collection products đang rỗng thì nạp sản phẩm mẫu rồi gọi lại
                    if (snapshot.isEmpty()) {
                        writeDefaultProducts(() -> getAllProducts(callback));
                        return;
                    }
                    // Chuyển từng DocumentSnapshot thành đối tượng Product
                    List<Product> products = new ArrayList<>();
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        products.add(toProduct(d));
                    }
                    callback.onResult(products);
                })
                .addOnFailureListener(callback::onError);
    }

    // Ghi hàng loạt danh sách sản phẩm mẫu lên Firestore bằng WriteBatch
    private void writeDefaultProducts(Runnable onComplete) {
        WriteBatch batch = firestore.batch();
        for (Product p : defaultProducts()) {
            batch.set(firestore.collection("products").document(String.valueOf(p.getId())), productData(p));
        }
        batch.commit().addOnCompleteListener(task -> {
            if (onComplete != null) onComplete.run();
        });
    }

    // Lấy chi tiết 1 sản phẩm theo id
    public void getProductById(int id, ProductCallback callback) {
        firestore.collection("products")
                .document(String.valueOf(id))
                .get()
                .addOnSuccessListener(d -> callback.onResult(d.exists() ? toProduct(d) : null))
                .addOnFailureListener(callback::onError);
    }

    // --- GIỎ HÀNG ---

    // Thêm sản phẩm vào giỏ (nếu đã có sẵn trong giỏ thì cộng dồn số lượng)
    public void addToCart(String email, int productId, int quantity, BooleanCallback callback) {
        String normalized = normalizeEmail(email);
        String docId = normalized + "_" + productId;

        firestore.collection("carts").document(docId).get()
                .addOnSuccessListener(document -> {
                    // Lấy số lượng hiện có trong giỏ (nếu chưa có thì bằng 0)
                    int current = (document.exists() && document.getLong("quantity") != null)
                            ? document.getLong("quantity").intValue() : 0;

                    // Đóng gói dữ liệu giỏ hàng mới và lưu đè lên document
                    Map<String, Object> values = new HashMap<>();
                    values.put("userEmail", normalized);
                    values.put("productId", productId);
                    values.put("quantity", current + quantity);

                    firestore.collection("carts").document(docId).set(values)
                            .addOnCompleteListener(task -> callback.onResult(task.isSuccessful()));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // Lấy danh sách món trong giỏ kèm thông tin chi tiết từng sản phẩm
    public void getCartItems(String email, CartCallback callback) {
        String normalized = normalizeEmail(email);
        firestore.collection("carts")
                .whereEqualTo("userEmail", normalized)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onResult(new ArrayList<>());
                        return;
                    }

                    // Dùng biến đếm nguyên tử (AtomicInteger) để đợi tải xong toàn bộ thông tin sản phẩm
                    List<CartItem> items = new ArrayList<>();
                    int totalDocs = snapshot.size();
                    AtomicInteger completedCount = new AtomicInteger(0);

                    for (DocumentSnapshot cart : snapshot.getDocuments()) {
                        Long productId = cart.getLong("productId");
                        Long quantity = cart.getLong("quantity");

                        if (productId == null || quantity == null) {
                            if (completedCount.incrementAndGet() == totalDocs) {
                                callback.onResult(items);
                            }
                            continue;
                        }

                        // Truy vấn thông tin sản phẩm tương ứng với productId trong giỏ
                        getProductById(productId.intValue(), new ProductCallback() {
                            @Override
                            public void onResult(Product product) {
                                if (product != null) {
                                    items.add(new CartItem(product.getId(), product, quantity.intValue()));
                                }
                                if (completedCount.incrementAndGet() == totalDocs) {
                                    callback.onResult(items);
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                if (completedCount.incrementAndGet() == totalDocs) {
                                    callback.onResult(items);
                                }
                            }
                        });
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    // Cập nhật số lượng món trong giỏ (nếu số lượng <= 0 thì xóa khỏi giỏ)
    public void updateCartQuantity(String email, int productId, int quantity, BooleanCallback callback) {
        String docId = normalizeEmail(email) + "_" + productId;
        if (quantity <= 0) {
            firestore.collection("carts").document(docId).delete()
                    .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
        } else {
            firestore.collection("carts").document(docId).update("quantity", quantity)
                    .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
        }
    }

    // Xóa 1 món khỏi giỏ hàng
    public void removeCartItem(String email, int productId, BooleanCallback callback) {
        String docId = normalizeEmail(email) + "_" + productId;
        firestore.collection("carts").document(docId).delete()
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    // Xóa toàn bộ giỏ hàng của người dùng sau khi đặt đơn hoặc bấm Xóa tất cả
    public void clearCart(String email, BooleanCallback callback) {
        firestore.collection("carts")
                .whereEqualTo("userEmail", normalizeEmail(email))
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        batch.delete(d.getReference());
                    }
                    batch.commit().addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // Đếm tổng số lượng sản phẩm đang có trong giỏ hàng để hiển thị lên huy hiệu (badge)
    public void getCartCount(String email, IntConsumer callback) {
        firestore.collection("carts")
                .whereEqualTo("userEmail", normalizeEmail(email))
                .get()
                .addOnSuccessListener(snapshot -> {
                    int count = 0;
                    for (DocumentSnapshot d : snapshot) {
                        if (d.getLong("quantity") != null) {
                            count += d.getLong("quantity").intValue();
                        }
                    }
                    callback.accept(count);
                })
                .addOnFailureListener(e -> callback.accept(0));
    }

    // --- ĐƠN HÀNG ---

    // Tạo đơn hàng mới, lưu danh sách sản phẩm vào sub-collection items và xóa giỏ hàng
    public void createOrder(String email, String name, String phone, String address, String note,
                            String payment, List<CartItem> cartItems, OrderCallback callback) {
        if (cartItems == null || cartItems.isEmpty()) {
            callback.onResult(-1);
            return;
        }

        // Tạo mã đơn hàng số nguyên duy nhất theo thời gian hiện tại và định dạng ngày đặt
        String normalized = normalizeEmail(email);
        long numericId = System.currentTimeMillis() % Integer.MAX_VALUE;
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        // Tính tổng tiền toàn bộ đơn hàng
        long total = 0;
        for (CartItem item : cartItems) {
            total += item.getSubtotal();
        }

        // Đóng gói thông tin chung của đơn hàng
        Map<String, Object> order = new HashMap<>();
        order.put("id", numericId);
        order.put("userEmail", normalized);
        order.put("receiverName", name.trim());
        order.put("receiverPhone", phone.trim());
        order.put("receiverAddress", address.trim());
        order.put("note", note == null ? "" : note.trim());
        order.put("paymentMethod", payment);
        order.put("totalAmount", total);
        order.put("orderDate", date);
        order.put("status", "Đặt hàng thành công");

        // Lưu document đơn hàng chính, sau đó ghi từng sản phẩm vào sub-collection "items"
        String orderId = String.valueOf(numericId);
        firestore.collection("orders").document(orderId).set(order)
                .addOnSuccessListener(v -> {
                    WriteBatch batch = firestore.batch();
                    for (int i = 0; i < cartItems.size(); i++) {
                        CartItem item = cartItems.get(i);
                        Product p = item.getProduct();

                        Map<String, Object> data = new HashMap<>();
                        data.put("id", i + 1);
                        data.put("orderId", numericId);
                        data.put("productId", p == null ? 0 : p.getId());
                        data.put("productName", p == null ? "Sản phẩm" : p.getName());
                        data.put("productIcon", p == null ? "📦" : p.getIcon());
                        data.put("price", p == null ? 0 : p.getPrice());
                        data.put("quantity", item.getQuantity());
                        data.put("subtotal", item.getSubtotal());

                        batch.set(firestore.collection("orders")
                                .document(orderId)
                                .collection("items")
                                .document(String.valueOf(i + 1)), data);
                    }

                    // Cam kết ghi xong toàn bộ sản phẩm trong đơn thì xóa sạch giỏ hàng
                    batch.commit()
                            .addOnSuccessListener(x -> clearCart(normalized, ok -> callback.onResult(ok ? numericId : -1)))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    // Lấy danh sách đơn hàng của user, sắp xếp đơn mới nhất lên đầu
    public void getUserOrders(String email, OrdersCallback callback) {
        firestore.collection("orders")
                .whereEqualTo("userEmail", normalizeEmail(email))
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> result = new ArrayList<>();
                    for (DocumentSnapshot d : snapshot) {
                        result.add(toOrder(d));
                    }
                    result.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
                    callback.onResult(result);
                })
                .addOnFailureListener(callback::onError);
    }

    // Lấy chi tiết các sản phẩm trong 1 đơn hàng
    public void getOrderItems(long orderId, OrderItemsCallback callback) {
        firestore.collection("orders")
                .document(String.valueOf(orderId))
                .collection("items")
                .orderBy("id", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<OrderItem> result = new ArrayList<>();
                    for (DocumentSnapshot d : snapshot) {
                        result.add(toOrderItem(d));
                    }
                    callback.onResult(result);
                })
                .addOnFailureListener(callback::onError);
    }

    // --- TÀI KHOẢN ---

    // Kiểm tra email đã đăng ký tài khoản trên Firestore chưa
    public void checkEmailExists(String email, Consumer<Boolean> callback) {
        firestore.collection("users")
                .document(normalizeEmail(email))
                .get()
                .addOnSuccessListener(d -> callback.accept(d.exists()));
    }

    // Đăng ký tài khoản mới và lưu lên collection "users"
    public void registerUser(String fullName, String email, String password, String phone, BooleanCallback callback) {
        String normalized = normalizeEmail(email);
        Map<String, Object> data = new HashMap<>();
        data.put("id", normalized.hashCode());
        data.put("fullName", fullName.trim());
        data.put("email", normalized);
        data.put("password", password);
        data.put("avatar", "👤");
        data.put("phone", phone == null ? "" : phone.trim());
        data.put("address", "");

        firestore.collection("users").document(normalized).set(data)
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    // Kiểm tra đăng nhập bằng email, họ tên hoặc phần tiền tố trước dấu @ của email
    public void checkLogin(String account, String password, UserCallback callback) {
        String value = account == null ? "" : account.trim().toLowerCase();
        firestore.collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    User found = null;
                    for (DocumentSnapshot d : snapshot) {
                        String email = d.getString("email");
                        String name = d.getString("fullName");
                        boolean isAccountMatch = value.equals(email)
                                || value.equalsIgnoreCase(name)
                                || (email != null && email.startsWith(value + "@"));

                        if (isAccountMatch && password.equals(d.getString("password"))) {
                            found = toUser(d);
                            break;
                        }
                    }
                    callback.onResult(found);
                })
                .addOnFailureListener(callback::onError);
    }

    // Lấy thông tin user theo email
    public void getUserByEmail(String email, UserCallback callback) {
        firestore.collection("users")
                .document(normalizeEmail(email))
                .get()
                .addOnSuccessListener(d -> callback.onResult(d.exists() ? toUser(d) : null))
                .addOnFailureListener(callback::onError);
    }

    // Kiểm tra mật khẩu cũ có khớp không trước khi cho phép đổi mật khẩu
    public void checkPassword(String email, String password, BooleanCallback callback) {
        getUserByEmail(email, new UserCallback() {
            @Override
            public void onResult(User user) {
                callback.onResult(user != null && password.equals(user.getPassword()));
            }

            @Override
            public void onError(Exception e) {
                callback.onResult(false);
            }
        });
    }

    // Kiểm tra tên đăng nhập mới có bị trùng với tài khoản khác không
    public void checkUsernameExists(String username, String currentEmail, Consumer<Boolean> callback) {
        firestore.collection("users")
                .whereEqualTo("fullName", username.trim())
                .get()
                .addOnSuccessListener(s -> {
                    boolean isDuplicate = !s.isEmpty()
                            && !(s.size() == 1 && s.getDocuments().get(0).getId().equals(normalizeEmail(currentEmail)));
                    callback.accept(isDuplicate);
                });
    }

    // Cập nhật từng phần thông tin user (tên, SĐT, địa chỉ, mật khẩu) bằng SetOptions.merge()
    public void updateUser(String email, Map<String, Object> values, BooleanCallback callback) {
        firestore.collection("users")
                .document(normalizeEmail(email))
                .set(values, SetOptions.merge())
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    // --- CHUYỂN ĐỔI DỮ LIỆU & DATA MẪU ---

    // Chuyển DocumentSnapshot trên Firestore thành đối tượng Product
    private Product toProduct(DocumentSnapshot d) {
        return new Product(
                value(d, "id"),
                d.getString("name"),
                d.getString("category"),
                longValue(d, "price"),
                d.getString("description"),
                d.getString("icon")
        );
    }

    // Chuyển DocumentSnapshot trên Firestore thành đối tượng Order
    private Order toOrder(DocumentSnapshot d) {
        return new Order(
                value(d, "id"),
                d.getString("userEmail"),
                d.getString("receiverName"),
                d.getString("receiverPhone"),
                d.getString("receiverAddress"),
                d.getString("note"),
                d.getString("paymentMethod"),
                longValue(d, "totalAmount"),
                d.getString("orderDate"),
                d.getString("status")
        );
    }

    // Chuyển DocumentSnapshot trên Firestore thành đối tượng OrderItem
    private OrderItem toOrderItem(DocumentSnapshot d) {
        return new OrderItem(
                value(d, "id"),
                value(d, "orderId"),
                value(d, "productId"),
                d.getString("productName"),
                d.getString("productIcon"),
                longValue(d, "price"),
                value(d, "quantity"),
                longValue(d, "subtotal")
        );
    }

    // Chuyển DocumentSnapshot trên Firestore thành đối tượng User
    private User toUser(DocumentSnapshot d) {
        return new User(
                value(d, "id"),
                d.getString("fullName"),
                d.getString("email"),
                d.getString("password"),
                d.getString("avatar"),
                d.getString("phone"),
                d.getString("address")
        );
    }

    // Đọc giá trị số nguyên int an toàn từ DocumentSnapshot (tránh lỗi null)
    private int value(DocumentSnapshot d, String key) {
        Long value = d.getLong(key);
        return value == null ? 0 : value.intValue();
    }

    // Đọc giá trị số nguyên dài long an toàn từ DocumentSnapshot (tránh lỗi null)
    private long longValue(DocumentSnapshot d, String key) {
        Long value = d.getLong(key);
        return value == null ? 0 : value;
    }

    // Đóng gói đối tượng Product thành Map để ghi lên Firestore
    private Map<String, Object> productData(Product p) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", p.getId());
        d.put("name", p.getName());
        d.put("category", p.getCategory());
        d.put("price", p.getPrice());
        d.put("description", p.getDescription());
        d.put("icon", p.getIcon());
        return d;
    }

    // Danh sách 12 sản phẩm mẫu mặc định của cửa hàng
    private Product[] defaultProducts() {
        return new Product[]{
                new Product(1, "Áo thun cotton basic", "Thời trang", 189000, "Áo thun cotton 100% mềm mịn, thấm hút mồ hôi tốt, form regular dễ phối đồ.", "👕"),
                new Product(2, "Giày sneaker trắng thể thao", "Giày dép", 890000, "Giày sneaker trắng thiết kế tối giản, đế cao su êm ái và chống trượt.", "👟"),
                new Product(3, "Balo laptop chống nước cao cấp", "Phụ kiện", 459000, "Balo vải Oxford chống nước, ngăn đệm chống sốc cho laptop đến 15.6 inch.", "🎒"),
                new Product(4, "Đồng hồ thông minh Smartwatch Pro", "Công nghệ", 1790000, "Đồng hồ thông minh theo dõi nhịp tim, giấc ngủ và số bước chân.", "⌚"),
                new Product(5, "Tai nghe không dây Bluetooth TWS", "Công nghệ", 690000, "Tai nghe không dây Bluetooth 5.3, chống ồn chủ động ANC.", "🎧"),
                new Product(6, "Kính mát phân cực UV400", "Phụ kiện", 320000, "Tròng kính Polarized phân cực, chống 100% tia UV400.", "🕶️"),
                new Product(7, "Bình giữ nhiệt inox 304 600ml", "Gia dụng", 245000, "Bình inox 304 cao cấp dung tích 600ml, giữ nóng 12 giờ.", "🥤"),
                new Product(8, "Sạc dự phòng 20000mAh PD 22.5W", "Công nghệ", 490000, "Pin dự phòng dung lượng 20000mAh, hỗ trợ sạc nhanh PD 22.5W.", "🔋"),
                new Product(9, "Mũ lưỡi trai phong cách Unisex", "Thời trang", 129000, "Mũ lưỡi trai vải kaki thoáng mát, khóa kim loại điều chỉnh.", "🧢"),
                new Product(10, "Bàn phím cơ không dây RGB", "Công nghệ", 1250000, "Bàn phím cơ layout 87 phím, switch gõ êm, đèn nền RGB.", "⌨️"),
                new Product(11, "Chuột quang không dây Silent", "Công nghệ", 299000, "Chuột bấm siêu êm, cảm biến quang học 4000 DPI.", "🖱️"),
                new Product(12, "Áo khoác Hoodie nỉ dày dặn", "Thời trang", 359000, "Áo Hoodie chất nỉ lót bông mềm mại, form oversize trẻ trung.", "🧥")
        };
    }
}