package com.bob.productservice.mapper;

import com.bob.productservice.dto.ImageResponseDTO;
import com.bob.productservice.model.Image;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ImageMapper {

    ImageResponseDTO toDto(Image image);

    List<ImageResponseDTO> toDtoList(List<Image> images);

}
