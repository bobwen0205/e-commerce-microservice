package com.bob.cartservice.controller;

import com.bob.cartservice.dto.AddToCartRequestDTO;
import com.bob.cartservice.dto.CartResponseDTO;
import com.bob.cartservice.exception.CartValidationException;
import com.bob.cartservice.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // In a real app, userId usually comes from JWT Token / SecurityContext
    // For now, we pass it as a request param or header

    @PostMapping("/{userId}/add")
    public ResponseEntity<CartResponseDTO> addToCart(
            @PathVariable String userId,
            @RequestBody AddToCartRequestDTO request) {
        return ResponseEntity.ok(cartService.addItemToCart(userId, request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<CartResponseDTO> getCart(@PathVariable String userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @DeleteMapping("/{userId}/item/{productId}")
    public ResponseEntity<CartResponseDTO> removeItem(
            @PathVariable String userId,
            @PathVariable String productId) {
        return ResponseEntity.ok(cartService.removeItemFromCart(userId, productId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearCart(@PathVariable String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/checkout-validate")
    public ResponseEntity<?> validateCart(@PathVariable String userId) {
        try {
            CartResponseDTO cartResponse = cartService.validateCartForCheckout(userId);
            return ResponseEntity.ok(cartResponse);
        } catch (CartValidationException e) {
            // Step 8: Return 400 with details
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("invalidItems", e.getInvalidItems());
            errorResponse.put("updatedCart", e.getUpdatedCart());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}