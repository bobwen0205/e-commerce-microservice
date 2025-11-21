package com.bob.productservice.kafka;

import com.bob.product.proto.Product;
import com.bob.product.proto.ProductDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public void sendProductUpdatedEvent(com.bob.productservice.model.Product productEntity) {
        // 1. Convert Entity to Protobuf Message
        // We use the existing 'Product' proto definition as our Event object
        Product event = Product.newBuilder()
                .setId(productEntity.getId().toString())
                .setName(productEntity.getName())
                .setBrand(productEntity.getBrand())
                .setPrice(productEntity.getPrice().toString()) // BigDecimal -> String
                .setInventory(productEntity.getInventory())
                .build();

        // 2. Send as byte[]
        try {
            log.info("Publishing product.updated event for Product ID: {}", event.getId());
            kafkaTemplate.send("product.updated", event.getId(), event.toByteArray());
        } catch (Exception e) {
            log.error("Error sending ProductUpdated event: {}", event, e);
        }
    }

    public void sendProductDeletedEvent(com.bob.productservice.model.Product productEntity) {
        try {
            ProductDeletedEvent event = ProductDeletedEvent.newBuilder()
                    .setProductId(productEntity.getId().toString())
                    .setProductName(productEntity.getName())
                    .setEventType("DELETED")
                    .setTimestamp(Instant.now().toString())
                    .build();

            log.info("Publishing product.deleted event for Product ID: {}", event.getProductId());

            // Send to "product.deleted" topic
            kafkaTemplate.send("product.deleted", event.getProductId(), event.toByteArray());

        } catch (Exception e) {
            log.error("Error sending ProductDeleted event", e);
        }
    }
}