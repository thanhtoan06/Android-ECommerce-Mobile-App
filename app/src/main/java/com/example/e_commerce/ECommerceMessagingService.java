package com.example.e_commerce;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class ECommerceMessagingService extends FirebaseMessagingService {

    private static final String TAG = "ECommerceMessaging";
    public static final String CHANNEL_ID = "orders";

    @Override
    public void onNewToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getEmail())) {
            return;
        }
        PushNotificationManager.registerToken(this, user.getEmail(), token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String title = message.getNotification() == null ? null : message.getNotification().getTitle();
        String body = message.getNotification() == null ? null : message.getNotification().getBody();
        if (TextUtils.isEmpty(title)) {
            title = message.getData().get("title");
        }
        if (TextUtils.isEmpty(body)) {
            body = message.getData().get("body");
        }
        showNotification(title, body, message.getData().get("orderId"));
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Đơn hàng",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Cập nhật về đơn hàng của bạn");
        manager.createNotificationChannel(channel);
    }

    private void showNotification(String title, String body, String orderId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (!notificationManager.areNotificationsEnabled()) {
            return;
        }

        createNotificationChannel(this);
        int numericOrderId = parseOrderId(orderId);
        Intent intent;
        if (numericOrderId > 0) {
            intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("order_id", numericOrderId);
        } else {
            intent = new Intent(this, NotificationActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notificationId = ((orderId == null ? "" : orderId) + System.currentTimeMillis()
                + (title == null ? 0 : title.hashCode())).hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(TextUtils.isEmpty(title) ? "Thông báo đơn hàng" : title)
                .setContentText(TextUtils.isEmpty(body) ? "Đơn hàng của bạn vừa được cập nhật." : body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(notificationId, notification.build());
    }

    private int parseOrderId(String orderId) {
        if (TextUtils.isEmpty(orderId)) {
            return -1;
        }
        try {
            return Integer.parseInt(orderId);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Push message contains an invalid order ID.", e);
            return -1;
        }
    }
}
