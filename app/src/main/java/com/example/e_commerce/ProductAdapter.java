/**
 * ProductAdapter.java
 * ─────────────────────────────────────────────────────────
 * Adapter kết nối mảng Product[] với ListView trong ProductListActivity.
 * Hiển thị icon sản phẩm (với màu nền động), danh mục, tên, giá và mô tả sản phẩm.
 * Dùng ViewHolder Pattern để tối ưu hiệu suất cuộn ListView.
 * ─────────────────────────────────────────────────────────
 */
package com.example.e_commerce;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ProductAdapter extends ArrayAdapter<Product> {

    private final LayoutInflater inflater; // Bộ nạp layout XML thành View

    // Hàm khởi tạo Adapter
    public ProductAdapter(@NonNull Context context, @NonNull Product[] products) {
        super(context, 0, products);
        this.inflater = LayoutInflater.from(context);
    }

    // Tạo và điền dữ liệu cho từng dòng sản phẩm trong danh sách
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // Tái sử dụng View nếu có thể (ViewHolder pattern)
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_product, parent, false);
            holder = new ViewHolder();
            holder.tvIcon = convertView.findViewById(R.id.tvIcon);
            holder.flIconContainer = convertView.findViewById(R.id.flIconContainer);
            holder.tvCategory = convertView.findViewById(R.id.tvCategory);
            holder.tvName = convertView.findViewById(R.id.tvName);
            holder.tvPrice = convertView.findViewById(R.id.tvPrice);
            holder.tvDescription = convertView.findViewById(R.id.tvDescription);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy thông tin sản phẩm tại vị trí hiện tại
        Product product = getItem(position);
        if (product != null) {
            // Hiển thị icon sản phẩm
            holder.tvIcon.setText(product.getIcon());

            // Thiết lập màu nền cho khung chứa icon
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(0f);
            drawable.setColor(product.getColor());
            holder.flIconContainer.setBackground(drawable);

            // Gán dữ liệu vào các TextView
            holder.tvCategory.setText(product.getCategory());
            holder.tvName.setText(product.getName());
            holder.tvPrice.setText(product.getFormattedPrice());
            holder.tvDescription.setText(product.getDescription());
        }

        return convertView;
    }

    // Lớp lưu trữ các thành phần View của một dòng item để tối ưu hiệu suất
    private static class ViewHolder {
        TextView tvIcon;
        View flIconContainer;
        TextView tvCategory;
        TextView tvName;
        TextView tvPrice;
        TextView tvDescription;
    }
}
