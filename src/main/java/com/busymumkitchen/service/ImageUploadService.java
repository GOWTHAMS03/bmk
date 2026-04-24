package com.busymumkitchen.service;

import com.busymumkitchen.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private S3Client s3;

    @Value("${s3.bucket:}")
    private String bucket;

    @Value("${s3.folder:bmk-menu}")
    private String folder;

    @Value("${AWS_REGION:us-east-1}")
    private String region;

    private static final List<String> ALLOWED_TYPES =
            Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> EXT_TO_MIME = Map.of(
            "jpg",  "image/jpeg",
            "jpeg", "image/jpeg",
            "png",  "image/png",
            "webp", "image/webp",
            "gif",  "image/gif"
    );
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    @PostConstruct
    public void init() {
        if (bucket == null || bucket.isBlank()) {
            log.warn("S3 bucket is not configured. Set S3_BUCKET env var or s3.bucket property.");
        } else {
            log.info("S3 configured for bucket: {} in region {}", bucket, region);
        }
        s3 = S3Client.builder().region(Region.of(region)).build();
    }

    /**
     * Uploads an image to S3 and returns the public HTTPS URL.
     */
    public String upload(MultipartFile file) {
        validateFile(file);

        if (bucket == null || bucket.isBlank()) {
            throw new BadRequestException("Image upload is not configured. Please contact the administrator.");
        }

        try {
            String filename = file.getOriginalFilename();
            String ext = "jpg";
            if (filename != null && filename.contains(".")) {
                ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            }
            String key = folder + "/" + UUID.randomUUID() + "." + ext;

            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            s3.putObject(put, RequestBody.fromBytes(file.getBytes()));

            String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
            log.info("Image uploaded to S3: {}", url);
            return url;

        } catch (IOException e) {
            log.error("S3 upload IOException: {}", e.getMessage());
            throw new BadRequestException("Image upload failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("S3 upload error: {}", e.getMessage(), e);
            throw new BadRequestException("Image upload failed: " + e.getMessage());
        }
    }

    /**
     * Deletes an image from S3 by its object key (extracted from the URL).
     */
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String key = extractKey(imageUrl);
            if (key != null && !key.isBlank()) {
                DeleteObjectRequest dor = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
                s3.deleteObject(dor);
                log.info("Deleted S3 image: {}", key);
            }
        } catch (Exception e) {
            log.warn("Failed to delete S3 image {}: {}", imageUrl, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided");
        }
        String contentType = file.getContentType();
        // When mobile clients send application/octet-stream, resolve by file extension
        if (contentType == null || contentType.equalsIgnoreCase("application/octet-stream")) {
            String filename = file.getOriginalFilename();
            if (filename != null && filename.contains(".")) {
                String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
                contentType = EXT_TO_MIME.get(ext);
            }
        }
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(
                    "Invalid file type. Allowed: JPEG, PNG, WebP, GIF. Got: " + file.getContentType());
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException(
                    "File too large. Maximum allowed size is 5 MB. Got: "
                            + (file.getSize() / 1024 / 1024) + " MB");
        }
    }

    private String extractKey(String url) {
        try {
            // Expect URLs like: https://<bucket>.s3.<region>.amazonaws.com/<key>
            int idx = url.indexOf(".amazonaws.com/");
            if (idx == -1) return null;
            return url.substring(idx + ".amazonaws.com/".length());
        } catch (Exception e) {
            return null;
        }
    }
}
