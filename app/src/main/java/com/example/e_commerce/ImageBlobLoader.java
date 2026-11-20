package com.example.e_commerce;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageBlobLoader {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService DECODE_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<String, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private ImageBlobLoader() {
    }

    public static void bind(ImageView imageView, byte[] imageBytes, String imageKey,
            @DrawableRes int placeholder) {
        imageView.setImageResource(placeholder);
        if (imageBytes == null || imageBytes.length == 0) {
            imageView.setTag(null);
            return;
        }

        String requestKey = imageKey + ":" + imageBytes.length + ":" + Arrays.hashCode(imageBytes);
        imageView.setTag(requestKey);
        Bitmap cached = BITMAP_CACHE.get(requestKey);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        DECODE_EXECUTOR.execute(() -> {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                return;
            }
            BITMAP_CACHE.put(requestKey, bitmap);
            MAIN_HANDLER.post(() -> {
                if (requestKey.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }
}
