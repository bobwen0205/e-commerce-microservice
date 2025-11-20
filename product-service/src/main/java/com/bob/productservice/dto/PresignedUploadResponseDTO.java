package com.bob.productservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PresignedUploadResponseDTO {

    private UUID imageId;
    private String fileName;
    private String uploadUrl;   // pre-signed PUT URL
    private String downloadUrl; // optional: pre-signed GET URL (preview)
}
