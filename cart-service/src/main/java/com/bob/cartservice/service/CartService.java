package com.bob.cartservice.service;

import com.bob.cartservice.dto.AddToCartRequestDTO;
import com.bob.cartservice.dto.CartResponseDTO;

public interface CartService {
    CartResponseDTO addItemToCart(String userId, AddToCartRequestDTO request);

    CartResponseDTO getCart(String userId);

    CartResponseDTO removeItemFromCart(String userId, String productId);

    void clearCart(String userId);

    // METHOD called by KafkaConsumer
    void handleProductUpdate(com.bob.product.proto.Product event);

    // Method to handle checkout validation
    CartResponseDTO validateCartForCheckout(String userId);
}