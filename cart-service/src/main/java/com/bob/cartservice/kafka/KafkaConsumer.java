package com.bob.cartservice.kafka;

import com.bob.cartservice.service.CartService;
import com.bob.product.proto.Product;
import com.bob.product.proto.ProductDeletedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumer {

    private final CartService cartService;

    @KafkaListener(topics = "product.updated", groupId = "cart-service-group")
    public void consumeProductUpdatedEvent(byte[] message) {
        try {
            // 1. Parse byte[] back to Protobuf Object
            Product productEvent = Product.parseFrom(message);

            log.info("Consumed product update for ID: {}", productEvent.getId());

            // 2. Trigger business logic
            cartService.handleProductUpdate(productEvent);

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse Product protobuf message", e);
        }
    }

    @KafkaListener(topics = "product.deleted", groupId = "cart-service-group")
    public void consumeProductDeletedEvent(byte[] message) {
        try {
            ProductDeletedEvent event = ProductDeletedEvent.parseFrom(message);
            log.info("Consumed product deletion for ID: {}", event.getProductId());

            // Trigger cleanup
            cartService.handleProductDeletion(event.getProductId());

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse ProductDeletedEvent protobuf message", e);
        }
    }
}