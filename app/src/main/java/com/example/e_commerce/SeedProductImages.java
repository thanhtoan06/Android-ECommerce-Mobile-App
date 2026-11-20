package com.example.e_commerce;

import android.content.Context;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SeedProductImages {

    private static final Map<Integer, String> ASSET_FILES = new LinkedHashMap<>();

    static {
        ASSET_FILES.put(1, "01-samsung-galaxy-a16-5g-128gb.jpg");
        ASSET_FILES.put(2, "02-samsung-galaxy-a55-5g-256gb.jpg");
        ASSET_FILES.put(3, "03-xiaomi-14t-512gb.jpg");
        ASSET_FILES.put(4, "04-asus-vivobook-15-x1504va-i5-16gb-512gb.jpg");
        ASSET_FILES.put(5, "05-macbook-air-13-m3-16gb-256gb.jpg");
        ASSET_FILES.put(6, "06-lenovo-loq-15irx9-i5-rtx4050.jpg");
        ASSET_FILES.put(7, "07-samsung-galaxy-buds-fe.jpg");
        ASSET_FILES.put(8, "08-samsung-galaxy-buds3.jpg");
        ASSET_FILES.put(9, "09-sony-wh-1000xm5.jpg");
        ASSET_FILES.put(10, "10-xiaomi-smart-band-9-pro.jpg");
        ASSET_FILES.put(11, "11-samsung-galaxy-watch7-44mm-bluetooth.jpg");
        ASSET_FILES.put(12, "12-garmin-forerunner-265.jpg");
        ASSET_FILES.put(13, "13-anker-735-ganprime-65w.jpg");
        ASSET_FILES.put(14, "14-anker-325-powercore-20k.jpg");
        ASSET_FILES.put(15, "15-ugreen-revodok-8-in-1-usb-c-hub.jpg");
    }

    private SeedProductImages() {
    }

    public static Map<Integer, byte[]> load(Context context) throws IOException {
        Map<Integer, byte[]> images = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : ASSET_FILES.entrySet()) {
            try {
                images.put(entry.getKey(),
                        ImageDataUtils.compressAsset(context, "products/" + entry.getValue()));
            } catch (IOException error) {
                throw new IOException("Thiếu hoặc không đọc được ảnh products/" + entry.getValue(), error);
            }
        }
        return images;
    }
}
