
package com.example.e_commerce;

import java.util.List;

public class ProductRepository {

    public interface ProductsCallback {
        void onResult(Product[] products);

        void onError(Exception e);
    }

    public interface ProductCallback {
        void onResult(Product product);

        void onError(Exception e);
    }

    public static void getAll(ProductsCallback callback) {
        FirestoreRepository.getInstance().getAllProducts(new FirestoreRepository.ProductsCallback() {
            @Override
            public void onResult(List<Product> products) {

                callback.onResult(products.toArray(new Product[0]));
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

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
