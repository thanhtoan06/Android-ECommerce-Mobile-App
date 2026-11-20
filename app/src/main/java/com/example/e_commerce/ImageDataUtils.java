package com.example.e_commerce;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageDataUtils {

    public static final int MAX_IMAGE_BYTES = 300 * 1024;
    private static final int PRODUCT_MAX_DIMENSION = 1400;
    private static final int AVATAR_MAX_DIMENSION = 512;
    private static final ExecutorService IMAGE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface ImageCallback {
        void onSuccess(byte[] imageBytes);

        void onError(Exception error);
    }

    private interface ImageSource {
        InputStream open() throws IOException;
    }

    private ImageDataUtils() {
    }

    public static byte[] compressAsset(Context context, String assetPath) throws IOException {
        Context appContext = context.getApplicationContext();
        return compress(() -> appContext.getAssets().open(assetPath), PRODUCT_MAX_DIMENSION);
    }

    public static void compressUriAsync(Context context, Uri uri, boolean avatar, ImageCallback callback) {
        Context appContext = context.getApplicationContext();
        int maxDimension = avatar ? AVATAR_MAX_DIMENSION : PRODUCT_MAX_DIMENSION;
        IMAGE_EXECUTOR.execute(() -> {
            try {
                byte[] compressed = compress(() -> appContext.getContentResolver().openInputStream(uri), maxDimension);
                MAIN_HANDLER.post(() -> callback.onSuccess(compressed));
            } catch (IOException | RuntimeException error) {
                MAIN_HANDLER.post(() -> callback.onError(error));
            }
        });
    }

    private static byte[] compress(ImageSource source, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = source.open()) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Tệp đã chọn không phải ảnh hợp lệ.");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxDimension) {
            options.inSampleSize *= 2;
        }

        Bitmap bitmap;
        try (InputStream stream = source.open()) {
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Không thể đọc ảnh đã chọn.");
        }

        int quality = 85;
        try {
            for (int attempt = 0; attempt < 12; attempt++) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw new IOException("Không thể nén ảnh.");
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length <= MAX_IMAGE_BYTES) {
                    return bytes;
                }

                if (quality > 45) {
                    quality -= 10;
                } else {
                    int width = Math.max(1, (int) (bitmap.getWidth() * 0.8f));
                    int height = Math.max(1, (int) (bitmap.getHeight() * 0.8f));
                    Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);
                    if (resized != bitmap) {
                        bitmap.recycle();
                        bitmap = resized;
                    }
                    quality = 80;
                }
            }
        } finally {
            bitmap.recycle();
        }
        throw new IOException("Ảnh quá lớn sau khi nén. Hãy chọn ảnh khác.");
    }
}
