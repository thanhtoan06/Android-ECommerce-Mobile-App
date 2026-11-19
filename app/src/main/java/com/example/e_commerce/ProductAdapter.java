// Bộ chuyển đổi mảng sản phẩm (Product) thành từng thẻ giao diện lưới (item_product.xml) để hiển thị lên GridView
package com.example.e_commerce;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ProductAdapter extends ArrayAdapter<Product> {

    // Khai báo đối tượng nạp giao diện XML cho từng thẻ sản phẩm
    private final LayoutInflater inflater;

    // Hàm khởi tạo Adapter nhận vào Context và mảng sản phẩm
    public ProductAdapter(@NonNull Context context, @NonNull Product[] products) {
        super(context, 0, products);
        this.inflater = LayoutInflater.from(context);
    }

    // Tạo và gán dữ liệu thật của từng sản phẩm vào giao diện thẻ lưới (item_product.xml)
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // Tái sử dụng thẻ giao diện cũ nếu có để cuộn danh sách mượt hơn (ViewHolder)
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_product, parent, false);
            holder = new ViewHolder();
            holder.tvIcon = convertView.findViewById(R.id.tvIcon);
            holder.tvCategory = convertView.findViewById(R.id.tvCategory);
            holder.tvName = convertView.findViewById(R.id.tvName);
            holder.tvPrice = convertView.findViewById(R.id.tvPrice);
            holder.tvStock = convertView.findViewById(R.id.tvStock);
            convertView.setTag(holder);
        } else {
            // Lấy lại ViewHolder đã lưu sẵn nếu thẻ giao diện được tái sử dụng
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy sản phẩm tại vị trí hiện tại và hiển thị thông tin lên các TextView
        Product product = getItem(position);
        if (product != null) {
            if (holder.tvIcon != null) holder.tvIcon.setText(product.getIcon());
            if (holder.tvCategory != null) holder.tvCategory.setText(product.getCategory());
            if (holder.tvName != null) holder.tvName.setText(product.getName());
            if (holder.tvPrice != null) holder.tvPrice.setText(product.getFormattedPrice());
            if (holder.tvStock != null) {
                int stock = product.getStock();
                if (stock <= 0) {
                    holder.tvStock.setText("Hết hàng");
                    holder.tvStock.setTextColor(0xFFEF4444);
                } else if (stock <= 5) {
                    holder.tvStock.setText("Còn " + stock + " sp");
                    holder.tvStock.setTextColor(0xFFF97316);
                } else {
                    holder.tvStock.setText("Còn " + stock + " sp");
                    holder.tvStock.setTextColor(0xFF64748B);
                }
            }
        }

        return convertView;
    }

    // Lớp giữ tham chiếu các View của một thẻ sản phẩm
    private static class ViewHolder {
        TextView tvIcon;
        TextView tvCategory;
        TextView tvName;
        TextView tvPrice;
        TextView tvStock;
    }
}
