// Adapter hiển thị từng thẻ đơn hàng trong Lịch sử mua hàng; tải chi tiết sản phẩm trực tiếp từ Firebase và lưu bản sao SQLite.
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

    // Tạo và đổ dữ liệu cho từng thẻ đơn hàng (mã đơn, ngày đặt, danh sách món đã mua, người nhận, tổng tiền)
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // Khai báo biến ViewHolder giữ sẵn các ô chữ của 1 thẻ đơn hàng để tái sử dụng cho mượt
        ViewHolder holder;

        // Nếu thẻ chưa được tạo thì nạp file giao diện item_order.xml và ánh xạ các thành phần con
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
            // Gắn ViewHolder vào thẻ giao diện này để lần sau tái sử dụng ngay
            convertView.setTag(holder);
        } else {
            // Lấy lại ViewHolder đã lưu sẵn nếu thẻ giao diện được tái sử dụng
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy đơn hàng tại vị trí hiện tại và đổ các thông tin chung lên thẻ
        Order order = getItem(position);
        if (order != null) {
            // Đánh dấu mã đơn hàng đang gắn với thẻ này để tránh vẽ nhầm khi cuộn danh sách nhanh
            int currentOrderId = order.getId();
            holder.boundOrderId = currentOrderId;

            // Hiển thị mã đơn hàng (ví dụ: #DH00001), ngày đặt và trạng thái đơn
            holder.tvOrderCode.setText(String.format(Locale.getDefault(), "#DH%05d", currentOrderId));
            holder.tvOrderDate.setText("📅 " + order.getOrderDate());
            holder.tvOrderStatus.setText("✓ " + order.getStatus());

            // Hiển thị tên + SĐT người nhận, địa chỉ giao hàng và phương thức thanh toán
            holder.tvReceiverInfo.setText("👤 Người nhận: " + order.getReceiverName() + " (" + order.getReceiverPhone() + ")");
            holder.tvReceiverAddress.setText("📍 Địa chỉ: " + order.getReceiverAddress());
            holder.tvPaymentMethod.setText("💳 Thanh toán: " + order.getPaymentMethod());

            // Hiển thị sẵn tổng tiền đơn hàng trong lúc chờ tải chi tiết từng món
            holder.tvItemCount.setText("Tổng tiền:");
            holder.tvOrderTotal.setText(order.getFormattedTotal());

            // Xóa sạch các dòng sản phẩm cũ của thẻ trước đó (do tái sử dụng giao diện)
            holder.llOrderItems.removeAllViews();

            // Đọc danh sách chi tiết các món hàng thuộc đơn này từ Firebase (kho chính)
            repository.getOrderItems(currentOrderId, new FirestoreRepository.OrderItemsCallback() {
                @Override
                public void onResult(List<OrderItem> items) {
                    // Lưu bản sao chi tiết đơn hàng xuống SQLite sau khi tải thành công từ Firebase
                    databaseHelper.saveRemoteOrder(order, items);

                    // Kiểm tra thẻ giao diện này vẫn đang hiển thị đúng đơn hàng ban đầu trước khi vẽ
                    if (holder.boundOrderId != currentOrderId) return;

                    // Xóa danh sách cũ và duyệt từng món hàng để vẽ dòng hiển thị
                    holder.llOrderItems.removeAllViews();
                    int totalQty = 0;
                    for (OrderItem item : items) {
                        totalQty += item.getQuantity();

                        // Tạo một hàng ngang mới chứa: biểu tượng + tên sản phẩm + thành tiền
                        LinearLayout row = new LinearLayout(getContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(0, 6, 0, 6);
                        row.setGravity(Gravity.CENTER_VERTICAL);

                        // Tạo ô chữ hiển thị biểu tượng (icon) của sản phẩm và thêm vào hàng
                        TextView tvIcon = new TextView(getContext());
                        tvIcon.setText(item.getProductIcon());
                        tvIcon.setTextSize(18f);
                        row.addView(tvIcon);

                        // Tạo ô chữ hiển thị tên sản phẩm kèm số lượng mua (ví dụ: Áo thun ×2)
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

                        // Tạo ô chữ in đậm hiển thị thành tiền của món hàng đó và thêm vào hàng
                        TextView tvSubtotal = new TextView(getContext());
                        tvSubtotal.setText(item.getFormattedSubtotal());
                        tvSubtotal.setTextColor(getContext().getColor(R.color.text_primary));
                        tvSubtotal.setTextSize(13f);
                        tvSubtotal.setTypeface(null, Typeface.BOLD);
                        row.addView(tvSubtotal);

                        // Đưa nguyên hàng sản phẩm vừa tạo vào khung danh sách món của đơn hàng
                        holder.llOrderItems.addView(row);
                    }

                    // Cập nhật lại tổng số lượng sản phẩm và tổng tiền của đơn hàng
                    holder.tvItemCount.setText("Tổng tiền (" + totalQty + " sản phẩm):");
                    holder.tvOrderTotal.setText(order.getFormattedTotal());
                }

                @Override
                public void onError(Exception e) {
                    // Nếu lỗi mạng khi tải chi tiết thì làm trống khung danh sách món
                    if (holder.boundOrderId == currentOrderId) {
                        holder.llOrderItems.removeAllViews();
                    }
                }
            });
        }

        return convertView;
    }

    // Lớp ViewHolder lưu giữ tham chiếu các thành phần giao diện của 1 thẻ đơn hàng
    private static class ViewHolder {
        int boundOrderId;
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