package com.example.e_commerce;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import androidx.core.content.ContextCompat;

public final class PushNotificationManager {

    private static final String TAG = "PushNotifications";
    private static final int REQUEST_NOTIFICATIONS_PERMISSION = 4501;
    private static final String PREFERENCES_NAME = "push_notification_state";
    private static final String KEY_REGISTERED_EMAIL = "registered_email";
    private static final String KEY_REGISTERED_TOKEN = "registered_token";
    private static final String KEY_PERMISSION_REQUESTED = "permission_requested";

    private PushNotificationManager() {
    }

    public static void registerCurrentToken(Context context, String email) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> registerToken(context, email, token))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to retrieve the FCM token.", e));
    }

    public static void registerToken(Context context, String email, String token) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (email.equals(preferences.getString(KEY_REGISTERED_EMAIL, ""))
                && token.equals(preferences.getString(KEY_REGISTERED_TOKEN, ""))) {
            return;
        }

        FirestoreRepository.getInstance().savePushToken(email, token, success -> {
            if (!success) {
                Log.e(TAG, "Failed to save the FCM token.");
                return;
            }
            preferences.edit()
                    .putString(KEY_REGISTERED_EMAIL, email)
                    .putString(KEY_REGISTERED_TOKEN, token)
                    .apply();
        });
    }

    public static void removeCurrentToken(Context context, String email,
            FirestoreRepository.BooleanCallback callback) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                callback.onResult(false);
                return;
            }
            FirestoreRepository.getInstance().removePushToken(email, task.getResult(), success -> {
                if (success) {
                    context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .remove(KEY_REGISTERED_EMAIL)
                            .remove(KEY_REGISTERED_TOKEN)
                            .apply();
                }
                callback.onResult(success);
            });
        });
    }

    public static void requestPermissionIfNeeded(Activity activity) {
        ECommerceMessagingService.createNotificationChannel(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            if (!preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
                preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply();
                activity.requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        REQUEST_NOTIFICATIONS_PERMISSION);
            }
        }
    }
}
