package com.bob.cartservice.dto;

import lombok.Data;

@Data
public class AddToCartRequestDTO {
    private String productId;
    private int quantity;
}