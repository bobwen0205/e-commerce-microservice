package com.bob.cartservice.repository;

import com.bob.cartservice.model.Cart;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

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
}