
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

    private static final String GUEST_EMAIL = "guest@cart.local";
    private static FirestoreRepository instance;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface ProductsCallback {
        void onResult(List<Product> products);

        void onError(Exception e);
    }

    public interface ProductCallback {
        void onResult(Product product);

        void onError(Exception e);
    }

    public interface CartCallback {
        void onResult(List<CartItem> items);

        void onError(Exception e);
    }

    public interface BooleanCallback {
        void onResult(boolean success);
    }

    public interface UserCallback {
        void onResult(User user);

        void onError(Exception e);
    }

    public interface OrdersCallback {
        void onResult(List<Order> orders);

        void onError(Exception e);
    }

    public interface OrderItemsCallback {
        void onResult(List<OrderItem> items);

        void onError(Exception e);
    }

    public interface OrderCallback {
        void onResult(long id);

        void onError(Exception e);
    }

    private FirestoreRepository() {
    }

    public static synchronized FirestoreRepository getInstance() {
        if (instance == null) {
            instance = new FirestoreRepository();
        }
        return instance;
    }

    private String normalizeEmail(String email) {
        return (email == null || email.trim().isEmpty()) ? GUEST_EMAIL : email.trim().toLowerCase();
    }

    public void seedProductsIfNeeded() {
        firestore.collection("products").limit(1).get().addOnSuccessListener(snapshot -> {

            if (!snapshot.isEmpty())
                return;
            writeDefaultProducts(null);
        });
    }

    public void getAllProducts(ProductsCallback callback) {
        firestore.collection("products").orderBy("id", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.isEmpty()) {
                        writeDefaultProducts(() -> getAllProducts(callback));
                        return;
                    }

                    List<Product> products = new ArrayList<>();
                    int[] defaultStocks = { 45, 28, 63, 17, 52, 34, 80, 23, 71, 12, 55, 39 };

                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        Product p = toProduct(d);

                        if (d.getLong("stock") == null) {
                            int fallbackStock = (p.getId() >= 1 && p.getId() <= defaultStocks.length)
                                    ? defaultStocks[p.getId() - 1]
                                    : 30;
                            Map<String, Object> update = new HashMap<>();
                            update.put("stock", fallbackStock);
                            firestore.collection("products").document(d.getId()).set(update, SetOptions.merge());
                            p = new Product(p.getId(), p.getName(), p.getCategory(), p.getPrice(), p.getDescription(),
                                    p.getIcon(), fallbackStock);
                        }
                        products.add(p);
                    }
                    callback.onResult(products);
                }).addOnFailureListener(callback::onError);
    }

    private void writeDefaultProducts(Runnable onComplete) {
        WriteBatch batch = firestore.batch();
        for (Product p : defaultProducts()) {
            batch.set(firestore.collection("products").document(String.valueOf(p.getId())), productData(p));
        }
        batch.commit().addOnCompleteListener(task -> {
            if (onComplete != null)
                onComplete.run();
        });
    }

    public void getProductById(int id, ProductCallback callback) {
        firestore.collection("products").document(String.valueOf(id)).get()
                .addOnSuccessListener(d -> callback.onResult(d.exists() ? toProduct(d) : null))
                .addOnFailureListener(callback::onError);
    }

    public void decreaseStock(int productId, int quantity, BooleanCallback callback) {
        String docId = String.valueOf(productId);
        firestore.collection("products").document(docId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                if (callback != null)
                    callback.onResult(false);
                return;
            }
            int currentStock = snapshot.getLong("stock") != null ? snapshot.getLong("stock").intValue() : 0;
            int newStock = Math.max(0, currentStock - quantity);
            Map<String, Object> update = new HashMap<>();
            update.put("stock", newStock);
            firestore.collection("products").document(docId).update(update).addOnCompleteListener(t -> {
                if (callback != null)
                    callback.onResult(t.isSuccessful());
            });
        }).addOnFailureListener(e -> {
            if (callback != null)
                callback.onResult(false);
        });
    }

    public void addToCart(String email, int productId, int quantity, BooleanCallback callback) {
        String normalized = normalizeEmail(email);

        String docId = normalized + "_" + productId;

        firestore.collection("carts").document(docId).get().addOnSuccessListener(document -> {

            int current = (document.exists() && document.getLong("quantity") != null)
                    ? document.getLong("quantity").intValue()
                    : 0;

            Map<String, Object> values = new HashMap<>();
            values.put("userEmail", normalized);
            values.put("productId", productId);
            values.put("quantity", current + quantity);

            firestore.collection("carts").document(docId).set(values)
                    .addOnCompleteListener(task -> callback.onResult(task.isSuccessful()));
        }).addOnFailureListener(e -> callback.onResult(false));
    }

    public void syncCartItem(String email, int productId, int quantity, BooleanCallback callback) {
        String docId = normalizeEmail(email) + "_" + productId;
        if (quantity <= 0) {

            firestore.collection("carts").document(docId).delete()
                    .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
            return;
        }

        Map<String, Object> values = new HashMap<>();
        values.put("userEmail", normalizeEmail(email));
        values.put("productId", productId);
        values.put("quantity", quantity);
        firestore.collection("carts").document(docId).set(values)
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    public void getCartItems(String email, CartCallback callback) {
        String normalized = normalizeEmail(email);
        firestore.collection("carts").whereEqualTo("userEmail", normalized).get().addOnSuccessListener(snapshot -> {

            if (snapshot.isEmpty()) {
                callback.onResult(new ArrayList<>());
                return;
            }

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
        }).addOnFailureListener(callback::onError);
    }

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

    public void removeCartItem(String email, int productId, BooleanCallback callback) {
        String docId = normalizeEmail(email) + "_" + productId;
        firestore.collection("carts").document(docId).delete()
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    public void clearCart(String email, BooleanCallback callback) {
        firestore.collection("carts").whereEqualTo("userEmail", normalizeEmail(email)).get()
                .addOnSuccessListener(snapshot -> {

                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        batch.delete(d.getReference());
                    }
                    batch.commit().addOnCompleteListener(t -> {
                        callback.onResult(t.isSuccessful());
                    });
                }).addOnFailureListener(e -> callback.onResult(false));
    }

    public void removeOrderedCartItems(String email, List<CartItem> orderedItems, BooleanCallback callback) {
        if (orderedItems == null || orderedItems.isEmpty()) {
            if (callback != null)
                callback.onResult(true);
            return;
        }
        WriteBatch batch = firestore.batch();
        String normalized = normalizeEmail(email);
        for (CartItem item : orderedItems) {
            if (item.getProduct() != null) {
                String docId = normalized + "_" + item.getProduct().getId();
                batch.delete(firestore.collection("carts").document(docId));
            }
        }
        batch.commit().addOnCompleteListener(t -> {
            if (callback != null)
                callback.onResult(t.isSuccessful());
        });
    }

    public void getCartCount(String email, IntConsumer callback) {
        firestore.collection("carts").whereEqualTo("userEmail", normalizeEmail(email)).get()
                .addOnSuccessListener(snapshot -> {
                    int count = 0;
                    for (DocumentSnapshot d : snapshot) {
                        if (d.getLong("quantity") != null) {
                            count += d.getLong("quantity").intValue();
                        }
                    }
                    callback.accept(count);
                }).addOnFailureListener(e -> callback.accept(0));
    }

    public void createOrder(String email, String name, String phone, String address, String note, String payment,
            List<CartItem> cartItems, OrderCallback callback) {
        if (cartItems == null || cartItems.isEmpty()) {
            callback.onResult(-1);
            return;
        }

        String normalized = normalizeEmail(email);
        long numericId = System.currentTimeMillis() % Integer.MAX_VALUE;
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        long total = 0;
        for (CartItem item : cartItems) {
            total += item.getSubtotal();
        }

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

        String orderId = String.valueOf(numericId);
        firestore.collection("orders").document(orderId).set(order).addOnSuccessListener(v -> {

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

                batch.set(firestore.collection("orders").document(orderId).collection("items")
                        .document(String.valueOf(i + 1)), data);
            }

            batch.commit().addOnSuccessListener(x -> {
                AtomicInteger pendingDecrements = new AtomicInteger(cartItems.size());
                for (CartItem item : cartItems) {
                    Product p = item.getProduct();
                    if (p == null) {
                        if (pendingDecrements.decrementAndGet() == 0) {
                            removeOrderedCartItems(normalized, cartItems, ok -> callback.onResult(ok ? numericId : -1));
                        }
                        continue;
                    }
                    decreaseStock(p.getId(), item.getQuantity(), ok -> {
                        if (pendingDecrements.decrementAndGet() == 0) {
                            removeOrderedCartItems(normalized, cartItems,
                                    done -> callback.onResult(done ? numericId : -1));
                        }
                    });
                }
            }).addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    public void syncOrder(long orderId, String email, String name, String phone, String address, String note,
            String payment, long total, String orderDate, String status, List<OrderItem> items,
            BooleanCallback callback) {

        String normalized = normalizeEmail(email);
        Map<String, Object> order = new HashMap<>();
        order.put("id", orderId);
        order.put("userEmail", normalized);
        order.put("receiverName", name);
        order.put("receiverPhone", phone);
        order.put("receiverAddress", address);
        order.put("note", note == null ? "" : note);
        order.put("paymentMethod", payment);
        order.put("totalAmount", total);
        order.put("orderDate", orderDate);
        order.put("status", status);

        String orderDoc = String.valueOf(orderId);
        firestore.collection("orders").document(orderDoc).set(order).addOnSuccessListener(v -> {
            WriteBatch batch = firestore.batch();
            for (OrderItem item : items) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", item.getId());
                data.put("orderId", item.getOrderId());
                data.put("productId", item.getProductId());
                data.put("productName", item.getProductName());
                data.put("productIcon", item.getProductIcon());
                data.put("price", item.getPrice());
                data.put("quantity", item.getQuantity());
                data.put("subtotal", item.getSubtotal());
                batch.set(firestore.collection("orders").document(orderDoc).collection("items")
                        .document(String.valueOf(item.getId())), data);
            }

            batch.commit().addOnCompleteListener(t -> {
                if (!t.isSuccessful()) {
                    callback.onResult(false);
                    return;
                }
                clearCart(normalized, callback);
            });
        }).addOnFailureListener(e -> callback.onResult(false));
    }

    public void getUserOrders(String email, OrdersCallback callback) {
        firestore.collection("orders").whereEqualTo("userEmail", normalizeEmail(email)).get()
                .addOnSuccessListener(snapshot -> {
                    List<Order> result = new ArrayList<>();
                    for (DocumentSnapshot d : snapshot) {
                        result.add(toOrder(d));
                    }

                    result.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
                    callback.onResult(result);
                }).addOnFailureListener(callback::onError);
    }

    public void getOrderItems(long orderId, OrderItemsCallback callback) {
        firestore.collection("orders").document(String.valueOf(orderId)).collection("items")
                .orderBy("id", Query.Direction.ASCENDING).get().addOnSuccessListener(snapshot -> {
                    List<OrderItem> result = new ArrayList<>();
                    for (DocumentSnapshot d : snapshot) {
                        result.add(toOrderItem(d));
                    }
                    callback.onResult(result);
                }).addOnFailureListener(callback::onError);
    }

    public void registerUser(String fullName, String email, String phone, BooleanCallback callback) {
        String normalized = normalizeEmail(email);
        Map<String, Object> data = new HashMap<>();
        data.put("id", normalized.hashCode());
        data.put("fullName", fullName.trim());
        data.put("email", normalized);
        data.put("avatar", "👤");
        data.put("phone", phone == null ? "" : phone.trim());
        data.put("address", "");

        firestore.collection("users").document(normalized).set(data)
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    public void getUserByAccount(String account, UserCallback callback) {
        String value = account == null ? "" : account.trim();
        String lowerValue = value.toLowerCase();
        firestore.collection("users").get().addOnSuccessListener(snapshot -> {
            User found = null;
            for (DocumentSnapshot d : snapshot) {
                String email = d.getString("email");
                String name = d.getString("fullName");
                String username = d.getString("username");
                String phone = d.getString("phone");

                boolean isEmailMatch = email != null && (lowerValue.equals(email.trim().toLowerCase())
                        || email.toLowerCase().startsWith(lowerValue + "@"));
                boolean isNameMatch = (name != null && (value.equalsIgnoreCase(name.trim()) || lowerValue.equals(name.trim().toLowerCase())))
                        || (username != null && (value.equalsIgnoreCase(username.trim()) || lowerValue.equals(username.trim().toLowerCase())));
                boolean isPhoneMatch = isPhoneMatch(value, phone);

                boolean isAccountMatch = isEmailMatch || isNameMatch || isPhoneMatch;

                if (isAccountMatch) {
                    found = toUser(d);
                    break;
                }
            }
            callback.onResult(found);
        }).addOnFailureListener(callback::onError);
    }

    private boolean isPhoneMatch(String input, String target) {
        if (input == null || target == null) {
            return false;
        }
        String cleanInput = input.trim().replaceAll("[^0-9]", "");
        String cleanTarget = target.trim().replaceAll("[^0-9]", "");
        if (cleanInput.isEmpty() || cleanTarget.isEmpty()) {
            return false;
        }
        if (cleanInput.equals(cleanTarget)) {
            return true;
        }
        // Normalize Vietnamese phone numbers (+84xxxxxxxxx vs 0xxxxxxxxx)
        if (cleanInput.startsWith("84") && cleanInput.length() >= 10) {
            String withZero = "0" + cleanInput.substring(2);
            if (withZero.equals(cleanTarget)) {
                return true;
            }
        }
        if (cleanTarget.startsWith("84") && cleanTarget.length() >= 10) {
            String withZero = "0" + cleanTarget.substring(2);
            return cleanInput.equals(withZero);
        }
        return false;
    }

    public void getUserByEmail(String email, UserCallback callback) {
        firestore.collection("users").document(normalizeEmail(email)).get()
                .addOnSuccessListener(d -> callback.onResult(d.exists() ? toUser(d) : null))
                .addOnFailureListener(callback::onError);
    }

    public void checkUsernameExists(String username, String currentEmail, Consumer<Boolean> callback) {
        firestore.collection("users").whereEqualTo("fullName", username.trim()).get().addOnSuccessListener(s -> {
            boolean isDuplicate = !s.isEmpty()
                    && !(s.size() == 1 && s.getDocuments().get(0).getId().equals(normalizeEmail(currentEmail)));
            callback.accept(isDuplicate);
        }).addOnFailureListener(e -> callback.accept(false));
    }

    public void updateUser(String email, Map<String, Object> values, BooleanCallback callback) {
        firestore.collection("users").document(normalizeEmail(email)).set(values, SetOptions.merge())
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    private Product toProduct(DocumentSnapshot d) {
        int stock = (d.getLong("stock") != null) ? d.getLong("stock").intValue() : 0;
        return new Product(value(d, "id"), d.getString("name"), d.getString("category"), longValue(d, "price"),
                d.getString("description"), d.getString("icon"), stock);
    }

    private Order toOrder(DocumentSnapshot d) {
        return new Order(value(d, "id"), d.getString("userEmail"), d.getString("receiverName"),
                d.getString("receiverPhone"), d.getString("receiverAddress"), d.getString("note"),
                d.getString("paymentMethod"), longValue(d, "totalAmount"), d.getString("orderDate"),
                d.getString("status"));
    }

    private OrderItem toOrderItem(DocumentSnapshot d) {
        return new OrderItem(value(d, "id"), value(d, "orderId"), value(d, "productId"), d.getString("productName"),
                d.getString("productIcon"), longValue(d, "price"), value(d, "quantity"), longValue(d, "subtotal"));
    }

    private User toUser(DocumentSnapshot d) {
        return new User(value(d, "id"), d.getString("fullName"), d.getString("email"), d.getString("avatar"),
                d.getString("phone"), d.getString("address"));
    }

    private int value(DocumentSnapshot d, String key) {
        Long value = d.getLong(key);
        return value == null ? 0 : value.intValue();
    }

    private long longValue(DocumentSnapshot d, String key) {
        Long value = d.getLong(key);
        return value == null ? 0 : value;
    }

    private Map<String, Object> productData(Product p) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", p.getId());
        d.put("name", p.getName());
        d.put("category", p.getCategory());
        d.put("price", p.getPrice());
        d.put("description", p.getDescription());
        d.put("icon", p.getIcon());
        d.put("stock", p.getStock());
        return d;
    }

    private Product[] defaultProducts() {
        return new Product[] {
                new Product(1, "Áo thun cotton basic", "Thời trang", 189000,
                        "Áo thun cotton 100% mềm mịn, thấm hút mồ hôi tốt, form regular dễ phối đồ.", "👕", 45),
                new Product(2, "Giày sneaker trắng thể thao", "Giày dép", 890000,
                        "Giày sneaker trắng thiết kế tối giản, đế cao su êm ái và chống trượt.", "👟", 28),
                new Product(3, "Balo laptop chống nước cao cấp", "Phụ kiện", 459000,
                        "Balo vải Oxford chống nước, ngăn đệm chống sốc cho laptop đến 15.6 inch.", "🎒", 63),
                new Product(4, "Đồng hồ thông minh Smartwatch Pro", "Công nghệ", 1790000,
                        "Đồng hồ thông minh theo dõi nhịp tim, giấc ngủ và số bước chân.", "⌚", 17),
                new Product(5, "Tai nghe không dây Bluetooth TWS", "Công nghệ", 690000,
                        "Tai nghe không dây Bluetooth 5.3, chống ồn chủ động ANC.", "🎧", 52),
                new Product(6, "Kính mát phân cực UV400", "Phụ kiện", 320000,
                        "Tròng kính Polarized phân cực, chống 100% tia UV400.", "🕶️", 34),
                new Product(7, "Bình giữ nhiệt inox 304 600ml", "Gia dụng", 245000,
                        "Bình inox 304 cao cấp dung tích 600ml, giữ nóng 12 giờ.", "🥤", 80),
                new Product(8, "Sạc dự phòng 20000mAh PD 22.5W", "Công nghệ", 490000,
                        "Pin dự phòng dung lượng 20000mAh, hỗ trợ sạc nhanh PD 22.5W.", "🔋", 23),
                new Product(9, "Mũ lưỡi trai phong cách Unisex", "Thời trang", 129000,
                        "Mũ lưỡi trai vải kaki thoáng mát, khóa kim loại điều chỉnh.", "🧢", 71),
                new Product(10, "Bàn phím cơ không dây RGB", "Công nghệ", 1250000,
                        "Bàn phím cơ layout 87 phím, switch gõ êm, đèn nền RGB.", "⌨️", 12),
                new Product(11, "Chuột quang không dây Silent", "Công nghệ", 299000,
                        "Chuột bấm siêu êm, cảm biến quang học 4000 DPI.", "🖱️", 55),
                new Product(12, "Áo khoác Hoodie nỉ dày dặn", "Thời trang", 359000,
                        "Áo Hoodie chất nỉ lót bông mềm mại, form oversize trẻ trung.", "🧥", 39) };
    }
}
