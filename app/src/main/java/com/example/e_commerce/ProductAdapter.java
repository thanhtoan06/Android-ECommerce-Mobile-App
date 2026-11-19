
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

    private final LayoutInflater inflater;

    public ProductAdapter(@NonNull Context context, @NonNull Product[] products) {
        super(context, 0, products);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

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

            holder = (ViewHolder) convertView.getTag();
        }

        Product product = getItem(position);
        if (product != null) {
            if (holder.tvIcon != null)
                holder.tvIcon.setText(product.getIcon());
            if (holder.tvCategory != null)
                holder.tvCategory.setText(product.getCategory());
            if (holder.tvName != null)
                holder.tvName.setText(product.getName());
            if (holder.tvPrice != null)
                holder.tvPrice.setText(product.getFormattedPrice());
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

    private static class ViewHolder {
        TextView tvIcon;
        TextView tvCategory;
        TextView tvName;
        TextView tvPrice;
        TextView tvStock;
    }
}
