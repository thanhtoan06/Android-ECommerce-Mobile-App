/**
 * ProductRepository.java: Lớp trung gian lấy danh sách sản phẩm từ SQLite (DatabaseHelper) cung cấp cho các màn hình.
 */
package com.example.e_commerce;

import android.content.Context;
import java.util.List;

public class ProductRepository {

    // Lấy toàn bộ danh sách sản phẩm từ SQLite
    public static Product[] getAll(Context context) {
        if (context == null) return new Product[0];
        List<Product> list = DatabaseHelper.getInstance(context).getAllProducts();
        return list != null ? list.toArray(new Product[0]) : new Product[0];
    }

    // Tìm thông tin một sản phẩm theo mã ID từ SQLite
    public static Product findById(Context context, int id) {
        if (context == null) return null;
        return DatabaseHelper.getInstance(context).getProductById(id);
    }
}