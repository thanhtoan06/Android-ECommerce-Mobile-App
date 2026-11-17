//Quản lý cơ sở dữ liệu SQLite cục bộ (gồm 5 bảng: users, products, cart, orders, order_items).
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

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ecommerce.db";
    private static final int DATABASE_VERSION = 6;

    // Tên bảng và các cột của bảng Người dùng (users)
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FULL_NAME = "full_name";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_AVATAR = "avatar";
    public static final String COLUMN_PHONE = "phone";
    public static final String COLUMN_ADDRESS = "address";

    // Tên bảng và các cột của bảng Sản phẩm (products)
    public static final String TABLE_PRODUCTS = "products";
    public static final String COLUMN_PROD_ID = "id";
    public static final String COLUMN_PROD_NAME = "name";
    public static final String COLUMN_PROD_CATEGORY = "category";
    public static final String COLUMN_PROD_PRICE = "price";
    public static final String COLUMN_PROD_DESCRIPTION = "description";
    public static final String COLUMN_PROD_ICON = "icon";

    // Tên bảng và các cột của bảng Giỏ hàng (cart)
    public static final String TABLE_CART = "cart";
    public static final String COLUMN_CART_ID = "id";
    public static final String COLUMN_CART_USER_EMAIL = "user_email";
    public static final String COLUMN_CART_PRODUCT_ID = "product_id";
    public static final String COLUMN_CART_QUANTITY = "quantity";

    // Tên bảng và các cột của bảng Đơn hàng (orders)
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

    // Tên bảng và các cột của bảng Chi tiết đơn hàng (order_items)
    public static final String TABLE_ORDER_ITEMS = "order_items";
    public static final String COLUMN_OI_ID = "id";
    public static final String COLUMN_OI_ORDER_ID = "order_id";
    public static final String COLUMN_OI_PRODUCT_ID = "product_id";
    public static final String COLUMN_OI_PRODUCT_NAME = "product_name";
    public static final String COLUMN_OI_PRODUCT_ICON = "product_icon";
    public static final String COLUMN_OI_PRICE = "price";
    public static final String COLUMN_OI_QUANTITY = "quantity";
    public static final String COLUMN_OI_SUBTOTAL = "subtotal";

    private static DatabaseHelper instance;

    // Lấy đối tượng DatabaseHelper dùng chung cho toàn bộ ứng dụng (Singleton)
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    // Hàm khởi tạo kết nối cơ sở dữ liệu SQLite
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Tự động chạy lần đầu tiên khi tạo database: tạo các bảng và nạp 12 sản phẩm mẫu
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsersQuery = "CREATE TABLE " + TABLE_USERS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_FULL_NAME + " TEXT NOT NULL, "
                + COLUMN_EMAIL + " TEXT UNIQUE NOT NULL, "
                + COLUMN_PASSWORD + " TEXT NOT NULL, "
                + COLUMN_AVATAR + " TEXT DEFAULT '👤', "
                + COLUMN_PHONE + " TEXT, "
                + COLUMN_ADDRESS + " TEXT"
                + ")";
        db.execSQL(createUsersQuery);

        createProductsTable(db);
        seedProductsData(db);
        createCartTable(db);
        createOrdersTable(db);
    }

    // Tự động chạy khi nâng cấp DATABASE_VERSION
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            ensureUserColumns(db);
        } else {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDER_ITEMS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CART);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            onCreate(db);
        }
    }

    // Chạy mỗi khi mở database: đảm bảo các bảng luôn tồn tại và có sẵn dữ liệu sản phẩm
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureUserColumns(db);
        createProductsTable(db);
        createCartTable(db);
        createOrdersTable(db);
        checkAndSeedProductsIfEmpty(db);
    }

    private void ensureUserColumns(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_PHONE + " TEXT");
        } catch (Exception ignored) {}
        try {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_ADDRESS + " TEXT");
        } catch (Exception ignored) {}
    }

    // Tạo bảng lưu danh sách sản phẩm (products)
    private void createProductsTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_PRODUCTS + " ("
                + COLUMN_PROD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PROD_NAME + " TEXT NOT NULL, "
                + COLUMN_PROD_CATEGORY + " TEXT NOT NULL, "
                + COLUMN_PROD_PRICE + " INTEGER NOT NULL, "
                + COLUMN_PROD_DESCRIPTION + " TEXT NOT NULL, "
                + COLUMN_PROD_ICON + " TEXT NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    // Tạo bảng lưu giỏ hàng của người dùng (cart)
    private void createCartTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_CART + " ("
                + COLUMN_CART_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_CART_USER_EMAIL + " TEXT NOT NULL, "
                + COLUMN_CART_PRODUCT_ID + " INTEGER NOT NULL, "
                + COLUMN_CART_QUANTITY + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    // Tạo 2 bảng lưu đơn hàng (orders) và chi tiết từng món trong đơn (order_items)
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

    // Kiểm tra nếu bảng sản phẩm đang trống thì tự động thêm 12 sản phẩm mẫu vào
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

    // Thêm sẵn 12 sản phẩm mẫu vào bảng products
    private void seedProductsData(SQLiteDatabase db) {
        Product[] defaultProducts = {
                new Product(1, "Áo thun cotton basic", "Thời trang", 189000,
                        "Áo thun cotton 100% mềm mịn, thấm hút mồ hôi tốt, form regular dễ phối đồ. Phù hợp mặc hằng ngày, đi làm hoặc đi chơi.", "👕"),
                new Product(2, "Giày sneaker trắng thể thao", "Giày dép", 890000,
                        "Giày sneaker trắng thiết kế tối giản, đế cao su êm ái và chống trượt. Dễ phối với quần jean, quần short hay váy.", "👟"),
                new Product(3, "Balo laptop chống nước cao cấp", "Phụ kiện", 459000,
                        "Balo vải Oxford chống nước, ngăn đệm chống sốc cho laptop đến 15.6 inch, nhiều ngăn phụ tiện lợi, quai đeo êm vai.", "🎒"),
                new Product(4, "Đồng hồ thông minh Smartwatch Pro", "Công nghệ", 1790000,
                        "Đồng hồ thông minh theo dõi nhịp tim, giấc ngủ và số bước chân. Màn hình AMOLED sáng rõ, pin dùng liên tục khoảng 7-10 ngày.", "⌚"),
                new Product(5, "Tai nghe không dây Bluetooth TWS", "Công nghệ", 690000,
                        "Tai nghe không dây kết nối Bluetooth 5.3, chống ồn chủ động ANC, âm bass sống động. Hộp sạc cho tổng thời gian nghe đến 28 giờ.", "🎧"),
                new Product(6, "Kính mát phân cực UV400", "Phụ kiện", 320000,
                        "Tròng kính Polarized phân cực giảm chói, chống 100% tia UV400. Gọng hợp kim siêu nhẹ bền bỉ khi lái xe hoặc đi biển.", "🕶️"),
                new Product(7, "Bình giữ nhiệt inox 304 600ml", "Gia dụng", 245000,
                        "Bình inox 304 cao cấp dung tích 600ml, giữ nóng 12 giờ và giữ lạnh 24 giờ. Nắp khóa kín, không rò rỉ khi bỏ vào balo.", "🥤"),
                new Product(8, "Sạc dự phòng 20000mAh PD 22.5W", "Công nghệ", 490000,
                        "Pin dự phòng dung lượng 20000mAh, hỗ trợ sạc nhanh PD 22.5W, màn hình LED hiển thị % pin. Thiết kế nhỏ gọn, an toàn cháy nổ.", "🔋"),
                new Product(9, "Mũ lưỡi trai phong cách Unisex", "Thời trang", 129000,
                        "Mũ lưỡi trai vải kaki thoáng mát, khóa kim loại điều chỉnh phía sau. Che nắng thời trang khi ra ngoài.", "🧢"),
                new Product(10, "Bàn phím cơ không dây RGB", "Công nghệ", 1250000,
                        "Bàn phím cơ layout 87 phím, switch gõ cực êm, đèn nền LED RGB nhiều chế độ. Hỗ trợ Bluetooth, Wireless 2.4G và Type-C.", "⌨️"),
                new Product(11, "Chuột quang không dây Silent", "Công nghệ", 299000,
                        "Chuột bấm siêu êm chống ồn (Silent), cảm biến quang học 4000 DPI độ trễ cực thấp, pin tiết kiệm dùng tới 12 tháng liên tục.", "🖱️"),
                new Product(12, "Áo khoác Hoodie nỉ dày dặn", "Thời trang", 359000,
                        "Áo Hoodie chất nỉ lót bông mềm mại giữ ấm hoàn hảo, form dáng oversize trẻ trung, năng động, phong cách đường phố cá tính.", "🧥")
        };

        for (Product product : defaultProducts) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PROD_ID, product.getId());
            values.put(COLUMN_PROD_NAME, product.getName());
            values.put(COLUMN_PROD_CATEGORY, product.getCategory());
            values.put(COLUMN_PROD_PRICE, product.getPrice());
            values.put(COLUMN_PROD_DESCRIPTION, product.getDescription());
            values.put(COLUMN_PROD_ICON, product.getIcon());
            db.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    // Lấy toàn bộ danh sách sản phẩm từ SQLite để hiển thị lên màn hình
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
                    list.add(new Product(id, name, category, price, description, icon));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // Tìm thông tin chi tiết của 1 sản phẩm theo mã ID
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
                return new Product(id, name, category, price, description, icon);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    // Thêm sản phẩm vào giỏ hàng (nếu đã có thì cộng thêm số lượng, chưa có thì thêm dòng mới)
    public boolean addToCart(String userEmail, int productId, int quantity) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CART,
                    new String[]{COLUMN_CART_ID, COLUMN_CART_QUANTITY},
                    COLUMN_CART_USER_EMAIL + " = ? AND " + COLUMN_CART_PRODUCT_ID + " = ?",
                    new String[]{userEmail, String.valueOf(productId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int cartId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CART_ID));
                int newQty = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CART_QUANTITY)) + quantity;
                ContentValues values = new ContentValues();
                values.put(COLUMN_CART_QUANTITY, newQty);
                return db.update(TABLE_CART, values, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
            } else {
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

    // Lấy danh sách sản phẩm trong giỏ hàng của người dùng (kết nối 2 bảng cart và products)
    public List<CartItem> getCartItems(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        List<CartItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT c." + COLUMN_CART_ID + ", c." + COLUMN_CART_QUANTITY + ", "
                + "p." + COLUMN_PROD_ID + ", p." + COLUMN_PROD_NAME + ", p." + COLUMN_PROD_CATEGORY + ", "
                + "p." + COLUMN_PROD_PRICE + ", p." + COLUMN_PROD_DESCRIPTION + ", p." + COLUMN_PROD_ICON
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
                    Product product = new Product(prodId, name, category, price, description, icon);
                    list.add(new CartItem(cartId, product, quantity));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // Cập nhật số lượng sản phẩm trong giỏ (nếu giảm về <= 0 thì xóa khỏi giỏ)
    public boolean updateCartQuantity(int cartId, int newQuantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        if (newQuantity <= 0) {
            return db.delete(TABLE_CART, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_CART_QUANTITY, newQuantity);
        return db.update(TABLE_CART, values, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
    }

    // Xóa một sản phẩm cụ thể khỏi giỏ hàng
    public boolean removeCartItem(int cartId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CART, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
    }

    // Xóa toàn bộ sản phẩm trong giỏ hàng của người dùng
    public boolean clearCart(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?", new String[]{userEmail.trim().toLowerCase()}) >= 0;
    }

    // Tính tổng số lượng sản phẩm hiện có trong giỏ hàng
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

    // Tạo đơn hàng mới: lưu thông tin đơn, lưu từng sản phẩm vào order_items và xóa giỏ hàng
    public long createOrder(String userEmail, String receiverName, String receiverPhone,
                            String receiverAddress, String note, String paymentMethod,
                            List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) return -1;
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        long totalAmount = 0;
        for (CartItem item : cartItems) totalAmount += item.getSubtotal();

        String orderDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        String status = "Đặt hàng thành công";

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction(); // Dùng Transaction để đảm bảo lưu đủ cả đơn hàng lẫn chi tiết món
        long orderId = -1;
        try {
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

            db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?", new String[]{userEmail});
            db.setTransactionSuccessful();
            return orderId;
        } finally {
            db.endTransaction();
        }
    }

    // Lấy danh sách lịch sử đơn hàng của người dùng (đơn mới nhất xếp trên cùng)
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

    // Lấy danh sách chi tiết từng sản phẩm thuộc một đơn hàng cụ thể
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

    // Kiểm tra xem email này đã được đăng ký tài khoản chưa
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

    // Lưu thông tin tài khoản mới vào bảng users với avatar mặc định (kèm SĐT)
    public boolean registerUser(String fullName, String email, String password, String phone) {
        if (checkEmailExists(email)) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FULL_NAME, fullName.trim());
        values.put(COLUMN_EMAIL, email.trim().toLowerCase());
        values.put(COLUMN_PASSWORD, password);
        values.put(COLUMN_AVATAR, "👤");
        values.put(COLUMN_PHONE, phone != null ? phone.trim() : "");
        values.put(COLUMN_ADDRESS, "");
        return db.insert(TABLE_USERS, null, values) != -1;
    }

    // Lưu thông tin tài khoản mới (tương thích ngược)
    public boolean registerUser(String fullName, String email, String password) {
        return registerUser(fullName, email, password, "");
    }

    // Kiểm tra đăng nhập (cho phép nhập email, họ tên hoặc tên trước dấu @) và trả về đối tượng User
    public User checkLogin(String account, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String cleanAccount = account != null ? account.trim().toLowerCase() : "";
        String prefixMatch = cleanAccount + "@%";

        try {
            cursor = db.query(TABLE_USERS,
                    null,
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

                String phone = "";
                int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE);
                if (phoneIndex != -1 && !cursor.isNull(phoneIndex)) phone = cursor.getString(phoneIndex);

                String address = "";
                int addressIndex = cursor.getColumnIndex(COLUMN_ADDRESS);
                if (addressIndex != -1 && !cursor.isNull(addressIndex)) address = cursor.getString(addressIndex);

                return new User(id, fullName, userEmail, userPassword, avatar, phone, address);
            }
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // Lấy thông tin người dùng theo email
    public User getUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_USERS,
                    null,
                    "LOWER(" + COLUMN_EMAIL + ") = ?",
                    new String[]{email.trim().toLowerCase()},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String fullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME));
                String userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL));
                String userPassword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD));
                String avatar = "👤";
                int avatarIndex = cursor.getColumnIndex(COLUMN_AVATAR);
                if (avatarIndex != -1 && !cursor.isNull(avatarIndex)) avatar = cursor.getString(avatarIndex);

                String phone = "";
                int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE);
                if (phoneIndex != -1 && !cursor.isNull(phoneIndex)) phone = cursor.getString(phoneIndex);

                String address = "";
                int addressIndex = cursor.getColumnIndex(COLUMN_ADDRESS);
                if (addressIndex != -1 && !cursor.isNull(addressIndex)) address = cursor.getString(addressIndex);

                return new User(id, fullName, userEmail, userPassword, avatar, phone, address);
            }
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // Kiểm tra xem mật khẩu hiện tại của tài khoản có đúng không
    public boolean checkPassword(String email, String password) {
        if (email == null || password == null) return false;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_USERS,
                    new String[]{COLUMN_ID},
                    "LOWER(" + COLUMN_EMAIL + ") = ? AND " + COLUMN_PASSWORD + " = ?",
                    new String[]{email.trim().toLowerCase(), password},
                    null, null, null);
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // Kiểm tra tên đăng nhập đã được người dùng khác sử dụng chưa
    public boolean checkUsernameExists(String username, String currentEmail) {
        if (username == null || username.trim().isEmpty()) return false;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_USERS,
                    new String[]{COLUMN_ID},
                    "LOWER(" + COLUMN_FULL_NAME + ") = ? AND LOWER(" + COLUMN_EMAIL + ") != ?",
                    new String[]{username.trim().toLowerCase(), currentEmail != null ? currentEmail.trim().toLowerCase() : ""},
                    null, null, null);
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // Cập nhật tên đăng nhập / họ tên mới cho người dùng
    public boolean updateUsername(String email, String newUsername) {
        if (email == null || newUsername == null || newUsername.trim().isEmpty()) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FULL_NAME, newUsername.trim());
        int rows = db.update(TABLE_USERS, values,
                "LOWER(" + COLUMN_EMAIL + ") = ?",
                new String[]{email.trim().toLowerCase()});
        return rows > 0;
    }

    // Cập nhật số điện thoại cho người dùng
    public boolean updatePhone(String email, String newPhone) {
        if (email == null) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PHONE, newPhone != null ? newPhone.trim() : "");
        int rows = db.update(TABLE_USERS, values,
                "LOWER(" + COLUMN_EMAIL + ") = ?",
                new String[]{email.trim().toLowerCase()});
        return rows > 0;
    }

    // Cập nhật địa chỉ cho người dùng
    public boolean updateAddress(String email, String newAddress) {
        if (email == null) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ADDRESS, newAddress != null ? newAddress.trim() : "");
        int rows = db.update(TABLE_USERS, values,
                "LOWER(" + COLUMN_EMAIL + ") = ?",
                new String[]{email.trim().toLowerCase()});
        return rows > 0;
    }

    // Cập nhật mật khẩu mới cho người dùng
    public boolean updatePassword(String email, String newPassword) {
        if (email == null || newPassword == null || newPassword.trim().isEmpty()) return false;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PASSWORD, newPassword);
        int rows = db.update(TABLE_USERS, values,
                "LOWER(" + COLUMN_EMAIL + ") = ?",
                new String[]{email.trim().toLowerCase()});
        return rows > 0;
    }
}