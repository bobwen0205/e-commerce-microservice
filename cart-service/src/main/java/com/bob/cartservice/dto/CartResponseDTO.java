package com.bob.cartservice.dto;

import com.bob.cartservice.model.CartItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartResponseDTO {
    private String userId;
    private List<CartItem> items;
    private BigDecimal totalAmount;
    private int totalQuantity;
}