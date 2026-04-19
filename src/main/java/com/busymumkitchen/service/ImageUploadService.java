package com.busymumkitchen.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.busymumkitchen.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:bmk-menu}")
    private String folder;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

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
    public void validateCredentials() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Cloudinary API key is not configured. " +
                    "Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET env vars.");
        } else {
            log.info("Cloudinary configured for cloud: {}", cloudName);
        }
    }

    /**
     * Uploads an image to Cloudinary and returns the secure HTTPS URL.
     * The URL can be stored directly in the menu_items.image_url column.
     */
    @SuppressWarnings("unchecked")
    public String upload(MultipartFile file) {
        validateFile(file);

        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException(
                    "Image upload is not configured. Please contact the administrator.");
        }

        try {
            String publicId = folder + "/" + UUID.randomUUID();

            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id",      publicId,
                            "resource_type",  "image",
                            "overwrite",      false,
                            "folder",         folder
                    )
            );

            String url = (String) result.get("secure_url");
            log.info("Image uploaded to Cloudinary: {}", url);
            return url;

        } catch (IOException e) {
            log.error("Cloudinary upload IOException: {}", e.getMessage());
            throw new BadRequestException("Image upload failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Cloudinary upload error: {}", e.getMessage(), e);
            throw new BadRequestException("Image upload failed: " + e.getMessage());
        }
    }

    /**
     * Deletes an image from Cloudinary by its public ID (extracted from the URL).
     * Safe to call — exceptions are swallowed so a delete failure won't break other operations.
     */
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String publicId = extractPublicId(imageUrl);
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.info("Deleted Cloudinary image: {}", publicId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete Cloudinary image {}: {}", imageUrl, e.getMessage());
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

    /**
     * Extracts the Cloudinary public_id from a secure_url.
     * Example: https://res.cloudinary.com/demo/image/upload/v123/bmk-menu/uuid.jpg
     *       → bmk-menu/uuid
     */
    private String extractPublicId(String url) {
        try {
            // Pattern: .../upload/v<version>/<public_id>.<ext>
            int uploadIdx = url.indexOf("/upload/");
            if (uploadIdx == -1) return null;
            String path = url.substring(uploadIdx + 8); // skip "/upload/"
            // Remove version segment if present (v1234567/)
            if (path.startsWith("v") && path.indexOf('/') > 0) {
                path = path.substring(path.indexOf('/') + 1);
            }
            // Remove extension
            int dotIdx = path.lastIndexOf('.');
            return dotIdx > 0 ? path.substring(0, dotIdx) : path;
        } catch (Exception e) {
            return null;
        }
    }
}
