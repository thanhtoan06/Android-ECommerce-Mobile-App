package com.example.e_commerce;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InitialCatalogSeeder {

    private static volatile boolean legacyMarkerCleanupComplete;

    private InitialCatalogSeeder() {
    }

    public static Task<Boolean> seedCatalogAndVouchersIfNeeded(FirebaseFirestore firestore) {
        TaskCompletionSource<Boolean> completion = new TaskCompletionSource<>();
        firestore.collection("products").get().addOnSuccessListener(products ->
                firestore.collection("categories").get().addOnSuccessListener(categories ->
                        firestore.collection("vouchers").get().addOnSuccessListener(vouchers ->
                                writeCatalogAndVouchers(firestore, products, categories, vouchers, completion)
                        ).addOnFailureListener(completion::setException)
                ).addOnFailureListener(completion::setException)
        ).addOnFailureListener(completion::setException);
        return completion.getTask();
    }

    private static void writeCatalogAndVouchers(FirebaseFirestore firestore, QuerySnapshot oldProducts,
            QuerySnapshot oldCategories, QuerySnapshot oldVouchers,
            TaskCompletionSource<Boolean> completion) {
        List<Product> products = catalogProducts();
        List<Map<String, Object>> categories = catalogCategories();
        List<Map<String, Object>> vouchers = catalogVouchers();
        Set<String> existingProductIds = new HashSet<>();
        Set<String> existingCategoryIds = new HashSet<>();
        Set<String> existingVoucherIds = new HashSet<>();

        for (DocumentSnapshot document : oldProducts) {
            existingProductIds.add(document.getId());
        }
        for (DocumentSnapshot document : oldCategories) {
            existingCategoryIds.add(document.getId());
        }
        for (DocumentSnapshot document : oldVouchers) {
            existingVoucherIds.add(document.getId());
        }

        int missingCount = 0;
        for (Product product : products) {
            if (!existingProductIds.contains(String.valueOf(product.getId()))) {
                missingCount++;
            }
        }
        for (Map<String, Object> category : categories) {
            if (!existingCategoryIds.contains(category.get("id"))) {
                missingCount++;
            }
        }
        for (Map<String, Object> voucher : vouchers) {
            if (!existingVoucherIds.contains(voucher.get("id"))) {
                missingCount++;
            }
        }

        boolean removeLegacyMarker = !legacyMarkerCleanupComplete;
        if (missingCount == 0 && !removeLegacyMarker) {
            completion.setResult(false);
            return;
        }
        if (missingCount + (removeLegacyMarker ? 1 : 0) > 500) {
            completion.setException(
                    new IllegalStateException("Initial catalog exceeds Firestore's 500-write batch limit."));
            return;
        }

        com.google.firebase.firestore.WriteBatch batch = firestore.batch();
        if (removeLegacyMarker) {
            batch.delete(firestore.collection("appMetadata").document("catalogSeed"));
        }

        for (Product product : products) {
            String productId = String.valueOf(product.getId());
            if (!existingProductIds.contains(productId)) {
                batch.set(firestore.collection("products").document(productId), productData(product));
            }
        }
        for (Map<String, Object> category : categories) {
            String categoryId = (String) category.get("id");
            if (!existingCategoryIds.contains(categoryId)) {
                batch.set(firestore.collection("categories").document(categoryId), category);
            }
        }
        for (Map<String, Object> voucher : vouchers) {
            String voucherId = (String) voucher.get("id");
            if (!existingVoucherIds.contains(voucherId)) {
                batch.set(firestore.collection("vouchers").document(voucherId), voucher);
            }
        }

        boolean addedCatalogData = missingCount > 0;
        batch.commit().addOnSuccessListener(ignored -> {
            if (removeLegacyMarker) {
                legacyMarkerCleanupComplete = true;
            }
            completion.setResult(addedCatalogData);
        })
                .addOnFailureListener(completion::setException);
    }

    private static List<Product> catalogProducts() {
        List<Product> products = new ArrayList<>();
        products.add(product(1, "Samsung Galaxy A16 5G 128GB", "Điện thoại", 5_990_000,
                "Điện thoại 5G màn hình Super AMOLED 6,7 inch 90Hz, camera chính 50MP và pin 5.000mAh.", "📱", 38,
                specs("Màn hình", "Super AMOLED 6,7 inch 90Hz", "RAM", "8GB", "Bộ nhớ", "128GB",
                        "Camera chính", "50MP", "Pin", "5.000mAh")));
        products.add(product(2, "Samsung Galaxy A55 5G 256GB", "Điện thoại", 12_990_000,
                "Điện thoại 5G với màn hình Super AMOLED 120Hz, camera chính 50MP chống rung quang học.", "📱", 21,
                specs("Màn hình", "Super AMOLED 6,6 inch 120Hz", "Chip", "Exynos 1480", "RAM", "8GB",
                        "Bộ nhớ", "256GB", "Camera chính", "50MP OIS")));
        products.add(product(3, "Xiaomi 14T 512GB", "Điện thoại", 19_990_000,
                "Điện thoại Xiaomi 14T với camera Leica, màn hình AMOLED 144Hz và chip MediaTek Dimensity 8300-Ultra.",
                "📱", 12,
                specs("Màn hình", "AMOLED 6,67 inch 144Hz", "Chip", "Dimensity 8300-Ultra", "RAM", "12GB",
                        "Bộ nhớ", "512GB", "Camera chính", "Leica 50MP OIS")));

        products.add(product(4, "ASUS Vivobook 15 X1504VA Core i5 16GB 512GB", "Laptop", 14_990_000,
                "Laptop 15,6 inch Full HD dùng Intel Core i5-1335U, RAM 16GB và SSD 512GB.", "💻", 17,
                specs("Màn hình", "15,6 inch Full HD", "CPU", "Intel Core i5-1335U", "RAM", "16GB",
                        "Ổ cứng", "512GB SSD")));
        products.add(product(5, "Apple MacBook Air 13-inch M3 16GB 256GB", "Laptop", 22_990_000,
                "MacBook Air 13,6 inch Liquid Retina sử dụng chip Apple M3, bộ nhớ thống nhất 16GB và SSD 256GB.",
                "💻", 10,
                specs("Màn hình", "Liquid Retina 13,6 inch", "Chip", "Apple M3", "Bộ nhớ thống nhất", "16GB",
                        "Ổ cứng", "256GB SSD")));
        products.add(product(6, "Lenovo LOQ 15IRX9 Core i5 RTX 4050", "Laptop", 31_990_000,
                "Laptop gaming màn hình 144Hz, Intel Core i5-13450HX và NVIDIA GeForce RTX 4050 6GB.", "💻", 7,
                specs("Màn hình", "15,6 inch Full HD 144Hz", "CPU", "Intel Core i5-13450HX", "RAM", "16GB",
                        "Đồ họa", "GeForce RTX 4050 6GB", "Ổ cứng", "512GB SSD")));

        products.add(product(7, "Samsung Galaxy Buds FE", "Tai nghe", 1_290_000,
                "Tai nghe true wireless có chống ồn chủ động, chế độ âm thanh xung quanh và hộp sạc.", "🎧", 52,
                specs("Kết nối", "Bluetooth", "Chống ồn", "ANC", "Thời lượng nghe", "Tối đa 6 giờ",
                        "Chống nước", "IPX2")));
        products.add(product(8, "Samsung Galaxy Buds3", "Tai nghe", 2_490_000,
                "Tai nghe true wireless thiết kế mở, hỗ trợ chống ồn chủ động và điều khiển cảm ứng.", "🎧", 29,
                specs("Kết nối", "Bluetooth 5.4", "Chống ồn", "ANC", "Thời lượng nghe", "Tối đa 5 giờ",
                        "Chống nước", "IP57")));
        products.add(product(9, "Sony WH-1000XM5", "Tai nghe", 6_990_000,
                "Tai nghe chụp tai không dây chống ồn chủ động, hỗ trợ kết nối đa điểm và nghe nhạc tối đa 30 giờ.",
                "🎧", 14,
                specs("Kiểu tai nghe", "Chụp tai không dây", "Chống ồn", "ANC", "Thời lượng pin", "Tối đa 30 giờ",
                        "Kết nối", "Bluetooth")));

        products.add(product(10, "Xiaomi Smart Band 9 Pro", "Đồng hồ thông minh", 1_990_000,
                "Vòng đeo thông minh màn hình AMOLED 1,74 inch, GPS tích hợp và khả năng kháng nước 5 ATM.", "⌚", 34,
                specs("Màn hình", "AMOLED 1,74 inch", "GPS", "Tích hợp", "Chống nước", "5 ATM",
                        "Thời lượng pin", "Tối đa 21 ngày")));
        products.add(product(11, "Samsung Galaxy Watch7 44mm Bluetooth", "Đồng hồ thông minh", 4_590_000,
                "Đồng hồ thông minh màn hình Super AMOLED, chip Exynos W1000, GPS và theo dõi sức khỏe.", "⌚", 19,
                specs("Màn hình", "Super AMOLED 1,5 inch", "Chip", "Exynos W1000", "Kết nối", "Bluetooth",
                        "Chống nước", "5 ATM")));
        products.add(product(12, "Garmin Forerunner 265", "Đồng hồ thông minh", 8_990_000,
                "Đồng hồ chạy bộ GPS với màn hình AMOLED, định vị đa băng tần và các chỉ số luyện tập.", "⌚", 8,
                specs("Màn hình", "AMOLED 1,3 inch", "GPS", "Đa băng tần", "Chống nước", "5 ATM",
                        "Thời lượng pin", "Tối đa 13 ngày")));

        products.add(product(13, "Anker 735 Charger GaNPrime 65W", "Phụ kiện & Sạc", 790_000,
                "Củ sạc GaN 65W nhỏ gọn với hai cổng USB-C và một cổng USB-A.", "🔌", 64,
                specs("Công suất", "Tối đa 65W", "Cổng", "2 USB-C, 1 USB-A", "Công nghệ", "GaNPrime")));
        products.add(product(14, "Anker 325 Power Bank PowerCore 20K", "Phụ kiện & Sạc", 1_190_000,
                "Pin dự phòng dung lượng 20.000mAh với cổng USB-C và hai cổng USB-A.", "🔋", 47,
                specs("Dung lượng", "20.000mAh", "Cổng ra", "2 USB-A", "Cổng vào", "USB-C")));
        products.add(product(15, "UGREEN Revodok 8-in-1 USB-C Hub", "Phụ kiện & Sạc", 1_490_000,
                "Bộ chuyển đổi USB-C đa cổng với HDMI 4K, USB, đầu đọc thẻ và sạc PD.", "🧩", 25,
                specs("Cổng", "HDMI, USB-A, USB-C, SD/microSD", "HDMI", "Tối đa 4K", "Sạc PD", "Tối đa 100W")));
        return products;
    }

    private static Product product(int id, String name, String category, long price, String description, String icon,
            int stock, Map<String, String> specifications) {
        return new Product(id, name, category, price, description, icon, stock, 0, 0.0, 0, specifications);
    }

    private static Map<String, String> specs(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
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
        Date campaignEnd = daysFromNow(180);
        List<Map<String, Object>> vouchers = new ArrayList<>();
        vouchers.add(voucher("ITEM_5K_ALL", "TECH5K", "Giảm 5.000đ mỗi sản phẩm",
                "Giảm 5.000đ cho từng sản phẩm đủ điều kiện trong toàn shop.",
                "product", "fixed_per_item", 5_000, "all", "", 0, 0, 30, campaignEnd, 1));
        vouchers.add(voucher("ITEM_20P_ALL", "TECH20", "Giảm 20% toàn shop",
                "Giảm 20% trên từng sản phẩm đủ điều kiện, tối đa 2.000.000đ.",
                "product", "percentage_per_item", 20, "all", "", 0, 2_000_000, 30, campaignEnd, 2));
        vouchers.add(voucher("ITEM_10P_HEADPHONE", "AUDIO10", "Giảm 10% tai nghe",
                "Áp dụng cho các sản phẩm thuộc danh mục Tai nghe.",
                "product", "percentage_per_item", 10, "category", "Tai nghe", 0, 1_000_000, 30, campaignEnd, 3));
        vouchers.add(voucher("ITEM_15P_ACCESSORY", "GEAR15", "Giảm 15% phụ kiện & sạc",
                "Áp dụng cho danh mục Phụ kiện & Sạc.",
                "product", "percentage_per_item", 15, "category", "Phụ kiện & Sạc", 0, 500_000, 30, campaignEnd, 4));
        vouchers.add(voucher("ITEM_LAPTOP_500K", "LAPTOP500", "Giảm 500.000đ laptop",
                "Giảm 500.000đ trên từng laptop khi đơn laptop đạt từ 15.000.000đ.",
                "product", "fixed_per_item", 500_000, "category", "Laptop", 15_000_000, 1_000_000, 30,
                campaignEnd, 5));

        vouchers.add(voucher("SHIP_FREE_ALL", "SHIPFREE", "Miễn phí vận chuyển",
                "Miễn phí vận chuyển tối đa 50.000đ cho một đơn.",
                "shipping", "free_shipping", 100, "all", "", 0, 50_000, 30, campaignEnd, 6));
        vouchers.add(voucher("SHIP_70P_ALL", "SHIP70", "Giảm 70% phí vận chuyển",
                "Giảm 70% phí vận chuyển, tối đa 30.000đ.",
                "shipping", "shipping_percentage", 70, "all", "", 0, 30_000, 30, campaignEnd, 7));
        vouchers.add(voucher("SHIP_FREE_AUDIO", "AUDIO-SHIP", "Miễn phí ship tai nghe",
                "Miễn phí vận chuyển đơn có sản phẩm Tai nghe, tối đa 40.000đ.",
                "shipping", "free_shipping", 100, "category", "Tai nghe", 0, 40_000, 30, campaignEnd, 8));
        vouchers.add(voucher("SHIP_20K_500", "SHIP20K", "Giảm 20.000đ phí ship",
                "Giảm 20.000đ phí vận chuyển cho đơn từ 500.000đ.",
                "shipping", "shipping_fixed", 20_000, "all", "", 500_000, 20_000, 30, campaignEnd, 9));
        vouchers.add(voucher("SHIP_FREE_3M", "SHIP3M", "Miễn phí ship đơn lớn",
                "Miễn phí vận chuyển tối đa 50.000đ cho đơn từ 3.000.000đ.",
                "shipping", "free_shipping", 100, "all", "", 3_000_000, 50_000, 30, campaignEnd, 10));
        return vouchers;
    }

    private static Map<String, Object> voucher(String id, String code, String name, String description, String type,
            String discountType, long value, String appliesTo, String category, long minimumOrderAmount,
            long maximumDiscountAmount, int validityAfterSaveDays, Date campaignEnd, int sortOrder) {
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
        data.put("validityAfterSaveDays", validityAfterSaveDays);
        data.put("campaignEndsAt", new Timestamp(campaignEnd));
        data.put("isActive", true);
        data.put("sortOrder", sortOrder);
        return data;
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
        data.put("ratingSum", product.getAverageRating() * product.getReviewCount());
        data.put("reviewCount", product.getReviewCount());
        data.put("specifications", product.getSpecifications());
        return data;
    }

    private static Date daysFromNow(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }
}
