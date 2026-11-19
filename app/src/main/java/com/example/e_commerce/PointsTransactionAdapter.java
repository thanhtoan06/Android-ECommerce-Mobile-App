package com.example.e_commerce;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PointsTransactionAdapter extends BaseAdapter {

    private final Context context;
    private final List<PointsRepository.PointsEntry> entries;
    private final LayoutInflater inflater;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
    private final SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    public PointsTransactionAdapter(Context context, List<PointsRepository.PointsEntry> entries) {
        this.context = context;
        this.entries = entries;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public PointsRepository.PointsEntry getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null
                ? inflater.inflate(R.layout.item_points_transaction, parent, false)
                : convertView;
        PointsRepository.PointsEntry entry = getItem(position);

        ImageView icon = row.findViewById(R.id.ivTransactionIcon);
        TextView title = row.findViewById(R.id.tvTransactionTitle);
        TextView date = row.findViewById(R.id.tvTransactionDate);
        TextView detail = row.findViewById(R.id.tvTransactionDetail);
        TextView amount = row.findViewById(R.id.tvTransactionAmount);

        title.setText(entry.getDescription().isEmpty() ? titleForType(entry.getType()) : entry.getDescription());
        icon.setImageResource(iconForType(entry.getType()));
        icon.setColorFilter(colorForType(entry.getType()));
        amount.setText(formatAmount(entry.getAmount()));
        amount.setTextColor(entry.getAmount() < 0
                ? ContextCompat.getColor(context, R.color.danger_dark)
                : ContextCompat.getColor(context, R.color.success_dark));

        Date createdAt = entry.getCreatedAt();
        date.setText(createdAt == null ? "Đang cập nhật thời gian" : dateFormat.format(createdAt));

        String detailText = detailFor(entry);
        detail.setText(detailText);
        detail.setVisibility(detailText.isEmpty() ? View.GONE : View.VISIBLE);
        return row;
    }

    private String detailFor(PointsRepository.PointsEntry entry) {
        List<String> details = new ArrayList<>();
        if (entry.getOffsetAmount() > 0) {
            if ("recovery".equals(entry.getType())) {
                details.add("Đã trừ " + formatNumber(Math.abs(entry.getNetAmount()))
                        + " Xu · ghi nợ " + formatNumber(entry.getOffsetAmount()) + " Xu");
            } else {
                details.add("Thực nhận " + formatNumber(entry.getNetAmount()) + " Xu · bù trừ "
                        + formatNumber(entry.getOffsetAmount()) + " Xu");
            }
        }
        if (entry.getExpiresAt() != null) {
            details.add("Hết hạn: " + expiryFormat.format(entry.getExpiresAt()));
        }
        details.add("Số dư: " + formatNumber(entry.getBalanceAfter()) + " Xu");
        return android.text.TextUtils.join(" · ", details);
    }

    private int iconForType(String type) {
        switch (type) {
            case "spend":
                return R.drawable.ic_cart;
            case "refund":
                return R.drawable.ic_check_circle;
            case "recovery":
                return R.drawable.ic_delete;
            case "expiry":
                return R.drawable.ic_calendar;
            case "earn":
            default:
                return R.drawable.ic_coin;
        }
    }

    private int colorForType(String type) {
        if ("recovery".equals(type) || "expiry".equals(type) || "spend".equals(type)) {
            return ContextCompat.getColor(context, R.color.danger_dark);
        }
        return ContextCompat.getColor(context, R.color.primary);
    }

    private String titleForType(String type) {
        switch (type) {
            case "spend":
                return "Sử dụng Xu";
            case "refund":
                return "Hoàn Xu";
            case "recovery":
                return "Thu hồi Xu";
            case "expiry":
                return "Xu hết hạn";
            case "earn":
            default:
                return "Nhận Xu";
        }
    }

    private String formatAmount(long value) {
        String prefix = value > 0 ? "+" : value < 0 ? "−" : "";
        return prefix + formatNumber(Math.abs(value)) + " Xu";
    }

    private String formatNumber(long value) {
        return numberFormat.format(value);
    }
}
