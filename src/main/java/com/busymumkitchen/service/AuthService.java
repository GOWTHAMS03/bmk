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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final EmailOtpService emailOtpService;
    private final KeyValueStore keyValueStore;

    @Value("${otp.expiry-seconds}")
    private int otpExpirySeconds;

    @Value("${otp.length}")
    private int otpLength;

    @Value("${otp.max-attempts}")
    private int maxOtpAttempts;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    /** OTP delivery method: DEV, EMAIL, or SMS */
    @Value("${otp.delivery-method:DEV}")
    private String otpDeliveryMethod;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp_attempts:";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    public void sendOtp(SendOtpRequest request) {
        String phoneNumber = request.getPhoneNumber();

        // Check rate limiting
        String attemptsKey = OTP_ATTEMPTS_PREFIX + phoneNumber;
        Integer attempts = (Integer) keyValueStore.getValue(attemptsKey);
        if (attempts != null && attempts >= maxOtpAttempts) {
            throw new BadRequestException("Too many OTP requests. Please try again later.");
        }

        // Generate OTP (in DEV mode, always use fixed "123456")
        String otp = "DEV".equalsIgnoreCase(otpDeliveryMethod) ? "123456" : generateOtp();

        // Store OTP
        String otpKey = OTP_PREFIX + phoneNumber;
        keyValueStore.setValue(otpKey, otp, Duration.ofSeconds(otpExpirySeconds));

        // Track attempts
        keyValueStore.increment(attemptsKey);
        keyValueStore.expire(attemptsKey, Duration.ofMinutes(30));

        // Deliver OTP based on configured method
        switch (otpDeliveryMethod.toUpperCase()) {
            case "EMAIL" -> {
                // Email-based OTP (FREE via Gmail SMTP)
                String email = request.getEmail();
                if (email == null || email.isBlank()) {
                    // Try to find email from existing user
                    User existingUser = userRepository.findByPhoneNumber(phoneNumber).orElse(null);
                    if (existingUser != null && existingUser.getEmail() != null) {
                        email = existingUser.getEmail();
                    }
                }
                if (email != null && !email.isBlank()) {
                    emailOtpService.sendOtpEmail(email, otp);
                    log.info("OTP sent via EMAIL to user with phone: {}", phoneNumber);
                } else {
                    // Fallback: log to console if no email available
                    log.warn("No email found for {}. OTP: {} (provide email in request or register first)", phoneNumber, otp);
                }
            }
            case "SMS" -> {
                // SMS-based OTP (Twilio/AWS SNS — paid)
                smsService.sendOtp(phoneNumber, otp);
            }
            default -> {
                // DEV mode — fixed OTP "123456", printed to console
                log.warn("\n=====================================================\n" +
                         "  [DEV MODE] OTP = 123456 (always)                    \n" +
                         "  Phone  : {}                                         \n" +
                         "  Method : DEV (free, no SMS/email sent)              \n" +
                         "  → Set OTP_DELIVERY_METHOD=EMAIL for free email OTP  \n" +
                         "  → Set OTP_DELIVERY_METHOD=SMS for paid SMS OTP      \n" +
                         "=====================================================\n",
                         phoneNumber);
            }
        }
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

    // ── Admin Email + Password Login ──────────────────────────────────────────

    public AuthResponse adminLogin(AdminLoginRequest request) {
        User admin = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Invalid credentials");
        }

        if (admin.getPasswordHash() == null) {
            throw new BadRequestException("Password not set. Please log in via OTP first and set your password.");
        }

        if (!PASSWORD_ENCODER.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }

        if (!admin.getIsActive()) {
            throw new BadRequestException("Account is deactivated. Contact support.");
        }

        log.info("Admin login successful for email: {}", request.getEmail());
        return buildAuthResponse(admin, false);
    }

    @Transactional
    public void setAdminPassword(String adminId, String newPassword) {
        User admin = userRepository.findById(java.util.UUID.fromString(adminId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Only admin accounts can set passwords via this endpoint");
        }

        admin.setPasswordHash(PASSWORD_ENCODER.encode(newPassword));
        userRepository.save(admin);
        log.info("Admin password updated for userId: {}", adminId);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }
}
