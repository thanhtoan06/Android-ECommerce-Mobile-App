
package com.example.e_commerce;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class CartAdapter extends ArrayAdapter<CartItem> {

    public interface OnCartItemListener {
        void onQuantityChanged(CartItem item, int newQuantity);

        void onItemDeleted(CartItem item);

        void onItemSelectionChanged(CartItem item, boolean isSelected);
    }

    private final LayoutInflater inflater;
    private final OnCartItemListener listener;

    public CartAdapter(@NonNull Context context, @NonNull List<CartItem> items, OnCartItemListener listener) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_cart, parent, false);
            holder = new ViewHolder();
            holder.cbItem = convertView.findViewById(R.id.cbCartItem);
            holder.ivProductImage = convertView.findViewById(R.id.ivCartProductImage);
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

            holder = (ViewHolder) convertView.getTag();
        }

        CartItem item = getItem(position);
        if (item != null) {

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

                ImageBlobLoader.bind(holder.ivProductImage, product.getImageBytes(),
                        "product-" + product.getId(), R.drawable.ic_product_image_placeholder);
                holder.tvCategory.setText(product.getCategory());
                holder.tvName.setText(product.getName());

                if (stock <= 0) {
                    holder.tvUnitPrice.setText("Đơn giá: " + product.getFormattedPrice() + " (Hết hàng)");
                    holder.tvUnitPrice.setTextColor(Color.parseColor("#EF4444"));

                    holder.btnPlus.setEnabled(false);
                    holder.btnPlus.setAlpha(0.3f);
                } else {
                    holder.tvUnitPrice.setText("Đơn giá: " + product.getFormattedPrice() + " (Kho: " + stock + ")");
                    holder.tvUnitPrice.setTextColor(Color.parseColor("#64748B"));

                    if (item.getQuantity() >= stock) {
                        holder.btnPlus.setEnabled(false);
                        holder.btnPlus.setAlpha(0.3f);
                    } else {
                        holder.btnPlus.setEnabled(true);
                        holder.btnPlus.setAlpha(1.0f);
                    }
                }
            }

            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
            holder.tvSubtotal.setText(item.getFormattedSubtotal());

            holder.btnMinus.setOnClickListener(v -> {
                if (listener != null)
                    listener.onQuantityChanged(item, item.getQuantity() - 1);
            });

            holder.btnPlus.setOnClickListener(v -> {
                if (product != null) {
                    if (stock <= 0) {
                        Toast.makeText(getContext(), "Sản phẩm \"" + product.getName() + "\" hiện đã hết hàng!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (item.getQuantity() >= stock) {
                        Toast.makeText(getContext(), "Đã đạt số lượng tồn kho tối đa (" + stock + " sp)!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (listener != null)
                    listener.onQuantityChanged(item, item.getQuantity() + 1);
            });

            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null)
                    listener.onItemDeleted(item);
            });
        }

        return convertView;
    }

    private static class ViewHolder {
        CheckBox cbItem;
        ImageView ivProductImage;
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
