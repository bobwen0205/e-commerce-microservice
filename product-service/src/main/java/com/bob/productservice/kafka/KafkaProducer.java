package com.bob.productservice.kafka;

import com.bob.product.proto.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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
}