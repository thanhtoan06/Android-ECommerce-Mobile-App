// Lớp trung gian truy xuất dữ liệu sản phẩm từ FirestoreRepository và chuyển đổi sang định dạng phù hợp cho các màn hình sản phẩm
package com.example.e_commerce;

import java.util.List;

public class ProductRepository {

    // Giao diện nhận kết quả trả về là mảng danh sách sản phẩm
    public interface ProductsCallback {
        void onResult(Product[] products);
        void onError(Exception e);
    }

    // Giao diện nhận kết quả trả về là một sản phẩm chi tiết
    public interface ProductCallback {
        void onResult(Product product);
        void onError(Exception e);
    }

    // Lấy toàn bộ danh sách sản phẩm từ Firestore (tự nạp dữ liệu mẫu nếu chưa có)
    public static void getAll(ProductsCallback callback) {
        FirestoreRepository.getInstance().getAllProducts(new FirestoreRepository.ProductsCallback() {
            @Override
            public void onResult(List<Product> products) {
                // Chuyển đổi List<Product> sang mảng Product[] để truyền vào ProductAdapter
                callback.onResult(products.toArray(new Product[0]));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    // Tìm chi tiết một sản phẩm theo mã ID
    public static void findById(int id, ProductCallback callback) {
        FirestoreRepository.getInstance().getProductById(id, new FirestoreRepository.ProductCallback() {
            @Override
            public void onResult(Product product) {
                callback.onResult(product);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
}