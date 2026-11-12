/**
 * Màn hình hiển thị danh sách sản phẩm lấy từ cơ sở dữ liệu SQLite và thanh menu điều hướng dưới cùng.
 */
package com.example.e_commerce;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProductListActivity extends AppCompatActivity {

    private ListView listView;
    private Product[] products;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kích hoạt giao diện hiển thị tràn viền (Edge-to-Edge)
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_product_list);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        // Xử lý căn lề tự động tránh bị che bởi thanh trạng thái và thanh điều hướng hệ thống
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Lấy thông tin người dùng từ SharedPreferences qua SharedPrefManager
        SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
        String username = prefManager.getUsername();
        String userEmail = prefManager.getEmail();
        String avatar = prefManager.getAvatar();

        TextView tvUserGreeting = findViewById(R.id.tvUserGreeting);
        if (tvUserGreeting != null) {
            String displayName = !username.isEmpty() ? username : userEmail;
            if (!displayName.isEmpty()) {
                tvUserGreeting.setText(avatar + " Xin chào, " + displayName + " 👋");
            } else {
                tvUserGreeting.setText("👤 Xin chào quý khách 👋");
            }
        }

        listView = findViewById(R.id.listView);
        loadProductsFromDatabase();

        // Sự kiện khi bấm vào một sản phẩm trong danh sách -> Chuyển sang màn hình chi tiết
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (products != null && position < products.length) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, products[position].getId());
                startActivity(intent);
            }
        });

        // Xử lý sự kiện bấm vào mục Trang chủ / Sản phẩm trên thanh menu dưới cùng
        findViewById(R.id.menuHome).setOnClickListener(v ->
                Toast.makeText(ProductListActivity.this, "Bạn đang ở trang Sản phẩm", Toast.LENGTH_SHORT).show()
        );

        // Xử lý sự kiện bấm vào mục Giỏ hàng -> Mở trang Giỏ hàng thật!
        findViewById(R.id.menuCart).setOnClickListener(v -> {
            Intent intent = new Intent(ProductListActivity.this, CartActivity.class);
            startActivity(intent);
        });

        // Xử lý sự kiện bấm vào mục Tài khoản -> Mở trang Tài khoản
        findViewById(R.id.menuAccount).setOnClickListener(v -> {
            Intent intent = new Intent(ProductListActivity.this, AccountActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProductsFromDatabase();
    }

    private void loadProductsFromDatabase() {
        // Lấy danh sách sản phẩm trực tiếp từ SQLite Database
        products = ProductRepository.getAll(this);
        ProductAdapter adapter = new ProductAdapter(this, products);
        listView.setAdapter(adapter);
    }
}
