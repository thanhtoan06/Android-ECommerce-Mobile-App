// Bộ chuyển đổi danh sách đơn hàng (Order) thành từng thẻ chi tiết hiển thị trên ListView và tải danh sách sản phẩm đã mua từ Firestore
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

    // Khai báo đối tượng nạp giao diện và kho dữ liệu Firestore
    private final LayoutInflater inflater;
    private final FirestoreRepository repository;

    // Hàm khởi tạo Adapter nhận vào Context và danh sách đơn hàng
    public OrderAdapter(@NonNull Context context, @NonNull List<Order> orders) {
        super(context, 0, orders);
        this.inflater = LayoutInflater.from(context);
        this.repository = FirestoreRepository.getInstance();
    }

    // Tạo và gán dữ liệu từng đơn hàng (mã đơn, sản phẩm đã mua, người nhận, tổng tiền) vào giao diện item_order.xml
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        // Tái sử dụng View cũ để cuộn danh sách đơn hàng mượt mà (ViewHolder)
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
            // Lấy lại ViewHolder đã gắn sẵn nếu dòng giao diện được tái sử dụng
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy đối tượng Order tại vị trí hiện tại và hiển thị thông tin chung của đơn hàng
        Order order = getItem(position);
        if (order != null) {
            holder.tvOrderCode.setText(String.format(Locale.getDefault(), "#DH%05d", order.getId()));
            holder.tvOrderDate.setText("📅 " + order.getOrderDate());
            holder.tvOrderStatus.setText("✓ " + order.getStatus());

            holder.tvReceiverInfo.setText("👤 Người nhận: " + order.getReceiverName() + " (" + order.getReceiverPhone() + ")");
            holder.tvReceiverAddress.setText("📍 Địa chỉ: " + order.getReceiverAddress());
            holder.tvPaymentMethod.setText("💳 Thanh toán: " + order.getPaymentMethod());

            // Xóa các dòng sản phẩm cũ trước khi tải danh sách sản phẩm mới của đơn này
            holder.llOrderItems.removeAllViews();

            // Truy vấn danh sách sản phẩm thuộc đơn hàng từ Firestore
            repository.getOrderItems(order.getId(), new FirestoreRepository.OrderItemsCallback() {
                @Override
                public void onResult(List<OrderItem> items) {
                    holder.llOrderItems.removeAllViews();

                    // Duyệt qua từng sản phẩm đã mua trong đơn, tạo dòng hiển thị động và tính tổng số lượng
                    int totalQty = 0;
                    for (OrderItem item : items) {
                        totalQty += item.getQuantity();

                        // Tạo hàng ngang chứa biểu tượng, tên sản phẩm × số lượng và thành tiền
                        LinearLayout row = new LinearLayout(getContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(0, 6, 0, 6);
                        row.setGravity(Gravity.CENTER_VERTICAL);

                        // Biểu tượng minh họa sản phẩm
                        TextView tvIcon = new TextView(getContext());
                        tvIcon.setText(item.getProductIcon());
                        tvIcon.setTextSize(18f);
                        row.addView(tvIcon);

                        // Tên sản phẩm và số lượng mua
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

                        // Thành tiền của dòng sản phẩm
                        TextView tvSubtotal = new TextView(getContext());
                        tvSubtotal.setText(item.getFormattedSubtotal());
                        tvSubtotal.setTextColor(getContext().getColor(R.color.text_primary));
                        tvSubtotal.setTextSize(13f);
                        tvSubtotal.setTypeface(null, Typeface.BOLD);
                        row.addView(tvSubtotal);

                        holder.llOrderItems.addView(row);
                    }

                    // Hiển thị tổng số lượng sản phẩm và tổng số tiền thanh toán của đơn
                    holder.tvItemCount.setText("Tổng tiền (" + totalQty + " sản phẩm):");
                    holder.tvOrderTotal.setText(order.getFormattedTotal());
                }

                @Override
                public void onError(Exception e) {
                    // Xóa trắng khung danh sách sản phẩm nếu truy vấn bị lỗi
                    holder.llOrderItems.removeAllViews();
                }
            });
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