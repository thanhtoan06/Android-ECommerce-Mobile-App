/**
 * OrderAdapter.java: Bộ chuyển đổi danh sách đơn hàng Order thành từng thẻ đơn hàng chi tiết hiển thị trên ListView.
 */
package com.example.e_commerce;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

public class OrderAdapter extends ArrayAdapter<Order> {

    private final LayoutInflater inflater;
    private final DatabaseHelper dbHelper;

    // Hàm khởi tạo Adapter nhận vào Context và danh sách đơn hàng
    public OrderAdapter(@NonNull Context context, @NonNull List<Order> orders) {
        super(context, 0, orders);
        this.inflater = LayoutInflater.from(context);
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    // Tạo và gán dữ liệu từng đơn hàng (mã đơn, sản phẩm đã mua, người nhận, tổng tiền) vào giao diện item_order.xml
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_order, parent, false);
            holder = new ViewHolder();
            holder.tvOrderCode = convertView.findViewById(R.id.tvOrderCode);
            holder.tvOrderDate = convertView.findViewById(R.id.tvOrderDate);
            holder.tvOrderStatus = convertView.findViewById(R.id.tvOrderStatus);
            holder.llOrderItems = convertView.findViewById(R.id.llOrderItems);
            holder.tvReceiverInfo = convertView.findViewById(R.id.tvReceiverInfo);
            holder.tvReceiverAddress = convertView.findViewById(R.id.tvReceiverAddress);
            holder.tvPaymentMethod = convertView.findViewById(R.id.tvPaymentMethod);
            holder.tvItemCount = convertView.findViewById(R.id.tvItemCount);
            holder.tvOrderTotal = convertView.findViewById(R.id.tvOrderTotal);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Order order = getItem(position);
        if (order != null) {
            holder.tvOrderCode.setText(String.format(Locale.getDefault(), "#DH%05d", order.getId()));
            holder.tvOrderDate.setText("📅 " + order.getOrderDate());
            holder.tvOrderStatus.setText("✓ " + order.getStatus());

            holder.tvReceiverInfo.setText("👤 Người nhận: " + order.getReceiverName() + " (" + order.getReceiverPhone() + ")");
            holder.tvReceiverAddress.setText("📍 Địa chỉ: " + order.getReceiverAddress());
            holder.tvPaymentMethod.setText("💳 Thanh toán: " + order.getPaymentMethod());

            // Truy vấn bảng order_items trong SQLite để hiển thị từng sản phẩm thuộc đơn hàng này
            List<OrderItem> items = dbHelper.getOrderItems(order.getId());
            holder.llOrderItems.removeAllViews();

            int totalQty = 0;
            for (OrderItem item : items) {
                totalQty += item.getQuantity();

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 6, 0, 6);
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvIcon = new TextView(getContext());
                tvIcon.setText(item.getProductIcon());
                tvIcon.setTextSize(18f);
                row.addView(tvIcon);

                TextView tvName = new TextView(getContext());
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nameParams.setMarginStart(16);
                tvName.setLayoutParams(nameParams);
                tvName.setText(item.getProductName() + "  ×" + item.getQuantity());
                tvName.setTextColor(getContext().getColor(R.color.text_primary));
                tvName.setTextSize(13f);
                tvName.setSingleLine(true);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(tvName);

                TextView tvSubtotal = new TextView(getContext());
                tvSubtotal.setText(item.getFormattedSubtotal());
                tvSubtotal.setTextColor(getContext().getColor(R.color.text_primary));
                tvSubtotal.setTextSize(13f);
                tvSubtotal.setTypeface(null, Typeface.BOLD);
                row.addView(tvSubtotal);

                holder.llOrderItems.addView(row);
            }

            holder.tvItemCount.setText("Tổng tiền (" + totalQty + " sản phẩm):");
            holder.tvOrderTotal.setText(order.getFormattedTotal());
        }

        return convertView;
    }

    // Lớp giữ tham chiếu các thành phần View của một thẻ đơn hàng
    private static class ViewHolder {
        TextView tvOrderCode;
        TextView tvOrderDate;
        TextView tvOrderStatus;
        LinearLayout llOrderItems;
        TextView tvReceiverInfo;
        TextView tvReceiverAddress;
        TextView tvPaymentMethod;
        TextView tvItemCount;
        TextView tvOrderTotal;
    }
}