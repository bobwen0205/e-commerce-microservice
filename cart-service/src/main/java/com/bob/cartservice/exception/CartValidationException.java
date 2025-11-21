package com.bob.cartservice.exception;

import com.bob.cartservice.dto.CartResponseDTO;
import lombok.Getter;

import java.util.List;

@Getter
public class CartValidationException extends RuntimeException {
    private final List<String> invalidItems;
    private final CartResponseDTO updatedCart;

    public CartValidationException(String message, List<String> invalidItems, CartResponseDTO updatedCart) {
        super(message);
        this.invalidItems = invalidItems;
        this.updatedCart = updatedCart;
    }
}