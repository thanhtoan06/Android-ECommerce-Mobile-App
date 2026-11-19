package com.example.e_commerce;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<AppNotification> notifications;

    public NotificationAdapter(Context context, List<AppNotification> notifications) {
        this.inflater = LayoutInflater.from(context);
        this.notifications = notifications;
    }

    @Override
    public int getCount() {
        return notifications.size();
    }

    @Override
    public AppNotification getItem(int position) {
        return notifications.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? inflater.inflate(R.layout.item_notification, parent, false) : convertView;
        AppNotification notification = getItem(position);

        TextView tvTitle = row.findViewById(R.id.tvNotificationTitle);
        TextView tvMessage = row.findViewById(R.id.tvNotificationMessage);
        TextView tvDate = row.findViewById(R.id.tvNotificationDate);
        TextView tvUnread = row.findViewById(R.id.tvNotificationUnread);

        tvTitle.setText(notification.getTitle());
        tvMessage.setText(notification.getMessage());
        tvDate.setText(formatDate(notification.getCreatedAt()));
        tvUnread.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);
        return row;
    }

    private String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            return "Mới đây";
        }
        Date date = timestamp.toDate();
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(date);
    }
}
