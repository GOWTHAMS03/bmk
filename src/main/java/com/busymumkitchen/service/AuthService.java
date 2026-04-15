package com.busymumkitchen.service;

import com.busymumkitchen.dto.auth.*;
import com.busymumkitchen.exception.BadRequestException;
import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.User;
import com.busymumkitchen.model.enums.UserRole;
import com.busymumkitchen.repository.UserRepository;
import com.busymumkitchen.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SmsService smsService;
    private final KeyValueStore keyValueStore;

    @Value("${otp.expiry-seconds}")
    private int otpExpirySeconds;

    @Value("${otp.length}")
    private int otpLength;

    @Value("${otp.max-attempts}")
    private int maxOtpAttempts;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp_attempts:";

    public void sendOtp(SendOtpRequest request) {
        String phoneNumber = request.getPhoneNumber();

        // Check rate limiting
        String attemptsKey = OTP_ATTEMPTS_PREFIX + phoneNumber;
        Integer attempts = (Integer) keyValueStore.getValue(attemptsKey);
        if (attempts != null && attempts >= maxOtpAttempts) {
            throw new BadRequestException("Too many OTP requests. Please try again later.");
        }

        // Generate OTP
        String otp = generateOtp();

        // Store OTP
        String otpKey = OTP_PREFIX + phoneNumber;
        keyValueStore.setValue(otpKey, otp, Duration.ofSeconds(otpExpirySeconds));

        // Track attempts
        keyValueStore.increment(attemptsKey);
        keyValueStore.expire(attemptsKey, Duration.ofMinutes(30));

        // Send OTP via SMS (dev fallback: printed to console when aws.sns.enabled=false)
        smsService.sendOtp(phoneNumber, otp);
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String otp = request.getOtp();

        // Verify OTP
        String otpKey = OTP_PREFIX + phoneNumber;
        String storedOtp = (String) keyValueStore.getValue(otpKey);

        if (storedOtp == null) {
            throw new BadRequestException("OTP expired. Please request a new one.");
        }

        if (!storedOtp.equals(otp)) {
            throw new BadRequestException("Invalid OTP. Please try again.");
        }

        // Clear OTP after successful verification
        keyValueStore.delete(otpKey);
        keyValueStore.delete(OTP_ATTEMPTS_PREFIX + phoneNumber);

        // Find or create user
        boolean isNewUser = false;
        User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);

        if (user == null) {
            isNewUser = true;
            user = User.builder()
                    .phoneNumber(phoneNumber)
                    .role(UserRole.CUSTOMER)
                    .isActive(true)
                    .build();
            user = userRepository.save(user);
        }

        if (!user.getIsActive()) {
            throw new BadRequestException("Account is deactivated. Contact support.");
        }

        // Generate tokens
        return buildAuthResponse(user, isNewUser);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        if (!"REFRESH".equals(tokenType)) {
            throw new BadRequestException("Invalid token type");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return buildAuthResponse(user, false);
    }

    @Transactional
    public AuthResponse.UserInfo register(RegisterRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new BadRequestException("Please verify your phone number first"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setWhatsappNumber(request.getWhatsappNumber());
        userRepository.save(user);

        return AuthResponse.UserInfo.builder()
                .id(user.getId().toString())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isNewUser(false)
                .build();
    }

    private AuthResponse buildAuthResponse(User user, boolean isNewUser) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getPhoneNumber(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiry / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .phoneNumber(user.getPhoneNumber())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .isNewUser(isNewUser)
                        .build())
                .build();
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }
}
