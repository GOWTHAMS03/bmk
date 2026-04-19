package com.busymumkitchen.controller;

import com.busymumkitchen.dto.auth.*;
import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.security.SecurityUtils;
import com.busymumkitchen.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(
            @Valid @RequestBody SendOtpRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(
                "OTP sent successfully",
                Map.of("otpExpiry", 300, "message", "Check your email/SMS or use 123456 in DEV mode")
        ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse.UserInfo userInfo = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", userInfo));
    }

    // ── Admin Email + Password Login ──────────────────────────────────────────

    /**
     * Admin-only login via email and password.
     * OTP login still works; this is an alternative for convenience.
     * Role: none (public) — but only ADMIN accounts can produce a valid token.
     */
    @PostMapping("/admin-login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(
            @Valid @RequestBody AdminLoginRequest request) {
        AuthResponse response = authService.adminLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Set / change password for the currently authenticated admin.
     * Role: ADMIN only.
     */
    @PostMapping("/admin/set-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setAdminPassword(
            @Valid @RequestBody SetAdminPasswordRequest request) {
        String adminId = securityUtils.getCurrentUser().getId().toString();
        authService.setAdminPassword(adminId, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully"));
    }
}
