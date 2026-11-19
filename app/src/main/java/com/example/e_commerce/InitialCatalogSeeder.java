package com.example.e_commerce;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InitialCatalogSeeder {

    private static final String SEED_MARKER = "demoSeed";

    private InitialCatalogSeeder() {
    }

    public static Task<Boolean> seedCatalogAndVouchersIfNeeded(FirebaseFirestore firestore) {
        TaskCompletionSource<Boolean> completion = new TaskCompletionSource<>();

        firestore.collection("appMetadata").document(SEED_MARKER).get()
                .addOnSuccessListener(marker -> {
                    if (marker.exists()) {
                        completion.setResult(false);
                        return;
                    }
                    seedAll(firestore, completion);
                })
                .addOnFailureListener(completion::setException);

        return completion.getTask();
    }

    private static void seedAll(FirebaseFirestore firestore,
            TaskCompletionSource<Boolean> completion) {
        List<Map<String, Object>> operations = new ArrayList<>();

        addCatalogData(operations);
        addAccountsData(firestore, operations);

        commitOperations(firestore, operations, 0, completion);
    }

    private static void addCatalogData(List<Map<String, Object>> operations) {
        for (Product product : catalogProducts()) {
            operations.add(document(firestoreDocumentKey("products", product.getId()),
                    productData(product), false));
        }

        for (Map<String, Object> category : catalogCategories()) {
            operations.add(document(firestoreDocumentKey("categories", category.get("id")),
                    category, false));
        }

        for (Map<String, Object> voucher : catalogVouchers()) {
            operations.add(document(firestoreDocumentKey("vouchers", voucher.get("id")),
                    voucher, false));
        }

        for (SeedReview review : initialReviews()) {
            operations.add(document(firestoreDocumentKey("reviews", review.id),
                    review.data, false));
        }
    }

    private static void addAccountsData(FirebaseFirestore firestore,
            List<Map<String, Object>> operations) {
        DemoAccount[] accounts = demoAccounts();
        String[] statuses = {
                "Đặt hàng thành công",
                "Chờ xác nhận",
                "Đang giao",
                "Đã giao",
                "Đã hoàn trả"
        };
        Date now = new Date();
        Timestamp nowTimestamp = new Timestamp(now);

        for (int accountIndex = 0; accountIndex < accounts.length; accountIndex++) {
            DemoAccount account = accounts[accountIndex];
            DocumentReference userRef = firestore.collection("users").document(account.email);
            long points = 2500L + accountIndex * 750L;

            Map<String, Object> user = new HashMap<>();
            user.put("id", accountIndex + 1);
            user.put("uid", accountUid(accountIndex, account));
            user.put("fullName", account.name);
            user.put("email", account.email);
            user.put("avatar", "👤");
            user.put("phone", account.phone);
            user.put("address", account.address);
            user.put("pointsBalance", points);
            user.put("pointsDebt", 0L);
            user.put("attendanceDay", 0L);
            user.put("lastAttendanceDate", "");
            user.put("pointsUpdatedAt", FieldValue.serverTimestamp());
            user.put("demoAccount", true);
            operations.add(document(firestoreDocumentKey("users", account.email),
                    user, false));

            Timestamp expiresAt = new Timestamp(daysFromNow(30));
            operations.add(document(firestoreDocumentKey(
                            "users/" + account.email + "/pointLots",
                            "demo-lot-" + (accountIndex + 1)),
                    pointLot(points, nowTimestamp, expiresAt), false));
            operations.add(document(firestoreDocumentKey(
                            "users/" + account.email + "/pointTransactions",
                            "demo-grant-" + (accountIndex + 1)),
                    pointTransaction(points, nowTimestamp, expiresAt), false));

            addSavedAndUsedVouchers(operations, account.email, nowTimestamp, expiresAt);
            addCarts(operations, account.email, accountIndex);
            addOrders(operations, account, accountIndex, statuses, now);
        }

        addReviewOnlyOrders(operations, accounts, nowTimestamp);
    }

    private static void addSavedAndUsedVouchers(List<Map<String, Object>> operations,
            String email, Timestamp now, Timestamp expiresAt) {
        String[] voucherIds = {
                "ITEM_5K_ALL",
                "SHIP_FREE_ALL",
                "ITEM_10P_HEADPHONE",
                "ITEM_20P_ALL",
                "SHIP_70P_ALL"
        };

        for (int index = 0; index < voucherIds.length; index++) {
            Map<String, Object> voucher = catalogVoucherById(voucherIds[index]);
            if (voucher == null) {
                continue;
            }

            voucher.put("state", index < 3 ? "saved" : "used");
            voucher.put("savedAt", now);
            voucher.put("expiresAt", expiresAt);
            if (index >= 3) {
                voucher.put("usedAt", now);
            }
            operations.add(document(firestoreDocumentKey(
                            "users/" + email + "/vouchers",
                            voucherIds[index]),
                    voucher, false));
        }
    }

    private static void addCarts(List<Map<String, Object>> operations,
            String email, int accountIndex) {
        List<Product> products = catalogProducts();
        for (int index = 0; index < 3; index++) {
            Product product = products.get((accountIndex * 3 + index) % products.size());
            Map<String, Object> cart = new HashMap<>();
            cart.put("userEmail", email);
            cart.put("productId", product.getId());
            cart.put("quantity", index + 1);
            operations.add(document(firestoreDocumentKey("carts",
                    email + "_" + product.getId()), cart, false));
        }
    }

    private static void addOrders(List<Map<String, Object>> operations,
            DemoAccount account, int accountIndex, String[] statuses, Date now) {
        List<Product> products = catalogProducts();

        for (int statusIndex = 0; statusIndex < statuses.length; statusIndex++) {
            String status = statuses[statusIndex];
            int orderId = 100000 + accountIndex * 100 + statusIndex + 1;
            Product product = products.get(
                    (accountIndex * 3 + statusIndex) % products.size());
            int quantity = statusIndex + 1;
            long originalSubtotal = product.getPrice() * quantity;
            long productDiscount = "Đã giao".equals(status)
                    ? Math.min(5000L * quantity, originalSubtotal)
                    : 0L;
            long shippingFee = 30000L;
            long shippingDiscount = "Đang giao".equals(status) ? shippingFee : 0L;
            long pointsUsed = "Đặt hàng thành công".equals(status) ? 500L : 0L;
            long total = originalSubtotal - productDiscount
                    + shippingFee - shippingDiscount - pointsUsed;

            Date orderDate = new Date(
                    now.getTime() - (statuses.length - statusIndex) * 24L * 60 * 60 * 1000);

            Map<String, Object> order = new HashMap<>();
            order.put("id", orderId);
            order.put("userEmail", account.email);
            order.put("receiverName", account.name);
            order.put("receiverPhone", account.phone);
            order.put("receiverAddress", account.address);
            order.put("note", statusIndex == 0 ? "Giao trong giờ hành chính." : "");
            order.put("paymentMethod", "Thanh toán khi nhận hàng (COD)");
            order.put("itemsSubtotal", originalSubtotal);
            order.put("productDiscount", productDiscount);
            order.put("shippingFee", shippingFee);
            order.put("shippingDiscount", shippingDiscount);
            order.put("pointsUsed", pointsUsed);
            order.put("pointsDiscount", pointsUsed);
            order.put("productVoucherId", productDiscount > 0 ? "ITEM_5K_ALL" : "");
            order.put("productVoucherCode", productDiscount > 0 ? "TECH5K" : "");
            order.put("shippingVoucherId", shippingDiscount > 0 ? "SHIP_FREE_ALL" : "");
            order.put("shippingVoucherCode", shippingDiscount > 0 ? "SHIPFREE" : "");
            order.put("totalAmount", total);
            order.put("orderDate", formatDate(orderDate));
            order.put("status", status);
            order.put("createdAt", new Timestamp(orderDate));
            order.put("updatedAt", new Timestamp(now));
            if ("Đã giao".equals(status) || "Đã hoàn trả".equals(status)) {
                order.put("deliveredAt", new Timestamp(orderDate));
            }
            if ("Đã hoàn trả".equals(status)) {
                order.put("returnStatus", "accepted");
                order.put("returnReasonType", "wrong_model");
                order.put("returnReason", "Không đúng mẫu");
            }
            operations.add(document(firestoreDocumentKey("orders", orderId),
                    order, false));

            operations.add(document(firestoreDocumentKey(
                            "orders/" + orderId + "/items", 1),
                    orderItem(orderId, product, quantity,
                            originalSubtotal, productDiscount), false));

            operations.add(document(firestoreDocumentKey("notifications",
                            "order_" + orderId + "_placed"),
                    statusNotification(account.email, orderId,
                            "Đặt hàng thành công",
                            "Đơn hàng #" + orderCode(orderId) + " đã được tiếp nhận.",
                            "order_placed", null, now), false));

            operations.add(document(firestoreDocumentKey("notifications",
                            "order_" + orderId + "_status_" + Integer.toHexString(status.hashCode())),
                    statusNotification(account.email, orderId,
                            "Cập nhật đơn hàng",
                            "Đơn hàng #" + orderCode(orderId)
                                    + " chuyển sang trạng thái: " + status + ".",
                            "order_status", status, now), false));

            if ("Đã giao".equals(status)) {
                operations.add(document(firestoreDocumentKey(
                                "products/" + product.getId() + "/soldOrders",
                                orderId),
                        soldOrder(orderId, quantity, orderDate), false));

                String reviewId = orderId + "_" + product.getId();
                operations.add(document(firestoreDocumentKey("reviews", reviewId),
                        orderReview(orderId, product, account, accountUid(accountIndex, account),
                                statusIndex, orderDate), false));
            }
        }
    }

    private static void addReviewOnlyOrders(List<Map<String, Object>> operations,
            DemoAccount[] accounts, Timestamp now) {
        List<Product> products = catalogProducts();

        for (int productIndex = 0; productIndex < products.size(); productIndex++) {
            Product product = products.get(productIndex);
            DemoAccount account = accounts[productIndex % accounts.length];
            int accountIndex = productIndex % accounts.length;
            int orderId = 200000 + product.getId();
            int quantity = 1 + productIndex % 3;
            long subtotal = product.getPrice() * quantity;
            Date deliveredDate = new Date(
                    now.toDate().getTime() - (productIndex + 1) * 24L * 60 * 60 * 1000);

            Map<String, Object> order = new HashMap<>();
            order.put("id", orderId);
            order.put("userEmail", account.email);
            order.put("receiverName", account.name);
            order.put("receiverPhone", account.phone);
            order.put("receiverAddress", account.address);
            order.put("note", "Đơn đánh giá mẫu.");
            order.put("paymentMethod", "Thanh toán khi nhận hàng (COD)");
            order.put("itemsSubtotal", subtotal);
            order.put("productDiscount", 0L);
            order.put("shippingFee", 30000L);
            order.put("shippingDiscount", 30000L);
            order.put("pointsUsed", 0L);
            order.put("pointsDiscount", 0L);
            order.put("productVoucherId", "");
            order.put("productVoucherCode", "");
            order.put("shippingVoucherId", "SHIP_FREE_ALL");
            order.put("shippingVoucherCode", "SHIPFREE");
            order.put("totalAmount", subtotal);
            order.put("orderDate", formatDate(deliveredDate));
            order.put("status", "Đã giao");
            order.put("createdAt", new Timestamp(deliveredDate));
            order.put("updatedAt", now);
            order.put("deliveredAt", new Timestamp(deliveredDate));
            operations.add(document(firestoreDocumentKey("orders", orderId),
                    order, false));

            operations.add(document(firestoreDocumentKey(
                            "orders/" + orderId + "/items", 1),
                    orderItem(orderId, product, quantity, subtotal, 0L), false));

            String reviewId = orderId + "_" + product.getId();
            operations.add(document(firestoreDocumentKey("reviews", reviewId),
                    productReview(orderId, product, account,
                            accountUid(accountIndex, account), productIndex, deliveredDate),
                    false));

            operations.add(document(firestoreDocumentKey(
                            "products/" + product.getId() + "/soldOrders",
                            orderId),
                    soldOrder(orderId, quantity, deliveredDate), false));

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("soldCount", demoSoldCount(product.getId()) + quantity);
            metrics.put("averageRating", demoAverageRating(product.getId()));
            metrics.put("reviewCount", demoReviewCount(product.getId()) + 1);
            metrics.put("ratingSum",
                    demoAverageRating(product.getId())
                            * (demoReviewCount(product.getId()) + 1));
            operations.add(document(firestoreDocumentKey("products", product.getId()),
                    metrics, true));
        }
    }

    private static void commitOperations(FirebaseFirestore firestore,
            List<Map<String, Object>> operations, int start,
            TaskCompletionSource<Boolean> completion) {
        if (start >= operations.size()) {
            firestore.collection("appMetadata").document(SEED_MARKER).set(seedMarker())
                    .addOnSuccessListener(ignored -> completion.setResult(true))
                    .addOnFailureListener(completion::setException);
            return;
        }

        WriteBatch batch = firestore.batch();
        int end = Math.min(start + 450, operations.size());
        for (int index = start; index < end; index++) {
            writeOperation(firestore, batch, operations.get(index));
        }
        batch.commit()
                .addOnSuccessListener(ignored ->
                        commitOperations(firestore, operations, end, completion))
                .addOnFailureListener(completion::setException);
    }

    private static void writeOperation(FirebaseFirestore firestore, WriteBatch batch,
            Map<String, Object> operation) {
        String collection = (String) operation.get("_collection");
        Object rawId = operation.get("_id");
        Object data = operation.get("_data");
        boolean merge = Boolean.TRUE.equals(operation.get("_merge"));
        DocumentReference reference = firestore.collection(collection)
                .document(String.valueOf(rawId));
        if (merge) {
            batch.set(reference, (Map<String, Object>) data,
                    com.google.firebase.firestore.SetOptions.merge());
        } else {
            batch.set(reference, (Map<String, Object>) data);
        }
    }

    private static Map<String, Object> document(String collectionAndId,
            Map<String, Object> data, boolean merge) {
        Map<String, Object> operation = new HashMap<>();
        int separator = collectionAndId.indexOf('|');
        operation.put("_collection", collectionAndId.substring(0, separator));
        operation.put("_id", collectionAndId.substring(separator + 1));
        operation.put("_data", data);
        operation.put("_merge", merge);
        return operation;
    }

    private static String firestoreDocumentKey(String collection, Object id) {
        return collection + "|" + String.valueOf(id);
    }

    private static Map<String, Object> pointLot(long points, Timestamp receivedAt,
            Timestamp expiresAt) {
        Map<String, Object> lot = new HashMap<>();
        lot.put("amount", points);
        lot.put("remainingAmount", points);
        lot.put("receivedAt", receivedAt);
        lot.put("expiresAt", expiresAt);
        lot.put("source", "demo_seed");
        lot.put("referenceId", "demo_seed");
        return lot;
    }

    private static Map<String, Object> pointTransaction(long points, Timestamp createdAt,
            Timestamp expiresAt) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("type", "earn");
        transaction.put("amount", points);
        transaction.put("netAmount", points);
        transaction.put("offsetAmount", 0L);
        transaction.put("balanceAfter", points);
       transaction.put("debtAfter", 0L);
        transaction.put("description", "Xu demo khởi tạo.");
        transaction.put("source", "demo_seed");
        transaction.put("referenceId", "demo_seed");
        transaction.put("createdAt", createdAt);
        transaction.put("expiresAt", expiresAt);
        return transaction;
    }

    private static DemoAccount[] demoAccounts() {
        return new DemoAccount[]{
                new DemoAccount("demo01@example.com", "Nguyen Van A",
                        "0912345001", "12 Nguyen Hue, Quan 1, TP. Ho Chi Minh"),
                new DemoAccount("demo02@example.com", "Tran Thi B",
                        "0912345002", "45 Le Loi, Quan 1, TP. Ho Chi Minh"),
                new DemoAccount("demo03@example.com", "Le Minh C",
                        "0912345003", "78 Dien Bien Phu, Quan Binh Thanh, TP. Ho Chi Minh"),
                new DemoAccount("demo04@example.com", "Pham Hoang D",
                        "0912345004", "23 Cach Mang Thang Tam, Quan 3, TP. Ho Chi Minh"),
                new DemoAccount("demo05@example.com", "Vu Thanh H",
                        "0912345005", "56 Phan Xich Long, Quan Phu Nhuan, TP. Ho Chi Minh")
        };
    }

    private static String accountUid(int accountIndex, DemoAccount account) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null
                && account.email.equalsIgnoreCase(
                        FirebaseAuth.getInstance().getCurrentUser().getEmail() == null
                                ? ""
                                : FirebaseAuth.getInstance().getCurrentUser().getEmail())) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return "initial-customer-" + (accountIndex + 1);
    }

    private static List<Product> catalogProducts() {
        List<Product> products = new ArrayList<>();
        products.add(product(1, "Samsung Galaxy A16 5G 128GB", "Điện thoại", 5990000,
                "Điện thoại 5G màn hình Super AMOLED 6,7 inch 90Hz, camera chính 50MP và pin 5.000mAh.",
                "📱", 38,
                specs("Màn hình", "Super AMOLED 6,7 inch 90Hz", "RAM", "8GB",
                        "Bộ nhớ", "128GB", "Camera chính", "50MP", "Pin", "5.000mAh")));
        products.add(product(2, "Samsung Galaxy A55 5G 256GB", "Điện thoại", 12990000,
                "Điện thoại 5G với màn hình Super AMOLED 120Hz, camera chính 50MP chống rung quang học.",
                "📱", 21,
                specs("Màn hình", "Super AMOLED 6,6 inch 120Hz", "Chip", "Exynos 1480",
                        "RAM", "8GB", "Bộ nhớ", "256GB", "Camera chính", "50MP OIS")));
        products.add(product(3, "Xiaomi 14T 512GB", "Điện thoại", 19990000,
                "Điện thoại Xiaomi 14T camera Leica, màn hình AMOLED 144Hz và chip Dimensity 8300-Ultra.",
                "📱", 12,
                specs("Màn hình", "AMOLED 6,67 inch 144Hz", "Chip", "Dimensity 8300-Ultra",
                        "RAM", "12GB", "Bộ nhớ", "512GB", "Camera chính", "Leica 50MP OIS")));
        products.add(product(4, "ASUS Vivobook 15 X1504VA Core i5 16GB 512GB", "Laptop", 14990000,
                "Laptop 15,6 inch Full HD dùng Intel Core i5-1335U, RAM 16GB và SSD 512GB.",
                "💻", 17,
                specs("Màn hình", "15,6 inch Full HD", "CPU", "Intel Core i5-1335U",
                        "RAM", "16GB", "Ổ cứng", "512GB SSD")));
        products.add(product(5, "Apple MacBook Air 13-inch M3 16GB 256GB", "Laptop", 22990000,
                "MacBook Air 13,6 inch Liquid Retina dùng chip Apple M3.",
                "💻", 10,
                specs("Màn hình", "Liquid Retina 13,6 inch", "Chip", "Apple M3",
                        "Bộ nhớ thống nhất", "16GB", "Ổ cứng", "256GB SSD")));
        products.add(product(6, "Lenovo LOQ 15IRX9 Core i5 RTX 4050", "Laptop", 31990000,
                "Laptop gaming màn hình 144Hz, Intel Core i5-13450HX và RTX 4050.",
                "💻", 7,
                specs("Màn hình", "15,6 inch Full HD 144Hz", "CPU", "Intel Core i5-13450HX",
                        "RAM", "16GB", "Đồ họa", "GeForce RTX 4050 6GB", "Ổ cứng", "512GB SSD")));
        products.add(product(7, "Samsung Galaxy Buds FE", "Tai nghe", 1290000,
                "Tai nghe true wireless có chống ồn chủ động.",
                "🎧", 52,
                specs("Kết nối", "Bluetooth", "Chống ồn", "ANC",
                        "Thời lượng nghe", "Tối đa 6 giờ", "Chống nước", "IPX2")));
        products.add(product(8, "Samsung Galaxy Buds3", "Tai nghe", 2490000,
                "Tai nghe true wireless hỗ trợ chống ồn chủ động.",
                "🎧", 29,
                specs("Kết nối", "Bluetooth 5.4", "Chống ồn", "ANC",
                        "Thời lượng nghe", "Tối đa 5 giờ", "Chống nước", "IP57")));
        products.add(product(9, "Sony WH-1000XM5", "Tai nghe", 6990000,
                "Tai nghe chụp tai chống ồn chủ động và kết nối đa điểm.",
                "🎧", 14,
                specs("Kiểu tai nghe", "Chụp tai không dây", "Chống ồn", "ANC",
                        "Thời lượng pin", "Tối đa 30 giờ", "Kết nối", "Bluetooth")));
        products.add(product(10, "Xiaomi Smart Band 9 Pro", "Đồng hồ thông minh", 1990000,
                "Vòng đeo thông minh màn hình AMOLED 1,74 inch.",
                "⌚", 34,
                specs("Màn hình", "AMOLED 1,74 inch", "GPS", "Tích hợp",
                        "Chống nước", "5 ATM", "Thời lượng pin", "Tối đa 21 ngày")));
        products.add(product(11, "Samsung Galaxy Watch7 44mm Bluetooth", "Đồng hồ thông minh", 4590000,
                "Đồng hồ thông minh màn hình Super AMOLED.",
                "⌚", 19,
                specs("Màn hình", "Super AMOLED 1,5 inch", "Chip", "Exynos W1000",
                        "Kết nối", "Bluetooth", "Chống nước", "5 ATM")));
        products.add(product(12, "Garmin Forerunner 265", "Đồng hồ thông minh", 8990000,
                "Đồng hồ chạy bộ GPS với màn hình AMOLED.",
                "⌚", 8,
                specs("Màn hình", "AMOLED 1,3 inch", "GPS", "Đa băng tần",
                        "Chống nước", "5 ATM", "Thời lượng pin", "Tối đa 13 ngày")));
        products.add(product(13, "Anker 735 Charger GaNPrime 65W", "Phụ kiện & Sạc", 790000,
                "Củ sạc GaN 65W nhỏ gọn.",
                "🔌", 64,
                specs("Công suất", "Tối đa 65W", "Cổng", "2 USB-C, 1 USB-A",
                        "Công nghệ", "GaNPrime")));
        products.add(product(14, "Anker 325 Power Bank PowerCore 20K", "Phụ kiện & Sạc", 1190000,
                "Pin dự phòng dung lượng 20.000mAh.",
                "🔋", 47,
                specs("Dung lượng", "20.000mAh", "Cổng ra", "2 USB-A",
                        "Cổng vào", "USB-C")));
        products.add(product(15, "UGREEN Revodok 8-in-1 USB-C Hub", "Phụ kiện & Sạc", 1490000,
                "Bộ chuyển đổi USB-C đa cổng với HDMI 4K.",
                "🧩", 25,
                specs("Cổng", "HDMI, USB-A, USB-C, SD/microSD",
                        "HDMI", "Tối đa 4K", "Sạc PD", "Tối đa 100W")));
        return products;
    }

    private static Product product(int id, String name, String category, long price,
            String description, String icon, int stock, Map<String, String> specifications) {
        return new Product(id, name, category, price, description, icon, stock,
                demoSoldCount(id), demoAverageRating(id), demoReviewCount(id), specifications);
    }

    private static Map<String, String> specs(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }

    private static List<Map<String, Object>> catalogCategories() {
        return Arrays.asList(
                category("phones", "Điện thoại", 1),
                category("laptops", "Laptop", 2),
                category("headphones", "Tai nghe", 3),
                category("smartwatches", "Đồng hồ thông minh", 4),
                category("accessories-charging", "Phụ kiện & Sạc", 5));
    }

    private static Map<String, Object> category(String id, String name, int sortOrder) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("sortOrder", sortOrder);
        data.put("isActive", true);
        return data;
    }

    private static List<Map<String, Object>> catalogVouchers() {
        List<Map<String, Object>> vouchers = new ArrayList<>();
        Date campaignEnd = daysFromNow(180);
        vouchers.add(voucher("ITEM_5K_ALL", "TECH5K", "Giảm 5.000đ mỗi sản phẩm",
                "Giảm 5.000đ cho từng sản phẩm đủ điều kiện trong toàn shop.",
                "product", "fixed_per_item", 5000, "all", "", 0, 0, 30, campaignEnd, 1));
        vouchers.add(voucher("ITEM_20P_ALL", "TECH20", "Giảm 20% toàn shop",
                "Giảm 20% trên từng sản phẩm đủ điều kiện, tối đa 2.000.000đ.",
                "product", "percentage_per_item", 20, "all", "", 0, 2000000, 30, campaignEnd, 2));
        vouchers.add(voucher("ITEM_10P_HEADPHONE", "AUDIO10", "Giảm 10% tai nghe",
                "Áp dụng cho các sản phẩm thuộc danh mục Tai nghe.",
                "product", "percentage_per_item", 10, "category", "Tai nghe", 0, 1000000, 30, campaignEnd, 3));
        vouchers.add(voucher("ITEM_15P_ACCESSORY", "GEAR15", "Giảm 15% phụ kiện & sạc",
                "Áp dụng cho danh mục Phụ kiện & Sạc.",
                "product", "percentage_per_item", 15, "category", "Phụ kiện & Sạc", 0, 500000, 30, campaignEnd, 4));
        vouchers.add(voucher("ITEM_LAPTOP_500K", "LAPTOP500", "Giảm 500.000đ laptop",
                "Giảm 500.000đ trên từng laptop khi đơn laptop đạt từ 15.000.000đ.",
                "product", "fixed_per_item", 500000, "category", "Laptop", 15000000, 1000000, 30, campaignEnd, 5));
        vouchers.add(voucher("SHIP_FREE_ALL", "SHIPFREE", "Miễn phí vận chuyển",
                "Miễn phí vận chuyển tối đa 50.000đ.",
                "shipping", "free_shipping", 100, "all", "", 0, 50000, 30, campaignEnd, 6));
        vouchers.add(voucher("SHIP_70P_ALL", "SHIP70", "Giảm 70% phí vận chuyển",
                "Giảm 70% phí vận chuyển, tối đa 30.000đ.",
                "shipping", "shipping_percentage", 70, "all", "", 0, 30000, 30, campaignEnd, 7));
        vouchers.add(voucher("SHIP_FREE_AUDIO", "AUDIO-SHIP", "Miễn phí ship tai nghe",
                "Miễn phí vận chuyển đơn có sản phẩm Tai nghe.",
                "shipping", "free_shipping", 100, "category", "Tai nghe", 0, 40000, 30, campaignEnd, 8));
        vouchers.add(voucher("SHIP_20K_500", "SHIP20K", "Giảm 20.000đ phí ship",
                "Giảm 20.000đ phí vận chuyển cho đơn từ 500.000đ.",
                "shipping", "shipping_fixed", 20000, "all", "", 500000, 20000, 30, campaignEnd, 9));
        vouchers.add(voucher("SHIP_FREE_3M", "SHIP3M", "Miễn phí ship đơn lớn",
                "Miễn phí vận chuyển cho đơn từ 3.000.000đ.",
                "shipping", "free_shipping", 100, "all", "", 3000000, 50000, 30, campaignEnd, 10));
        return vouchers;
    }

    private static Map<String, Object> voucher(String id, String code, String name,
            String description, String type, String discountType, long value,
            String appliesTo, String category, long minimumOrderAmount,
            long maximumDiscountAmount, int validityDays, Date campaignEnd, int sortOrder) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("code", code);
        data.put("name", name);
        data.put("description", description);
        data.put("type", type);
        data.put("discountType", discountType);
        data.put("value", value);
        data.put("appliesTo", appliesTo);
        data.put("category", category);
        data.put("minimumOrderAmount", minimumOrderAmount);
        data.put("maximumDiscountAmount", maximumDiscountAmount);
        data.put("validityAfterSaveDays", validityDays);
        data.put("campaignEndsAt", new Timestamp(campaignEnd));
        data.put("isActive", true);
        data.put("sortOrder", sortOrder);
        return data;
    }

    private static Map<String, Object> catalogVoucherById(String voucherId) {
        for (Map<String, Object> voucher : catalogVouchers()) {
            if (voucherId.equals(voucher.get("id"))) {
                return new HashMap<>(voucher);
            }
        }
        return null;
    }

    private static Map<String, Object> productData(Product product) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", product.getId());
        data.put("name", product.getName());
        data.put("category", product.getCategory());
        data.put("price", product.getPrice());
        data.put("description", product.getDescription());
        data.put("icon", product.getIcon());
        data.put("stock", product.getStock());
        data.put("soldCount", product.getSoldCount());
        data.put("averageRating", product.getAverageRating());
        data.put("ratingSum",
                product.getAverageRating() * product.getReviewCount());
        data.put("reviewCount", product.getReviewCount());
        data.put("specifications", product.getSpecifications());
        return data;
    }

    private static Map<String, Object> orderItem(int orderId, Product product,
            int quantity, long subtotal, long discountAmount) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", 1);
        item.put("orderId", orderId);
        item.put("productId", product.getId());
        item.put("productName", product.getName());
        item.put("productIcon", product.getIcon());
        item.put("price", product.getPrice());
        item.put("quantity", quantity);
        item.put("originalSubtotal", subtotal);
        item.put("discountAmount", discountAmount);
        item.put("subtotal", subtotal - discountAmount);
        return item;
    }

    private static Map<String, Object> soldOrder(int orderId, int quantity, Date date) {
        Map<String, Object> soldOrder = new HashMap<>();
        soldOrder.put("orderId", orderId);
        soldOrder.put("quantity", quantity);
        soldOrder.put("createdAt", new Timestamp(date));
        return soldOrder;
    }

    private static Map<String, Object> statusNotification(String email, int orderId,
            String title, String message, String type, String status, Date now) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userEmail", email);
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("orderId", orderId);
        if (status != null) {
            notification.put("status", status);
        }
        notification.put("createdAt", new Timestamp(now));
        notification.put("isRead", false);
        return notification;
    }

    private static Map<String, Object> orderReview(int orderId, Product product,
            DemoAccount account, String reviewerId, int variant, Date date) {
        Map<String, Object> review = new HashMap<>();
        review.put("orderId", orderId);
        review.put("orderItemId", 1);
        review.put("productId", product.getId());
        review.put("productName", product.getName());
        review.put("productIcon", product.getIcon());
        review.put("reviewerId", reviewerId);
        review.put("reviewerName", account.name);
        review.put("anonymous", false);
        review.put("rating", 4 + variant % 2);
        review.put("comment", demoReviewComment(product, variant));
        review.put("likesCount", 2L + variant);
        review.put("viewsCount", 12L + variant * 3L);
        review.put("createdAt", new Timestamp(date));
        review.put("updatedAt", new Timestamp(date));
        return review;
    }

    private static Map<String, Object> productReview(int orderId, Product product,
            DemoAccount account, String reviewerId, int productIndex, Date date) {
        String[] comments = {
                "Màn hình sáng đẹp, pin dùng cả ngày và hiệu năng mượt.",
                "Điện thoại chạy nhanh, camera chụp sắc nét.",
                "Máy có hiệu năng mạnh, chụp ảnh đẹp.",
                "Laptop khởi động nhanh, gõ phím thoải mái.",
                "Máy nhẹ, màn hình đẹp, pin ổn và sử dụng mượt.",
                "Tản nhiệt tốt, chơi game ổn và màn hình 144Hz.",
                "Tai nghe kết nối nhanh, bass vừa tai.",
                "Âm thanh rõ, hộp sạc nhỏ gọn.",
                "Chống ồn hiệu quả, âm treble rõ.",
                "Vòng đeo nhẹ, dữ liệu theo dõi khá chính xác.",
                "Đồng hồ đẹp, GPS bắt nhanh.",
                "Đồng hồ chạy bộ chuyên dụng và pin lâu.",
                "Củ sạc nhỏ gọn, sạc nhanh.",
                "Dung lượng lớn, sạc nhanh và tiện mang đi.",
                "Hub USB-C đủ cổng, xuất HDMI 4K ổn."
        };
        Map<String, Object> review = new HashMap<>();
        review.put("orderId", orderId);
        review.put("orderItemId", 1);
        review.put("productId", product.getId());
        review.put("productName", product.getName());
        review.put("productIcon", product.getIcon());
        review.put("reviewerId", reviewerId);
        review.put("reviewerName", account.name);
        review.put("anonymous", false);
        review.put("rating", 4 + productIndex % 2);
        review.put("comment", comments[productIndex]);
        review.put("likesCount", 1L + productIndex % 5);
        review.put("viewsCount", 8L + productIndex % 12);
        review.put("createdAt", new Timestamp(date));
        review.put("updatedAt", new Timestamp(date));
        return review;
    }

    private static String demoReviewComment(Product product, int variant) {
        String[] comments = {
                "Chất lượng vượt mong đợi, hàng mới và giao nhanh.",
                "Sản phẩm hoạt động ổn, đóng gói chắc chắn.",
                "Thiết kế đẹp, dễ sử dụng và đúng thông số mô tả.",
                "Hiệu suất tốt, giá phù hợp.",
                "Dùng thử vài ngày và rất hài lòng."
        };
        return "Đánh giá cho " + product.getName() + ": "
                + comments[variant % comments.length];
    }

    private static Map<String, Object> seedMarker() {
        Map<String, Object> marker = new HashMap<>();
        marker.put("version", 1);
        marker.put("seededAt", FieldValue.serverTimestamp());
        List<String> emails = new ArrayList<>();
        for (DemoAccount account : demoAccounts()) {
            emails.add(account.email);
        }
        marker.put("accountEmails", emails);
        return marker;
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date);
    }

    private static String orderCode(int orderId) {
        return "DH" + String.format(Locale.getDefault(), "%05d", orderId);
    }

    private static Date daysFromNow(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }

    private static int demoSoldCount(int productId) {
        int[] values = {17, 9, 14, 9, 7, 6, 13, 11, 8, 16, 12, 5, 24, 21, 18};
        return values[(productId - 1) % values.length];
    }

    private static double demoAverageRating(int productId) {
        double[] values = {4.5, 4.2, 4.7, 5.0, 4.6, 4.4, 4.0, 4.3, 4.8, 4.1, 4.6, 4.7, 4.2, 4.5, 4.4};
        return values[(productId - 1) % values.length];
    }

    private static int demoReviewCount(int productId) {
        int[] values = {2, 1, 3, 1, 2, 1, 1, 2, 2, 3, 2, 1, 4, 3, 2};
        return values[(productId - 1) % values.length];
    }

    private static List<SeedReview> initialReviews() {
        List<SeedReview> reviews = new ArrayList<>();
        reviews.add(seedReview(90001, 1, "Samsung Galaxy A16 5G 128GB", "📱", 5,
                "Máy chạy mượt, pin dùng ổn định."));
        reviews.add(seedReview(90002, 1, "Samsung Galaxy A16 5G 128GB", "📱", 4,
                "Sản phẩm đúng mô tả, sử dụng tốt."));
        reviews.add(seedReview(90003, 4, "ASUS Vivobook 15 X1504VA Core i5 16GB 512GB", "💻", 5,
                "Máy đáp ứng tốt nhu cầu học tập và làm việc."));
        reviews.add(seedReview(90004, 7, "Samsung Galaxy Buds FE", "🎧", 4,
                "Âm thanh rõ, kết nối ổn định."));
        return reviews;
    }

    private static SeedReview seedReview(int orderId, int productId, String productName,
            String productIcon, int rating, String comment) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("orderItemId", orderId);
        data.put("productId", productId);
        data.put("productName", productName);
        data.put("productIcon", productIcon);
        data.put("reviewerId", "initial-customer-" + orderId);
        data.put("reviewerName", "");
        data.put("anonymous", true);
        data.put("rating", rating);
        data.put("comment", comment);
        data.put("likesCount", 0L);
        data.put("viewsCount", 0L);
        data.put("createdAt", Timestamp.now());
        data.put("updatedAt", Timestamp.now());
        return new SeedReview(ReviewRules.documentId(orderId, productId), data);
    }

    private static final class DemoAccount {
        private final String email;
        private final String name;
        private final String phone;
        private final String address;

        private DemoAccount(String email, String name, String phone, String address) {
            this.email = email;
            this.name = name;
            this.phone = phone;
            this.address = address;
        }
    }

    private static final class SeedReview {
        private final String id;
        private final Map<String, Object> data;

        private SeedReview(String id, Map<String, Object> data) {
            this.id = id;
            this.data = data;
        }
    }
}
