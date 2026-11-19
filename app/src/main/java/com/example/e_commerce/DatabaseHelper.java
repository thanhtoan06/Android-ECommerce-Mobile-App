// Quản lý kho bản sao dữ liệu dưới máy điện thoại (SQLite), gồm 5 bảng: người dùng, sản phẩm, giỏ hàng, đơn hàng và chi tiết đơn hàng.
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

    // Tên file cơ sở dữ liệu lưu trên máy và số phiên bản của cấu trúc bảng (nâng lên 8 để có cột stock)
    private static final String DATABASE_NAME = "ecommerce.db";
    private static final int DATABASE_VERSION = 8;

    // Tên bảng và tên các cột của bảng Người dùng (users)
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FULL_NAME = "full_name";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_AVATAR = "avatar";
    public static final String COLUMN_PHONE = "phone";
    public static final String COLUMN_ADDRESS = "address";

    // Tên bảng và tên các cột của bảng Sản phẩm (products)
    public static final String TABLE_PRODUCTS = "products";
    public static final String COLUMN_PROD_ID = "id";
    public static final String COLUMN_PROD_NAME = "name";
    public static final String COLUMN_PROD_CATEGORY = "category";
    public static final String COLUMN_PROD_PRICE = "price";
    public static final String COLUMN_PROD_DESCRIPTION = "description";
    public static final String COLUMN_PROD_ICON = "icon";
    public static final String COLUMN_PROD_STOCK = "stock"; // Cột số lượng tồn kho thực tế

    // Tên bảng và tên các cột của bảng Giỏ hàng (cart)
    public static final String TABLE_CART = "cart";
    public static final String COLUMN_CART_ID = "id";
    public static final String COLUMN_CART_USER_EMAIL = "user_email";
    public static final String COLUMN_CART_PRODUCT_ID = "product_id";
    public static final String COLUMN_CART_QUANTITY = "quantity";

    // Tên bảng và tên các cột của bảng Đơn hàng (orders)
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

    // Tên bảng và tên các cột của bảng Chi tiết từng món trong đơn hàng (order_items)
    public static final String TABLE_ORDER_ITEMS = "order_items";
    public static final String COLUMN_OI_ID = "id";
    public static final String COLUMN_OI_ORDER_ID = "order_id";
    public static final String COLUMN_OI_PRODUCT_ID = "product_id";
    public static final String COLUMN_OI_PRODUCT_NAME = "product_name";
    public static final String COLUMN_OI_PRODUCT_ICON = "product_icon";
    public static final String COLUMN_OI_PRICE = "price";
    public static final String COLUMN_OI_QUANTITY = "quantity";
    public static final String COLUMN_OI_SUBTOTAL = "subtotal";

    // Biến giữ duy nhất 1 kết nối DatabaseHelper dùng chung cho toàn bộ ứng dụng (Singleton)
    private static DatabaseHelper instance;

    // Chuẩn hóa chuỗi email về chữ thường (nếu trống thì gán email khách mặc định)
    private String normalizeEmail(String email) {
        return email == null || email.trim().isEmpty()
                ? "guest@cart.local" : email.trim().toLowerCase();
    }

    // Lấy kết nối DatabaseHelper dùng chung cho mọi màn hình trong ứng dụng
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    // Hàm khởi tạo kết nối tới file ecommerce.db trên máy
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Tự động chạy 1 lần duy nhất khi cài app lần đầu: tạo đủ 5 bảng và nạp sẵn 12 sản phẩm mẫu
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tạo bảng users lưu bản sao tài khoản người dùng
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

        // Tạo tiếp 4 bảng còn lại và nạp 12 sản phẩm mẫu
        createProductsTable(db);
        seedProductsData(db);
        createCartTable(db);
        createOrdersTable(db);
    }

    // Tự động chạy khi tăng DATABASE_VERSION: bổ sung bảng/cột mới mà không làm mất dữ liệu cũ
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Giữ nguyên bảng cũ để bảo toàn bản sao giỏ hàng và đơn mua của người dùng
        if (oldVersion < 5) {
            ensureUserColumns(db);
        }
        if (oldVersion < 8) {
            ensureProductStockColumn(db);
        }
        createProductsTable(db);
        createCartTable(db);
        createOrdersTable(db);
        checkAndSeedProductsIfEmpty(db);
        ensureDefaultStockValues(db);
    }

    // Chạy mỗi lần mở database: kiểm tra chắc chắn các bảng và cột đều tồn tại đầy đủ
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureUserColumns(db);
        ensureProductStockColumn(db);
        createProductsTable(db);
        createCartTable(db);
        createOrdersTable(db);
        checkAndSeedProductsIfEmpty(db);
        ensureDefaultStockValues(db);
    }

    // Bổ sung cột số điện thoại và địa chỉ vào bảng users nếu máy đang dùng phiên bản cũ
    private void ensureUserColumns(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_PHONE + " TEXT");
        } catch (Exception ignored) {}
        try {
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN " + COLUMN_ADDRESS + " TEXT");
        } catch (Exception ignored) {}
    }

    // Bổ sung cột stock vào bảng products nếu thiết bị đang dùng phiên bản CSDL cũ
    private void ensureProductStockColumn(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE_PRODUCTS + " ADD COLUMN " + COLUMN_PROD_STOCK + " INTEGER NOT NULL DEFAULT 0");
        } catch (Exception ignored) {}
    }

    // Đảm bảo các sản phẩm mẫu có sẵn trong bảng products có giá trị tồn kho thật (nếu đang là 0)
    private void ensureDefaultStockValues(SQLiteDatabase db) {
        int[] defaultStocks = {45, 28, 63, 17, 52, 34, 80, 23, 71, 12, 55, 39};
        for (int i = 0; i < defaultStocks.length; i++) {
            int prodId = i + 1;
            int stock = defaultStocks[i];
            db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET " + COLUMN_PROD_STOCK + " = ? WHERE "
                    + COLUMN_PROD_ID + " = ? AND (" + COLUMN_PROD_STOCK + " IS NULL OR " + COLUMN_PROD_STOCK + " <= 0)",
                    new Object[]{stock, prodId});
        }
    }

    // Tạo bảng products (lưu danh sách sản phẩm bán trong cửa hàng) kèm cột tồn kho thực tế
    private void createProductsTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_PRODUCTS + " ("
                + COLUMN_PROD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PROD_NAME + " TEXT NOT NULL, "
                + COLUMN_PROD_CATEGORY + " TEXT NOT NULL, "
                + COLUMN_PROD_PRICE + " INTEGER NOT NULL, "
                + COLUMN_PROD_DESCRIPTION + " TEXT NOT NULL, "
                + COLUMN_PROD_ICON + " TEXT NOT NULL, "
                + COLUMN_PROD_STOCK + " INTEGER NOT NULL DEFAULT 0"
                + ")";
        db.execSQL(sql);
    }

    // Tạo bảng cart (lưu bản sao các món hàng khách đã thêm vào giỏ)
    private void createCartTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_CART + " ("
                + COLUMN_CART_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_CART_USER_EMAIL + " TEXT NOT NULL, "
                + COLUMN_CART_PRODUCT_ID + " INTEGER NOT NULL, "
                + COLUMN_CART_QUANTITY + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    // Tạo 2 bảng: orders (thông tin chung đơn hàng) và order_items (chi tiết từng món trong đơn)
    private void createOrdersTable(SQLiteDatabase db) {
        // Tạo bảng orders
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

        // Tạo bảng order_items
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

    // Đếm số sản phẩm trong bảng products: nếu đang bằng 0 (trống) thì tự động nạp 12 sản phẩm mẫu
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

    // Nạp sẵn danh sách 12 sản phẩm mẫu có giá trị tồn kho thực tế vào bảng products
    private void seedProductsData(SQLiteDatabase db) {
        // Khai báo mảng 12 sản phẩm mặc định có số lượng tồn kho ngẫu nhiên thật
        Product[] defaultProducts = {
                new Product(1, "Áo thun cotton basic", "Thời trang", 189000,
                        "Áo thun cotton 100% mềm mịn, thấm hút mồ hôi tốt, form regular dễ phối đồ. Phù hợp mặc hằng ngày, đi làm hoặc đi chơi.", "👕", 45),
                new Product(2, "Giày sneaker trắng thể thao", "Giày dép", 890000,
                        "Giày sneaker trắng thiết kế tối giản, đế cao su êm ái và chống trượt. Dễ phối với quần jean, quần short hay váy.", "👟", 28),
                new Product(3, "Balo laptop chống nước cao cấp", "Phụ kiện", 459000,
                        "Balo vải Oxford chống nước, ngăn đệm chống sốc cho laptop đến 15.6 inch, nhiều ngăn phụ tiện lợi, quai đeo êm vai.", "🎒", 63),
                new Product(4, "Đồng hồ thông minh Smartwatch Pro", "Công nghệ", 1790000,
                        "Đồng hồ thông minh theo dõi nhịp tim, giấc ngủ và số bước chân. Màn hình AMOLED sáng rõ, pin dùng liên tục khoảng 7-10 ngày.", "⌚", 17),
                new Product(5, "Tai nghe không dây Bluetooth TWS", "Công nghệ", 690000,
                        "Tai nghe không dây kết nối Bluetooth 5.3, chống ồn chủ động ANC, âm bass sống động. Hộp sạc cho tổng thời gian nghe đến 28 giờ.", "🎧", 52),
                new Product(6, "Kính mát phân cực UV400", "Phụ kiện", 320000,
                        "Tròng kính Polarized phân cực giảm chói, chống 100% tia UV400. Gọng hợp kim siêu nhẹ bền bỉ khi lái xe hoặc đi biển.", "🕶️", 34),
                new Product(7, "Bình giữ nhiệt inox 304 600ml", "Gia dụng", 245000,
                        "Bình inox 304 cao cấp dung tích 600ml, giữ nóng 12 giờ và giữ lạnh 24 giờ. Nắp khóa kín, không rò rỉ khi bỏ vào balo.", "🥤", 80),
                new Product(8, "Sạc dự phòng 20000mAh PD 22.5W", "Công nghệ", 490000,
                        "Pin dự phòng dung lượng 20000mAh, hỗ trợ sạc nhanh PD 22.5W, màn hình LED hiển thị % pin. Thiết kế nhỏ gọn, an toàn cháy nổ.", "🔋", 23),
                new Product(9, "Mũ lưỡi trai phong cách Unisex", "Thời trang", 129000,
                        "Mũ lưỡi trai vải kaki thoáng mát, khóa kim loại điều chỉnh phía sau. Che nắng thời trang khi ra ngoài.", "🧢", 71),
                new Product(10, "Bàn phím cơ không dây RGB", "Công nghệ", 1250000,
                        "Bàn phím cơ layout 87 phím, switch gõ cực êm, đèn nền LED RGB nhiều chế độ. Hỗ trợ Bluetooth, Wireless 2.4G và Type-C.", "⌨️", 12),
                new Product(11, "Chuột quang không dây Silent", "Công nghệ", 299000,
                        "Chuột bấm siêu êm chống ồn (Silent), cảm biến quang học 4000 DPI độ trễ cực thấp, pin tiết kiệm dùng tới 12 tháng liên tục.", "🖱️", 55),
                new Product(12, "Áo khoác Hoodie nỉ dày dặn", "Thời trang", 359000,
                        "Áo Hoodie chất nỉ lót bông mềm mại giữ ấm hoàn hảo, form dáng oversize trẻ trung, năng động, phong cách đường phố cá tính.", "🧥", 39)
        };

        // Duyệt qua từng sản phẩm và chèn vào bảng products (nếu trùng ID thì bỏ qua)
        for (Product product : defaultProducts) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PROD_ID, product.getId());
            values.put(COLUMN_PROD_NAME, product.getName());
            values.put(COLUMN_PROD_CATEGORY, product.getCategory());
            values.put(COLUMN_PROD_PRICE, product.getPrice());
            values.put(COLUMN_PROD_DESCRIPTION, product.getDescription());
            values.put(COLUMN_PROD_ICON, product.getIcon());
            values.put(COLUMN_PROD_STOCK, product.getStock());
            db.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    // Chuyển một hàng dữ liệu từ Cursor thành đối tượng Product (đọc cả cột stock)
    private Product cursorToProduct(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROD_ID));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_NAME));
        String category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_CATEGORY));
        long price = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROD_PRICE));
        String description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_DESCRIPTION));
        String icon = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROD_ICON));
        int stockIndex = cursor.getColumnIndex(COLUMN_PROD_STOCK);
        int stock = (stockIndex != -1 && !cursor.isNull(stockIndex)) ? cursor.getInt(stockIndex) : 0;
        return new Product(id, name, category, price, description, icon, stock);
    }

    // Đọc toàn bộ danh sách sản phẩm từ bản sao SQLite, sắp xếp theo ID tăng dần
    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCTS, null, null, null, null, null, COLUMN_PROD_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    list.add(cursorToProduct(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // Tìm thông tin chi tiết của 1 sản phẩm từ bản sao SQLite dựa vào mã ID
    public Product getProductById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCTS, null, COLUMN_PROD_ID + " = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToProduct(cursor);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    // Lưu hoặc cập nhật bản sao sản phẩm nhận từ Firebase xuống bảng products của SQLite (kèm stock)
    public void saveProduct(Product product) {
        if (product == null) return;
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROD_ID, product.getId());
        values.put(COLUMN_PROD_NAME, product.getName());
        values.put(COLUMN_PROD_CATEGORY, product.getCategory());
        values.put(COLUMN_PROD_PRICE, product.getPrice());
        values.put(COLUMN_PROD_DESCRIPTION, product.getDescription());
        values.put(COLUMN_PROD_ICON, product.getIcon());
        values.put(COLUMN_PROD_STOCK, product.getStock());
        getWritableDatabase().insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // Trừ số lượng tồn kho trong SQLite sau khi đặt hàng thành công (không để âm dưới 0)
    public void decreaseStock(int productId, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET " + COLUMN_PROD_STOCK
                + " = MAX(0, " + COLUMN_PROD_STOCK + " - ?) WHERE " + COLUMN_PROD_ID + " = ?",
                new Object[]{quantity, productId});
    }

    // Lưu bản sao món hàng vào bảng cart SQLite (cộng dồn số lượng nếu đã có sẵn)
    public boolean addToCart(String userEmail, int productId, int quantity) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        try {
            // Kiểm tra xem người dùng này đã có sẵn sản phẩm này trong bảng cart chưa
            cursor = db.query(TABLE_CART,
                    new String[]{COLUMN_CART_ID, COLUMN_CART_QUANTITY},
                    COLUMN_CART_USER_EMAIL + " = ? AND " + COLUMN_CART_PRODUCT_ID + " = ?",
                    new String[]{userEmail, String.valueOf(productId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Nếu đã có -> lấy số lượng cũ cộng thêm số lượng mới và cập nhật lại
                int cartId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CART_ID));
                int newQty = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CART_QUANTITY)) + quantity;
                ContentValues values = new ContentValues();
                values.put(COLUMN_CART_QUANTITY, newQty);
                return db.update(TABLE_CART, values, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
            } else {
                // Nếu chưa có -> chèn một dòng mới vào bảng cart
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

    // Lấy toàn bộ bản sao món hàng trong giỏ của 1 người dùng (nối bảng cart và bảng products, lấy đủ stock)
    public List<CartItem> getCartItems(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        List<CartItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Câu lệnh SQL nối bảng cart với bảng products theo mã sản phẩm để lấy đủ thông tin bao gồm cả tồn kho
        String query = "SELECT c." + COLUMN_CART_ID + ", c." + COLUMN_CART_QUANTITY + ", "
                + "p." + COLUMN_PROD_ID + ", p." + COLUMN_PROD_NAME + ", p." + COLUMN_PROD_CATEGORY + ", "
                + "p." + COLUMN_PROD_PRICE + ", p." + COLUMN_PROD_DESCRIPTION + ", p." + COLUMN_PROD_ICON + ", "
                + "COALESCE(p." + COLUMN_PROD_STOCK + ", 0)"
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
                    int stock = cursor.getInt(8);
                    Product product = new Product(prodId, name, category, price, description, icon, stock);
                    list.add(new CartItem(cartId, product, quantity));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    // Cập nhật số lượng mới của 1 dòng trong bảng cart theo mã cartId (nếu <= 0 thì xóa dòng đó)
    public boolean updateCartQuantity(int cartId, int newQuantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        if (newQuantity <= 0) {
            return db.delete(TABLE_CART, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_CART_QUANTITY, newQuantity);
        return db.update(TABLE_CART, values, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
    }

    // Xóa 1 dòng sản phẩm khỏi bảng cart theo mã dòng cartId
    public boolean removeCartItem(int cartId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CART, COLUMN_CART_ID + " = ?", new String[]{String.valueOf(cartId)}) > 0;
    }

    // Xóa bản sao 1 sản phẩm khỏi bảng cart theo email người dùng và mã sản phẩm (sau khi Firebase xóa thành công)
    public boolean removeCartItem(String userEmail, int productId) {
        return getWritableDatabase().delete(TABLE_CART,
                COLUMN_CART_USER_EMAIL + " = ? AND " + COLUMN_CART_PRODUCT_ID + " = ?",
                new String[]{normalizeEmail(userEmail), String.valueOf(productId)}) > 0;
    }

    // Cập nhật số lượng bản sao trong bảng cart theo email và mã sản phẩm (sau khi Firebase cập nhật thành công)
    public boolean updateCartQuantity(String userEmail, int productId, int newQuantity) {
        if (newQuantity <= 0) return removeCartItem(userEmail, productId);
        ContentValues values = new ContentValues();
        values.put(COLUMN_CART_QUANTITY, newQuantity);
        return getWritableDatabase().update(TABLE_CART, values,
                COLUMN_CART_USER_EMAIL + " = ? AND " + COLUMN_CART_PRODUCT_ID + " = ?",
                new String[]{normalizeEmail(userEmail), String.valueOf(productId)}) > 0;
    }

    // Xóa sạch toàn bộ bản sao giỏ hàng của một tài khoản email trong SQLite
    public boolean clearCart(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?", new String[]{userEmail.trim().toLowerCase()}) >= 0;
    }

    // Tính tổng số lượng sản phẩm trong bản sao giỏ hàng để hiển thị huy hiệu số đỏ
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

    // Lấy số lượng hiện có của 1 sản phẩm cụ thể trong bản sao giỏ hàng của người dùng
    public int getCartQuantity(String userEmail, int productId) {
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CART, new String[]{COLUMN_CART_QUANTITY},
                    "LOWER(" + COLUMN_CART_USER_EMAIL + ") = ? AND " + COLUMN_CART_PRODUCT_ID + " = ?",
                    new String[]{userEmail.trim().toLowerCase(), String.valueOf(productId)},
                    null, null, null);
            return cursor != null && cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // Tạo đơn hàng cục bộ trong SQLite: lưu orders -> lưu order_items -> trừ tồn kho -> xóa cart trong 1 Transaction
    public long createOrder(String userEmail, String receiverName, String receiverPhone,
                            String receiverAddress, String note, String paymentMethod,
                            List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) return -1;
        if (userEmail == null || userEmail.trim().isEmpty()) userEmail = "guest@cart.local";
        userEmail = userEmail.trim().toLowerCase();

        // Tính tổng tiền của toàn bộ đơn hàng
        long totalAmount = 0;
        for (CartItem item : cartItems) totalAmount += item.getSubtotal();

        // Lấy ngày giờ đặt hàng hiện tại và đặt trạng thái mặc định
        String orderDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        String status = "Đặt hàng thành công";

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        long orderId = -1;
        try {
            // Bước 1: Chèn thông tin chung của đơn hàng vào bảng orders
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

            // Bước 2: Duyệt từng món trong giỏ để lưu vào bảng chi tiết order_items và trừ tồn kho
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

                // Tự động trừ số lượng tồn kho tương ứng trong SQLite
                if (p != null) {
                    db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET " + COLUMN_PROD_STOCK
                                    + " = MAX(0, " + COLUMN_PROD_STOCK + " - ?) WHERE " + COLUMN_PROD_ID + " = ?",
                            new Object[]{item.getQuantity(), p.getId()});
                }
            }

            // Bước 3: Xóa sạch giỏ hàng của người dùng sau khi đặt đơn thành công
            db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?", new String[]{userEmail});
            db.setTransactionSuccessful();
            return orderId;
        } finally {
            db.endTransaction();
        }
    }

    // Lưu bản sao đơn hàng đã được Firebase tạo thành công vào SQLite (và trừ tồn kho trong SQLite)
    public boolean saveRemoteOrder(Order order, List<OrderItem> items) {
        if (order == null || items == null || items.isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Bước 1: Chèn hoặc ghi đè thông tin chung của đơn hàng vào bảng orders
            ContentValues orderValues = new ContentValues();
            orderValues.put(COLUMN_ORDER_ID, order.getId());
            orderValues.put(COLUMN_ORDER_USER_EMAIL, normalizeEmail(order.getUserEmail()));
            orderValues.put(COLUMN_ORDER_RECEIVER_NAME, order.getReceiverName());
            orderValues.put(COLUMN_ORDER_RECEIVER_PHONE, order.getReceiverPhone());
            orderValues.put(COLUMN_ORDER_RECEIVER_ADDRESS, order.getReceiverAddress());
            orderValues.put(COLUMN_ORDER_NOTE, order.getNote());
            orderValues.put(COLUMN_ORDER_PAYMENT_METHOD, order.getPaymentMethod());
            orderValues.put(COLUMN_ORDER_TOTAL_AMOUNT, order.getTotalAmount());
            orderValues.put(COLUMN_ORDER_DATE, order.getOrderDate());
            orderValues.put(COLUMN_ORDER_STATUS, order.getStatus());
            if (db.insertWithOnConflict(TABLE_ORDERS, null, orderValues, SQLiteDatabase.CONFLICT_REPLACE) == -1) return false;

            // Bước 2: Xóa chi tiết cũ của mã đơn này (nếu có) và chèn danh sách món hàng mới từ Firebase
            db.delete(TABLE_ORDER_ITEMS, COLUMN_OI_ORDER_ID + " = ?",
                    new String[]{String.valueOf(order.getId())});
            for (OrderItem item : items) {
                ContentValues itemValues = new ContentValues();
                itemValues.put(COLUMN_OI_ID, item.getId());
                itemValues.put(COLUMN_OI_ORDER_ID, order.getId());
                itemValues.put(COLUMN_OI_PRODUCT_ID, item.getProductId());
                itemValues.put(COLUMN_OI_PRODUCT_NAME, item.getProductName());
                itemValues.put(COLUMN_OI_PRODUCT_ICON, item.getProductIcon());
                itemValues.put(COLUMN_OI_PRICE, item.getPrice());
                itemValues.put(COLUMN_OI_QUANTITY, item.getQuantity());
                itemValues.put(COLUMN_OI_SUBTOTAL, item.getSubtotal());
                if (db.insertWithOnConflict(TABLE_ORDER_ITEMS, null, itemValues,
                        SQLiteDatabase.CONFLICT_REPLACE) == -1) return false;

                // Tự động trừ tồn kho sản phẩm trong SQLite khi nhận đơn hàng mới
                db.execSQL("UPDATE " + TABLE_PRODUCTS + " SET " + COLUMN_PROD_STOCK
                                + " = MAX(0, " + COLUMN_PROD_STOCK + " - ?) WHERE " + COLUMN_PROD_ID + " = ?",
                        new Object[]{item.getQuantity(), item.getProductId()});
            }

            // Bước 3: Dọn sạch bản sao giỏ hàng của người dùng trong SQLite và xác nhận Transaction
            db.delete(TABLE_CART, COLUMN_CART_USER_EMAIL + " = ?",
                    new String[]{normalizeEmail(order.getUserEmail())});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    // Đọc danh sách các đơn hàng đã đặt từ bản sao SQLite (sắp xếp đơn mới nhất lên đầu)
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

    // Đọc danh sách chi tiết các món hàng bên trong 1 đơn hàng từ bản sao SQLite theo mã orderId
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

    // Kiểm tra xem địa chỉ email này đã tồn tại trong bảng users của bản sao SQLite chưa
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

    // Lưu bản sao tài khoản người dùng vào bảng users của SQLite (sau khi Firebase đăng ký/tải về thành công)
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

    // Lưu bản sao tài khoản rút gọn (không truyền SĐT thì tự gán chuỗi rỗng)
    public boolean registerUser(String fullName, String email, String password) {
        return registerUser(fullName, email, password, "");
    }

    // Kiểm tra đăng nhập trên bản sao SQLite (hỗ trợ khớp email, họ tên hoặc phần trước ký tự @)
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

    // Tìm và trả về thông tin bản sao tài khoản từ SQLite dựa theo địa chỉ email
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

    // Kiểm tra mật khẩu trên bản sao SQLite (luồng chính kiểm tra trên Firebase trước)
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

    // Kiểm tra trùng tên hiển thị trên bản sao SQLite (luồng chính kiểm tra trên Firebase trước)
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

    // Cập nhật tên hiển thị mới vào bản sao SQLite (sau khi Firebase cập nhật thành công)
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

    // Cập nhật số điện thoại mới vào bản sao SQLite (sau khi Firebase cập nhật thành công)
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

    // Cập nhật địa chỉ giao hàng mới vào bản sao SQLite (sau khi Firebase cập nhật thành công)
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

    // Cập nhật mật khẩu mới vào bản sao SQLite (sau khi Firebase cập nhật thành công)
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
