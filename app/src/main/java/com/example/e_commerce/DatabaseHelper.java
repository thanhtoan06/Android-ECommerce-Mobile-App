package com.example.e_commerce;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DatabaseHelper.java
 * ─────────────────────────────────────────────────────────────────────────────
 * Quản lý toàn bộ cơ sở dữ liệu SQLite cục bộ của ứng dụng (ecommerce.db).
 * Bao gồm các bảng: users, products, cart, orders, order_items.
 * Dùng mẫu Singleton để toàn app dùng chung 1 instance duy nhất.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ecommerce.db";
    private static final int DATABASE_VERSION = 3; // Tăng version khi thêm bảng mới

    // ─── BẢNG USERS: Lưu tài khoản người dùng ───
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FULL_NAME = "full_name";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_AVATAR = "avatar";

    // ─── BẢNG PRODUCTS: Lưu danh sách sản phẩm ───
    public static final String TABLE_PRODUCTS = "products";
    public static final String COLUMN_PROD_ID = "id";
    public static final String COLUMN_PROD_NAME = "name";
    public static final String COLUMN_PROD_CATEGORY = "category";
    public static final String COLUMN_PROD_PRICE = "price";
    public static final String COLUMN_PROD_DESCRIPTION = "description";
    public static final String COLUMN_PROD_ICON = "icon";
    public static final String COLUMN_PROD_COLOR = "color";

    // ─── BẢNG CART: Lưu sản phẩm trong giỏ hàng của từng user ───
    public static final String TABLE_CART = "cart";
    public static final String COLUMN_CART_ID = "id";
    public static final String COLUMN_CART_USER_EMAIL = "user_email";
    public static final String COLUMN_CART_PRODUCT_ID = "product_id";
    public static final String COLUMN_CART_QUANTITY = "quantity";

    // ─── BẢNG ORDERS: Lưu thông tin đơn hàng đã đặt ───
    public static final String TABLE_ORDERS = "orders";
    public static final String COLUMN_ORDER_ID = "id";
    public static final String COLUMN_ORDER_USER_EMAIL = "user_email";
    public static final String COLUMN_ORDER_RECEIVER_NAME = "receiver_name";
    public static final String COLUMN_ORDER_RECEIVER_PHONE = "receiver_phone";
    public static final String COLUMN_ORDER_RECEIVER_ADDRESS = "receiver_address";
    public static final String COLUMN_ORDER_NOTE = "note";
    public static final String COLUMN_ORDER_PAYMENT_METHOD = "payment_method";
    public static final String COLUMN_ORDER_TOTAL_AMOUNT = "total_amount";
    public static final String COLUMN_ORDER_DATE = "order_date";
    public static final String COLUMN_ORDER_STATUS = "status";

    // ─── BẢNG ORDER_ITEMS: Lưu chi tiết từng sản phẩm trong đơn hàng ───
    public static final String TABLE_ORDER_ITEMS = "order_items";
    public static final String COLUMN_OI_ID = "id";
    public static final String COLUMN_OI_ORDER_ID = "order_id";
    public static final String COLUMN_OI_PRODUCT_ID = "product_id";
    public static final String COLUMN_OI_PRODUCT_NAME = "product_name";
    public static final String COLUMN_OI_PRODUCT_ICON = "product_icon";
    public static final String COLUMN_OI_PRICE = "price";
    public static final String COLUMN_OI_QUANTITY = "quantity";
    public static final String COLUMN_OI_SUBTOTAL = "subtotal";

    // Instance duy nhất (Singleton)
    private static DatabaseHelper instance;

    /** Trả về instance duy nhất của DatabaseHelper (thread-safe Singleton) */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ─── Gọi khi tạo DB lần đầu ───
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tạo bảng users
        String createUsersQuery = "CREATE TABLE " + TABLE_USERS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FULL_NAME + " TEXT NOT NULL, "
                + COLUMN_EMAIL + " TEXT UNIQUE NOT NULL, "
                + COLUMN_PASSWORD + " TEXT NOT NULL, "
                + COLUMN_AVATAR + " TEXT DEFAULT '👤'"
                + ")";
        db.execSQL(createUsersQuery);

        // Tài khoản quản trị viên mẫu: admin@gmail.com / 123456
        ContentValues adminValues = new ContentValues();
        adminValues.put(COLUMN_FULL_NAME, "Quản Trị Viên");
        adminValues.put(COLUMN_EMAIL, "admin@gmail.com");
        adminValues.put(COLUMN_PASSWORD, "123456");
        adminValues.put(COLUMN_AVATAR, "👨‍💼");
        db.insert(TABLE_USERS, null, adminValues);

        // Tạo bảng products và nạp dữ liệu mẫu
        createProductsTable(db);
        seedProductsData(db);

        // Tạo bảng cart
        createCartTable(db);

        // Tạo bảng orders và order_items
        createOrdersTable(db);
    }

    // ─── Gọi khi nâng cấp DATABASE_VERSION ───
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // version 1→2: thêm cột avatar vào bảng users
            try {
                db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_AVATAR + " TEXT DEFAULT '👤'");
            } catch (Exception e) { /* Bỏ qua nếu cột đã tồn tại */ }
        }
        if (oldVersion < 3) {
            // version 2→3: thêm các bảng products, cart, orders, order_items
            createProductsTable(db);
            seedProductsData(db);
            createCartTable(db);
            createOrdersTable(db);
        }
    }

    // ─── Gọi mỗi khi DB được mở ─── Đảm bảo các bảng luôn tồn tại
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        createProductsTable(db);
        createCartTable(db);
        createOrdersTable(db);
        checkAndSeedProductsIfEmpty(db); // Tự nạp sản phẩm mẫu nếu bảng trống
    }

    // ─── Tạo bảng products nếu chưa tồn tại ───
    private void createProductsTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_PRODUCTS + " ("
                + COLUMN_PROD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PROD_NAME + " TEXT NOT NULL, "
                + COLUMN_PROD_CATEGORY + " TEXT NOT NULL, "
                + COLUMN_PROD_PRICE + " INTEGER NOT NULL, "
                + COLUMN_PROD_DESCRIPTION + " TEXT NOT NULL, "
                + COLUMN_PROD_ICON + " TEXT NOT NULL, "
                + COLUMN_PROD_COLOR + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    // ─── Tạo bảng cart nếu chưa tồn tại ───
    private void createCartTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_CART + " ("
                + COLUMN_CART_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_CART_USER_EMAIL + " TEXT NOT NULL, "
                + COLUMN_CART_PRODUCT_ID + " INTEGER NOT NULL, "
                + COLUMN_CART_QUANTITY + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    // ─── Tạo bảng orders và order_items nếu chưa tồn tại ───
    private void createOrdersTable(SQLiteDatabase db) {
        String sqlOrders = "CREATE TABLE IF NOT EXISTS " + TABLE_ORDERS + " ("
                + COLUMN_ORDER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_ORDER_USER_EMAIL + " TEXT NOT NULL, "
                + COLUMN_ORDER_RECEIVER_NAME + " TEXT NOT NULL, "
                + COLUMN_ORDER_RECEIVER_PHONE + " TEXT NOT NULL, "
                + COLUMN_ORDER_RECEIVER_ADDRESS + " TEXT NOT NULL, "
                + COLUMN_ORDER_NOTE + " TEXT, "
                + COLUMN_ORDER_PAYMENT_METHOD + " TEXT NOT NULL, "
                + COLUMN_ORDER_TOTAL_AMOUNT + " INTEGER NOT NULL, "
                + COLUMN_ORDER_DATE + " TEXT NOT NULL, "
                + COLUMN_ORDER_STATUS + " TEXT NOT NULL"
                + ")";
        db.execSQL(sqlOrders);

        String sqlOrderItems = "CREATE TABLE IF NOT EXISTS " + TABLE_ORDER_ITEMS + " ("
                + COLUMN_OI_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_OI_ORDER_ID + " INTEGER NOT NULL, "
                + COLUMN_OI_PRODUCT_ID + " INTEGER NOT NULL, "
                + COLUMN_OI_PRODUCT_NAME + " TEXT NOT NULL, "
                + COLUMN_OI_PRODUCT_ICON + " TEXT NOT NULL, "
                + COLUMN_OI_PRICE + " INTEGER NOT NULL, "
                + COLUMN_OI_QUANTITY + " INTEGER NOT NULL, "
                + COLUMN_OI_SUBTOTAL + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sqlOrderItems);
    }

    /** Tự động nạp sản phẩm mẫu nếu bảng products hiện đang trống */
    private void checkAndSeedProductsIfEmpty(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRODUCTS, null);
            if (cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 0) {
                seedProductsData(db);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Nạp 12 sản phẩm mẫu vào bảng products (chỉ chèn nếu ID chưa tồn tại - CONFLICT_IGNORE)
     */
    private void seedProductsData(SQLiteDatabase db) {
        Product[] defaultProducts = {
                new Product(1, "Áo thun cotton basic", "Thời trang", 189000,
                        "Áo thun cotton 100% mềm mịn, thấm hút mồ hôi tốt, form regular dễ phối đồ. Phù hợp mặc hằng ngày, đi làm hoặc đi chơi.",
                        "👕", 0xFFE3F2FD),
                new Product(2, "Giày sneaker trắng thể thao", "Giày dép", 890000,
                        "Giày sneaker trắng thiết kế tối giản, đế cao su êm ái và chống trượt. Dễ phối với quần jean, quần short hay váy.",
                        "👟", 0xFFFFF3E0),
                new Product(3, "Balo laptop chống nước cao cấp", "Phụ kiện", 459000,
                        "Balo vải Oxford chống nước, ngăn đệm chống sốc cho laptop đến 15.6 inch, nhiều ngăn phụ tiện lợi, quai đeo êm vai.",
                        "🎒", 0xFFE8F5E9),
                new Product(4, "Đồng hồ thông minh Smartwatch Pro", "Công nghệ", 1790000,
                        "Đồng hồ thông minh theo dõi nhịp tim, giấc ngủ và số bước chân. Màn hình AMOLED sáng rõ, pin dùng liên tục khoảng 7-10 ngày.",
                        "⌚", 0xFFEDE7F6),
                new Product(5, "Tai nghe không dây Bluetooth TWS", "Công nghệ", 690000,
                        "Tai nghe không dây kết nối Bluetooth 5.3, chống ồn chủ động ANC, âm bass sống động. Hộp sạc cho tổng thời gian nghe đến 28 giờ.",
                        "🎧", 0xFFFCE4EC),
                new Product(6, "Kính mát phân cực UV400", "Phụ kiện", 320000,
                        "Tròng kính Polarized phân cực giảm chói, chống 100% tia UV400. Gọng hợp kim siêu nhẹ bền bỉ khi lái xe hoặc đi biển.",
                        "🕶️", 0xFFE0F7FA),
                new Product(7, "Bình giữ nhiệt inox 304 600ml", "Gia dụng", 245000,
                        "Bình inox 304 cao cấp dung tích 600ml, giữ nóng 12 giờ và giữ lạnh 24 giờ. Nắp khóa kín, không rò rỉ khi bỏ vào balo.",
                        "🥤", 0xFFFFFDE7),
                new Product(8, "Sạc dự phòng 20000mAh PD 22.5W", "Công nghệ", 490000,
                        "Pin dự phòng dung lượng 20000mAh, hỗ trợ sạc nhanh PD 22.5W, màn hình LED hiển thị % pin. Thiết kế nhỏ gọn, an toàn cháy nổ.",
                        "🔋", 0xFFF1F8E9),
                new Product(9, "Mũ lưỡi trai phong cách Unisex", "Thời trang", 129000,
                        "Mũ lưỡi trai vải kaki thoáng mát, khóa kim loại điều chỉnh phía sau. Che nắng thời trang khi ra ngoài.",
                        "🧢", 0xFFFFEBEE),
                new Product(10, "Bàn phím cơ không dây RGB", "Công nghệ", 1250000,
                        "Bàn phím cơ layout 87 phím, switch gõ cực êm, đèn nền LED RGB nhiều chế độ. Hỗ trợ Bluetooth, Wireless 2.4G và Type-C.",
                        "⌨️", 0xFFE8EAF6),
                new Product(11, "Chuột quang không dây Silent", "Công nghệ", 299000,
                        "Chuột bấm siêu êm chống ồn (Silent), cảm biến quang học 4000 DPI độ trễ cực thấp, pin tiết kiệm dùng tới 12 tháng liên tục.",
                        "🖱️", 0xFFE0F2F1),
                new Product(12, "Áo khoác Hoodie nỉ dày dặn", "Thời trang", 359000,
                        "Áo Hoodie chất nỉ lót bông mềm mại giữ ấm hoàn hảo, form dáng oversize trẻ trung, năng động, phong cách đường phố cá tính.",
                        "🧥", 0xFFF3E5F5)
        };

        for (Product product : defaultProducts) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PROD_ID, product.getId());
            values.put(COLUMN_PROD_NAME, product.getName());
            values.put(COLUMN_PROD_CATEGORY, product.getCategory());
            values.put(COLUMN_PROD_PRICE, product.getPrice());
            values.put(COLUMN_PROD_DESCRIPTION, product.getDescription());
            values.put(COLUMN_PROD_ICON, product.getIcon());
            values.put(COLUMN_PROD_COLOR, product.getColor());
            db.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    // ══════════════════════════════════════════════════════
    // SẢN PHẨM (PRODUCTS)
    // ══════════════════════════════════════════════════════

    /** Lấy toàn bộ danh sách sản phẩm từ SQLite, sắp xếp theo ID tăng dần */
    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCTS, null, null, null, null, null, COLUMN_PROD_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROD_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_NAME));
                    String category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_CATEGORY));
                    long price = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROD_PRICE));
                    String description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_DESCRIPTION));
                    String icon = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_ICON));
                    int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROD_COLOR));
                    list.add(new Product(id, name, category, price, description, icon, color));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /** Tìm 1 sản phẩm theo ID; trả về null nếu không tìm thấy */
    public Product getProductById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCTS, null, COLUMN_PROD_ID + " = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_NAME));
                String category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_CATEGORY));
                long price = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROD_PRICE));
                String description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_DESCRIPTION));
                String icon = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_ICON));
                int color = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROD_COLOR));
                return new Product(id, name, category, price, description, icon, color);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    // ══════════════════════════════════════════════════════
    // GIỎ HÀNG (CART)
    // ══════════════════════════════════════════════════════

    /**
     * Thêm sản phẩm vào giỏ hàng của user.
     * - Nếu sản phẩm đã có trong giỏ → cộng dồn số lượng.
     * - Nếu chưa có → chèn bản ghi mới.
     */
    public boolean addToCart(String userEmail, int productId, int quantity) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        try {
            // Kiểm tra sản phẩm đã có trong giỏ chưa
            cursor = db.query(TABLE_CART,
                    new String[]{COLUMN_CART_ID, COLUMN_CART_QUANTITY},
                    COLUMN_CART_USER_EMAIL + " = ? AND " + COLUMN_CART_PRODUCT_ID + " = ?",
                    new String[]{userEmail, String.valueOf(productId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Đã có → cập nhật số lượng
                int cartId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CART_ID));
                int newQty = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CART_QUANTITY)) + quantity;
                ContentValues values = new ContentValues();
                values.put(COLUMN_CART_QUANTITY, newQty);
                return db.update(TABLE_CART, values, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
            } else {
                // Chưa có → chèn mới
                ContentValues values = new ContentValues();
                values.put(COLUMN_CART_USER_EMAIL, userEmail);
                values.put(COLUMN_CART_PRODUCT_ID, productId);
                values.put(COLUMN_CART_QUANTITY, quantity);
                return db.insert(TABLE_CART, null, values) != -1;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Lấy danh sách CartItem của user (JOIN bảng cart + products)
     * để hiển thị đầy đủ tên, giá, icon, số lượng lên màn hình giỏ hàng.
     */
    public List<CartItem> getCartItems(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        List<CartItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // JOIN bảng cart với products để lấy đầy đủ thông tin sản phẩm
        String query = "SELECT c." + COLUMN_CART_ID + ", c." + COLUMN_CART_QUANTITY + ", "
                + "p." + COLUMN_PROD_ID + ", p." + COLUMN_PROD_NAME + ", p." + COLUMN_PROD_CATEGORY + ", "
                + "p." + COLUMN_PROD_PRICE + ", p." + COLUMN_PROD_DESCRIPTION + ", p." + COLUMN_PROD_ICON + ", p." + COLUMN_PROD_COLOR
                + " FROM " + TABLE_CART + " c "
                + " INNER JOIN " + TABLE_PRODUCTS + " p ON c." + COLUMN_CART_PRODUCT_ID + " = p." + COLUMN_PROD_ID
                + " WHERE LOWER(c." + COLUMN_CART_USER_EMAIL + ") = ?"
                + " ORDER BY c." + COLUMN_CART_ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{userEmail});
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int cartId = cursor.getInt(0);
                    int quantity = cursor.getInt(1);
                    int prodId = cursor.getInt(2);
                    String name = cursor.getString(3);
                    String category = cursor.getString(4);
                    long price = cursor.getLong(5);
                    String description = cursor.getString(6);
                    String icon = cursor.getString(7);
                    int color = cursor.getInt(8);
                    Product product = new Product(prodId, name, category, price, description, icon, color);
                    list.add(new CartItem(cartId, product, quantity));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /**
     * Cập nhật số lượng của 1 mục trong giỏ.
     * Nếu newQuantity <= 0 thì tự động xóa luôn mục đó.
     */
    public boolean updateCartQuantity(int cartId, int newQuantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        if (newQuantity <= 0) {
            return db.delete(TABLE_CART, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_CART_QUANTITY, newQuantity);
        return db.update(TABLE_CART, values, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
    }

    /** Xóa 1 sản phẩm khỏi giỏ hàng theo cartId */
    public boolean removeCartItem(int cartId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CART, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
    }

    /** Xóa toàn bộ giỏ hàng của user */
    public boolean clearCart(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?", new String[]{userEmail.trim().toLowerCase()}) >= 0;
    }

    /** Đếm tổng số lượng sản phẩm trong giỏ hàng (dùng hiển thị badge) */
    public int getCartCount(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT SUM(" + COLUMN_CART_QUANTITY + ") FROM " + TABLE_CART
                    + " WHERE LOWER(" + COLUMN_CART_USER_EMAIL + ") = ?", new String[]{userEmail.trim().toLowerCase()});
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }

    // ══════════════════════════════════════════════════════
    // ĐẶT HÀNG / THANH TOÁN (ORDERS)
    // ══════════════════════════════════════════════════════

    /**
     * Tạo đơn hàng mới trong SQLite (sử dụng Transaction để đảm bảo toàn vẹn dữ liệu):
     * 1. Chèn vào bảng orders.
     * 2. Chèn từng sản phẩm vào bảng order_items.
     * 3. Xóa sạch giỏ hàng của user sau khi đặt thành công.
     * @return orderId nếu thành công, -1 nếu thất bại.
     */
    public long createOrder(String userEmail, String receiverName, String receiverPhone,
                            String receiverAddress, String note, String paymentMethod,
                            List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) return -1;
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        // Tính tổng tiền đơn hàng
        long totalAmount = 0;
        for (CartItem item : cartItems) totalAmount += item.getSubtotal();

        String orderDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        String status = "Đặt hàng thành công";

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        long orderId = -1;
        try {
            // 1. Chèn vào bảng orders
            ContentValues orderValues = new ContentValues();
            orderValues.put(COLUMN_ORDER_USER_EMAIL, userEmail);
            orderValues.put(COLUMN_ORDER_RECEIVER_NAME, receiverName.trim());
            orderValues.put(COLUMN_ORDER_RECEIVER_PHONE, receiverPhone.trim());
            orderValues.put(COLUMN_ORDER_RECEIVER_ADDRESS, receiverAddress.trim());
            orderValues.put(COLUMN_ORDER_NOTE, note != null ? note.trim() : "");
            orderValues.put(COLUMN_ORDER_PAYMENT_METHOD, paymentMethod);
            orderValues.put(COLUMN_ORDER_TOTAL_AMOUNT, totalAmount);
            orderValues.put(COLUMN_ORDER_DATE, orderDate);
            orderValues.put(COLUMN_ORDER_STATUS, status);
            orderId = db.insert(TABLE_ORDERS, null, orderValues);
            if (orderId == -1) return -1;

            // 2. Chèn từng sản phẩm vào bảng order_items
            for (CartItem item : cartItems) {
                Product p = item.getProduct();
                ContentValues itemValues = new ContentValues();
                itemValues.put(COLUMN_OI_ORDER_ID, orderId);
                itemValues.put(COLUMN_OI_PRODUCT_ID, p != null ? p.getId() : 0);
                itemValues.put(COLUMN_OI_PRODUCT_NAME, p != null ? p.getName() : "Sản phẩm");
                itemValues.put(COLUMN_OI_PRODUCT_ICON, p != null ? p.getIcon() : "📦");
                itemValues.put(COLUMN_OI_PRICE, p != null ? p.getPrice() : 0);
                itemValues.put(COLUMN_OI_QUANTITY, item.getQuantity());
                itemValues.put(COLUMN_OI_SUBTOTAL, item.getSubtotal());
                if (db.insert(TABLE_ORDER_ITEMS, null, itemValues) == -1) return -1;
            }

            // 3. Xóa sạch giỏ hàng sau khi đặt hàng thành công
            db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?", new String[]{userEmail});
            db.setTransactionSuccessful();
            return orderId;
        } finally {
            db.endTransaction();
        }
    }

    /** Lấy danh sách đơn hàng của user, sắp xếp mới nhất trước (DESC) */
    public List<Order> getUserOrders(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        List<Order> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_ORDERS, null,
                    COLUMN_ORDER_USER_EMAIL + " = ?",
                    new String[]{userEmail}, null, null, COLUMN_ORDER_ID + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ORDER_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_RECEIVER_NAME));
                    String phone = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_RECEIVER_PHONE));
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_RECEIVER_ADDRESS));
                    String note = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_NOTE));
                    String payment = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_PAYMENT_METHOD));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ORDER_TOTAL_AMOUNT));
                    String date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_DATE));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORDER_STATUS));
                    list.add(new Order(id, userEmail, name, phone, address, note, payment, total, date, status));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    /** Lấy danh sách chi tiết sản phẩm (order_items) của 1 đơn hàng theo orderId */
    public List<OrderItem> getOrderItems(long orderId) {
        List<OrderItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_ORDER_ITEMS, null,
                    COLUMN_OI_ORDER_ID + " = ?",
                    new String[]{String.valueOf(orderId)}, null, null, COLUMN_OI_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OI_ID));
                    int prodId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OI_PRODUCT_ID));
                    String prodName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OI_PRODUCT_NAME));
                    String prodIcon = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OI_PRODUCT_ICON));
                    long price = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_OI_PRICE));
                    int quantity = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_OI_QUANTITY));
                    long subtotal = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_OI_SUBTOTAL));
                    list.add(new OrderItem(id, (int) orderId, prodId, prodName, prodIcon, price, quantity, subtotal));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // ══════════════════════════════════════════════════════
    // NGƯỜI DÙNG (USERS)
    // ══════════════════════════════════════════════════════

    /** Kiểm tra email đã được đăng ký chưa (phân biệt chữ thường) */
    public boolean checkEmailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_USERS, new String[]{COLUMN_ID},
                    COLUMN_EMAIL + " = ?", new String[]{email.trim().toLowerCase()},
                    null, null, null);
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /** Đăng ký tài khoản mới kèm avatar; trả về false nếu email đã tồn tại */
    public boolean registerUser(String fullName, String email, String password, String avatar) {
        if (checkEmailExists(email)) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FULL_NAME, fullName.trim());
        values.put(COLUMN_EMAIL, email.trim().toLowerCase());
        values.put(COLUMN_PASSWORD, password);
        values.put(COLUMN_AVATAR, (avatar != null && !avatar.isEmpty()) ? avatar : "👤");
        return db.insert(TABLE_USERS, null, values) != -1;
    }

    /** Đăng ký tài khoản mới với avatar mặc định 👤 */
    public boolean registerUser(String fullName, String email, String password) {
        return registerUser(fullName, email, password, "👤");
    }

    /** Cập nhật avatar của user theo email */
    public boolean updateAvatar(String email, String newAvatar) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AVATAR, newAvatar);
        return db.update(TABLE_USERS, values, COLUMN_EMAIL + " = ?", new String[]{email.trim().toLowerCase()}) > 0;
    }

    /**
     * Xác thực đăng nhập - hỗ trợ 3 cách nhập (không phân biệt hoa thường):
     * 1. Email đầy đủ (vd: admin@gmail.com)
     * 2. Họ và tên (vd: Quản Trị Viên)
     * 3. Phần trước @ của email (vd: admin → khớp admin@gmail.com)
     * @return User nếu đúng, null nếu sai tài khoản hoặc mật khẩu
     */
    public User checkLogin(String account, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String cleanAccount = account != null ? account.trim().toLowerCase() : "";
        String prefixMatch = cleanAccount + "@%"; // Khớp dạng "admin@..."

        try {
            cursor = db.query(TABLE_USERS,
                    new String[]{COLUMN_ID, COLUMN_FULL_NAME, COLUMN_EMAIL, COLUMN_PASSWORD, COLUMN_AVATAR},
                    "(LOWER(" + COLUMN_EMAIL + ") = ? OR LOWER(" + COLUMN_FULL_NAME + ") = ? OR LOWER(" + COLUMN_EMAIL + ") LIKE ?) AND " + COLUMN_PASSWORD + " = ?",
                    new String[]{cleanAccount, cleanAccount, prefixMatch, password},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String fullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME));
                String userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL));
                String userPassword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD));
                String avatar = "👤";
                int avatarIndex = cursor.getColumnIndex(COLUMN_AVATAR);
                if (avatarIndex != -1 && !cursor.isNull(avatarIndex)) avatar = cursor.getString(avatarIndex);
                return new User(id, fullName, userEmail, userPassword, avatar);
            }
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
