/**
 * ProductRepository.java
 * ─────────────────────────────────────────────────────────
 * Lớp trung gian (Repository Pattern) cung cấp sản phẩm cho các Activity.
 * Toàn bộ dữ liệu được lấy từ cơ sở dữ liệu SQLite thật qua DatabaseHelper.
 * Có danh sách FALLBACK_PRODUCTS dự phòng nếu context null hoặc DB rỗng.
 * ─────────────────────────────────────────────────────────
 */
package com.example.e_commerce;

import android.content.Context;
import java.util.List;

public class ProductRepository {

    // Dữ liệu dự phòng khi không có context (trường hợp hiếm gặp)
    private static final Product[] FALLBACK_PRODUCTS = {
            new Product(1, "Áo thun cotton basic", "Thời trang", 189000,
                    "Áo thun cotton 100% mềm mịn, thấm hút mồ hôi tốt, form regular dễ phối đồ.",
                    "👕", 0xFFE3F2FD)
    };

    /**
     * Lấy toàn bộ danh sách sản phẩm từ SQLite (ưu tiên) hoặc FALLBACK nếu lỗi.
     * @param context Context cần thiết để khởi tạo DatabaseHelper
     */
    public static Product[] getAll(Context context) {
        if (context == null) return FALLBACK_PRODUCTS;
        List<Product> list = DatabaseHelper.getInstance(context).getAllProducts();
        if (list == null || list.isEmpty()) return FALLBACK_PRODUCTS;
        return list.toArray(new Product[0]);
    }

    /**
     * Tìm sản phẩm theo ID từ SQLite.
     * @return Product nếu tìm thấy, null nếu không có
     */
    public static Product findById(Context context, int id) {
        if (context == null) return null;
        return DatabaseHelper.getInstance(context).getProductById(id);
    }

    // ─── Phương thức tương thích ngược (không dùng context) ───
    // Chỉ dùng trong trường hợp khẩn cấp, không khuyến khích
    @Deprecated
    public static Product[] getAll() {
        return FALLBACK_PRODUCTS;
    }

    @Deprecated
    public static Product findById(int id) {
        for (Product product : FALLBACK_PRODUCTS) {
            if (product.getId() == id) return product;
        }
        return null;
    }
}
