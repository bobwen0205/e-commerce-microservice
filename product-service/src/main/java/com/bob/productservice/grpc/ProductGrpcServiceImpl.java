package com.bob.productservice.grpc;

import com.bob.product.proto.*;
import com.bob.productservice.model.Product;
import com.bob.productservice.repository.ProductRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class ProductGrpcServiceImpl extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductRepository productRepository;

    @Override
    public void getProductById(GetProductRequest request,
                               StreamObserver<com.bob.product.proto.Product> responseObserver) {
        String productId = request.getProductId();

        try {
            Product product = productRepository.findById(UUID.fromString(productId))
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            com.bob.product.proto.Product protoProduct = mapToProto(product);

            responseObserver.onNext(protoProduct);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void listProducts(ListProductsRequest request,
                             StreamObserver<ListProductsResponse> responseObserver) {
        List<String> productIds = request.getProductIdsList();

        List<Product> products;
        if (productIds.isEmpty()) {
            products = productRepository.findAll();
        } else {
            List<UUID> uuids = productIds.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
            products = productRepository.findAllById(uuids);
        }

        List<com.bob.product.proto.Product> protoProducts = products.stream()
                .map(this::mapToProto)
                .collect(Collectors.toList());

        ListProductsResponse response = ListProductsResponse.newBuilder()
                .addAllProducts(protoProducts)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void validateCartItems(ValidateCartItemsRequest request,
                                  StreamObserver<ValidateCartItemsResponse> responseObserver) {

        // 1. Extract IDs from request
        List<UUID> productIds = request.getItemsList().stream()
                .map(item -> UUID.fromString(item.getProductId()))
                .collect(Collectors.toList());

        // 2. Bulk Fetch from DB
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 3. Validate each item
        List<CartItemValidationResult> results = request.getItemsList().stream().map(item -> {
            UUID id = UUID.fromString(item.getProductId());
            Product product = productMap.get(id);

            CartItemValidationResult.Builder resultBuilder = CartItemValidationResult.newBuilder()
                    .setProductId(item.getProductId());

            if (product == null) {
                return resultBuilder
                        .setValid(false)
                        .setMessage("PRODUCT_NOT_FOUND")
                        .build();
            }

            if (!product.isActive()) {
                return resultBuilder
                        .setValid(false)
                        .setMessage("PRODUCT_INACTIVE")
                        .build();
            }

            if (product.getInventory() < item.getQuantity()) {
                return resultBuilder
                        .setValid(false)
                        .setMessage("INSUFFICIENT_INVENTORY")
                        .setAvailableQuantity(product.getInventory())
                        .setCurrentPrice(product.getPrice().toString())
                        .build();
            }

            // Valid
            return resultBuilder
                    .setValid(true)
                    .setCurrentPrice(product.getPrice().toString())
                    .setAvailableQuantity(product.getInventory())
                    .build();

        }).collect(Collectors.toList());

        // 4. Send Response
        ValidateCartItemsResponse response = ValidateCartItemsResponse.newBuilder()
                .addAllResults(results)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    private com.bob.product.proto.Product mapToProto(Product product) {
        return com.bob.product.proto.Product.newBuilder()
                .setId(product.getId().toString())
                .setName(product.getName())
                .setBrand(product.getBrand())
                .setDescription(product.getDescription())
                .setPrice(product.getPrice().toString()) // BigDecimal to String
                .setInventory(product.getInventory())
                .setCategoryName(product.getCategory() != null ? product.getCategory().getName() : "")
                .build();
    }
}