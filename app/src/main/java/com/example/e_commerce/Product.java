
package com.example.e_commerce;

import java.text.NumberFormat;
import java.util.Locale;

public class Product {

    private final int id;
    private final String name;
    private final String category;
    private final long price;
    private final String description;
    private final String icon;
    private final int stock;

    public Product(int id, String name, String category, long price, String description, String icon, int stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.icon = icon;
        this.stock = stock;
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

    public boolean isInStock() {
        return stock > 0;
    }
}
