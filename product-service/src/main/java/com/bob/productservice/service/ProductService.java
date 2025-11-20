package com.bob.productservice.service;

import com.bob.productservice.dto.ProductRequestDTO;
import com.bob.productservice.dto.ProductResponseDTO;
import com.bob.productservice.model.Product;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponseDTO addProduct(ProductRequestDTO product);

    Product getProduct(UUID id);

    ProductResponseDTO getProductById(UUID id);

    void deleteProductById(UUID id);

    ProductResponseDTO updateProduct(ProductRequestDTO request, UUID productId);

    List<ProductResponseDTO> getAllProducts();

    List<ProductResponseDTO> getProductsByCategory(String category);

    List<ProductResponseDTO> getProductByBrand(String brand);

    List<ProductResponseDTO> getProductsByCategoryAndBrand(String category, String brand);

    List<ProductResponseDTO> getProductsByName(String name);

    List<ProductResponseDTO> getProductsByBrandAndName(String brand, String name);

    List<ProductResponseDTO> searchProducts(String brand, String name, String category);

    Long countProductsByBrandAndName(String brand, String name);
}

