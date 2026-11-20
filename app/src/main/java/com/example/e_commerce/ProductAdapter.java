
package com.example.e_commerce;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends ArrayAdapter<Product> {

    private final LayoutInflater inflater;
    private final List<Voucher> savedVouchers;

    public ProductAdapter(@NonNull Context context, @NonNull Product[] products, @NonNull List<Voucher> savedVouchers) {
        super(context, 0, products);
        this.inflater = LayoutInflater.from(context);
        this.savedVouchers = savedVouchers;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_product, parent, false);
            holder = new ViewHolder();
            holder.ivProductImage = convertView.findViewById(R.id.ivProductImage);
            holder.tvCategory = convertView.findViewById(R.id.tvCategory);
            holder.tvName = convertView.findViewById(R.id.tvName);
            holder.tvAvgStar = convertView.findViewById(R.id.tvAvgStar);
            holder.tvProductReviewSummary = convertView.findViewById(R.id.tvProductReviewSummary);
            holder.tvPrice = convertView.findViewById(R.id.tvPrice);
            holder.tvOriginalPrice = convertView.findViewById(R.id.tvOriginalPrice);
            holder.layoutProductVoucherTag = convertView.findViewById(R.id.layoutProductVoucherTag);
            holder.tvProductVoucherTag = convertView.findViewById(R.id.tvProductVoucherTag);
            holder.tvProductFreeShipping = convertView.findViewById(R.id.tvProductFreeShipping);
            holder.tvStock = convertView.findViewById(R.id.tvStock);
            convertView.setTag(holder);
        } else {

            holder = (ViewHolder) convertView.getTag();
        }

        Product product = getItem(position);
        if (product != null) {
            if (holder.ivProductImage != null) {
                ImageBlobLoader.bind(holder.ivProductImage, product.getImageBytes(),
                        "product-" + product.getId(), R.drawable.ic_product_image_placeholder);
            }
            if (holder.tvCategory != null)
                holder.tvCategory.setText(product.getCategory());
            if (holder.tvName != null)
                holder.tvName.setText(product.getName());
            if (holder.tvAvgStar != null) {
                holder.tvAvgStar.setText(String.format(Locale.forLanguageTag("vi-VN"),
                        "★ %.1f", product.getAverageRating()));
            }
            if (holder.tvProductReviewSummary != null) {
                holder.tvProductReviewSummary.setText(String.format(Locale.forLanguageTag("vi-VN"),
                        "(%d) · Đã bán %d",
                        product.getReviewCount(), product.getSoldCount()));
            }
            bindVoucherPrice(holder, product);
            if (holder.tvStock != null) {
                int stock = product.getStock();
                if (stock <= 0) {
                    holder.tvStock.setText("Hết hàng");
                    holder.tvStock.setTextColor(0xFFEF4444);
                } else if (stock <= 5) {
                    holder.tvStock.setText("Còn " + stock + " sp");
                    holder.tvStock.setTextColor(0xFFF97316);
                } else {
                    holder.tvStock.setText("Còn " + stock + " sp");
                    holder.tvStock.setTextColor(0xFF64748B);
                }
            }
        }

        return convertView;
    }

    private void bindVoucherPrice(ViewHolder holder, Product product) {
        Voucher bestProductVoucher = null;
        long bestDiscount = 0;
        long bestShippingDiscount = 0;
        CartItem singleItem = new CartItem(product.getId(), product, 1);
        for (Voucher voucher : savedVouchers) {
            if (!voucher.isSaved() || voucher.isExpired(new Date())) {
                continue;
            }
            if (voucher.isProductVoucher()) {
                VoucherCalculator.ProductDiscount discount =
                        VoucherCalculator.calculateProductDiscount(java.util.Collections.singletonList(singleItem),
                                voucher);
                if (discount.getTotalDiscount() > bestDiscount) {
                    bestDiscount = discount.getTotalDiscount();
                    bestProductVoucher = voucher;
                }
            } else if (voucher.isShippingVoucher()) {
                long shippingDiscount = VoucherCalculator.calculateShippingDiscount(
                        java.util.Collections.singletonList(singleItem),
                        voucher,
                        VoucherCalculator.BASE_SHIPPING_FEE);
                bestShippingDiscount = Math.max(bestShippingDiscount, shippingDiscount);
            }
        }

        if (bestProductVoucher != null) {
            holder.tvOriginalPrice.setText(product.getFormattedPrice());
            holder.tvOriginalPrice.setPaintFlags(
                    holder.tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvOriginalPrice.setVisibility(View.VISIBLE);
            holder.tvPrice.setText(formatMoney(product.getPrice() - bestDiscount));
            holder.tvProductVoucherTag.setText(voucherDiscountLabel(bestProductVoucher, bestDiscount));
            holder.layoutProductVoucherTag.setVisibility(View.VISIBLE);
        } else {
            holder.tvOriginalPrice.setVisibility(View.GONE);
            holder.tvPrice.setText(product.getFormattedPrice());
            holder.layoutProductVoucherTag.setVisibility(View.GONE);
        }
        if (bestShippingDiscount > 0) {
            holder.tvProductFreeShipping.setText(bestShippingDiscount >= VoucherCalculator.BASE_SHIPPING_FEE
                    ? "Free ship"
                    : "Ship -" + formatMoney(bestShippingDiscount));
            holder.tvProductFreeShipping.setVisibility(View.VISIBLE);
        } else {
            holder.tvProductFreeShipping.setVisibility(View.GONE);
        }
    }

    private String voucherDiscountLabel(Voucher voucher, long actualDiscount) {
        if ("percentage_per_item".equals(voucher.getDiscountType())) {
            return "Voucher -" + voucher.getValue() + "%";
        }
        return "Voucher -" + formatMoney(actualDiscount);
    }

    private String formatMoney(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private static class ViewHolder {
        ImageView ivProductImage;
        TextView tvCategory;
        TextView tvName;
        TextView tvAvgStar;
        TextView tvProductReviewSummary;
        TextView tvPrice;
        TextView tvOriginalPrice;
        View layoutProductVoucherTag;
        TextView tvProductVoucherTag;
        TextView tvProductFreeShipping;
        TextView tvStock;
    }
}
