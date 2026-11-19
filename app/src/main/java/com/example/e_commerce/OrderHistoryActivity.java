
package com.example.e_commerce;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class OrderHistoryActivity extends AppCompatActivity {

    private ListView lvOrders;
    private View layoutEmptyOrders;
    private TextView tvOrderCountBadge;

    private final List<Order> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private FirestoreRepository repository;
    private String currentUserEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_order_history);

        repository = FirestoreRepository.getInstance();
        currentUserEmail = SharedPrefManager.getInstance(this).getEmail();

        TextView tvBack = findViewById(R.id.tvBack);
        lvOrders = findViewById(R.id.lvOrders);
        layoutEmptyOrders = findViewById(R.id.layoutEmptyOrders);
        tvOrderCountBadge = findViewById(R.id.tvOrderCountBadge);

        tvBack.setOnClickListener(v -> finish());

        adapter = new OrderAdapter(this, orderList);
        lvOrders.setAdapter(adapter);

        lvOrders.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < orderList.size()) {
                Order order = orderList.get(position);
                Intent intent = new Intent(this, OrderDetailActivity.class);
                intent.putExtra("order", order);
                intent.putExtra("order_id", order.getId());
                startActivity(intent);
            }
        });

        findViewById(R.id.btnShopNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        setupBottomMenu();

        loadOrderData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrderData();
    }

    private void setupBottomMenu() {
        View menuHome = findViewById(R.id.menuHome);
        View menuCart = findViewById(R.id.menuCart);
        View menuAccount = findViewById(R.id.menuAccount);

        ImageView ivMenuHome = findViewById(R.id.ivMenuHome);
        ImageView ivMenuCart = findViewById(R.id.ivMenuCart);
        ImageView ivMenuAccount = findViewById(R.id.ivMenuAccount);

        if (ivMenuHome != null)
            ivMenuHome.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuCart != null)
            ivMenuCart.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
        if (ivMenuAccount != null)
            ivMenuAccount.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));

        TextView tvMenuAccount = findViewById(R.id.tvMenuAccount);
        if (tvMenuAccount != null) {
            tvMenuAccount.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvMenuAccount.setTypeface(null, Typeface.BOLD);
        }

        TextView tvMenuHome = findViewById(R.id.tvMenuHome);
        if (tvMenuHome != null) {
            tvMenuHome.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvMenuHome.setTypeface(null, Typeface.NORMAL);
        }

        if (menuHome != null) {
            menuHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProductListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        if (menuCart != null) {
            menuCart.setOnClickListener(v -> {
                startActivity(new Intent(this, CartActivity.class));
                finish();
            });
        }

        if (menuAccount != null) {
            menuAccount.setOnClickListener(v -> {
                startActivity(new Intent(this, AccountActivity.class));
                finish();
            });
        }

        findViewById(R.id.menuNotifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
    }

    private void loadOrderData() {
        repository.getUserOrders(currentUserEmail, new FirestoreRepository.OrdersCallback() {
            @Override
            public void onResult(List<Order> orders) {

                orderList.clear();
                orderList.addAll(orders);
                adapter.notifyDataSetChanged();
                updateOrderVisibility();

            }

            @Override
            public void onError(Exception e) {

                Toast.makeText(OrderHistoryActivity.this, "Không thể tải đơn hàng từ Firebase.", Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void updateOrderVisibility() {
        if (orderList.isEmpty()) {

            layoutEmptyOrders.setVisibility(View.VISIBLE);
            lvOrders.setVisibility(View.GONE);
            tvOrderCountBadge.setText("0 đơn");
        } else {

            layoutEmptyOrders.setVisibility(View.GONE);
            lvOrders.setVisibility(View.VISIBLE);
            tvOrderCountBadge.setText(orderList.size() + " đơn");
        }
    }
}
