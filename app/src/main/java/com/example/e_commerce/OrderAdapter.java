// Adapter hiển thị từng thẻ đơn hàng tinh gọn trong Lịch sử mua hàng; hỗ trợ mở Chi tiết đơn hàng và tải dữ liệu từ Firebase / SQLite.
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

    // Khai báo bộ nạp giao diện XML, kho chính Firebase và kho bản sao SQLite
    private final LayoutInflater inflater;
    private final FirestoreRepository repository;
    private final DatabaseHelper databaseHelper;

    // Hàm khởi tạo OrderAdapter: nhận vào Context và danh sách các đơn hàng cần hiển thị
    public OrderAdapter(@NonNull Context context, @NonNull List<Order> orders) {
        super(context, 0, orders);
        this.inflater = LayoutInflater.from(context);
        this.repository = FirestoreRepository.getInstance();
        this.databaseHelper = DatabaseHelper.getInstance(context);
    }

    // Tạo và đổ dữ liệu cho từng thẻ đơn hàng (mã đơn, ngày đặt, tóm tắt món đã mua, tổng tiền, nút xem chi tiết)
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // Nếu thẻ chưa được tạo thì nạp file giao diện item_order.xml và ánh xạ các thành phần con
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

            // Hiển thị mã đơn hàng dạng chuẩn #DH00001, ngày đặt và trạng thái đơn (dùng icon vector, không emoji)
            holder.tvOrderCode.setText(String.format(Locale.getDefault(), "#DH%05d", currentOrderId));
            holder.tvOrderDate.setText(order.getOrderDate());
            holder.tvOrderStatus.setText(order.getStatus());

            // Hiển thị tổng thanh toán
            holder.tvItemCount.setText("Tổng thanh toán");
            holder.tvOrderTotal.setText(order.getFormattedTotal());

            // Tự động nạp tóm tắt nhanh từ bộ nhớ đệm SQLite nếu có sẵn để hiển thị ngay tức thì
            List<OrderItem> cachedItems = databaseHelper.getOrderItems(currentOrderId);
            if (cachedItems != null && !cachedItems.isEmpty()) {
                updateSummaryText(holder, cachedItems);
            } else {
                holder.tvOrderSummary.setText("Đang tải danh sách sản phẩm...");
            }

            // Xử lý sự kiện bấm nút "Xem chi tiết" hoặc bấm trực tiếp vào thẻ đơn hàng
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

            // Tải chi tiết đơn hàng từ Firebase để cập nhật dữ liệu mới nhất
            repository.getOrderItems(currentOrderId, new FirestoreRepository.OrderItemsCallback() {
                @Override
                public void onResult(List<OrderItem> items) {
                    if (items != null && !items.isEmpty()) {
                        databaseHelper.saveRemoteOrder(order, items);
                        if (holder.boundOrderId == currentOrderId) {
                            updateSummaryText(holder, items);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (holder.boundOrderId == currentOrderId) {
                        List<OrderItem> fallbackItems = databaseHelper.getOrderItems(currentOrderId);
                        if (fallbackItems != null && !fallbackItems.isEmpty()) {
                            updateSummaryText(holder, fallbackItems);
                        } else {
                            holder.tvOrderSummary.setText("Bấm để xem chi tiết đơn hàng");
                        }
                    }
                }
            });
        }

        return convertView;
    }

    // Ghép tóm tắt các món hàng đã mua (ví dụ: Áo thun cotton basic (×2), Giày sneaker (×1))
    private void updateSummaryText(ViewHolder holder, List<OrderItem> items) {
        if (holder.tvOrderSummary == null) return;
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

    // Lớp ViewHolder lưu giữ tham chiếu các thành phần giao diện của 1 thẻ đơn hàng
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
