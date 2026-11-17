// Bộ chuyển đổi danh sách sản phẩm trong giỏ hàng (CartItem) thành từng dòng hiển thị trên ListView và xử lý nút tăng, giảm, xóa
package com.example.e_commerce;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class CartAdapter extends ArrayAdapter<CartItem> {

    // Giao diện gửi sự kiện tăng/giảm số lượng hoặc xóa sản phẩm về cho CartActivity xử lý
    public interface OnCartItemListener {
        void onQuantityChanged(CartItem item, int newQuantity);
        void onItemDeleted(CartItem item);
    }

    // Khai báo đối tượng nạp giao diện và bộ lắng nghe sự kiện thao tác trên từng dòng
    private final LayoutInflater inflater;
    private final OnCartItemListener listener;

    // Hàm khởi tạo Adapter nhận vào Context, danh sách giỏ hàng và bộ lắng nghe sự kiện
    public CartAdapter(@NonNull Context context, @NonNull List<CartItem> items, OnCartItemListener listener) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    // Tạo và gán dữ liệu từng món hàng trong giỏ lên giao diện một dòng (item_cart.xml)
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // Tái sử dụng View cũ để cuộn danh sách mượt mà (ViewHolder)
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_cart, parent, false);
            holder = new ViewHolder();
            holder.tvIcon = convertView.findViewById(R.id.tvCartIcon);
            holder.tvCategory = convertView.findViewById(R.id.tvCartCategory);
            holder.tvName = convertView.findViewById(R.id.tvCartName);
            holder.tvUnitPrice = convertView.findViewById(R.id.tvCartUnitPrice);
            holder.tvSubtotal = convertView.findViewById(R.id.tvCartSubtotal);
            holder.tvQuantity = convertView.findViewById(R.id.tvQuantity);
            holder.btnMinus = convertView.findViewById(R.id.btnMinus);
            holder.btnPlus = convertView.findViewById(R.id.btnPlus);
            holder.btnDelete = convertView.findViewById(R.id.btnDeleteCartItem);
            convertView.setTag(holder);
        } else {
            // Lấy lại ViewHolder đã lưu sẵn nếu dòng giao diện được tái sử dụng
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy đối tượng CartItem tại vị trí hiện tại và đổ dữ liệu lên giao diện
        CartItem item = getItem(position);
        if (item != null) {
            Product product = item.getProduct();
            if (product != null) {
                // Hiển thị biểu tượng, danh mục, tên và đơn giá sản phẩm
                holder.tvIcon.setText(product.getIcon());
                holder.tvCategory.setText(product.getCategory());
                holder.tvName.setText(product.getName());
                holder.tvUnitPrice.setText("Đơn giá: " + product.getFormattedPrice());
            }

            // Hiển thị số lượng và thành tiền của dòng sản phẩm này
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
            holder.tvSubtotal.setText(item.getFormattedSubtotal());

            // Gắn sự kiện nút giảm (−): trừ 1 đơn vị số lượng
            holder.btnMinus.setOnClickListener(v -> {
                if (listener != null) listener.onQuantityChanged(item, item.getQuantity() - 1);
            });

            // Gắn sự kiện nút tăng (+): cộng thêm 1 đơn vị số lượng
            holder.btnPlus.setOnClickListener(v -> {
                if (listener != null) listener.onQuantityChanged(item, item.getQuantity() + 1);
            });

            // Gắn sự kiện nút xóa: gọi callback xóa sản phẩm khỏi giỏ
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onItemDeleted(item);
            });
        }

        return convertView;
    }

    // Lớp giữ tham chiếu các thành phần View của một dòng trong giỏ hàng
    private static class ViewHolder {
        TextView tvIcon;
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