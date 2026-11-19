package com.example.e_commerce;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VoucherRepository {

    private static VoucherRepository instance;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface WalletCallback {
        void onResult(VoucherWallet wallet);

        void onError(Exception error);
    }

    public interface SaveCallback {
        void onResult(SaveResult result);

        void onError(Exception error);
    }

    private VoucherRepository() {
    }

    public static synchronized VoucherRepository getInstance() {
        if (instance == null) {
            instance = new VoucherRepository();
        }
        return instance;
    }

    public void loadWallet(String email, WalletCallback callback) {
        String normalizedEmail = normalizeEmail(email);
        InitialCatalogSeeder.seedCatalogAndVouchersIfNeeded(firestore)
                .addOnSuccessListener(ignored -> firestore.collection("vouchers")
                        .orderBy("sortOrder", Query.Direction.ASCENDING)
                        .get()
                        .addOnSuccessListener(catalog -> userVouchers(normalizedEmail).get()
                                .addOnSuccessListener(owned -> callback.onResult(toWallet(catalog, owned)))
                                .addOnFailureListener(callback::onError))
                        .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    public void saveVoucher(String email, String voucherId, SaveCallback callback) {
        if (isBlank(voucherId)) {
            callback.onResult(SaveResult.failure("Không tìm thấy voucher cần lưu."));
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        DocumentReference catalogRef = firestore.collection("vouchers").document(voucherId);
        DocumentReference ownedRef = userVouchers(normalizedEmail).document(voucherId);
        Date now = new Date();

        firestore.runTransaction(transaction -> {
            DocumentSnapshot catalog = transaction.get(catalogRef);
            DocumentSnapshot owned = transaction.get(ownedRef);
            if (!catalog.exists()) {
                return SaveResult.failure("Voucher không còn tồn tại.");
            }
            if (owned.exists()) {
                return SaveResult.failure("Voucher này đã được lưu hoặc sử dụng.");
            }

            Voucher voucher = Voucher.fromDocument(catalog);
            if (!voucher.isActive()
                    || (voucher.getCampaignEndsAt() != null && !voucher.getCampaignEndsAt().after(now))) {
                return SaveResult.failure("Voucher đã hết hạn hoặc ngừng áp dụng.");
            }
            if (voucher.getValidityAfterSaveDays() <= 0) {
                return SaveResult.failure("Thời hạn voucher chưa được cấu hình hợp lệ.");
            }

            Calendar expiry = Calendar.getInstance();
            expiry.setTime(now);
            expiry.add(Calendar.DAY_OF_YEAR, voucher.getValidityAfterSaveDays());
            Date expiresAt = expiry.getTime();
            Date campaignEndsAt = voucher.getCampaignEndsAt();
            if (campaignEndsAt != null && campaignEndsAt.before(expiresAt)) {
                expiresAt = campaignEndsAt;
            }
            if (!expiresAt.after(now)) {
                return SaveResult.failure("Voucher đã hết hạn.");
            }

            java.util.Map<String, Object> savedData = new java.util.HashMap<>(catalog.getData());
            savedData.put("id", voucherId);
            savedData.put("state", "saved");
            savedData.put("savedAt", new Timestamp(now));
            savedData.put("expiresAt", new Timestamp(expiresAt));
            savedData.put("usedAt", null);
            savedData.put("isActive", true);
            transaction.set(ownedRef, savedData);
            return SaveResult.success("Đã lưu voucher vào kho của bạn.");
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    public static DocumentReference userVoucherDocument(FirebaseFirestore firestore, String email,
            String voucherId) {
        return firestore.collection("users").document(normalizeEmail(email))
                .collection("vouchers").document(voucherId);
    }

    private VoucherWallet toWallet(QuerySnapshot catalog, QuerySnapshot owned) {
        List<Voucher> available = new ArrayList<>();
        List<Voucher> saved = new ArrayList<>();
        List<Voucher> used = new ArrayList<>();
        Set<String> ownedIds = new HashSet<>();

        for (DocumentSnapshot document : owned.getDocuments()) {
            Voucher voucher = Voucher.fromDocument(document);
            ownedIds.add(voucher.getId());
            if (voucher.isUsed()) {
                used.add(voucher);
            } else if (voucher.isSaved()) {
                saved.add(voucher);
            }
        }

        Date now = new Date();
        for (DocumentSnapshot document : catalog.getDocuments()) {
            Voucher voucher = Voucher.fromDocument(document);
            Date campaignEndsAt = voucher.getCampaignEndsAt();
            if (voucher.isActive()
                    && (campaignEndsAt == null || campaignEndsAt.after(now))
                    && !ownedIds.contains(voucher.getId())) {
                available.add(voucher);
            }
        }
        return new VoucherWallet(available, saved, used);
    }

    private CollectionReference userVouchers(String email) {
        return firestore.collection("users").document(email).collection("vouchers");
    }

    private static String normalizeEmail(String email) {
        if (isBlank(email)) {
            throw new IllegalArgumentException("A signed-in user's email is required for vouchers.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class VoucherWallet {
        private final List<Voucher> available;
        private final List<Voucher> saved;
        private final List<Voucher> used;

        private VoucherWallet(List<Voucher> available, List<Voucher> saved, List<Voucher> used) {
            this.available = available;
            this.saved = saved;
            this.used = used;
        }

        public List<Voucher> getAvailable() {
            return available;
        }

        public List<Voucher> getSaved() {
            return saved;
        }

        public List<Voucher> getUsed() {
            return used;
        }
    }

    public static final class SaveResult {
        private final boolean successful;
        private final String message;

        private SaveResult(boolean successful, String message) {
            this.successful = successful;
            this.message = message;
        }

        private static SaveResult success(String message) {
            return new SaveResult(true, message);
        }

        private static SaveResult failure(String message) {
            return new SaveResult(false, message);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }
    }
}
