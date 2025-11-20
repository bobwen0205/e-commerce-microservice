package com.bob.productservice.service;

import com.bob.productservice.dto.ImageResponseDTO;
import com.bob.productservice.dto.PresignedUploadResponseDTO;

import java.util.UUID;

public interface ImageService {
    ImageResponseDTO getImageById(UUID id);

    void deleteImageById(UUID id);
    
    PresignedUploadResponseDTO createPresignedUploadUrl(UUID productId,
                                                        String originalFileName,
                                                        String contentType);

}
