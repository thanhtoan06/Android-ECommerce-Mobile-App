
package com.example.e_commerce;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public class SharedPrefManager {

    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_AVATAR_IMAGE = "avatar_image";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_ADDRESS = "address";

    private static SharedPrefManager instance;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    private SharedPrefManager(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    public void saveUser(String email, String username, String avatar, String phone, String address) {
        saveUser(email, username, avatar, phone, address, null);
    }

    public void saveUser(String email, String username, String avatar, String phone, String address,
            byte[] avatarImageBytes) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_AVATAR, (avatar != null && !avatar.isEmpty()) ? avatar : "👤");
        if (avatarImageBytes == null || avatarImageBytes.length == 0) {
            editor.remove(KEY_AVATAR_IMAGE);
        } else {
            editor.putString(KEY_AVATAR_IMAGE, Base64.encodeToString(avatarImageBytes, Base64.NO_WRAP));
        }
        editor.putString(KEY_PHONE, phone != null ? phone : "");
        editor.putString(KEY_ADDRESS, address != null ? address : "");
        editor.apply();
    }

    public void saveUser(String email, String username, String avatar) {
        saveUser(email, username, avatar, "", "");
    }

    public void updateUsername(String username) {
        editor.putString(KEY_USERNAME, username);
        editor.apply();
    }

    public void updatePhone(String phone) {
        editor.putString(KEY_PHONE, phone != null ? phone : "");
        editor.apply();
    }

    public void updateAddress(String address) {
        editor.putString(KEY_ADDRESS, address != null ? address : "");
        editor.apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getEmail() {
        return sharedPreferences.getString(KEY_EMAIL, "");
    }

    public String getUsername() {
        return sharedPreferences.getString(KEY_USERNAME, "");
    }

    public String getAvatar() {
        return sharedPreferences.getString(KEY_AVATAR, "👤");
    }

    public byte[] getAvatarImageBytes() {
        String encoded = sharedPreferences.getString(KEY_AVATAR_IMAGE, null);
        return encoded == null ? null : Base64.decode(encoded, Base64.NO_WRAP);
    }

    public String getPhone() {
        return sharedPreferences.getString(KEY_PHONE, "");
    }

    public String getAddress() {
        return sharedPreferences.getString(KEY_ADDRESS, "");
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
