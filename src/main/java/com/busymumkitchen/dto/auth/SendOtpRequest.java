package com.busymumkitchen.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendOtpRequest {

    /** E.164 phone number (e.g. +919876543210). Required if email not provided. */
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Invalid phone number format. Use E.164 format (e.g., +919876543210)")
    private String phoneNumber;

    /** Email address. Required if phoneNumber not provided. */
    @Email(message = "Invalid email format")
    private String email;

    @AssertTrue(message = "Either phone number or email is required")
    private boolean isPhoneOrEmailProvided() {
        return (phoneNumber != null && !phoneNumber.isBlank())
                || (email != null && !email.isBlank());
    }
}
