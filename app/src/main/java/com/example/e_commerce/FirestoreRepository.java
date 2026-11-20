
package com.example.e_commerce;

import android.util.Base64;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    public interface OrderReturnCallback {
        void onSuccess();

        void onError(Exception e);
    }

    public interface OrderCancellationCallback {
        void onSuccess();

        void onError(Exception e);
    }

    public interface NotificationsCallback {
        void onResult(List<AppNotification> notifications);

        void onError(Exception e);
    }

    public interface OrderCallback {
        void onResult(long id);

        void onError(Exception e);
    }

    public interface UsernameCallback {
        void onResult(boolean exists);

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

    public void getAllProducts(ProductsCallback callback) {
        InitialCatalogSeeder.seedCatalogAndVouchersIfNeeded(firestore)
                .addOnSuccessListener(seeded -> firestore.collection("products")
                        .orderBy("id", Query.Direction.ASCENDING).get()
                        .addOnSuccessListener(snapshot -> {
                            List<Product> products = new ArrayList<>();
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                products.add(toProduct(document));
                            }
                            callback.onResult(products);
                        }).addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
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
        if (productId <= 0 || quantity <= 0) {
            callback.onResult(false);
            return;
        }

        DocumentReference cartRef = firestore.collection("carts").document(docId);
        DocumentReference productRef = firestore.collection("products").document(String.valueOf(productId));
        firestore.runTransaction(transaction -> {
            DocumentSnapshot cart = transaction.get(cartRef);
            DocumentSnapshot product = transaction.get(productRef);
            Long stock = product.getLong("stock");
            Long existingQuantity = cart.getLong("quantity");
            long currentQuantity = existingQuantity == null ? 0 : existingQuantity;
            if (!product.exists() || stock == null || stock < 0 || currentQuantity < 0
                    || quantity > stock - currentQuantity) {
                return false;
            }

            Map<String, Object> values = new HashMap<>();
            values.put("userEmail", normalized);
            values.put("productId", productId);
            values.put("quantity", Math.addExact(currentQuantity, quantity));
            transaction.set(cartRef, values);
            return true;
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(error -> callback.onResult(false));
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
            AtomicReference<Exception> loadError = new AtomicReference<>();
            AtomicBoolean callbackDelivered = new AtomicBoolean(false);

            for (DocumentSnapshot cart : snapshot.getDocuments()) {
                Long productId = cart.getLong("productId");
                Long quantity = cart.getLong("quantity");

                if (productId == null || productId <= 0 || productId > Integer.MAX_VALUE
                        || quantity == null || quantity <= 0 || quantity > Integer.MAX_VALUE) {
                    loadError.compareAndSet(null, new IllegalStateException("Dữ liệu giỏ hàng không hợp lệ."));
                    completeCartLoad(completedCount, totalDocs, callbackDelivered, loadError, items, callback);
                    continue;
                }

                getProductById(productId.intValue(), new ProductCallback() {
                    @Override
                    public void onResult(Product product) {
                        if (product == null) {
                            loadError.compareAndSet(null,
                                    new IllegalStateException("Không tìm thấy sản phẩm trong giỏ hàng."));
                        } else {
                            synchronized (items) {
                                items.add(new CartItem(product.getId(), product, quantity.intValue()));
                            }
                        }
                        completeCartLoad(completedCount, totalDocs, callbackDelivered, loadError, items, callback);
                    }

                    @Override
                    public void onError(Exception e) {
                        loadError.compareAndSet(null, e);
                        completeCartLoad(completedCount, totalDocs, callbackDelivered, loadError, items, callback);
                    }
                });
            }
        }).addOnFailureListener(callback::onError);
    }

    private void completeCartLoad(AtomicInteger completedCount, int totalDocs, AtomicBoolean callbackDelivered,
            AtomicReference<Exception> loadError, List<CartItem> items, CartCallback callback) {
        if (completedCount.incrementAndGet() != totalDocs || !callbackDelivered.compareAndSet(false, true)) {
            return;
        }
        Exception error = loadError.get();
        if (error != null) {
            callback.onError(error);
            return;
        }
        synchronized (items) {
            callback.onResult(new ArrayList<>(items));
        }
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
            List<CartItem> cartItems, String productVoucherId, String shippingVoucherId, long pointsToUse,
            OrderCallback callback) {
        if (cartItems == null || cartItems.isEmpty()) {
            callback.onResult(-1);
            return;
        }
        if (pointsToUse < 0) {
            callback.onError(new IllegalArgumentException("Không thể sử dụng số Xu âm."));
            return;
        }

        String normalized = normalizeEmail(email);
        long numericId = System.currentTimeMillis() % Integer.MAX_VALUE;
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        DocumentReference orderRef = firestore.collection("orders").document(String.valueOf(numericId));
        DocumentReference notificationRef = firestore.collection("notifications")
                .document("order_" + numericId + "_placed");
        DocumentReference productVoucherRef = isBlank(productVoucherId)
                ? null
                : VoucherRepository.userVoucherDocument(firestore, normalized, productVoucherId);
        DocumentReference shippingVoucherRef = isBlank(shippingVoucherId)
                ? null
                : VoucherRepository.userVoucherDocument(firestore, normalized, shippingVoucherId);
        Map<Integer, Integer> orderedQuantities = new HashMap<>();
        Map<Integer, DocumentReference> productRefs = new HashMap<>();
        Map<Integer, DocumentReference> cartRefs = new HashMap<>();
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            if (product == null || item.getQuantity() <= 0) {
                callback.onError(new IllegalStateException("Giỏ hàng có sản phẩm không hợp lệ."));
                return;
            }
            int productId = product.getId();
            try {
                orderedQuantities.merge(productId, item.getQuantity(), Math::addExact);
            } catch (ArithmeticException error) {
                callback.onError(new IllegalStateException("Số lượng sản phẩm không hợp lệ.", error));
                return;
            }
            productRefs.put(productId, firestore.collection("products").document(String.valueOf(productId)));
            cartRefs.put(productId, firestore.collection("carts").document(normalized + "_" + productId));
        }

        OrderRequest request = new OrderRequest(
                normalized, name, phone, address, note, payment, cartItems, pointsToUse,
                numericId, date, orderRef, notificationRef, productVoucherRef, shippingVoucherRef,
                orderedQuantities, productRefs, cartRefs);
        if (pointsToUse == 0) {
            commitOrder(request, new ArrayList<>(), callback);
            return;
        }

        PointsRepository.getInstance().loadSpendableLots(normalized, new PointsRepository.SpendLotsCallback() {
            @Override
            public void onResult(List<DocumentSnapshot> lots) {
                commitOrder(request, lots, callback);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    private void commitOrder(OrderRequest request, List<DocumentSnapshot> pointLots, OrderCallback callback) {
        String normalized = request.normalized;
        String name = request.name;
        String phone = request.phone;
        String address = request.address;
        String note = request.note;
        String payment = request.payment;
        long numericId = request.numericId;
        String date = request.date;
        DocumentReference orderRef = request.orderRef;
        DocumentReference notificationRef = request.notificationRef;
        DocumentReference productVoucherRef = request.productVoucherRef;
        DocumentReference shippingVoucherRef = request.shippingVoucherRef;
        List<CartItem> cartItems = request.cartItems;
        Map<Integer, Integer> orderedQuantities = request.orderedQuantities;
        Map<Integer, DocumentReference> productRefs = request.productRefs;
        Map<Integer, DocumentReference> cartRefs = request.cartRefs;

        firestore.runTransaction(transaction -> {
            DocumentSnapshot existingOrder = transaction.get(orderRef);
            DocumentSnapshot productVoucherSnapshot = productVoucherRef == null
                    ? null
                    : transaction.get(productVoucherRef);
            DocumentSnapshot shippingVoucherSnapshot = shippingVoucherRef == null
                    ? null
                    : transaction.get(shippingVoucherRef);
            Map<Integer, DocumentSnapshot> productSnapshots = new HashMap<>();
            Map<Integer, DocumentSnapshot> cartSnapshots = new HashMap<>();
            for (Integer productId : orderedQuantities.keySet()) {
                productSnapshots.put(productId, transaction.get(productRefs.get(productId)));
                cartSnapshots.put(productId, transaction.get(cartRefs.get(productId)));
            }
            if (existingOrder.exists()) {
                throw new IllegalStateException("Mã đơn hàng bị trùng. Vui lòng đặt hàng lại.");
            }
            Map<Integer, Long> remainingStock = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : orderedQuantities.entrySet()) {
                int productId = entry.getKey();
                int quantity = entry.getValue();
                DocumentSnapshot productSnapshot = productSnapshots.get(productId);
                DocumentSnapshot cartSnapshot = cartSnapshots.get(productId);
                Long currentStock = productSnapshot.getLong("stock");
                Long cartQuantity = cartSnapshot.getLong("quantity");
                if (!productSnapshot.exists() || currentStock == null || currentStock < quantity) {
                    throw new IllegalStateException("Sản phẩm đã hết hàng hoặc không đủ số lượng.");
                }
                if (!cartSnapshot.exists() || cartQuantity == null || cartQuantity != quantity) {
                    throw new IllegalStateException("Giỏ hàng đã thay đổi. Vui lòng kiểm tra lại đơn hàng.");
                }
                remainingStock.put(productId, currentStock - quantity);
            }
            if (productVoucherSnapshot != null) {
                validateOwnedVoucher(productVoucherSnapshot, "product");
            }
            if (shippingVoucherSnapshot != null) {
                validateOwnedVoucher(shippingVoucherSnapshot, "shipping");
            }

            Voucher productVoucher = productVoucherSnapshot == null
                    ? null
                    : Voucher.fromDocument(productVoucherSnapshot);
            Voucher shippingVoucher = shippingVoucherSnapshot == null
                    ? null
                    : Voucher.fromDocument(shippingVoucherSnapshot);
            VoucherCalculator.ProductDiscount productDiscount =
                    VoucherCalculator.calculateProductDiscount(cartItems, productVoucher);
            if (productVoucher != null && !productDiscount.isApplicable()) {
                throw new IllegalStateException("Voucher giảm giá sản phẩm không còn đủ điều kiện.");
            }
            long shippingDiscount = VoucherCalculator.calculateShippingDiscount(
                    cartItems, shippingVoucher, VoucherCalculator.BASE_SHIPPING_FEE);
            if (shippingVoucher != null && shippingDiscount <= 0) {
                throw new IllegalStateException("Voucher vận chuyển không còn đủ điều kiện.");
            }

            long itemsSubtotal = 0;
            for (CartItem item : cartItems) {
                itemsSubtotal = Math.addExact(itemsSubtotal, item.getSubtotal());
            }
            long discountedItemsSubtotal = Math.max(0, itemsSubtotal - productDiscount.getTotalDiscount());
            long discountedShipping = Math.max(0, VoucherCalculator.BASE_SHIPPING_FEE - shippingDiscount);
            long pointsBalanceAfter = request.pointsToUse > 0
                    ? PointsRepository.getInstance().spendForOrder(
                            transaction,
                            normalized,
                            request.pointsToUse,
                            String.valueOf(numericId),
                            discountedItemsSubtotal,
                            pointLots)
                    : 0;
            long total = Math.subtractExact(
                    Math.addExact(discountedItemsSubtotal, discountedShipping),
                    request.pointsToUse);

            Map<String, Object> order = new HashMap<>();
            order.put("id", numericId);
            order.put("userEmail", normalized);
            order.put("receiverName", name.trim());
            order.put("receiverPhone", phone.trim());
            order.put("receiverAddress", address.trim());
            order.put("note", note == null ? "" : note.trim());
            order.put("paymentMethod", payment);
            order.put("itemsSubtotal", itemsSubtotal);
            order.put("productDiscount", productDiscount.getTotalDiscount());
            order.put("shippingFee", VoucherCalculator.BASE_SHIPPING_FEE);
            order.put("shippingDiscount", shippingDiscount);
            order.put("pointsUsed", request.pointsToUse);
            order.put("pointsDiscount", request.pointsToUse);
            order.put("productVoucherId", productVoucher == null ? "" : productVoucher.getId());
            order.put("productVoucherCode", productVoucher == null ? "" : productVoucher.getCode());
            order.put("shippingVoucherId", shippingVoucher == null ? "" : shippingVoucher.getId());
            order.put("shippingVoucherCode", shippingVoucher == null ? "" : shippingVoucher.getCode());
            if (request.pointsToUse > 0) {
                order.put("pointsBalanceAfter", pointsBalanceAfter);
                order.put("pointsSpendReferenceId", String.valueOf(numericId));
            }
            order.put("totalAmount", total);
            order.put("orderDate", date);
            order.put("status", "Đặt hàng thành công");
            transaction.set(orderRef, order);

            for (int index = 0; index < cartItems.size(); index++) {
                CartItem item = cartItems.get(index);
                Product product = item.getProduct();
                long originalSubtotal = item.getSubtotal();
                long lineDiscount = product == null
                        ? 0
                        : productDiscount.getDiscountForProduct(product.getId());

                Map<String, Object> data = new HashMap<>();
                data.put("id", index + 1);
                data.put("orderId", numericId);
                data.put("productId", product == null ? 0 : product.getId());
                data.put("productName", product == null ? "Sản phẩm" : product.getName());
                data.put("productIcon", product == null ? "📦" : product.getIcon());
                data.put("price", product == null ? 0 : product.getPrice());
                data.put("quantity", item.getQuantity());
                data.put("originalSubtotal", originalSubtotal);
                data.put("discountAmount", lineDiscount);
                data.put("subtotal", Math.max(0, originalSubtotal - lineDiscount));
                transaction.set(orderRef.collection("items").document(String.valueOf(index + 1)), data);
            }

            Map<String, Object> notification = new HashMap<>();
            notification.put("userEmail", normalized);
            notification.put("type", "order_placed");
            notification.put("title", "Đặt hàng thành công");
            notification.put("message", "Đơn hàng #DH"
                    + String.format(Locale.getDefault(), "%05d", numericId) + " đã được tiếp nhận.");
            notification.put("orderId", numericId);
            notification.put("createdAt", FieldValue.serverTimestamp());
            notification.put("isRead", false);
            transaction.set(notificationRef, notification);
            for (Integer productId : orderedQuantities.keySet()) {
                transaction.update(productRefs.get(productId), "stock", remainingStock.get(productId));
                transaction.delete(cartRefs.get(productId));
            }

            if (productVoucherRef != null) {
                transaction.update(productVoucherRef,
                        "state", "used",
                        "usedAt", FieldValue.serverTimestamp(),
                        "orderId", numericId,
                        "discountAmount", productDiscount.getTotalDiscount());
            }
            if (shippingVoucherRef != null) {
                transaction.update(shippingVoucherRef,
                        "state", "used",
                        "usedAt", FieldValue.serverTimestamp(),
                        "orderId", numericId,
                        "discountAmount", shippingDiscount);
            }
            return numericId;
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    private static final class OrderRequest {
        private final String normalized;
        private final String name;
        private final String phone;
        private final String address;
        private final String note;
        private final String payment;
        private final List<CartItem> cartItems;
        private final long pointsToUse;
        private final long numericId;
        private final String date;
        private final DocumentReference orderRef;
        private final DocumentReference notificationRef;
        private final DocumentReference productVoucherRef;
        private final DocumentReference shippingVoucherRef;
        private final Map<Integer, Integer> orderedQuantities;
        private final Map<Integer, DocumentReference> productRefs;
        private final Map<Integer, DocumentReference> cartRefs;

        private OrderRequest(String normalized, String name, String phone, String address, String note,
                String payment, List<CartItem> cartItems, long pointsToUse, long numericId, String date,
                DocumentReference orderRef, DocumentReference notificationRef,
                DocumentReference productVoucherRef, DocumentReference shippingVoucherRef,
                Map<Integer, Integer> orderedQuantities, Map<Integer, DocumentReference> productRefs,
                Map<Integer, DocumentReference> cartRefs) {
            this.normalized = normalized;
            this.name = name;
            this.phone = phone;
            this.address = address;
            this.note = note;
            this.payment = payment;
            this.cartItems = cartItems;
            this.pointsToUse = pointsToUse;
            this.numericId = numericId;
            this.date = date;
            this.orderRef = orderRef;
            this.notificationRef = notificationRef;
            this.productVoucherRef = productVoucherRef;
            this.shippingVoucherRef = shippingVoucherRef;
            this.orderedQuantities = orderedQuantities;
            this.productRefs = productRefs;
            this.cartRefs = cartRefs;
        }
    }

    private void validateOwnedVoucher(DocumentSnapshot voucher, String expectedType) {
        Timestamp expiresAt = voucher.getTimestamp("expiresAt");
        if (!"saved".equals(voucher.getString("state"))
                || !expectedType.equals(voucher.getString("type"))
                || expiresAt == null
                || !expiresAt.toDate().after(new Date())) {
            throw new IllegalStateException("Voucher đã hết hạn hoặc không còn khả dụng.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean sameVoucherId(String expected, String current) {
        return isBlank(expected) ? isBlank(current) : expected.equals(current);
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

    public void getUserNotifications(String email, NotificationsCallback callback) {
        firestore.collection("notifications").whereEqualTo("userEmail", normalizeEmail(email)).get()
                .addOnSuccessListener(snapshot -> {
                    List<AppNotification> result = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot) {
                        result.add(toNotification(document));
                    }
                    result.sort((first, second) -> Long.compare(
                            notificationTime(second.getCreatedAt()), notificationTime(first.getCreatedAt())));
                    callback.onResult(result);
                }).addOnFailureListener(callback::onError);
    }

    public void markNotificationRead(String notificationId, BooleanCallback callback) {
        firestore.collection("notifications").document(notificationId).update("isRead", true)
                .addOnCompleteListener(task -> callback.onResult(task.isSuccessful()));
    }

    public void markAllNotificationsRead(String email, BooleanCallback callback) {
        firestore.collection("notifications").whereEqualTo("userEmail", normalizeEmail(email)).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentReference> unreadNotifications = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot) {
                        if (!Boolean.TRUE.equals(document.getBoolean("isRead"))) {
                            unreadNotifications.add(document.getReference());
                        }
                    }
                    markNotificationReadBatch(unreadNotifications, 0, callback);
                }).addOnFailureListener(e -> callback.onResult(false));
    }

    public void syncOrderStatusNotifications(String email, BooleanCallback callback) {
        String normalized = normalizeEmail(email);
        firestore.collection("orders").whereEqualTo("userEmail", normalized).get().addOnSuccessListener(snapshot -> {
            List<Order> orders = new ArrayList<>();
            for (DocumentSnapshot document : snapshot) {
                orders.add(toOrder(document));
            }
            if (orders.isEmpty()) {
                callback.onResult(true);
                return;
            }

            AtomicInteger pending = new AtomicInteger(orders.size());
            AtomicBoolean successful = new AtomicBoolean(true);
            for (Order order : orders) {
                String status = order.getStatus();
                if (status == null || status.trim().isEmpty() || "Đặt hàng thành công".equals(status)) {
                    completeStatusSync(pending, successful, true, callback);
                    continue;
                }

                String notificationId = "order_" + order.getId() + "_status_"
                        + Integer.toHexString(status.hashCode());
                DocumentReference notificationRef = firestore.collection("notifications").document(notificationId);
                Map<String, Object> notification = new HashMap<>();
                notification.put("userEmail", normalized);
                notification.put("type", "order_status");
                notification.put("title", "Cập nhật đơn hàng");
                notification.put("message", "Đơn hàng #DH"
                        + String.format(Locale.getDefault(), "%05d", order.getId())
                        + " chuyển sang trạng thái: " + status + ".");
                notification.put("orderId", order.getId());
                notification.put("status", status);
                notification.put("createdAt", FieldValue.serverTimestamp());
                notification.put("isRead", false);
                firestore.runTransaction(transaction -> {
                    if (!transaction.get(notificationRef).exists()) {
                        transaction.set(notificationRef, notification);
                    }
                    return null;
                }).addOnCompleteListener(task ->
                        completeStatusSync(pending, successful, task.isSuccessful(), callback));
            }
        }).addOnFailureListener(e -> callback.onResult(false));
    }

    private void markNotificationReadBatch(List<DocumentReference> unreadNotifications, int startIndex,
            BooleanCallback callback) {
        if (startIndex >= unreadNotifications.size()) {
            callback.onResult(true);
            return;
        }

        WriteBatch batch = firestore.batch();
        int endIndex = Math.min(startIndex + 450, unreadNotifications.size());
        for (int i = startIndex; i < endIndex; i++) {
            batch.update(unreadNotifications.get(i), "isRead", true);
        }
        batch.commit().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onResult(false);
                return;
            }
            markNotificationReadBatch(unreadNotifications, endIndex, callback);
        });
    }

    private void completeStatusSync(AtomicInteger pending, AtomicBoolean successful, boolean itemSuccessful,
            BooleanCallback callback) {
        if (!itemSuccessful) {
            successful.set(false);
        }
        if (pending.decrementAndGet() == 0) {
            callback.onResult(successful.get());
        }
    }

    private long notificationTime(Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.toDate().getTime();
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

    public void cancelOrder(long orderId, String email, OrderCancellationCallback callback) {
        String normalizedEmail = normalizeEmail(email);
        if (GUEST_EMAIL.equals(normalizedEmail)) {
            callback.onError(new IllegalStateException("Vui lòng đăng nhập để hủy đơn hàng."));
            return;
        }

        DocumentReference orderRef = firestore.collection("orders").document(String.valueOf(orderId));
        orderRef.get().addOnSuccessListener(order -> {
            if (!order.exists() || !normalizedEmail.equals(order.getString("userEmail"))
                    || !OrderCancellationRules.isEligible(order.getString("status"))) {
                callback.onError(new IllegalStateException("Chỉ có thể hủy đơn hàng trước khi giao."));
                return;
            }

            orderRef.collection("items").get().addOnSuccessListener(items -> {
                if (items.isEmpty()) {
                    callback.onError(new IllegalStateException("Đơn hàng chưa có sản phẩm để hoàn kho."));
                    return;
                }
                applyOrderCancellation(orderId, normalizedEmail, order, items, callback);
            }).addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    private void applyOrderCancellation(long orderId, String email, DocumentSnapshot originalOrder,
            QuerySnapshot itemDocuments, OrderCancellationCallback callback) {
        DocumentReference orderRef = originalOrder.getReference();
        List<DocumentReference> itemRefs = new ArrayList<>();
        Map<String, OrderItem> expectedItems = new HashMap<>();
        Map<Integer, Long> quantities = new HashMap<>();
        Map<Integer, DocumentReference> productRefs = new HashMap<>();
        for (DocumentSnapshot itemDocument : itemDocuments.getDocuments()) {
            OrderItem item = toOrderItem(itemDocument);
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) {
                callback.onError(new IllegalStateException("Sản phẩm trong đơn hàng không hợp lệ."));
                return;
            }
            try {
                quantities.merge(item.getProductId(), (long) item.getQuantity(), Math::addExact);
            } catch (ArithmeticException error) {
                callback.onError(new IllegalStateException("Số lượng sản phẩm trong đơn không hợp lệ.", error));
                return;
            }
            itemRefs.add(itemDocument.getReference());
            expectedItems.put(itemDocument.getId(), item);
            productRefs.put(item.getProductId(),
                    firestore.collection("products").document(String.valueOf(item.getProductId())));
        }

        Map<Integer, DocumentReference> soldOrderRefs = new HashMap<>();
        for (Integer productId : quantities.keySet()) {
            soldOrderRefs.put(productId, productRefs.get(productId)
                    .collection("soldOrders").document(String.valueOf(orderId)));
        }

        String productVoucherId = originalOrder.getString("productVoucherId");
        String shippingVoucherId = originalOrder.getString("shippingVoucherId");
        DocumentReference productVoucherRef = isBlank(productVoucherId)
                ? null
                : VoucherRepository.userVoucherDocument(firestore, email, productVoucherId);
        DocumentReference shippingVoucherRef = isBlank(shippingVoucherId)
                ? null
                : VoucherRepository.userVoucherDocument(firestore, email, shippingVoucherId);
        DocumentReference notificationRef = firestore.collection("notifications")
                .document("order_" + orderId + "_cancelled");
        Date cancellationTime = new Date();

        firestore.runTransaction(transaction -> {
            DocumentSnapshot order = transaction.get(orderRef);
            if (!order.exists() || !email.equals(order.getString("userEmail"))
                    || !OrderCancellationRules.isEligible(order.getString("status"))) {
                throw new IllegalStateException("Đơn hàng không còn đủ điều kiện để hủy.");
            }
            if (!sameVoucherId(productVoucherId, order.getString("productVoucherId"))
                    || !sameVoucherId(shippingVoucherId, order.getString("shippingVoucherId"))) {
                throw new IllegalStateException("Thông tin voucher của đơn hàng đã thay đổi.");
            }

            Map<String, DocumentSnapshot> currentItems = new HashMap<>();
            for (DocumentReference itemRef : itemRefs) {
                currentItems.put(itemRef.getId(), transaction.get(itemRef));
            }
            Map<Integer, DocumentSnapshot> products = new HashMap<>();
            Map<Integer, DocumentSnapshot> soldMarkers = new HashMap<>();
            for (Integer productId : quantities.keySet()) {
                products.put(productId, transaction.get(productRefs.get(productId)));
                soldMarkers.put(productId, transaction.get(soldOrderRefs.get(productId)));
            }
            DocumentSnapshot productVoucher = productVoucherRef == null
                    ? null
                    : transaction.get(productVoucherRef);
            DocumentSnapshot shippingVoucher = shippingVoucherRef == null
                    ? null
                    : transaction.get(shippingVoucherRef);
            DocumentSnapshot existingNotification = transaction.get(notificationRef);

            for (DocumentReference itemRef : itemRefs) {
                DocumentSnapshot item = currentItems.get(itemRef.getId());
                if (!item.exists()) {
                    throw new IllegalStateException("Chi tiết đơn hàng đã thay đổi.");
                }
                OrderItem expected = expectedItems.get(itemRef.getId());
                OrderItem current = toOrderItem(item);
                if (expected == null || expected.getProductId() != current.getProductId()
                        || expected.getQuantity() != current.getQuantity()) {
                    throw new IllegalStateException("Chi tiết đơn hàng đã thay đổi.");
                }
            }
            for (Map.Entry<Integer, Long> entry : quantities.entrySet()) {
                DocumentSnapshot product = products.get(entry.getKey());
                if (!product.exists() || product.getLong("stock") == null) {
                    throw new IllegalStateException("Không tìm thấy tồn kho sản phẩm cần hoàn.");
                }
                Math.addExact(product.getLong("stock"), entry.getValue());
            }

            PointsRepository.getInstance().applyOrderCancellationInTransaction(
                    transaction, email, orderId, longValue(order, "pointsUsed"));

            for (Map.Entry<Integer, Long> entry : quantities.entrySet()) {
                int productId = entry.getKey();
                DocumentSnapshot product = products.get(productId);
                long restoredStock = Math.addExact(product.getLong("stock"), entry.getValue());
                DocumentSnapshot soldMarker = soldMarkers.get(productId);
                if (soldMarker.exists()) {
                    long soldQuantity = soldMarker.getLong("quantity") == null
                            ? entry.getValue()
                            : Math.max(0, soldMarker.getLong("quantity"));
                    long soldCount = Math.max(0, longValue(product, "soldCount") - soldQuantity);
                    transaction.update(productRefs.get(productId),
                            "stock", restoredStock,
                            "soldCount", soldCount);
                    transaction.delete(soldOrderRefs.get(productId));
                } else {
                    transaction.update(productRefs.get(productId), "stock", restoredStock);
                }
            }

            restoreVoucherIfValid(transaction, productVoucherRef, productVoucher, orderId, cancellationTime);
            restoreVoucherIfValid(transaction, shippingVoucherRef, shippingVoucher, orderId, cancellationTime);

            Map<String, Object> orderUpdate = new HashMap<>();
            orderUpdate.put("status", OrderCancellationRules.STATUS_CANCELLED);
            orderUpdate.put("cancelledAt", FieldValue.serverTimestamp());
            orderUpdate.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(orderRef, orderUpdate);

            if (!existingNotification.exists()) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("userEmail", email);
                notification.put("type", "order_cancel");
                notification.put("title", "Đơn hàng đã hủy");
                notification.put("message", "Đơn hàng #DH"
                        + String.format(Locale.getDefault(), "%05d", orderId)
                        + " đã được hủy theo yêu cầu của bạn.");
                notification.put("orderId", orderId);
                notification.put("status", OrderCancellationRules.STATUS_CANCELLED);
                notification.put("createdAt", FieldValue.serverTimestamp());
                notification.put("isRead", false);
                transaction.set(notificationRef, notification);
            }
            return null;
        }).addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void requestOrderReturn(long orderId, String email, String reasonType, String reason,
            OrderReturnCallback callback) {
        String normalizedEmail = normalizeEmail(email);
        if (!OrderReturnRules.isValidReason(reasonType, reason)) {
            callback.onError(new IllegalArgumentException("Vui lòng nhập lý do hoàn trả hợp lệ."));
            return;
        }

        DocumentReference orderRef = firestore.collection("orders").document(String.valueOf(orderId));
        Date requestTime = new Date();
        orderRef.get().addOnSuccessListener(order -> {
            if (!order.exists() || !normalizedEmail.equals(order.getString("userEmail"))
                    || !OrderReturnRules.isEligible(
                            order.getString("status"), timestampDate(order, "deliveredAt"),
                            order.getString("orderDate"), requestTime)) {
                callback.onError(new IllegalStateException(
                        "Chỉ có thể hoàn trả đơn đã giao trong vòng 7 ngày."));
                return;
            }

            orderRef.collection("items").get().addOnSuccessListener(items ->
                    PointsRepository.getInstance().loadSpendableLots(
                            normalizedEmail, new PointsRepository.SpendLotsCallback() {
                                @Override
                                public void onResult(List<DocumentSnapshot> lots) {
                                    applyOrderReturn(orderId, normalizedEmail, reasonType, reason,
                                            requestTime, order, items, lots, callback);
                                }

                                @Override
                                public void onError(Exception error) {
                                    callback.onError(error);
                                }
                            }))
                    .addOnFailureListener(callback::onError);
        }).addOnFailureListener(callback::onError);
    }

    private void applyOrderReturn(long orderId, String email, String reasonType, String reason, Date requestTime,
            DocumentSnapshot originalOrder, QuerySnapshot itemDocuments, List<DocumentSnapshot> pointLots,
            OrderReturnCallback callback) {
        if (itemDocuments.isEmpty()) {
            callback.onError(new IllegalStateException("Đơn hàng chưa có sản phẩm để hoàn trả."));
            return;
        }

        DocumentReference orderRef = originalOrder.getReference();
        List<DocumentReference> itemRefs = new ArrayList<>();
        Map<String, OrderItem> expectedItems = new HashMap<>();
        Map<Integer, Long> quantities = new HashMap<>();
        Map<Integer, DocumentReference> productRefs = new HashMap<>();
        for (DocumentSnapshot itemDocument : itemDocuments.getDocuments()) {
            OrderItem item = toOrderItem(itemDocument);
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) {
                callback.onError(new IllegalStateException("Sản phẩm trong đơn hàng không hợp lệ."));
                return;
            }
            try {
                quantities.merge(item.getProductId(), (long) item.getQuantity(), Math::addExact);
            } catch (ArithmeticException error) {
                callback.onError(new IllegalStateException("Số lượng hoàn trả không hợp lệ.", error));
                return;
            }
            itemRefs.add(itemDocument.getReference());
            expectedItems.put(itemDocument.getId(), item);
            productRefs.put(item.getProductId(),
                    firestore.collection("products").document(String.valueOf(item.getProductId())));
        }

        Map<Integer, DocumentReference> soldOrderRefs = new HashMap<>();
        for (Integer productId : quantities.keySet()) {
            soldOrderRefs.put(productId, productRefs.get(productId)
                    .collection("soldOrders").document(String.valueOf(orderId)));
        }

        String productVoucherId = originalOrder.getString("productVoucherId");
        String shippingVoucherId = originalOrder.getString("shippingVoucherId");
        DocumentReference productVoucherRef = isBlank(productVoucherId)
                ? null
                : VoucherRepository.userVoucherDocument(firestore, email, productVoucherId);
        DocumentReference shippingVoucherRef = isBlank(shippingVoucherId)
                ? null
                : VoucherRepository.userVoucherDocument(firestore, email, shippingVoucherId);
        DocumentReference notificationRef = firestore.collection("notifications")
                .document("order_" + orderId + "_returned");

        List<String> reviewIds = new ArrayList<>();
        for (Integer productId : quantities.keySet()) {
            reviewIds.add(ReviewRules.documentId((int) orderId, productId));
        }

        firestore.runTransaction(transaction -> {
            DocumentSnapshot order = transaction.get(orderRef);
            if (!order.exists() || !email.equals(order.getString("userEmail"))
                    || !OrderReturnRules.isEligible(
                            order.getString("status"), timestampDate(order, "deliveredAt"),
                            order.getString("orderDate"), requestTime)) {
                throw new IllegalStateException("Đơn hàng không còn đủ điều kiện hoàn trả.");
            }

            Map<String, DocumentSnapshot> currentItems = new HashMap<>();
            for (DocumentReference itemRef : itemRefs) {
                currentItems.put(itemRef.getId(), transaction.get(itemRef));
            }
            Map<Integer, DocumentSnapshot> products = new HashMap<>();
            Map<Integer, DocumentSnapshot> soldMarkers = new HashMap<>();
            for (Integer productId : quantities.keySet()) {
                products.put(productId, transaction.get(productRefs.get(productId)));
                soldMarkers.put(productId, transaction.get(soldOrderRefs.get(productId)));
            }
            DocumentSnapshot productVoucher = productVoucherRef == null
                    ? null
                    : transaction.get(productVoucherRef);
            DocumentSnapshot shippingVoucher = shippingVoucherRef == null
                    ? null
                    : transaction.get(shippingVoucherRef);
            DocumentSnapshot existingNotification = transaction.get(notificationRef);

            for (DocumentReference itemRef : itemRefs) {
                DocumentSnapshot item = currentItems.get(itemRef.getId());
                if (!item.exists()) {
                    throw new IllegalStateException("Chi tiết đơn hàng đã thay đổi.");
                }
                OrderItem expected = expectedItems.get(itemRef.getId());
                OrderItem current = toOrderItem(item);
                if (expected == null || expected.getProductId() != current.getProductId()
                        || expected.getQuantity() != current.getQuantity()) {
                    throw new IllegalStateException("Chi tiết đơn hàng đã thay đổi.");
                }
            }
            for (Map.Entry<Integer, Long> entry : quantities.entrySet()) {
                DocumentSnapshot product = products.get(entry.getKey());
                if (!product.exists() || product.getLong("stock") == null) {
                    throw new IllegalStateException("Không tìm thấy tồn kho sản phẩm cần hoàn.");
                }
                Math.addExact(product.getLong("stock"), entry.getValue());
            }

            PointsRepository.getInstance().applyOrderReturnInTransaction(
                    transaction, email, orderId, longValue(order, "pointsUsed"), reviewIds, pointLots);

            for (Map.Entry<Integer, Long> entry : quantities.entrySet()) {
                int productId = entry.getKey();
                DocumentSnapshot product = products.get(productId);
                long restoredStock = Math.addExact(product.getLong("stock"), entry.getValue());
                DocumentSnapshot soldMarker = soldMarkers.get(productId);
                if (soldMarker.exists()) {
                    long soldQuantity = soldMarker.getLong("quantity") == null
                            ? entry.getValue()
                            : Math.max(0, soldMarker.getLong("quantity"));
                    long soldCount = Math.max(0, longValue(product, "soldCount") - soldQuantity);
                    transaction.update(productRefs.get(productId),
                            "stock", restoredStock,
                            "soldCount", soldCount);
                    transaction.delete(soldOrderRefs.get(productId));
                } else {
                    transaction.update(productRefs.get(productId), "stock", restoredStock);
                }
            }

            restoreVoucherIfValid(transaction, productVoucherRef, productVoucher, orderId, requestTime);
            restoreVoucherIfValid(transaction, shippingVoucherRef, shippingVoucher, orderId, requestTime);

            Map<String, Object> orderUpdate = new HashMap<>();
            orderUpdate.put("status", OrderReturnRules.STATUS_RETURNED);
            orderUpdate.put("returnStatus", "accepted");
            orderUpdate.put("returnReasonType", reasonType);
            orderUpdate.put("returnReason", displayReturnReason(reasonType, reason));
            orderUpdate.put("returnRequestedAt", FieldValue.serverTimestamp());
            orderUpdate.put("returnAcceptedAt", FieldValue.serverTimestamp());
            orderUpdate.put("returnedAt", FieldValue.serverTimestamp());
            orderUpdate.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(orderRef, orderUpdate);

            if (!existingNotification.exists()) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("userEmail", email);
                notification.put("type", "order_return");
                notification.put("title", "Đơn hàng đã hoàn trả");
                notification.put("message", "Yêu cầu hoàn trả đơn hàng #DH"
                        + String.format(Locale.getDefault(), "%05d", orderId) + " đã được chấp nhận.");
                notification.put("orderId", orderId);
                notification.put("status", OrderReturnRules.STATUS_RETURNED);
                notification.put("createdAt", FieldValue.serverTimestamp());
                notification.put("isRead", false);
                transaction.set(notificationRef, notification);
            }
            return null;
        }).addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    private void restoreVoucherIfValid(com.google.firebase.firestore.Transaction transaction,
            DocumentReference voucherRef, DocumentSnapshot voucher, long orderId, Date now)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        if (voucherRef == null || voucher == null || !voucher.exists()
                || !"used".equals(voucher.getString("state"))
                || longValue(voucher, "orderId") != orderId) {
            return;
        }
        Timestamp expiresAt = voucher.getTimestamp("expiresAt");
        if (expiresAt == null || !expiresAt.toDate().after(now)) {
            return;
        }
        transaction.update(voucherRef,
                "state", "saved",
                "usedAt", FieldValue.delete(),
                "orderId", FieldValue.delete(),
                "discountAmount", FieldValue.delete());
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

    private Date timestampDate(DocumentSnapshot document, String field) {
        Timestamp timestamp = document.getTimestamp(field);
        return timestamp == null ? null : timestamp.toDate();
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
        if (!PhoneNumberRules.isValid(input) || target == null) {
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

    public void checkUsernameExists(String username, String currentEmail, UsernameCallback callback) {
        firestore.collection("users").whereEqualTo("fullName", username.trim()).get().addOnSuccessListener(s -> {
            boolean isDuplicate = !s.isEmpty()
                    && !(s.size() == 1 && s.getDocuments().get(0).getId().equals(normalizeEmail(currentEmail)));
            callback.onResult(isDuplicate);
        }).addOnFailureListener(callback::onError);
    }

    public void updateUser(String email, Map<String, Object> values, BooleanCallback callback) {
        firestore.collection("users").document(normalizeEmail(email)).set(values, SetOptions.merge())
                .addOnCompleteListener(t -> callback.onResult(t.isSuccessful()));
    }

    public void savePushToken(String email, String token, BooleanCallback callback) {
        if (token == null || token.trim().isEmpty()) {
            callback.onResult(false);
            return;
        }

        String normalized = normalizeEmail(email);
        Map<String, Object> data = new HashMap<>();
        data.put("userEmail", normalized);
        data.put("token", token);
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("users").document(normalized).collection("fcmTokens")
                .document(tokenDocumentId(token)).set(data, SetOptions.merge())
                .addOnCompleteListener(task -> callback.onResult(task.isSuccessful()));
    }

    public void removePushToken(String email, String token, BooleanCallback callback) {
        if (token == null || token.trim().isEmpty()) {
            callback.onResult(false);
            return;
        }

        firestore.collection("users").document(normalizeEmail(email)).collection("fcmTokens")
                .document(tokenDocumentId(token)).delete()
                .addOnCompleteListener(task -> callback.onResult(task.isSuccessful()));
    }

    private String tokenDocumentId(String token) {
        return Base64.encodeToString(token.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private Product toProduct(DocumentSnapshot d) {
        int stock = (d.getLong("stock") != null) ? d.getLong("stock").intValue() : 0;
        Object rawSpecifications = d.get("specifications");
        Map<String, String> specifications = new HashMap<>();
        if (rawSpecifications instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawSpecifications).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    specifications.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }
        double averageRating = d.get("averageRating") instanceof Number
                ? ((Number) d.get("averageRating")).doubleValue()
                : 0.0;
        Timestamp createdAt = d.getTimestamp("createdAt");
        return new Product(value(d, "id"), d.getString("name"), d.getString("category"), longValue(d, "price"),
                d.getString("description"), d.getString("icon"), stock, value(d, "soldCount"), averageRating,
                value(d, "reviewCount"), specifications, createdAt == null ? null : createdAt.toDate());
    }

    private Order toOrder(DocumentSnapshot d) {
        Order order = new Order(value(d, "id"), d.getString("userEmail"), d.getString("receiverName"),
                d.getString("receiverPhone"), d.getString("receiverAddress"), d.getString("note"),
                d.getString("paymentMethod"), longValue(d, "totalAmount"), d.getString("orderDate"),
                d.getString("status"));
        Long itemsSubtotal = d.getLong("itemsSubtotal");
        Long shippingFee = d.getLong("shippingFee");
        order.setItemsSubtotal(itemsSubtotal == null ? -1 : itemsSubtotal);
        order.setProductDiscount(longValue(d, "productDiscount"));
        order.setShippingFee(shippingFee == null ? -1 : shippingFee);
        order.setShippingDiscount(longValue(d, "shippingDiscount"));
        order.setPointsDiscount(longValue(d, "pointsDiscount"));
        Timestamp deliveredAt = d.getTimestamp("deliveredAt");
        order.setDeliveredAt(deliveredAt == null ? null : deliveredAt.toDate());
        order.setReturnReason(d.getString("returnReason"));
        return order;
    }

    private OrderItem toOrderItem(DocumentSnapshot d) {
        return new OrderItem(value(d, "id"), value(d, "orderId"), value(d, "productId"), d.getString("productName"),
                d.getString("productIcon"), longValue(d, "price"), value(d, "quantity"), longValue(d, "subtotal"));
    }

    private User toUser(DocumentSnapshot d) {
        return new User(value(d, "id"), d.getString("fullName"), d.getString("email"), d.getString("avatar"),
                d.getString("phone"), d.getString("address"));
    }

    private AppNotification toNotification(DocumentSnapshot document) {
        Long orderId = document.getLong("orderId");
        return new AppNotification(document.getId(), document.getString("type"), document.getString("title"),
                document.getString("message"), orderId == null ? 0 : orderId.intValue(),
                document.getTimestamp("createdAt"), Boolean.TRUE.equals(document.getBoolean("isRead")));
    }

    private int value(DocumentSnapshot d, String key) {
        Long value = d.getLong(key);
        return value == null ? 0 : value.intValue();
    }

    private long longValue(DocumentSnapshot d, String key) {
        Long value = d.getLong(key);
        return value == null ? 0 : value;
    }

}
