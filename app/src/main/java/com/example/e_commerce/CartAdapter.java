// Adapter hiển thị từng sản phẩm trong giỏ hàng lên ListView và bắt sự kiện tăng, giảm số lượng hoặc xóa món (kiểm tra tồn kho thực tế).
package com.example.e_commerce;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class CartAdapter extends ArrayAdapter<CartItem> {

    // Giao diện gửi sự kiện tăng/giảm số lượng, đổi trạng thái tích chọn hoặc xóa sản phẩm về cho CartActivity xử lý
    public interface OnCartItemListener {
        void onQuantityChanged(CartItem item, int newQuantity);
        void onItemDeleted(CartItem item);
        void onItemSelectionChanged(CartItem item, boolean isSelected);
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

        // Tái sử dụng View cũ để cuộn danh sách mượt mà (mô hình ViewHolder)
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_cart, parent, false);
            holder = new ViewHolder();
            holder.cbItem = convertView.findViewById(R.id.cbCartItem);
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
            // Cài đặt trạng thái ô tích chọn sản phẩm
            if (holder.cbItem != null) {
                holder.cbItem.setOnCheckedChangeListener(null);
                holder.cbItem.setChecked(item.isSelected());
                holder.cbItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    item.setSelected(isChecked);
                    if (listener != null) {
                        listener.onItemSelectionChanged(item, isChecked);
                    }
                });
            }
            Product product = item.getProduct();
            int stock = (product != null) ? product.getStock() : 0;

            if (product != null) {
                // Hiển thị biểu tượng, danh mục, tên và đơn giá sản phẩm
                holder.tvIcon.setText(product.getIcon());
                holder.tvCategory.setText(product.getCategory());
                holder.tvName.setText(product.getName());

                // Nếu sản phẩm hết hàng hoặc số lượng trong giỏ vượt quá tồn kho
                if (stock <= 0) {
                    holder.tvUnitPrice.setText("Đơn giá: " + product.getFormattedPrice() + " (Hết hàng)");
                    holder.tvUnitPrice.setTextColor(Color.parseColor("#EF4444")); // Đỏ cảnh báo
                    holder.btnPlus.setEnabled(false);
                    holder.btnPlus.setAlpha(0.3f);
                } else {
                    holder.tvUnitPrice.setText("Đơn giá: " + product.getFormattedPrice() + " (Kho: " + stock + ")");
                    holder.tvUnitPrice.setTextColor(Color.parseColor("#64748B"));

                    // Giới hạn nút (+) nếu số lượng đã bằng hoặc vượt tồn kho
                    if (item.getQuantity() >= stock) {
                        holder.btnPlus.setEnabled(false);
                        holder.btnPlus.setAlpha(0.3f);
                    } else {
                        holder.btnPlus.setEnabled(true);
                        holder.btnPlus.setAlpha(1.0f);
                    }
                }
            }

            // Hiển thị số lượng và thành tiền của dòng sản phẩm này
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
            holder.tvSubtotal.setText(item.getFormattedSubtotal());

            // Gắn sự kiện nút giảm (−): trừ 1 đơn vị số lượng và báo về CartActivity
            holder.btnMinus.setOnClickListener(v -> {
                if (listener != null) listener.onQuantityChanged(item, item.getQuantity() - 1);
            });

            // Gắn sự kiện nút tăng (+): kiểm tra không cho vượt quá stock
            holder.btnPlus.setOnClickListener(v -> {
                if (product != null) {
                    if (stock <= 0) {
                        Toast.makeText(getContext(), "Sản phẩm \"" + product.getName() + "\" hiện đã hết hàng!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (item.getQuantity() >= stock) {
                        Toast.makeText(getContext(), "Đã đạt số lượng tồn kho tối đa (" + stock + " sp)!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (listener != null) listener.onQuantityChanged(item, item.getQuantity() + 1);
            });

            // Gắn sự kiện nút xóa: gọi callback báo về CartActivity xóa sản phẩm khỏi giỏ
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onItemDeleted(item);
            });
        }

        return convertView;
    }

    // Lớp ViewHolder giữ tham chiếu các thành phần giao diện của một dòng trong giỏ hàng
    private static class ViewHolder {
        CheckBox cbItem;
        TextView tvIcon;
        TextView tvCategory;
        TextView tvName;
        TextView tvUnitPrice;
        TextView tvSubtotal;
        TextView tvQuantity;
        TextView btnMinus;
        TextView btnPlus;
        View btnDelete;
    }
}
