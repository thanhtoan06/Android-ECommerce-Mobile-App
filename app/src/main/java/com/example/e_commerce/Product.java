
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Product {

    private final int id;
    private final String name;
    private final String category;
    private final long price;
    private final String description;
    private final String icon;
    private final int stock;
    private final int soldCount;
    private final double averageRating;
    private final int reviewCount;
    private final Map<String, String> specifications;
    private final Date createdAt;

    public Product(int id, String name, String category, long price, String description, String icon, int stock) {
        this(id, name, category, price, description, icon, stock, 0, 0, 0, Collections.emptyMap());
    }

    public Product(int id, String name, String category, long price, String description, String icon, int stock,
            int soldCount, double averageRating, int reviewCount, Map<String, String> specifications) {
        this(id, name, category, price, description, icon, stock, soldCount, averageRating, reviewCount,
                specifications, null);
    }

    public Product(int id, String name, String category, long price, String description, String icon, int stock,
            int soldCount, double averageRating, int reviewCount, Map<String, String> specifications, Date createdAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.icon = icon;
        this.stock = stock;
        this.soldCount = soldCount;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.specifications = Collections.unmodifiableMap(
                new HashMap<>(specifications == null ? Collections.emptyMap() : specifications));
        this.createdAt = createdAt == null ? null : new Date(createdAt.getTime());
    }

    public Product(int id, String name, String category, long price, String description, String icon) {
        this(id, name, category, price, description, icon, 0);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public long getPrice() {
        return price;
    }

    public String getFormattedPrice() {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public int getStock() {
        return stock;
    }

    public int getSoldCount() {
        return soldCount;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public Map<String, String> getSpecifications() {
        return specifications;
    }

    public Date getCreatedAt() {
        return createdAt == null ? null : new Date(createdAt.getTime());
    }

    public boolean isInStock() {
        return stock > 0;
    }
}
