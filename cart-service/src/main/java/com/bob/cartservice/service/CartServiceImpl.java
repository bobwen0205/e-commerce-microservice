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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisCartRepository cartRepository;
    private final ProductGrpcClient productGrpcClient;

    @Override
    public CartResponseDTO addItemToCart(String userId, AddToCartRequestDTO request) {
        Cart cart = cartRepository.findByUserId(userId).orElse(new Cart());
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
            Product productProto = productGrpcClient.getProduct(request.getProductId());

            CartItem newItem = new CartItem();
            newItem.setProductId(productProto.getId());
            newItem.setName(productProto.getName());
            newItem.setBrand(productProto.getBrand());
            // Proto sends price as String, convert to BigDecimal
            newItem.setPrice(new BigDecimal(productProto.getPrice()));
            newItem.setQuantity(request.getQuantity());
            // Logic for image URL if available in proto, otherwise null
            // newItem.setImageUrl(...);
            newItem.setAvailable(productProto.getInventory() > 0);

            cart.getItems().add(newItem);

            // Add to Index
            cartRepository.addProductToCartIndex(request.getProductId(), userId);
        }

        calculateTotals(cart);
        cartRepository.save(cart);

        return mapToResponse(cart);
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
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));

        if (removed) {
            // Remove from Index
            cartRepository.removeProductFromCartIndex(productId, userId);
        }

        calculateTotals(cart);
        cartRepository.save(cart);

        return mapToResponse(cart);
    }

    @Override
    public void clearCart(String userId) {
        cartRepository.delete(userId);
    }

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

    // METHOD called by KafkaConsumer
    @Override
    public void handleProductUpdate(com.bob.product.proto.Product event) {
        // 1. Find all users who have this product in their cart
        Set<String> userIds = cartRepository.getUsersWithProduct(event.getId());

        for (String userId : userIds) {
            cartRepository.findByUserId(userId).ifPresent(cart -> {
                boolean changed = false;
                BigDecimal newPrice = new BigDecimal(event.getPrice()); // Proto string -> BigDecimal
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
                    cartRepository.save(cart);
                }
            });
        }
    }

    // Method to handle checkout validation
    @Override
    public CartResponseDTO validateCartForCheckout(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (cart.getItems().isEmpty()) {
            throw new ResourceNotFoundException("Cart is empty");
        }

        // 1. Prepare Request for gRPC
        List<CartItemRequest> protoItems = cart.getItems().stream()
                .map(item -> CartItemRequest.newBuilder()
                        .setProductId(item.getProductId())
                        .setQuantity(item.getQuantity())
                        .build())
                .toList();

        // 2. Call Product Service (Step 4)
        ValidateCartItemsResponse response = productGrpcClient.validateCartItems(protoItems);

        // 3. Process Results (Step 7)
        boolean cartChanged = false;
        List<String> invalidItemIds = new ArrayList<>();

        for (CartItemValidationResult result : response.getResultsList()) {
            String productId = result.getProductId();

            // Find item in current cart
            Optional<CartItem> cartItemOpt = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst();

            if (cartItemOpt.isPresent()) {
                CartItem item = cartItemOpt.get();

                if (!result.getValid()) {
                    // Case: Invalid (Inactive or No Stock) -> Remove item
                    cart.getItems().remove(item);
                    cartRepository.removeProductFromCartIndex(productId, userId);
                    cartChanged = true;
                    invalidItemIds.add(productId + " (" + result.getMessage() + ")");
                } else {
                    // Case: Valid, but check for price change
                    BigDecimal currentPrice = new BigDecimal(result.getCurrentPrice());
                    if (item.getPrice().compareTo(currentPrice) != 0) {
                        item.setPrice(currentPrice);
                        cartChanged = true;
                    }
                }
            }
        }

        // 4. Save and Return
        if (cartChanged) {
            calculateTotals(cart);
            cartRepository.save(cart);

            // [Step 8] Return specific error/status indicating cart has changed
            // We throw a custom exception or handle it in Controller.
            // For simplicity, we can throw a runtime exception that maps to 400.
            throw new CartValidationException("Cart items were updated during validation", invalidItemIds, mapToResponse(cart));
        }

        return mapToResponse(cart);
    }
}