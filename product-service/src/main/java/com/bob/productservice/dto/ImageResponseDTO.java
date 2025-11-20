package com.bob.productservice.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ImageResponseDTO {
    private UUID id;
    private String fileName;
    private String downloadUrl;
}
