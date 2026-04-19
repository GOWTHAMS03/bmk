package com.busymumkitchen.controller;

import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ImageUploadService imageUploadService;

    /**
     * POST /api/v1/upload/image
     *
     * Upload an image to Cloudinary (free bucket).
     * Returns: { "success": true, "data": { "url": "https://res.cloudinary.com/..." } }
     *
     * Requires ADMIN or KITCHEN_STAFF role.
     * Max size: 5 MB. Allowed types: JPEG, PNG, WebP, GIF.
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN_STAFF')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestPart("file") MultipartFile file) {

        String url = imageUploadService.upload(file);
        return ResponseEntity.ok(ApiResponse.success(
                "Image uploaded successfully",
                Map.of("url", url)
        ));
    }
}
