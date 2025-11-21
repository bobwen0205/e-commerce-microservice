package com.bob.cartservice.grpc;

import com.bob.product.proto.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductGrpcClient {

    // "product-service" must match the name configured in application.properties or service discovery
    @GrpcClient("product-service")
    private ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub;

    public Product getProduct(String productId) {
        GetProductRequest request = GetProductRequest.newBuilder()
                .setProductId(productId)
                .build();
        return productServiceBlockingStub.getProductById(request);
    }

    public ValidateCartItemsResponse validateCartItems(List<CartItemRequest> items) {
        ValidateCartItemsRequest request = ValidateCartItemsRequest.newBuilder()
                .addAllItems(items)
                .build();
        return productServiceBlockingStub.validateCartItems(request);
    }
}