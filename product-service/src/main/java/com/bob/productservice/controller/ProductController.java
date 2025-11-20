package com.bob.productservice.controller;

import com.bob.productservice.dto.ProductRequestDTO;
import com.bob.productservice.dto.ProductResponseDTO;
import com.bob.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/products")
@Tag(name = "Product", description = "API for managing Products")
public class ProductController {
    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get All Products")
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts() {
        List<ProductResponseDTO> productResponseDTOS = productService.getAllProducts();
        return ResponseEntity.ok(productResponseDTOS);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a Product by ID")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable UUID id) {
        ProductResponseDTO productResponseDTO = productService.getProductById(id);
        return ResponseEntity.ok(productResponseDTO);
    }

    @PostMapping
    @Operation(summary = "Add a new Product")
    public ResponseEntity<ProductResponseDTO> addProduct(@RequestBody @Valid ProductRequestDTO productRequestDTO) {
        ProductResponseDTO productResponseDTO = productService.addProduct(productRequestDTO);
        return ResponseEntity.ok(productResponseDTO);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a Product")
    public ResponseEntity<ProductResponseDTO> updateProduct(@RequestBody @Valid ProductRequestDTO productRequestDTO, @PathVariable UUID id) {
        ProductResponseDTO productResponseDTO = productService.updateProduct(productRequestDTO, id);
        return ResponseEntity.ok(productResponseDTO);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Product")
    public ResponseEntity<String> deleteProduct(@PathVariable UUID id) {
        productService.deleteProductById(id);
        return ResponseEntity.ok().body("Success!");
    }

    @GetMapping("/search")
    @Operation(summary = "Search Products")
    public ResponseEntity<List<ProductResponseDTO>> searchProducts(
            @RequestParam(required = false, defaultValue = "") String brand,
            @RequestParam(required = false, defaultValue = "") String name,
            @RequestParam(required = false, defaultValue = "") String category) {
        List<ProductResponseDTO> productResponseDTOS = productService.searchProducts(brand, name, category);
        return ResponseEntity.ok(productResponseDTOS);
    }

    @GetMapping("/count")
    @Operation(summary = "Count Products by Brand and Name")
    public ResponseEntity<Long> countProductsByBrandAndName(@RequestParam String brand, @RequestParam String name) {
        return ResponseEntity.ok(productService.countProductsByBrandAndName(brand, name));
    }
}
