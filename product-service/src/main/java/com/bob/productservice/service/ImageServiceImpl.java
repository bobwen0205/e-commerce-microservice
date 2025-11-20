package com.bob.productservice.service;

import com.bob.productservice.dto.ImageResponseDTO;
import com.bob.productservice.dto.PresignedUploadResponseDTO;
import com.bob.productservice.exception.ResourceNotFoundException;
import com.bob.productservice.mapper.ImageMapper;
import com.bob.productservice.model.Image;
import com.bob.productservice.model.Product;
import com.bob.productservice.repository.ImageRepository;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ImageServiceImpl implements ImageService {
    private final ImageRepository imageRepository;
    private final ImageMapper mapper;
    private final ProductService productService;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.upload-expiry}")
    private Integer uploadExpiry;

    @Value("${minio.download-expiry}")
    private Integer downloadExpiry;


    private Image getImage(UUID id) {
        return imageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
    }

    @Override
    public ImageResponseDTO getImageById(UUID id) {
        Image image = getImage(id);

        // Generate pre-signed download URL on the fly
        String downloadUrl = generatePresignedGetUrl(image.getBucket(), image.getObjectKey());

        ImageResponseDTO imageResponseDTO = new ImageResponseDTO();
        imageResponseDTO.setId(image.getId());
        imageResponseDTO.setFileName(image.getFileName());
        imageResponseDTO.setDownloadUrl(downloadUrl);
        return imageResponseDTO;
    }

    @Override
    public void deleteImageById(UUID id) {
        Image image = getImage(id);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(image.getBucket())
                    .object(image.getObjectKey())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object from MinIO", e);
        }
        imageRepository.delete(image);
    }

    @Override
    public PresignedUploadResponseDTO createPresignedUploadUrl(UUID productId, String originalFileName, String contentType) {
        Product product = productService.getProduct(productId);

        String objectKey = "product/" + productId + "/" + UUID.randomUUID() + "-" + originalFileName;

        // ensure bucket exists
        ensureBucketExists(bucket);

        // create and save Image entry
        Image image = Image.builder()
                .fileName(originalFileName)
                .contentType(contentType)
                .objectKey(objectKey)
                .bucket(bucket)
                .product(product)
                .build();

        Image savedImage = imageRepository.save(image);

        //generate pre-signed PUT URL
        String uploadUrl = generatePresignedPutUrl(bucket, objectKey, contentType);
        String downloadUrl = generatePresignedGetUrl(bucket, objectKey);

        return PresignedUploadResponseDTO.builder()
                .imageId(savedImage.getId())
                .fileName(savedImage.getFileName())
                .uploadUrl(uploadUrl)
                .downloadUrl(downloadUrl)
                .build();
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure MinIO bucket exists", e);
        }
    }

    private String generatePresignedPutUrl(String bucket, String objectKey, String contentType) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(Method.PUT)
                            .expiry(uploadExpiry)
                            .extraQueryParams(
                                    contentType != null
                                            ? java.util.Map.of("Content-Type", contentType)
                                            : null
                            )
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate pre-signed PUT URL", e);
        }
    }

    private String generatePresignedGetUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(Method.GET)
                            .expiry(downloadExpiry)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate pre-signed GET URL", e);
        }
    }


}
