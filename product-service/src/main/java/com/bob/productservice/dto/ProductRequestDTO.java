package com.bob.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequestDTO {
    @NotBlank(message = "Product name is required")
    @Size(max = 100, message = "Product name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Brand is required")
    @Size(max = 50, message = "Brand cannot exceed 50 characters")
    private String brand;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Price must be greater than 0.00")
    @DecimalMax(value = "9999.99", message = "Price must be less than or equal to 9999.99")
    private BigDecimal price;

    @NotNull(message = "Inventory is required")
    @Min(value = 1, message = "Inventory must be greater than and equal to 1")
    @Max(value = 9999, message = "Inventory must less than or equal to inventory")
    private int inventory;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @NotNull
    @Valid
    private CategoryRequestDto category;
}
