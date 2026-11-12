package com.example.e_commerce;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * CartAdapter.java
 * ─────────────────────────────────────────────────────────
 * Adapter kết nối danh sách CartItem với ListView trong CartActivity.
 * Hiển thị icon sản phẩm (màu động), tên, danh mục, đơn giá, thành tiền
 * và cung cấp nút (+), (−) để tăng/giảm số lượng và nút (×) để xóa.
 *
 * Dùng ViewHolder Pattern để tránh gọi findViewById() lặp lại → tối ưu hiệu suất cuộn.
 * Giao tiếp ngược với CartActivity qua interface OnCartItemListener.
 * ─────────────────────────────────────────────────────────
 */
public class CartAdapter extends ArrayAdapter<CartItem> {

    /**
     * Interface callback gửi sự kiện tương tác giỏ hàng về Activity xử lý.
     * Activity sẽ cập nhật SQLite và reload danh sách.
     */
    public interface OnCartItemListener {
        /** Gọi khi người dùng nhấn (+) hoặc (−) để đổi số lượng */
        void onQuantityChanged(CartItem item, int newQuantity);
        /** Gọi khi người dùng nhấn nút xóa (×) */
        void onItemDeleted(CartItem item);
    }

    private final LayoutInflater inflater;
    private final OnCartItemListener listener; // Callback về Activity

    public CartAdapter(@NonNull Context context, @NonNull List<CartItem> items, OnCartItemListener listener) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // ViewHolder Pattern: chỉ inflate và tìm view khi tạo lần đầu
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_cart, parent, false);
            holder = new ViewHolder();
            holder.tvIcon        = convertView.findViewById(R.id.tvCartIcon);
            holder.flIconContainer = convertView.findViewById(R.id.flIconContainer);
            holder.tvCategory    = convertView.findViewById(R.id.tvCartCategory);
            holder.tvName        = convertView.findViewById(R.id.tvCartName);
            holder.tvUnitPrice   = convertView.findViewById(R.id.tvCartUnitPrice);
            holder.tvSubtotal    = convertView.findViewById(R.id.tvCartSubtotal);
            holder.tvQuantity    = convertView.findViewById(R.id.tvQuantity);
            holder.btnMinus      = convertView.findViewById(R.id.btnMinus);
            holder.btnPlus       = convertView.findViewById(R.id.btnPlus);
            holder.btnDelete     = convertView.findViewById(R.id.btnDeleteCartItem);
            convertView.setTag(holder); // Lưu holder vào tag để tái sử dụng
        } else {
            holder = (ViewHolder) convertView.getTag(); // Lấy holder đã lưu
        }

        CartItem item = getItem(position);
        if (item != null) {
            Product product = item.getProduct();
            if (product != null) {
                // Hiển thị icon emoji của sản phẩm
                holder.tvIcon.setText(product.getIcon());

                // Tô màu nền hình tròn icon theo màu sản phẩm (GradientDrawable động)
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(0f);
                drawable.setColor(product.getColor());
                holder.flIconContainer.setBackground(drawable);

                holder.tvCategory.setText(product.getCategory());
                holder.tvName.setText(product.getName());
                holder.tvUnitPrice.setText("Đơn giá: " + product.getFormattedPrice());
            }

            // Hiển thị số lượng và thành tiền
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
            holder.tvSubtotal.setText(item.getFormattedSubtotal());

            // Nút (−): Giảm số lượng → gọi callback về Activity xử lý SQLite
            holder.btnMinus.setOnClickListener(v -> {
                if (listener != null) listener.onQuantityChanged(item, item.getQuantity() - 1);
            });

            // Nút (+): Tăng số lượng → gọi callback về Activity xử lý SQLite
            holder.btnPlus.setOnClickListener(v -> {
                if (listener != null) listener.onQuantityChanged(item, item.getQuantity() + 1);
            });

            // Nút (×): Xóa sản phẩm → gọi callback về Activity xác nhận và xóa SQLite
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onItemDeleted(item);
            });
        }

        return convertView;
    }

    // ViewHolder lưu tham chiếu các View để tránh tìm lại mỗi lần cuộn
    private static class ViewHolder {
        TextView tvIcon;
        FrameLayout flIconContainer;
        TextView tvCategory;
        TextView tvName;
        TextView tvUnitPrice;
        TextView tvSubtotal;
        TextView tvQuantity;
        TextView btnMinus;
        TextView btnPlus;
        TextView btnDelete;
    }
}
