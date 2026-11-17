// Bộ chuyển đổi mảng sản phẩm (Product) thành từng dòng giao diện (item_product.xml) để hiển thị lên ListView
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

    // Khai báo đối tượng nạp giao diện XML cho từng dòng sản phẩm
    private final LayoutInflater inflater;

    // Hàm khởi tạo Adapter nhận vào Context và mảng sản phẩm
    public ProductAdapter(@NonNull Context context, @NonNull Product[] products) {
        super(context, 0, products);
        this.inflater = LayoutInflater.from(context);
    }

    // Tạo và gán dữ liệu thật của từng sản phẩm vào giao diện một dòng (item_product.xml)
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // Tái sử dụng dòng giao diện cũ nếu có để cuộn danh sách mượt hơn (ViewHolder)
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_product, parent, false);
            holder = new ViewHolder();
            holder.tvIcon = convertView.findViewById(R.id.tvIcon);
            holder.tvCategory = convertView.findViewById(R.id.tvCategory);
            holder.tvName = convertView.findViewById(R.id.tvName);
            holder.tvPrice = convertView.findViewById(R.id.tvPrice);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            convertView.setTag(holder);
        } else {
            // Lấy lại ViewHolder đã lưu sẵn nếu dòng giao diện được tái sử dụng
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy sản phẩm tại vị trí hiện tại và hiển thị thông tin lên các TextView
        Product product = getItem(position);
        if (product != null) {
            holder.tvIcon.setText(product.getIcon());
            holder.tvCategory.setText(product.getCategory());
            holder.tvName.setText(product.getName());
            holder.tvPrice.setText(product.getFormattedPrice());
            holder.tvDescription.setText(product.getDescription());
        }

        return convertView;
    }

    // Lớp giữ tham chiếu các View của một dòng sản phẩm để tránh tìm lại findViewById nhiều lần
    private static class ViewHolder {
        TextView tvIcon;
        TextView tvCategory;
        TextView tvName;
        TextView tvPrice;
        TextView tvDescription;
    }
}