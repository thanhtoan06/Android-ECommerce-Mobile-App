const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { setGlobalOptions } = require("firebase-functions/v2");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

const db = getFirestore();

async function sendOrderPush(email, title, body, orderId, status) {
  const normalizedEmail = String(email || "").trim().toLowerCase();
  if (!normalizedEmail) {
    logger.warn("Skipped order push because the order has no userEmail.");
    return;
  }

  const tokenSnapshot = await db.collection("users").doc(normalizedEmail).collection("fcmTokens").get();
  const tokenEntries = tokenSnapshot.docs
    .map((document) => ({ ref: document.ref, token: document.get("token") }))
    .filter((entry) => typeof entry.token === "string" && entry.token.length > 0);

  if (tokenEntries.length === 0) {
    logger.info("No registered FCM tokens for order owner.", { email: normalizedEmail });
    return;
  }

  for (let start = 0; start < tokenEntries.length; start += 500) {
    const chunk = tokenEntries.slice(start, start + 500);
    const result = await getMessaging().sendEachForMulticast({
      tokens: chunk.map((entry) => entry.token),
      notification: { title, body },
      data: {
        type: status ? "order_status" : "order_placed",
        orderId: String(orderId),
        status: String(status || ""),
        title,
        body,
      },
      android: {
        priority: "high",
        notification: { channelId: "orders" },
      },
    });

    const invalidTokenDeletes = [];
    result.responses.forEach((response, index) => {
      if (!response.success
          && ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]
            .includes(response.error?.code)) {
        invalidTokenDeletes.push(chunk[index].ref.delete());
      }
    });
    await Promise.all(invalidTokenDeletes);
  }
}

function orderCode(orderId) {
  const numericId = Number(orderId);
  return `#DH${Number.isFinite(numericId) ? String(Math.trunc(numericId)).padStart(5, "0") : orderId}`;
}

function javaHashHex(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (Math.imul(31, hash) + value.charCodeAt(index)) | 0;
  }
  return (hash >>> 0).toString(16);
}

async function createNotificationIfMissing(notificationId, data) {
  try {
    await db.collection("notifications").doc(notificationId).create(data);
  } catch (error) {
    if (error.code !== 6 && error.code !== "already-exists") {
      throw error;
    }
  }
}

exports.notifyOrderCreated = onDocumentCreated("orders/{orderId}", async (event) => {
  const order = event.data?.data();
  if (!order) {
    return;
  }

  const orderId = order.id || event.params.orderId;
  const title = "Đặt hàng thành công";
  const body = `Đơn hàng ${orderCode(orderId)} đã được tiếp nhận.`;
  await createNotificationIfMissing(`order_${orderId}_placed`, {
    userEmail: String(order.userEmail || "").trim().toLowerCase(),
    type: "order_placed",
    title,
    message: body,
    orderId: Number(orderId),
    createdAt: FieldValue.serverTimestamp(),
    isRead: false,
  });
  await sendOrderPush(
    order.userEmail,
    title,
    body,
    orderId,
    ""
  );
});

exports.notifyOrderStatusChanged = onDocumentUpdated("orders/{orderId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.status === after.status || !after.status) {
    return;
  }

  const orderId = after.id || event.params.orderId;
  const title = "Cập nhật đơn hàng";
  const body = `Đơn hàng ${orderCode(orderId)} chuyển sang trạng thái: ${after.status}.`;
  await createNotificationIfMissing(
    `order_${orderId}_status_${javaHashHex(after.status)}`,
    {
      userEmail: String(after.userEmail || "").trim().toLowerCase(),
      type: "order_status",
      title,
      message: body,
      orderId: Number(orderId),
      status: String(after.status),
      createdAt: FieldValue.serverTimestamp(),
      isRead: false,
    }
  );
  await sendOrderPush(
    after.userEmail,
    title,
    body,
    orderId,
    after.status
  );
});
