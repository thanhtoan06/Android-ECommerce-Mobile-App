package com.example.e_commerce;

import android.app.Activity;
import android.content.Intent;

public final class AuthGate {

    private AuthGate() {
    }

    public static boolean requireLogin(Activity activity) {
        if (SharedPrefManager.getInstance(activity).isLoggedIn()) {
            return true;
        }

        activity.startActivity(new Intent(activity, LoginActivity.class));
        return false;
    }

    public static void returnToHome(Activity activity) {
        Intent intent = new Intent(activity, ProductListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
