
package com.example.e_commerce;

import android.content.Context;
import android.content.Intent;
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
    private final FirestoreRepository repository;

    public OrderAdapter(@NonNull Context context, @NonNull List<Order> orders) {
        super(context, 0, orders);
        this.inflater = LayoutInflater.from(context);
        this.repository = FirestoreRepository.getInstance();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_order, parent, false);
            holder = new ViewHolder();
            holder.cardOrder = convertView.findViewById(R.id.cardOrder);
            holder.tvOrderCode = convertView.findViewById(R.id.tvOrderCode);
            holder.tvOrderDate = convertView.findViewById(R.id.tvOrderDate);
            holder.tvOrderStatus = convertView.findViewById(R.id.tvOrderStatus);
            holder.tvOrderSummary = convertView.findViewById(R.id.tvOrderSummary);
            holder.btnOrderDetail = convertView.findViewById(R.id.btnOrderDetail);
            holder.tvItemCount = convertView.findViewById(R.id.tvItemCount);
            holder.tvOrderTotal = convertView.findViewById(R.id.tvOrderTotal);
            holder.llOrderItems = convertView.findViewById(R.id.llOrderItems);
            holder.tvReceiverInfo = convertView.findViewById(R.id.tvReceiverInfo);
            holder.tvReceiverAddress = convertView.findViewById(R.id.tvReceiverAddress);
            holder.tvPaymentMethod = convertView.findViewById(R.id.tvPaymentMethod);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Order order = getItem(position);
        if (order != null) {
            int currentOrderId = order.getId();
            holder.boundOrderId = currentOrderId;

            holder.tvOrderCode.setText(String.format(Locale.getDefault(), "#DH%05d", currentOrderId));
            holder.tvOrderDate.setText(order.getOrderDate());
            holder.tvOrderStatus.setText(order.getStatus());

            holder.tvItemCount.setText("Tổng thanh toán");
            holder.tvOrderTotal.setText(order.getFormattedTotal());

            holder.tvOrderSummary.setText("Đang tải danh sách sản phẩm...");

            View.OnClickListener openDetailListener = v -> {
                Intent intent = new Intent(getContext(), OrderDetailActivity.class);
                intent.putExtra("order", order);
                intent.putExtra("order_id", currentOrderId);
                getContext().startActivity(intent);
            };

            if (holder.btnOrderDetail != null) {
                holder.btnOrderDetail.setOnClickListener(openDetailListener);
            }
            if (holder.cardOrder != null) {
                holder.cardOrder.setOnClickListener(openDetailListener);
            }

            repository.getOrderItems(currentOrderId, new FirestoreRepository.OrderItemsCallback() {
                @Override
                public void onResult(List<OrderItem> items) {
                    if (items != null && !items.isEmpty()) {
                        if (holder.boundOrderId == currentOrderId) {
                            updateSummaryText(holder, items);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (holder.boundOrderId == currentOrderId) {
                        if (holder.boundOrderId == currentOrderId) {
                            holder.tvOrderSummary.setText("Bấm để xem chi tiết đơn hàng");
                        }
                    }
                }
            });
        }

        return convertView;
    }

    private void updateSummaryText(ViewHolder holder, List<OrderItem> items) {
        if (holder.tvOrderSummary == null)
            return;
        StringBuilder sb = new StringBuilder();
        int totalQty = 0;
        for (OrderItem item : items) {
            totalQty += item.getQuantity();
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item.getProductName()).append(" (×").append(item.getQuantity()).append(")");
        }
        if (sb.length() > 0) {
            holder.tvOrderSummary.setText(sb.toString());
        } else {
            holder.tvOrderSummary.setText("Không có thông tin món hàng");
        }
        if (holder.tvItemCount != null) {
            holder.tvItemCount.setText("Tổng thanh toán (" + totalQty + " món):");
        }
    }

    private static class ViewHolder {
        int boundOrderId;
        View cardOrder;
        TextView tvOrderCode;
        TextView tvOrderDate;
        TextView tvOrderStatus;
        TextView tvOrderSummary;
        View btnOrderDetail;
        TextView tvItemCount;
        TextView tvOrderTotal;
        LinearLayout llOrderItems;
        TextView tvReceiverInfo;
        TextView tvReceiverAddress;
        TextView tvPaymentMethod;
    }
}
