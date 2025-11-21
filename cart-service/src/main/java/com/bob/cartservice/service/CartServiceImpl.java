package com.bob.cartservice.service;

import com.bob.cartservice.dto.AddToCartRequestDTO;
import com.bob.cartservice.dto.CartResponseDTO;
import com.bob.cartservice.exception.CartValidationException;
import com.bob.cartservice.exception.ResourceNotFoundException;
import com.bob.cartservice.grpc.ProductGrpcClient;
import com.bob.cartservice.model.Cart;
import com.bob.cartservice.model.CartItem;
import com.bob.cartservice.repository.RedisCartRepository;
import com.bob.product.proto.CartItemRequest;
import com.bob.product.proto.CartItemValidationResult;
import com.bob.product.proto.Product;
import com.bob.product.proto.ValidateCartItemsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final RedisCartRepository cartRepository;
    private final ProductGrpcClient productGrpcClient;

    @Override
    public CartResponseDTO addItemToCart(String userId, AddToCartRequestDTO request) {
        // Use atomic updateCart (Optimistic Locking)
        Cart updatedCart = cartRepository.updateCart(userId, cart -> {
            if (cart.getUserId() == null) {
                cart.setUserId(userId);
            }

            // Check if item exists in cart
            Optional<CartItem> existingItem = cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(request.getProductId()))
                    .findFirst();

            if (existingItem.isPresent()) {
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + request.getQuantity());
            } else {
                // Fetch product details from Product Service via gRPC
                // Note: In optimistic locking, this might happen multiple times if retried
                Product productProto = productGrpcClient.getProduct(request.getProductId());

                CartItem newItem = new CartItem();
                newItem.setProductId(productProto.getId());
                newItem.setName(productProto.getName());
                newItem.setBrand(productProto.getBrand());
                // Proto sends price as String, convert to BigDecimal
                newItem.setPrice(new BigDecimal(productProto.getPrice()));
                newItem.setQuantity(request.getQuantity());
                newItem.setAvailable(productProto.getInventory() > 0);

                cart.getItems().add(newItem);

                // Add to Index (Idempotent)
                cartRepository.addProductToCartIndex(request.getProductId(), userId);
            }

            calculateTotals(cart);
            return cart;
        });

        return mapToResponse(updatedCart);
    }

    @Override
    public CartResponseDTO getCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        cart.setUserId(userId);
        return mapToResponse(cart);
    }

    @Override
    public CartResponseDTO removeItemFromCart(String userId, String productId) {
        Cart updatedCart = cartRepository.updateCart(userId, cart -> {
            boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));

            if (removed) {
                // Remove from Index
                cartRepository.removeProductFromCartIndex(productId, userId);
            }

            calculateTotals(cart);
            return cart;
        });

        return mapToResponse(updatedCart);
    }

    @Override
    public void clearCart(String userId) {
        cartRepository.delete(userId);
    }

    // METHOD called by KafkaConsumer (Concurrent Updates handled)
    @Override
    public void handleProductUpdate(com.bob.product.proto.Product event) {
        // 1. Find all users who have this product in their cart
        Set<String> userIds = cartRepository.getUsersWithProduct(event.getId());

        for (String userId : userIds) {
            cartRepository.updateCart(userId, cart -> {
                boolean changed = false;
                BigDecimal newPrice = new BigDecimal(event.getPrice());
                int newInventory = event.getInventory();

                Iterator<CartItem> iterator = cart.getItems().iterator();
                while (iterator.hasNext()) {
                    CartItem item = iterator.next();
                    if (item.getProductId().equals(event.getId())) {

                        // Logic: Remove if out of stock
                        if (newInventory < item.getQuantity()) {
                            iterator.remove();
                            cartRepository.removeProductFromCartIndex(event.getId(), userId);
                            changed = true;
                        }
                        // Logic: Update Price
                        else if (item.getPrice().compareTo(newPrice) != 0) {
                            item.setPrice(newPrice);
                            changed = true;
                        }
                    }
                }

                if (changed) {
                    calculateTotals(cart);
                }
                return cart;
            });
        }
    }

    // Method to handle checkout validation
    @Override
    public CartResponseDTO validateCartForCheckout(String userId) {
        // List to capture invalid items during the atomic update
        // We clear this at the start of the lambda to handle retries correctly
        List<String> invalidItemIds = new ArrayList<>();

        Cart updatedCart = cartRepository.updateCart(userId, cart -> {
            invalidItemIds.clear(); // Reset for retry

            if (cart.getItems().isEmpty()) {
                return cart; // Empty cart is valid (or handle as exception outside)
            }

            // 1. Prepare Request for gRPC
            List<CartItemRequest> protoItems = cart.getItems().stream()
                    .map(item -> CartItemRequest.newBuilder()
                            .setProductId(item.getProductId())
                            .setQuantity(item.getQuantity())
                            .build())
                    .toList();

            // 2. Call Product Service
            ValidateCartItemsResponse response = productGrpcClient.validateCartItems(protoItems);

            // 3. Process Results
            boolean cartChanged = false;

            for (CartItemValidationResult result : response.getResultsList()) {
                String productId = result.getProductId();

                Optional<CartItem> cartItemOpt = cart.getItems().stream()
                        .filter(i -> i.getProductId().equals(productId))
                        .findFirst();

                if (cartItemOpt.isPresent()) {
                    CartItem item = cartItemOpt.get();

                    if (!result.getValid()) {
                        // Invalid -> Remove
                        cart.getItems().remove(item);
                        cartRepository.removeProductFromCartIndex(productId, userId);
                        cartChanged = true;
                        invalidItemIds.add(productId + " (" + result.getMessage() + ")");
                    } else {
                        // Valid -> Check Price
                        BigDecimal currentPrice = new BigDecimal(result.getCurrentPrice());
                        if (item.getPrice().compareTo(currentPrice) != 0) {
                            item.setPrice(currentPrice);
                            cartChanged = true;
                        }
                    }
                }
            }

            if (cartChanged) {
                calculateTotals(cart);
            }
            return cart;
        });

        // 4. Post-transaction check
        // If invalid items were found, the cart was updated and saved.
        // Now we throw the exception to notify the user.
        if (!invalidItemIds.isEmpty()) {
            throw new CartValidationException("Cart items were updated during validation",
                    invalidItemIds,
                    mapToResponse(updatedCart));
        }

        if (updatedCart.getItems().isEmpty()) {
            throw new ResourceNotFoundException("Cart is empty");
        }

        return mapToResponse(updatedCart);
    }

    // Method to handle Product Deletion Event
    @Override
    public void handleProductDeletion(String productId) {
        // 1. Use Index to find relevant users
        Set<String> userIds = cartRepository.getUsersWithProduct(productId);

        log.info("Removing deleted product {} from {} active carts", productId, userIds.size());

        for (String userId : userIds) {
            cartRepository.updateCart(userId, cart -> {
                boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));

                if (removed) {
                    calculateTotals(cart);
                    // Clean up the index for this specific user/product pair
                    cartRepository.removeProductFromCartIndex(productId, userId);
                }
                return cart;
            });
        }
    }

    // --- Helper Methods ---

    private void calculateTotals(Cart cart) {
        int totalQty = 0;
        BigDecimal totalAmt = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            totalQty += item.getQuantity();
            BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmt = totalAmt.add(itemTotal);
        }

        cart.setTotalQuantity(totalQty);
        cart.setTotalAmount(totalAmt);
    }

    private CartResponseDTO mapToResponse(Cart cart) {
        return CartResponseDTO.builder()
                .userId(cart.getUserId())
                .items(cart.getItems())
                .totalAmount(cart.getTotalAmount())
                .totalQuantity(cart.getTotalQuantity())
                .build();
    }
}