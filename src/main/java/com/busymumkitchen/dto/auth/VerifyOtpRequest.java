package com.busymumkitchen.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    /** E.164 phone number. Required if email not provided. */
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phoneNumber;

    /** Email address. Required if phoneNumber not provided. */
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;

    @AssertTrue(message = "Either phone number or email is required")
    private boolean isPhoneOrEmailProvided() {
        return (phoneNumber != null && !phoneNumber.isBlank())
                || (email != null && !email.isBlank());
    }
}
