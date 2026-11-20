package com.example.e_commerce;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public final class PointsRepository {

    private static final String LOTS_COLLECTION = "pointLots";
    private static final String TRANSACTIONS_COLLECTION = "pointTransactions";
    private static PointsRepository instance;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface DashboardCallback {
        void onResult(PointsDashboard dashboard);

        void onError(Exception e);
    }

    public interface SpendLotsCallback {
        void onResult(List<DocumentSnapshot> lots);

        void onError(Exception e);
    }

    public interface ActionCallback {
        void onResult(ActionResult result);

        void onError(Exception e);
    }

    private interface CompletionCallback {
        void onComplete(Exception error);
    }

    private PointsRepository() {
    }

    public static synchronized PointsRepository getInstance() {
        if (instance == null) {
            instance = new PointsRepository();
        }
        return instance;
    }

    public void getDashboard(String email, DashboardCallback callback) {
        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            loadDashboard(normalizedEmail, callback);
        });
    }

    public void loadSpendableLots(String email, SpendLotsCallback callback) {
        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            loadActiveLots(normalizedEmail, callback::onResult, callback::onError);
        });
    }

    public void checkInToday(String email, ActionCallback callback) {
        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            performCheckIn(normalizedEmail, callback);
        });
    }

    public void grantPoints(String email, long amount, String source, String referenceId, ActionCallback callback) {
        if (amount <= 0 || isBlank(source) || isBlank(referenceId)) {
            callback.onResult(ActionResult.failure("Thông tin nhận Xu không hợp lệ.", 0, 0));
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            recordGrant(normalizedEmail, amount, source, referenceId, callback);
        });
    }

    public void spendPoints(String email, long amount, String referenceId, ActionCallback callback) {
        if (amount <= 0 || isBlank(referenceId)) {
            callback.onResult(ActionResult.failure("Thông tin sử dụng Xu không hợp lệ.", 0, 0));
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            loadActiveLots(normalizedEmail, snapshots -> performSpend(
                    normalizedEmail, amount, referenceId, snapshots, callback), callback::onError);
        });
    }

    public void refundPoints(String email, String spendReferenceId, long amount, String refundReferenceId,
            ActionCallback callback) {
        if (amount <= 0 || isBlank(spendReferenceId) || isBlank(refundReferenceId)) {
            callback.onResult(ActionResult.failure("Thông tin hoàn Xu không hợp lệ.", 0, 0));
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            performRefund(normalizedEmail, spendReferenceId, amount, refundReferenceId, callback);
        });
    }

    public void recoverPoints(String email, long amount, String referenceId, ActionCallback callback) {
        if (amount <= 0 || isBlank(referenceId)) {
            callback.onResult(ActionResult.failure("Thông tin thu hồi Xu không hợp lệ.", 0, 0));
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        expireDueLots(normalizedEmail, error -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            loadActiveLots(normalizedEmail, snapshots -> performRecovery(
                    normalizedEmail, amount, referenceId, snapshots, callback), callback::onError);
        });
    }

    private void loadDashboard(String email, DashboardCallback callback) {
        DocumentReference userRef = userDocument(email);
        userRef.get().addOnSuccessListener(user -> userRef.collection(TRANSACTIONS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(history -> loadActiveLots(email,
                        lots -> callback.onResult(toDashboard(user, history, lots)),
                        callback::onError))
                .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    private PointsDashboard toDashboard(DocumentSnapshot user, QuerySnapshot history,
            List<DocumentSnapshot> activeLots) {
        long balance = nonNegativeLong(user, "pointsBalance");
        long debt = nonNegativeLong(user, "pointsDebt");
        String lastDate = user.getString("lastAttendanceDate");
        int lastDay = (int) nonNegativeLong(user, "attendanceDay");
        PointsRules.CheckInState checkInState = PointsRules.evaluateCheckIn(
                lastDate, lastDay, new Date(), TimeZone.getDefault());

        List<PointsEntry> entries = new ArrayList<>();
        for (DocumentSnapshot document : history.getDocuments()) {
            entries.add(toEntry(document));
        }

        Date nextExpiryDate = null;
        long expiringPoints = 0;
        for (DocumentSnapshot lot : activeLots) {
            Timestamp expiresAt = lot.getTimestamp("expiresAt");
            if (expiresAt == null) {
                continue;
            }
            Date lotExpiryDate = expiresAt.toDate();
            long remaining = nonNegativeLong(lot, "remainingAmount");
            if (nextExpiryDate == null || lotExpiryDate.before(nextExpiryDate)) {
                nextExpiryDate = lotExpiryDate;
                expiringPoints = remaining;
            } else if (lotExpiryDate.equals(nextExpiryDate)) {
                expiringPoints = remaining > Long.MAX_VALUE - expiringPoints
                        ? Long.MAX_VALUE : expiringPoints + remaining;
            }
        }
        return new PointsDashboard(balance, debt, checkInState, entries, expiringPoints, nextExpiryDate);
    }

    private PointsEntry toEntry(DocumentSnapshot document) {
        Long amount = document.getLong("amount");
        Long netAmount = document.getLong("netAmount");
        Long offsetAmount = document.getLong("offsetAmount");
        Long balanceAfter = document.getLong("balanceAfter");
        Long debtAfter = document.getLong("debtAfter");
        Timestamp createdAt = document.getTimestamp("createdAt");
        Timestamp expiresAt = document.getTimestamp("expiresAt");
        return new PointsEntry(
                document.getString("type"),
                document.getString("description"),
                amount == null ? 0 : amount,
                netAmount == null ? 0 : netAmount,
                offsetAmount == null ? 0 : offsetAmount,
                balanceAfter == null ? 0 : balanceAfter,
                debtAfter == null ? 0 : debtAfter,
                createdAt == null ? null : createdAt.toDate(),
                expiresAt == null ? null : expiresAt.toDate());
    }

    private void performCheckIn(String email, ActionCallback callback) {
        Date now = new Date();
        TimeZone timeZone = TimeZone.getDefault();
        String today = PointsRules.dateKey(now, timeZone);
        DocumentReference userRef = userDocument(email);
        DocumentReference transactionRef = transactionsCollection(email).document(UUID.randomUUID().toString());
        DocumentReference lotRef = lotsCollection(email).document(UUID.randomUUID().toString());

        firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            PointsRules.CheckInState state = PointsRules.evaluateCheckIn(
                    user.getString("lastAttendanceDate"),
                    (int) nonNegativeLong(user, "attendanceDay"),
                    now,
                    timeZone);
            long balance = nonNegativeLong(user, "pointsBalance");

            if (state.isDeviceDateInvalid()) {
                return ActionResult.failure("Ngày trên thiết bị không hợp lệ.", balance, 0);
            }
            if (state.isCheckedInToday()) {
                return ActionResult.failure("Bạn đã điểm danh hôm nay.", balance, 0);
            }

            int day = state.getDay();
            long reward = PointsRules.rewardForDay(day);
            long debt = nonNegativeLong(user, "pointsDebt");
            long offset = PointsRules.debtOffset(reward, debt);
            long netReward = reward - offset;
            long newDebt = debt - offset;
            long newBalance = Math.addExact(balance, netReward);
            Date expiresAt = PointsRules.expirationDate(now, timeZone);

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("pointsBalance", newBalance);
            userUpdate.put("pointsDebt", newDebt);
            userUpdate.put("lastAttendanceDate", today);
            userUpdate.put("attendanceDay", day);
            userUpdate.put("pointsUpdatedAt", FieldValue.serverTimestamp());
            transaction.set(userRef, userUpdate, SetOptions.merge());

            if (netReward > 0) {
                transaction.set(lotRef,
                        lotData(netReward, now, expiresAt, "daily_checkin", transactionRef.getId()));
            }
            transaction.set(transactionRef, transactionData(
                    "earn", reward, netReward, offset, newBalance, newDebt, "Điểm danh ngày " + day + ".",
                    "daily_checkin", today, expiresAt));
            String message = offset == 0
                    ? "Điểm danh thành công, nhận " + reward + " Xu."
                    : "Điểm danh thành công, đã bù trừ " + offset + " Xu và nhận " + netReward + " Xu.";
            return ActionResult.success(message,
                    newBalance, newDebt, day, reward);
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    private void recordGrant(String email, long amount, String source, String referenceId, ActionCallback callback) {
        Date now = new Date();
        Date expiresAt = PointsRules.expirationDate(now, TimeZone.getDefault());
        String operationId = operationDocumentId("grant", referenceId);
        DocumentReference userRef = userDocument(email);
        DocumentReference transactionRef = transactionsCollection(email).document(operationId);
        DocumentReference lotRef = lotsCollection(email).document("grant_" + operationId);

        firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            DocumentSnapshot existing = transaction.get(transactionRef);
            long balance = nonNegativeLong(user, "pointsBalance");
            long debt = nonNegativeLong(user, "pointsDebt");
            if (existing.exists()) {
                boolean sameGrant = "earn".equals(existing.getString("type"))
                        && valueOrZero(existing.getLong("amount")) == amount
                        && source.equals(existing.getString("source"));
                return sameGrant
                        ? ActionResult.success("Khoản Xu đã được ghi nhận.", balance, debt, 0, amount)
                        : ActionResult.failure("Mã giao dịch đã được dùng cho khoản Xu khác.", balance, 0);
            }

            long offset = PointsRules.debtOffset(amount, debt);
            long netAmount = amount - offset;
            long newDebt = debt - offset;
            long newBalance = Math.addExact(balance, netAmount);
            updateUserPoints(transaction, userRef, newBalance, newDebt);
            if (netAmount > 0) {
                transaction.set(lotRef, lotData(netAmount, now, expiresAt, source, referenceId));
            }
            transaction.set(transactionRef, transactionData(
                    "earn", amount, netAmount, offset, newBalance, newDebt, "Nhận Xu.",
                    source, referenceId, expiresAt));
            return ActionResult.success("Đã ghi nhận " + netAmount + " Xu.", newBalance, newDebt, 0, amount);
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    private void performSpend(String email, long amount, String referenceId,
            List<DocumentSnapshot> candidateLots, ActionCallback callback) {
        Date now = new Date();
        String operationId = operationDocumentId("spend", referenceId);
        DocumentReference userRef = userDocument(email);
        DocumentReference transactionRef = transactionsCollection(email).document(operationId);
        List<DocumentReference> lotRefs = new ArrayList<>();
        for (DocumentSnapshot lot : candidateLots) {
            lotRefs.add(lot.getReference());
        }

        firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            DocumentSnapshot existing = transaction.get(transactionRef);
            long balance = nonNegativeLong(user, "pointsBalance");
            long debt = nonNegativeLong(user, "pointsDebt");
            if (existing.exists()) {
                boolean sameSpend = "spend".equals(existing.getString("type"))
                        && valueOrZero(existing.getLong("amount")) == -amount;
                return sameSpend
                        ? ActionResult.success("Giao dịch Xu đã được ghi nhận.", balance, debt, 0, amount)
                        : ActionResult.failure("Mã giao dịch đã được dùng cho thao tác Xu khác.", balance, 0);
            }
            if (debt > 0) {
                return ActionResult.failure("Xu đang được dùng để bù trừ khoản thu hồi.", balance, debt);
            }
            if (balance < amount) {
                return ActionResult.failure("Số dư Xu không đủ.", balance, 0);
            }

            List<DocumentSnapshot> lots = new ArrayList<>();
            for (DocumentReference lotRef : lotRefs) {
                lots.add(transaction.get(lotRef));
            }
            List<Map<String, Object>> allocations = new ArrayList<>();
            long remainingToSpend = amount;
            for (DocumentSnapshot lot : lots) {
                if (!lot.exists() || !isUnexpired(lot, now)) {
                    continue;
                }
                long remaining = nonNegativeLong(lot, "remainingAmount");
                long consumed = Math.min(remaining, remainingToSpend);
                if (consumed > 0) {
                    Map<String, Object> allocation = new HashMap<>();
                    allocation.put("lotId", lot.getId());
                    allocation.put("amount", consumed);
                    allocations.add(allocation);
                    remainingToSpend -= consumed;
                }
                if (remainingToSpend == 0) {
                    break;
                }
            }
            if (remainingToSpend > 0) {
                return ActionResult.failure("Không thể đối soát số dư Xu với các khoản còn hạn.", balance, 0);
            }

            for (Map<String, Object> allocation : allocations) {
                String lotId = (String) allocation.get("lotId");
                long consumed = (long) allocation.get("amount");
                DocumentReference lotRef = lotsCollection(email).document(lotId);
                DocumentSnapshot lot = findLotSnapshot(lots, lotId);
                long remaining = nonNegativeLong(lot, "remainingAmount") - consumed;
                transaction.update(lotRef, "remainingAmount", remaining);
            }

            long newBalance = balance - amount;
            updateUserPoints(transaction, userRef, newBalance);
            Map<String, Object> transactionData = transactionData(
                    "spend", -amount, -amount, 0, newBalance, 0,
                    "Sử dụng Xu.", "spend", referenceId, null);
            transactionData.put("allocations", allocations);
            transactionData.put("refundedAmount", 0L);
            transaction.set(transactionRef, transactionData);
            return ActionResult.success("Đã sử dụng " + amount + " Xu.", newBalance, 0, 0, amount);
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    public long spendForOrder(com.google.firebase.firestore.Transaction transaction, String email, long amount,
            String referenceId, long discountedMerchandiseSubtotal, List<DocumentSnapshot> candidateLots)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        if (amount <= 0 || isBlank(referenceId)) {
            throw new IllegalArgumentException("Thông tin sử dụng Xu cho đơn hàng không hợp lệ.");
        }
        if (amount > PointsRules.maximumCheckoutSpend(Long.MAX_VALUE, discountedMerchandiseSubtotal)) {
            throw new IllegalStateException("Số Xu sử dụng vượt quá 50% tiền hàng sau voucher.");
        }

        Date now = new Date();
        DocumentReference userRef = userDocument(email);
        DocumentReference transactionRef = transactionsCollection(email)
                .document(operationDocumentId("spend", referenceId));
        DocumentSnapshot user = transaction.get(userRef);
        DocumentSnapshot existing = transaction.get(transactionRef);
        List<DocumentSnapshot> lots = new ArrayList<>();
        for (DocumentSnapshot candidateLot : candidateLots) {
            lots.add(transaction.get(candidateLot.getReference()));
        }

        long balance = nonNegativeLong(user, "pointsBalance");
        long debt = nonNegativeLong(user, "pointsDebt");
        if (existing.exists()) {
            throw new IllegalStateException("Xu cho đơn hàng này đã được sử dụng.");
        }
        if (debt > 0) {
            throw new IllegalStateException("Xu đang được dùng để bù trừ khoản thu hồi.");
        }
        if (balance < amount) {
            throw new IllegalStateException("Số dư Xu đã thay đổi. Vui lòng kiểm tra lại.");
        }

        List<Map<String, Object>> allocations = new ArrayList<>();
        long remainingToSpend = amount;
        for (DocumentSnapshot lot : lots) {
            if (!lot.exists() || !isUnexpired(lot, now)) {
                continue;
            }
            long remaining = nonNegativeLong(lot, "remainingAmount");
            long consumed = Math.min(remaining, remainingToSpend);
            if (consumed > 0) {
                Map<String, Object> allocation = new HashMap<>();
                allocation.put("lotId", lot.getId());
                allocation.put("amount", consumed);
                allocations.add(allocation);
                remainingToSpend -= consumed;
            }
            if (remainingToSpend == 0) {
                break;
            }
        }
        if (remainingToSpend > 0) {
            throw new IllegalStateException("Không thể đối soát các khoản Xu còn hạn. Vui lòng thử lại.");
        }

        for (Map<String, Object> allocation : allocations) {
            String lotId = (String) allocation.get("lotId");
            long consumed = (long) allocation.get("amount");
            DocumentSnapshot lot = findLotSnapshot(lots, lotId);
            transaction.update(lotsCollection(email).document(lotId),
                    "remainingAmount", nonNegativeLong(lot, "remainingAmount") - consumed);
        }

        long newBalance = balance - amount;
        updateUserPoints(transaction, userRef, newBalance);
        Map<String, Object> spendData = transactionData(
                "spend", -amount, -amount, 0, newBalance, 0,
                "Dùng Xu thanh toán đơn hàng.", "checkout", referenceId, null);
        spendData.put("allocations", allocations);
        spendData.put("refundedAmount", 0L);
        transaction.set(transactionRef, spendData);
        return newBalance;
    }

    boolean grantPointsInTransaction(com.google.firebase.firestore.Transaction transaction, String email, long amount,
            String source, String referenceId) throws com.google.firebase.firestore.FirebaseFirestoreException {
        if (amount <= 0 || isBlank(source) || isBlank(referenceId)) {
            throw new IllegalArgumentException("Thông tin nhận Xu không hợp lệ.");
        }

        String normalizedEmail = normalizeEmail(email);
        Date now = new Date();
        Date expiresAt = PointsRules.expirationDate(now, TimeZone.getDefault());
        String operationId = operationDocumentId("grant", referenceId);
        DocumentReference userRef = userDocument(normalizedEmail);
        DocumentReference transactionRef = transactionsCollection(normalizedEmail).document(operationId);
        DocumentReference lotRef = lotsCollection(normalizedEmail).document("grant_" + operationId);
        DocumentSnapshot user = transaction.get(userRef);
        DocumentSnapshot existing = transaction.get(transactionRef);
        long balance = nonNegativeLong(user, "pointsBalance");
        long debt = nonNegativeLong(user, "pointsDebt");

        if (existing.exists()) {
            boolean sameGrant = "earn".equals(existing.getString("type"))
                    && valueOrZero(existing.getLong("amount")) == amount
                    && source.equals(existing.getString("source"));
            if (!sameGrant) {
                throw new IllegalStateException("Mã giao dịch đã được dùng cho khoản Xu khác.");
            }
            return false;
        }

        long offset = PointsRules.debtOffset(amount, debt);
        long netAmount = amount - offset;
        long newDebt = debt - offset;
        long newBalance = Math.addExact(balance, netAmount);
        updateUserPoints(transaction, userRef, newBalance, newDebt);
        if (netAmount > 0) {
            transaction.set(lotRef, lotData(netAmount, now, expiresAt, source, referenceId));
        }
        transaction.set(transactionRef, transactionData(
                "earn", amount, netAmount, offset, newBalance, newDebt, "Thưởng đánh giá sản phẩm.",
                source, referenceId, expiresAt));
        return true;
    }

    void applyOrderReturnInTransaction(com.google.firebase.firestore.Transaction transaction, String email,
            long orderId, long pointsToRefund, List<String> reviewIds, List<DocumentSnapshot> candidateLots)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        applyOrderRefundInTransaction(
                transaction, email, orderId, pointsToRefund, reviewIds, candidateLots, false);
    }

    void applyOrderCancellationInTransaction(com.google.firebase.firestore.Transaction transaction, String email,
            long orderId, long pointsToRefund)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        applyOrderRefundInTransaction(
                transaction, email, orderId, pointsToRefund, new ArrayList<>(), new ArrayList<>(), true);
    }

    private void applyOrderRefundInTransaction(
            com.google.firebase.firestore.Transaction transaction, String email,
            long orderId, long pointsToRefund, List<String> reviewIds, List<DocumentSnapshot> candidateLots,
            boolean isCancellation)
            throws com.google.firebase.firestore.FirebaseFirestoreException {
        if (pointsToRefund < 0) {
            throw new IllegalArgumentException("Số Xu hoàn không hợp lệ.");
        }

        List<String> reviewDocumentIds = reviewIds == null ? new ArrayList<>() : reviewIds;
        if (pointsToRefund == 0 && reviewDocumentIds.isEmpty()) {
            return;
        }

        String normalizedEmail = normalizeEmail(email);
        DocumentReference userRef = userDocument(normalizedEmail);
        DocumentSnapshot user = transaction.get(userRef);
        long balance = nonNegativeLong(user, "pointsBalance");
        long currentDebt = nonNegativeLong(user, "pointsDebt");
        long pointsAfterRefund = Math.addExact(balance, pointsToRefund);
        Date now = new Date();
        Date expiresAt = PointsRules.expirationDate(now, TimeZone.getDefault());

        String operationType = isCancellation ? "order_cancel" : "order_return";
        String spendReferenceId = String.valueOf(orderId);
        String refundReferenceId = operationType + "_" + orderId;
        DocumentReference spendRef = transactionsCollection(normalizedEmail)
                .document(operationDocumentId("spend", spendReferenceId));
        DocumentReference refundRef = transactionsCollection(normalizedEmail)
                .document(operationDocumentId("refund", refundReferenceId));
        DocumentReference refundLotRef = lotsCollection(normalizedEmail).document("refund_" + refundRef.getId());

        DocumentSnapshot spend = null;
        DocumentSnapshot existingRefund = null;
        DocumentSnapshot existingRefundLot = null;
        if (pointsToRefund > 0) {
            spend = transaction.get(spendRef);
            existingRefund = transaction.get(refundRef);
            existingRefundLot = transaction.get(refundLotRef);
            if (!spend.exists() || !"spend".equals(spend.getString("type"))) {
                throw new IllegalStateException("Không tìm thấy giao dịch Xu đã dùng trong đơn hàng.");
            }
            if (existingRefund.exists() || existingRefundLot.exists()) {
                throw new IllegalStateException("Xu của đơn hàng đã được hoàn trước đó.");
            }
            long originalSpend = Math.abs(valueOrZero(spend.getLong("amount")));
            long alreadyRefunded = nonNegativeLong(spend, "refundedAmount");
            if (pointsToRefund > originalSpend - alreadyRefunded) {
                throw new IllegalStateException("Số Xu hoàn vượt quá số Xu đã dùng cho đơn.");
            }
        }

        String recoveryReferenceId = operationType + "_review_reward_" + orderId;
        DocumentReference recoveryRef = transactionsCollection(normalizedEmail)
                .document(operationDocumentId("recovery", recoveryReferenceId));
        DocumentSnapshot existingRecovery = reviewDocumentIds.isEmpty()
                ? null
                : transaction.get(recoveryRef);
        if (existingRecovery != null && existingRecovery.exists()) {
            throw new IllegalStateException("Xu thưởng đánh giá của đơn đã được thu hồi trước đó.");
        }

        List<DocumentReference> reviewRefs = new ArrayList<>();
        List<DocumentReference> grantRefs = new ArrayList<>();
        List<DocumentSnapshot> reviewSnapshots = new ArrayList<>();
        List<DocumentSnapshot> grantSnapshots = new ArrayList<>();
        for (String reviewId : reviewDocumentIds) {
            DocumentReference reviewRef = firestore.collection("reviews").document(reviewId);
            reviewRefs.add(reviewRef);
            grantRefs.add(transactionsCollection(normalizedEmail)
                    .document(operationDocumentId("grant", reviewId)));
        }
        for (DocumentReference reviewRef : reviewRefs) {
            reviewSnapshots.add(transaction.get(reviewRef));
        }
        for (DocumentReference grantRef : grantRefs) {
            grantSnapshots.add(transaction.get(grantRef));
        }

        List<DocumentSnapshot> lots = new ArrayList<>();
        for (DocumentSnapshot candidateLot : candidateLots) {
            lots.add(transaction.get(candidateLot.getReference()));
        }
        long rewardToRecover = 0;
        List<DocumentReference> earnedReviewRefs = new ArrayList<>();
        for (int i = 0; i < reviewSnapshots.size(); i++) {
            DocumentSnapshot review = reviewSnapshots.get(i);
            DocumentSnapshot grant = grantSnapshots.get(i);
            if (!review.exists() || review.getTimestamp("rewardRecoveredAt") != null
                    || !grant.exists()
                    || !"earn".equals(grant.getString("type"))
                    || !"product_review".equals(grant.getString("source"))
                    || valueOrZero(grant.getLong("amount")) != ReviewRepository.REVIEW_REWARD) {
                continue;
            }
            Long netAmount = grant.getLong("netAmount");
            long recoverableReward = netAmount == null
                    ? ReviewRepository.REVIEW_REWARD
                    : Math.max(0, netAmount);
            if (recoverableReward > 0) {
                rewardToRecover = Math.addExact(rewardToRecover, recoverableReward);
                earnedReviewRefs.add(reviewRefs.get(i));
            }
        }
        if (rewardToRecover > 0 && existingRecovery != null && existingRecovery.exists()) {
            throw new IllegalStateException("Xu thưởng đánh giá của đơn đã được thu hồi trước đó.");
        }

        long availableToRecover = Math.addExact(pointsAfterRefund, 0);
        long targetDeduction = PointsRules.recoverableAmount(availableToRecover, rewardToRecover);
        long remainingToDeduct = targetDeduction;
        List<Map<String, Object>> allocations = new ArrayList<>();
        long refundLotDeduction = 0;
        for (DocumentSnapshot lot : lots) {
            if (remainingToDeduct == 0 || !lot.exists() || !isUnexpired(lot, now)) {
                continue;
            }
            long remaining = nonNegativeLong(lot, "remainingAmount");
            long deducted = Math.min(remaining, remainingToDeduct);
            if (deducted > 0) {
                Map<String, Object> allocation = new HashMap<>();
                allocation.put("lotId", lot.getId());
                allocation.put("amount", deducted);
                allocations.add(allocation);
                remainingToDeduct -= deducted;
            }
        }
        if (pointsToRefund > 0 && remainingToDeduct > 0) {
            refundLotDeduction = Math.min(pointsToRefund, remainingToDeduct);
            if (refundLotDeduction > 0) {
                Map<String, Object> allocation = new HashMap<>();
                allocation.put("lotId", refundLotRef.getId());
                allocation.put("amount", refundLotDeduction);
                allocations.add(allocation);
                remainingToDeduct -= refundLotDeduction;
            }
        }
        long amountDeducted = targetDeduction - remainingToDeduct;
        long finalBalance = pointsAfterRefund - amountDeducted;
        long newDebt = Math.addExact(currentDebt, rewardToRecover - amountDeducted);

        if (pointsToRefund > 0) {
            long alreadyRefunded = nonNegativeLong(spend, "refundedAmount");
            transaction.update(spendRef, "refundedAmount", alreadyRefunded + pointsToRefund);
            Map<String, Object> refundData = transactionData(
                    "refund", pointsToRefund, pointsToRefund, 0, pointsAfterRefund, currentDebt,
                    isCancellation ? "Hoàn Xu cho đơn hàng đã hủy." : "Hoàn Xu cho đơn hàng đã trả.",
                    operationType, refundReferenceId, expiresAt);
            refundData.put("spendReferenceId", spendReferenceId);
            transaction.set(refundRef, refundData);
            Map<String, Object> refundLot = lotData(
                    pointsToRefund, now, expiresAt, "refund", refundReferenceId);
            refundLot.put("remainingAmount", pointsToRefund - refundLotDeduction);
            transaction.set(refundLotRef, refundLot);
        }

        if (rewardToRecover > 0) {
            for (DocumentReference reviewRef : earnedReviewRefs) {
                transaction.update(reviewRef, "rewardRecoveredAt", FieldValue.serverTimestamp());
            }
            Map<String, Object> recoveryData = transactionData(
                    "recovery", -rewardToRecover, -amountDeducted, rewardToRecover - amountDeducted,
                    finalBalance, newDebt, "Thu hồi Xu thưởng đánh giá của đơn hoàn trả.",
                    operationType, recoveryReferenceId, null);
            recoveryData.put("requestedAmount", rewardToRecover);
            recoveryData.put("allocations", allocations);
            transaction.set(recoveryRef, recoveryData);
        }

        for (Map<String, Object> allocation : allocations) {
            String lotId = (String) allocation.get("lotId");
            long deducted = (long) allocation.get("amount");
            if (pointsToRefund > 0 && refundLotRef.getId().equals(lotId)) {
                continue;
            }
            DocumentSnapshot lot = findLotSnapshot(lots, lotId);
            transaction.update(lotsCollection(normalizedEmail).document(lotId),
                    "remainingAmount", nonNegativeLong(lot, "remainingAmount") - deducted);
        }
        if (pointsToRefund > 0 || rewardToRecover > 0) {
            updateUserPoints(transaction, userRef, finalBalance, newDebt);
        }
    }

    private void performRefund(String email, String spendReferenceId, long amount, String refundReferenceId,
            ActionCallback callback) {
        Date now = new Date();
        Date expiresAt = PointsRules.expirationDate(now, TimeZone.getDefault());
        DocumentReference userRef = userDocument(email);
        DocumentReference spendRef = transactionsCollection(email)
                .document(operationDocumentId("spend", spendReferenceId));
        DocumentReference refundRef = transactionsCollection(email)
                .document(operationDocumentId("refund", refundReferenceId));
        DocumentReference lotRef = lotsCollection(email).document("refund_" + refundRef.getId());

        firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            DocumentSnapshot spend = transaction.get(spendRef);
            DocumentSnapshot existingRefund = transaction.get(refundRef);
            long balance = nonNegativeLong(user, "pointsBalance");
            if (existingRefund.exists()) {
                boolean sameRefund = "refund".equals(existingRefund.getString("type"))
                        && valueOrZero(existingRefund.getLong("amount")) == amount
                        && spendReferenceId.equals(existingRefund.getString("spendReferenceId"));
                return sameRefund
                        ? ActionResult.success("Khoản hoàn Xu đã được ghi nhận.", balance, 0, 0, amount)
                        : ActionResult.failure("Mã hoàn đã được dùng cho giao dịch Xu khác.", balance, 0);
            }
            if (!spend.exists() || !"spend".equals(spend.getString("type"))) {
                return ActionResult.failure("Không tìm thấy giao dịch sử dụng Xu để hoàn.", balance, 0);
            }

            long originalAmount = Math.abs(valueOrZero(spend.getLong("amount")));
            long refundedAmount = nonNegativeLong(spend, "refundedAmount");
            if (amount > originalAmount - refundedAmount) {
                return ActionResult.failure("Số Xu hoàn vượt quá số đã sử dụng.", balance, 0);
            }

            long newBalance = Math.addExact(balance, amount);
            updateUserPoints(transaction, userRef, newBalance);
            transaction.update(spendRef, "refundedAmount", refundedAmount + amount);
            transaction.set(lotRef, lotData(amount, now, expiresAt, "refund", refundReferenceId));
            Map<String, Object> refundData = transactionData(
                    "refund", amount, amount, 0, newBalance, 0, "Hoàn Xu.",
                    "refund", refundReferenceId, expiresAt);
            refundData.put("spendReferenceId", spendReferenceId);
            transaction.set(refundRef, refundData);
            return ActionResult.success("Đã hoàn " + amount + " Xu.", newBalance, 0, 0, amount);
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    private void performRecovery(String email, long amount, String referenceId,
            List<DocumentSnapshot> candidateLots, ActionCallback callback) {
        Date now = new Date();
        String operationId = operationDocumentId("recovery", referenceId);
        DocumentReference userRef = userDocument(email);
        DocumentReference transactionRef = transactionsCollection(email).document(operationId);
        List<DocumentReference> lotRefs = new ArrayList<>();
        for (DocumentSnapshot lot : candidateLots) {
            lotRefs.add(lot.getReference());
        }

        firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            DocumentSnapshot existing = transaction.get(transactionRef);
            long balance = nonNegativeLong(user, "pointsBalance");
            long currentDebt = nonNegativeLong(user, "pointsDebt");
            if (existing.exists()) {
                Long requestedAmount = existing.getLong("requestedAmount");
                boolean sameRecovery = "recovery".equals(existing.getString("type"))
                        && (requestedAmount == null
                                ? valueOrZero(existing.getLong("amount")) == -amount
                                : requestedAmount == amount);
                long recoveredAmount = Math.abs(valueOrZero(existing.getLong("netAmount")));
                return sameRecovery
                        ? ActionResult.success(
                                "Khoản thu hồi Xu đã được ghi nhận.", balance, currentDebt, 0, recoveredAmount)
                        : ActionResult.failure("Mã giao dịch đã được dùng cho khoản thu hồi khác.", balance, 0);
            }

            List<DocumentSnapshot> lots = new ArrayList<>();
            for (DocumentReference lotRef : lotRefs) {
                lots.add(transaction.get(lotRef));
            }
            long amountToDeduct = PointsRules.recoverableAmount(balance, amount);
            long remainingToDeduct = amountToDeduct;
            List<Map<String, Object>> allocations = new ArrayList<>();
            for (DocumentSnapshot lot : lots) {
                if (!lot.exists() || !isUnexpired(lot, now)) {
                    continue;
                }
                long remaining = nonNegativeLong(lot, "remainingAmount");
                long deducted = Math.min(remaining, remainingToDeduct);
                if (deducted > 0) {
                    Map<String, Object> allocation = new HashMap<>();
                    allocation.put("lotId", lot.getId());
                    allocation.put("amount", deducted);
                    allocations.add(allocation);
                    remainingToDeduct -= deducted;
                }
                if (remainingToDeduct == 0) {
                    break;
                }
            }
            if (remainingToDeduct > 0) {
                return ActionResult.failure("Không thể đối soát số dư Xu với các khoản còn hạn.", balance, 0);
            }

            for (Map<String, Object> allocation : allocations) {
                String lotId = (String) allocation.get("lotId");
                long deducted = (long) allocation.get("amount");
                DocumentSnapshot lot = findLotSnapshot(lots, lotId);
                transaction.update(lotsCollection(email).document(lotId),
                        "remainingAmount", nonNegativeLong(lot, "remainingAmount") - deducted);
            }

            long newBalance = balance - amountToDeduct;
            long newDebt = Math.addExact(currentDebt, amount - amountToDeduct);
            updateUserPoints(transaction, userRef, newBalance, newDebt);
            String description = amountToDeduct < amount
                    ? "Thu hồi " + amount + " Xu; đã trừ " + amountToDeduct
                            + " Xu và ghi nợ " + (amount - amountToDeduct) + " Xu."
                    : "Thu hồi Xu.";
            Map<String, Object> recoveryData = transactionData(
                    "recovery", -amount, -amountToDeduct, amount - amountToDeduct, newBalance, newDebt, description,
                    "recovery", referenceId, null);
            recoveryData.put("requestedAmount", amount);
            recoveryData.put("allocations", allocations);
            transaction.set(transactionRef, recoveryData);
            return ActionResult.success(
                    "Đã thu hồi " + amountToDeduct + " Xu.", newBalance, newDebt, 0, amountToDeduct);
        }).addOnSuccessListener(callback::onResult)
                .addOnFailureListener(callback::onError);
    }

    private void loadActiveLots(String email,
            com.google.android.gms.tasks.OnSuccessListener<List<DocumentSnapshot>> onSuccess,
            com.google.android.gms.tasks.OnFailureListener onFailure) {
        Timestamp now = Timestamp.now();
        lotsCollection(email)
                .whereGreaterThan("expiresAt", now)
                .orderBy("expiresAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> lots = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (nonNegativeLong(document, "remainingAmount") > 0) {
                            lots.add(document);
                        }
                    }
                    onSuccess.onSuccess(lots);
                })
                .addOnFailureListener(onFailure);
    }

    private void expireDueLots(String email, CompletionCallback callback) {
        Timestamp now = Timestamp.now();
        lotsCollection(email)
                .whereLessThanOrEqualTo("expiresAt", now)
                .orderBy("expiresAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentReference> expiredRefs = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (nonNegativeLong(document, "remainingAmount") > 0) {
                            expiredRefs.add(document.getReference());
                        }
                    }
                    expireNext(email, expiredRefs, 0, callback);
                })
                .addOnFailureListener(callback::onComplete);
    }

    private void expireNext(String email, List<DocumentReference> expiredRefs, int index,
            CompletionCallback callback) {
        if (index >= expiredRefs.size()) {
            callback.onComplete(null);
            return;
        }

        DocumentReference lotRef = expiredRefs.get(index);
        DocumentReference userRef = userDocument(email);
        DocumentReference transactionRef = transactionsCollection(email).document(UUID.randomUUID().toString());
        Date now = new Date();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot user = transaction.get(userRef);
            DocumentSnapshot lot = transaction.get(lotRef);
            long remaining = nonNegativeLong(lot, "remainingAmount");
            Timestamp expiresAt = lot.getTimestamp("expiresAt");
            if (!lot.exists() || remaining == 0 || expiresAt == null || expiresAt.toDate().after(now)) {
                return null;
            }

            long balance = nonNegativeLong(user, "pointsBalance");
            long deducted = Math.min(balance, remaining);
            long newBalance = balance - deducted;
            transaction.update(lotRef, "remainingAmount", 0L, "expiredAt", FieldValue.serverTimestamp());
            updateUserPoints(transaction, userRef, newBalance);
            transaction.set(transactionRef, transactionData(
                    "expiry", -remaining, -deducted, 0, newBalance, 0,
                    "Xu hết hạn.", "expiry", lotRef.getId(), null));
            return null;
        }).addOnSuccessListener(ignored -> expireNext(email, expiredRefs, index + 1, callback))
                .addOnFailureListener(callback::onComplete);
    }

    private boolean isUnexpired(DocumentSnapshot lot, Date now) {
        Timestamp expiresAt = lot.getTimestamp("expiresAt");
        return expiresAt != null && expiresAt.toDate().after(now);
    }

    private Map<String, Object> lotData(long amount, Date receivedAt, Date expiresAt, String source,
            String referenceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("amount", amount);
        data.put("remainingAmount", amount);
        data.put("receivedAt", new Timestamp(receivedAt));
        data.put("expiresAt", new Timestamp(expiresAt));
        data.put("source", source);
        data.put("referenceId", referenceId);
        return data;
    }

    private Map<String, Object> transactionData(String type, long amount, long netAmount, long offsetAmount,
            long balanceAfter, long debtAfter, String description, String source, String referenceId,
            Date expiresAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("amount", amount);
        data.put("netAmount", netAmount);
        data.put("offsetAmount", offsetAmount);
        data.put("balanceAfter", balanceAfter);
        data.put("debtAfter", debtAfter);
        data.put("description", description);
        data.put("source", source);
        data.put("referenceId", referenceId);
        data.put("createdAt", FieldValue.serverTimestamp());
        if (expiresAt != null) {
            data.put("expiresAt", new Timestamp(expiresAt));
        }
        return data;
    }

    private void updateUserPoints(com.google.firebase.firestore.Transaction transaction, DocumentReference userRef,
            long balance) {
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("pointsBalance", balance);
        userUpdate.put("pointsUpdatedAt", FieldValue.serverTimestamp());
        transaction.set(userRef, userUpdate, SetOptions.merge());
    }

    private void updateUserPoints(com.google.firebase.firestore.Transaction transaction, DocumentReference userRef,
            long balance, long debt) {
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("pointsBalance", balance);
        userUpdate.put("pointsDebt", debt);
        userUpdate.put("pointsUpdatedAt", FieldValue.serverTimestamp());
        transaction.set(userRef, userUpdate, SetOptions.merge());
    }

    private DocumentSnapshot findLotSnapshot(List<DocumentSnapshot> lots, String lotId) {
        for (DocumentSnapshot lot : lots) {
            if (lotId.equals(lot.getId())) {
                return lot;
            }
        }
        throw new IllegalStateException("Point lot was not loaded for this transaction.");
    }

    private DocumentReference userDocument(String email) {
        return firestore.collection("users").document(email);
    }

    private CollectionReference lotsCollection(String email) {
        return userDocument(email).collection(LOTS_COLLECTION);
    }

    private CollectionReference transactionsCollection(String email) {
        return userDocument(email).collection(TRANSACTIONS_COLLECTION);
    }

    private String normalizeEmail(String email) {
        if (isBlank(email)) {
            throw new IllegalArgumentException("A signed-in user's email is required for points.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String operationDocumentId(String type, String referenceId) {
        return UUID.nameUUIDFromBytes((type + ":" + referenceId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long nonNegativeLong(DocumentSnapshot document, String field) {
        return Math.max(0, valueOrZero(document.getLong(field)));
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    public static final class PointsDashboard {
        private final long balance;
        private final long debt;
        private final PointsRules.CheckInState checkInState;
        private final List<PointsEntry> history;
        private final long expiringPoints;
        private final Date nextExpiryDate;

        private PointsDashboard(long balance, long debt, PointsRules.CheckInState checkInState,
                List<PointsEntry> history, long expiringPoints, Date nextExpiryDate) {
            this.balance = balance;
            this.debt = debt;
            this.checkInState = checkInState;
            this.history = history;
            this.expiringPoints = expiringPoints;
            this.nextExpiryDate = nextExpiryDate;
        }

        public long getBalance() {
            return balance;
        }

        public long getDebt() {
            return debt;
        }

        public PointsRules.CheckInState getCheckInState() {
            return checkInState;
        }

        public List<PointsEntry> getHistory() {
            return history;
        }

        public long getExpiringPoints() {
            return expiringPoints;
        }

        public Date getNextExpiryDate() {
            return nextExpiryDate;
        }
    }

    public static final class PointsEntry {
        private final String type;
        private final String description;
        private final long amount;
        private final long netAmount;
        private final long offsetAmount;
        private final long balanceAfter;
        private final long debtAfter;
        private final Date createdAt;
        private final Date expiresAt;

        private PointsEntry(String type, String description, long amount, long netAmount, long offsetAmount,
                long balanceAfter, long debtAfter, Date createdAt, Date expiresAt) {
            this.type = type == null ? "" : type;
            this.description = description == null ? "" : description;
            this.amount = amount;
            this.netAmount = netAmount;
            this.offsetAmount = offsetAmount;
            this.balanceAfter = balanceAfter;
            this.debtAfter = debtAfter;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public long getAmount() {
            return amount;
        }

        public long getNetAmount() {
            return netAmount;
        }

        public long getOffsetAmount() {
            return offsetAmount;
        }

        public long getBalanceAfter() {
            return balanceAfter;
        }

        public long getDebtAfter() {
            return debtAfter;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public Date getExpiresAt() {
            return expiresAt;
        }
    }

    public static final class ActionResult {
        private final boolean successful;
        private final String message;
        private final long balance;
        private final long debt;
        private final int day;
        private final long amount;

        private ActionResult(boolean successful, String message, long balance, long debt, int day, long amount) {
            this.successful = successful;
            this.message = message;
            this.balance = balance;
            this.debt = debt;
            this.day = day;
            this.amount = amount;
        }

        private static ActionResult success(String message, long balance, long debt, int day, long amount) {
            return new ActionResult(true, message, balance, debt, day, amount);
        }

        private static ActionResult failure(String message, long balance, long debt) {
            return new ActionResult(false, message, balance, debt, 0, 0);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public long getBalance() {
            return balance;
        }

        public long getDebt() {
            return debt;
        }

        public int getDay() {
            return day;
        }

        public long getAmount() {
            return amount;
        }
    }
}
