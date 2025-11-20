package com.bob.productservice.controller;

import com.bob.productservice.dto.ImageResponseDTO;
import com.bob.productservice.dto.PresignedUploadResponseDTO;
import com.bob.productservice.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/images")
@RequiredArgsConstructor
@Tag(name = "Images")
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "Create pre-signed upload URL for an image")
    @PostMapping("/presigned-upload")
    public ResponseEntity<PresignedUploadResponseDTO> createPresignedUploadUrl(
            @RequestParam UUID productId,
            @RequestParam String fileName,
            @RequestParam(required = false) String contentType
    ) {
        PresignedUploadResponseDTO response = imageService.createPresignedUploadUrl(
                productId, fileName, contentType
        );
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get image by ID (returns presigned download URL)")
    @GetMapping("/{id}")
    public ResponseEntity<ImageResponseDTO> getImage(@PathVariable UUID id) {
        return ResponseEntity.ok(imageService.getImageById(id));
    }

    @Operation(summary = "Delete image by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id) {
        imageService.deleteImageById(id);
        return ResponseEntity.noContent().build();
    }
}
