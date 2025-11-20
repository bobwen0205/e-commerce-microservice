package com.bob.cartservice.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItem {
    private String productId;
    private int quantity;

    // Snapshot of product (VERY IMPORTANT)
    private String name;
    private String brand;
    private BigDecimal price;
    private String imageUrl;

    // optional
    private boolean available;  // if product disabled/out-of-stock
}
