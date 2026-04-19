package com.busymumkitchen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends OTP via email using Gmail SMTP (FREE).
 *
 * Setup:
 *   1. Enable 2FA on your Google account
 *   2. Generate App Password: https://myaccount.google.com/apppasswords
 *   3. Set MAIL_USERNAME and MAIL_PASSWORD env vars
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpService {

    private final JavaMailSender mailSender;

    @Value("${mail.from:noreply@busymumkitchen.com}")
    private String fromEmail;

    @Value("${mail.from-name:BusyMumKitchen}")
    private String fromName;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your BusyMumKitchen Verification Code: " + otp);

            String html = buildOtpEmailHtml(otp);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("✅ OTP email sent to {}", maskEmail(toEmail));
        } catch (MessagingException e) {
            log.error("❌ Failed to send OTP email to {}: {}", maskEmail(toEmail), e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        } catch (Exception e) {
            log.error("❌ Failed to send OTP email to {}: {}", maskEmail(toEmail), e.getMessage());
        }
    }

    @Async
    public void sendOrderStatusEmail(String toEmail, String orderNumber, String dailyOrderNumber, String status) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Order " + (dailyOrderNumber != null ? dailyOrderNumber : orderNumber) + " — " + status);

            String html = "<div style='font-family:Arial,sans-serif;max-width:500px;margin:0 auto;padding:20px'>"
                    + "<h2 style='color:#FF6B35'>🍽️ BusyMumKitchen</h2>"
                    + "<p>Your order <strong>" + (dailyOrderNumber != null ? dailyOrderNumber : orderNumber)
                    + "</strong> status: <strong>" + status + "</strong></p>"
                    + "<p>Track your order in the app!</p>"
                    + "<p style='color:#888;font-size:12px'>— Team BusyMumKitchen</p></div>";

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Order status email sent to {}", maskEmail(toEmail));
        } catch (Exception e) {
            log.warn("Failed to send order status email to {}: {}", maskEmail(toEmail), e.getMessage());
        }
    }

    private String buildOtpEmailHtml(String otp) {
        return "<div style='font-family:Arial,sans-serif;max-width:500px;margin:0 auto;padding:20px;background:#fff;border-radius:10px'>"
                + "<div style='text-align:center;padding:20px 0'>"
                + "<h1 style='color:#FF6B35;margin:0'>🍽️ BusyMumKitchen</h1>"
                + "<p style='color:#666;margin:5px 0'>Homemade Food, Delivered Fresh</p>"
                + "</div>"
                + "<div style='background:#f8f9fa;border-radius:8px;padding:30px;text-align:center;margin:20px 0'>"
                + "<p style='color:#333;font-size:16px;margin:0 0 10px'>Your verification code is:</p>"
                + "<div style='font-size:36px;font-weight:bold;letter-spacing:8px;color:#FF6B35;padding:15px;background:#fff;border-radius:8px;border:2px dashed #FF6B35;display:inline-block'>"
                + otp
                + "</div>"
                + "<p style='color:#888;font-size:13px;margin:15px 0 0'>Valid for <strong>5 minutes</strong>. Do not share this code.</p>"
                + "</div>"
                + "<p style='color:#999;font-size:11px;text-align:center'>If you didn't request this code, please ignore this email.</p>"
                + "</div>";
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        String[] parts = email.split("@");
        String name = parts[0];
        if (name.length() <= 2) return name + "***@" + parts[1];
        return name.substring(0, 2) + "***@" + parts[1];
    }
}

