package com.bob.productservice.repository;

import com.bob.productservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByCategoryNameIgnoreCase(String category);

    List<Product> findByBrandIgnoreCase(String brand);

    List<Product> findByCategoryNameIgnoreCaseAndBrandIgnoreCase(String category, String brand);

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByBrandIgnoreCaseAndNameContainingIgnoreCase(String brand, String name);

    Long countByBrandIgnoreCaseAndNameContainingIgnoreCase(String brand, String name);

    // optional: custom search using @Query or Specification
    @Query("""
            SELECT p FROM Product p
            JOIN p.category c
            WHERE (NULLIF(:brand, '') IS NULL OR LOWER(p.brand) = LOWER(NULLIF(:brand, '')))
              AND (NULLIF(:name, '') IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', NULLIF(:name, ''), '%')))
              AND (NULLIF(:category, '') IS NULL OR LOWER(c.name) = LOWER(NULLIF(:category, '')))
            """)
    List<Product> searchProducts(
            @Param("brand") String brand,
            @Param("name") String name,
            @Param("category") String category
    );

}
