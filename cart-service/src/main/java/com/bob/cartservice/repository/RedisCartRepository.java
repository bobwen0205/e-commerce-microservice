package com.bob.cartservice.repository;

import com.bob.cartservice.model.Cart;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

@Repository
@RequiredArgsConstructor
public class RedisCartRepository {

    private final RedisTemplate<String, Cart> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String CART_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(30);

    public void save(Cart cart) {
        String key = CART_PREFIX + cart.getUserId();
        redisTemplate.opsForValue().set(key, cart, CART_TTL);
    }

    public Optional<Cart> findByUserId(String userId) {
        String key = CART_PREFIX + userId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void delete(String userId) {
        String key = CART_PREFIX + userId;
        redisTemplate.delete(key);
    }

    // These methods to manage the index
    // --- Indexing Methods (Use stringRedisTemplate) ---

    public void addProductToCartIndex(String productId, String userId) {
        // Use stringRedisTemplate here because we are storing String (userId), not Cart
        stringRedisTemplate.opsForSet().add("product-index:" + productId, userId);
    }

    public void removeProductFromCartIndex(String productId, String userId) {
        stringRedisTemplate.opsForSet().remove("product-index:" + productId, userId);
    }

    public Set<String> getUsersWithProduct(String productId) {
        Set<String> members = stringRedisTemplate.opsForSet().members("product-index:" + productId);
        if (members == null) return Collections.emptySet();
        return members;
    }

    /**
     * Atomic update using Redis Optimistic Locking (WATCH/MULTI/EXEC).
     *
     * @param userId   The user ID
     * @param modifier Function to apply changes to the cart
     * @return The updated Cart object
     */
    public Cart updateCart(String userId, UnaryOperator<Cart> modifier) {
        String key = CART_PREFIX + userId;

        while (true) {
            try {
                // Execute logic within a Session to ensure the same connection is used for WATCH/MULTI/EXEC
                List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) throws DataAccessException {
                        // 1. WATCH the key
                        operations.watch(key);

                        // 2. GET current state
                        Cart cart = (Cart) operations.opsForValue().get(key);
                        if (cart == null) {
                            cart = new Cart();
                            cart.setUserId(userId);
                        }

                        // 3. Apply Business Logic (Modify In-Memory)
                        // Note: External calls (like gRPC) inside here will hold the Redis connection.
                        // For this architecture, we accept this trade-off for atomicity.
                        Cart updatedCart = modifier.apply(cart);

                        // 4. Start Transaction
                        operations.multi();

                        // 5. SET updated state
                        operations.opsForValue().set(key, updatedCart, CART_TTL);

                        // 6. EXEC (Returns null/empty if WATCH failed)
                        return operations.exec();
                    }
                });

                // If results is not empty, transaction succeeded
                if (results != null && !results.isEmpty()) {
                    // We need to return the cart state that was just saved.
                    // Since 'modifier' is side-effect free on the input (ideally), re-running it or capturing it is fine.
                    // For simplicity, we'll fetch or reconstruct.
                    // Better pattern: Capture the result from the modifier in the callback scope or return it via results?
                    // The modifier changed the object reference passed to SET.
                    // We can just return the result of a fresh fetch or the object we constructed.
                    // Since we need the *result* of the modifier:
                    return findByUserId(userId).orElse(new Cart());
                }

                // If execution failed (results is null/empty), loop and RETRY (Step 7)

            } catch (Exception e) {
                // Log and throw or retry depending on error type
                throw new RuntimeException("Failed to update cart", e);
            }
        }
    }
}