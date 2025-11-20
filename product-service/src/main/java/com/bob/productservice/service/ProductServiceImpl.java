package com.bob.productservice.service;

import com.bob.productservice.dto.ProductRequestDTO;
import com.bob.productservice.dto.ProductResponseDTO;
import com.bob.productservice.exception.ResourceNotFoundException;
import com.bob.productservice.kafka.KafkaProducer;
import com.bob.productservice.mapper.ProductMapper;
import com.bob.productservice.model.Category;
import com.bob.productservice.model.Product;
import com.bob.productservice.repository.CategoryRepository;
import com.bob.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;
    private final KafkaProducer kafkaProducer;

    @Override
    public ProductResponseDTO addProduct(ProductRequestDTO productRequestDTO) {
        Category category = findOrCreateCategory(productRequestDTO.getCategory().getName());

        Product product = mapper.toEntity(productRequestDTO);
        product.setCategory(category);

        return mapper.toDto(productRepository.save(product));
    }

    @Override
    public Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Override
    public ProductResponseDTO getProductById(UUID id) {
        return mapper.toDto(getProduct(id));

    }

    @Override
    public void deleteProductById(UUID id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    @Override
    public ProductResponseDTO updateProduct(ProductRequestDTO request, UUID productId) {
        Product product = getProduct(productId);
        Category category = findOrCreateCategory(request.getCategory().getName());
        product.setName(request.getName());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setInventory(request.getInventory());
        product.setDescription(request.getDescription());
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);

        kafkaProducer.sendProductUpdatedEvent(savedProduct);

        return mapper.toDto(savedProduct);
    }

    @Override
    public List<ProductResponseDTO> getAllProducts() {
        return mapper.toDtoList(productRepository.findAll());
    }

    @Override
    public List<ProductResponseDTO> getProductsByCategory(String category) {
        return mapper.toDtoList(productRepository.findByCategoryNameIgnoreCase(category));
    }

    @Override
    public List<ProductResponseDTO> getProductByBrand(String brand) {
        return mapper.toDtoList(productRepository.findByBrandIgnoreCase(brand));
    }

    @Override
    public List<ProductResponseDTO> getProductsByCategoryAndBrand(String category, String brand) {
        return mapper.toDtoList(productRepository.findByCategoryNameIgnoreCaseAndBrandIgnoreCase(category, brand));
    }

    @Override
    public List<ProductResponseDTO> getProductsByName(String name) {
        return mapper.toDtoList(productRepository.findByNameContainingIgnoreCase(name));
    }

    @Override
    public List<ProductResponseDTO> getProductsByBrandAndName(String brand, String name) {
        return mapper.toDtoList(productRepository.findByBrandIgnoreCaseAndNameContainingIgnoreCase(brand, name));
    }

    @Override
    public List<ProductResponseDTO> searchProducts(String brand, String name, String category) {
        return mapper.toDtoList(productRepository.searchProducts(brand, name, category)); // custom Query or Specification
    }

    @Override
    public Long countProductsByBrandAndName(String brand, String name) {
        return productRepository.countByBrandIgnoreCaseAndNameContainingIgnoreCase(brand, name);
    }


    private Category findOrCreateCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }
}
