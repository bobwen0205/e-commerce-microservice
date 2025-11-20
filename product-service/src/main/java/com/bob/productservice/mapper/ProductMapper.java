package com.bob.productservice.mapper;

import com.bob.productservice.dto.ProductRequestDTO;
import com.bob.productservice.dto.ProductResponseDTO;
import com.bob.productservice.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "category", source = "category.name")
    ProductResponseDTO toDto(Product product);

    @Mapping(target = "category", ignore = true)
    Product toEntity(ProductRequestDTO productRequestDTO);

    List<ProductResponseDTO> toDtoList(List<Product> products);

}
