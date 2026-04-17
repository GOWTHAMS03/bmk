package com.busymumkitchen.service;

import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.model.mongo.NotificationLog;
import com.busymumkitchen.repository.mongo.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final NotificationLogRepository notificationLogRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${whatsapp.api-url}")
    private String apiUrl;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token}")
    private String accessToken;

    // ── Twilio WhatsApp config (optional) ──────────────────────────────────────
    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.whatsapp-from:whatsapp:+14155238886}")
    private String twilioWhatsappFrom;

    /**
     * Primary method called for every order status change.
     * Routes to Twilio (if configured) → Meta Graph API text → logs only (dev).
     */
    @Async
    public void sendOrderStatusNotification(String to, String orderNumber, OrderStatus status) {
        String message = buildStatusMessage(orderNumber, status);

        if (twilioAccountSid != null && !twilioAccountSid.isBlank()) {
            sendViaTwilio(to, message);
        } else if (accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank()) {
            sendTextViaMetaApi(to, message);
        } else {
            log.info("[DEV MODE] WhatsApp notification to {}: {}", to, message);
            saveNotificationLog(to, "ORDER_STATUS", message, "DEV_LOG", null);
        }
    }

    @Async
    public void sendOrderConfirmation(String whatsappNumber, String orderNumber,
                                       String totalAmount, List<String> itemNames) {
        String message = buildStatusMessage(orderNumber, OrderStatus.PLACED);
        if (twilioAccountSid != null && !twilioAccountSid.isBlank()) {
            sendViaTwilio(whatsappNumber, message);
        } else if (accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank()) {
            sendTextViaMetaApi(whatsappNumber, message);
        } else {
            log.info("[DEV MODE] WhatsApp order confirmation to {}: {}", whatsappNumber, message);
        }
    }

    @Async
    public void sendOrderStatusUpdate(String whatsappNumber, String orderNumber, String status) {
        try {
            sendOrderStatusNotification(whatsappNumber, orderNumber, OrderStatus.valueOf(status));
        } catch (Exception e) {
            log.warn("Unknown order status for WhatsApp: {}", status);
        }
    }

    @Async
    public void sendDeliveryNotification(String whatsappNumber, String orderNumber,
                                          String deliveryPartnerName) {
        String message = "🚗 Your order *" + orderNumber + "* is out for delivery! "
                + "Your rider " + deliveryPartnerName + " is on the way. Please be available.";
        if (twilioAccountSid != null && !twilioAccountSid.isBlank()) {
            sendViaTwilio(whatsappNumber, message);
        } else {
            log.info("[DEV MODE] WhatsApp delivery notification to {}: {}", whatsappNumber, message);
        }
    }

    // ── Twilio sender ────────────────────────────────────────────────────────
    private void sendViaTwilio(String toNumber, String message) {
        String normalizedTo = toNumber.startsWith("+") ? toNumber : "+" + toNumber;
        String twilioUrl = "https://api.twilio.com/2010-04-01/Accounts/"
                + twilioAccountSid + "/Messages.json";

        NotificationLog logEntry = NotificationLog.builder()
                .type("WHATSAPP_TWILIO")
                .recipient(normalizedTo)
                .templateName("ORDER_STATUS")
                .message(message)
                .build();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String credentials = twilioAccountSid + ":" + twilioAuthToken;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedCredentials);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("From", twilioWhatsappFrom);
            body.add("To", "whatsapp:" + normalizedTo);
            body.add("Body", message);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    twilioUrl, HttpMethod.POST, request, String.class);

            logEntry.setStatus("SENT");
            logEntry.setProviderResponse(response.getBody());
            log.info("Twilio WhatsApp sent to {}", normalizedTo);
        } catch (Exception e) {
            logEntry.setStatus("FAILED");
            logEntry.setFailureReason(e.getMessage());
            log.error("Twilio WhatsApp failed for {}: {}", normalizedTo, e.getMessage());
        }
        notificationLogRepository.save(logEntry);
    }

    // ── Meta Graph API text sender ───────────────────────────────────────────
    private void sendTextViaMetaApi(String recipient, String message) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", recipient,
                "type", "text",
                "text", Map.of("preview_url", false, "body", message)
        );
        sendMessage(recipient, body, "ORDER_STATUS_TEXT");
    }

    private void sendMessage(String recipient, Map<String, Object> body, String templateName) {
        NotificationLog logEntry = NotificationLog.builder()
                .type("WHATSAPP")
                .recipient(recipient)
                .templateName(templateName)
                .message(body.toString())
                .build();

        try {
            String url = apiUrl + "/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            logEntry.setStatus("SENT");
            logEntry.setProviderResponse(response.getBody());
            log.info("WhatsApp message sent to {}", recipient);

        } catch (Exception e) {
            logEntry.setStatus("FAILED");
            logEntry.setFailureReason(e.getMessage());
            log.error("Failed to send WhatsApp message to {}: {}", recipient, e.getMessage());
        }

        notificationLogRepository.save(logEntry);
    }

    private void saveNotificationLog(String recipient, String type, String message,
                                     String status, String response) {
        NotificationLog log2 = NotificationLog.builder()
                .type("WHATSAPP")
                .recipient(recipient)
                .templateName(type)
                .message(message)
                .status(status)
                .providerResponse(response)
                .build();
        notificationLogRepository.save(log2);
    }

    // ── Message builder ──────────────────────────────────────────────────────
    public static String buildStatusMessage(String orderNumber, OrderStatus status) {
        return switch (status) {
            case PLACED ->
                "🛒 Your order *" + orderNumber + "* has been placed successfully! "
                + "We'll confirm it shortly. Thank you for ordering!";
            case CONFIRMED ->
                "✅ Your order *" + orderNumber + "* is confirmed. "
                + "We are now preparing your food 🍗 Please allow 20-30 minutes.";
            case ACCEPTED ->
                "👍 Your order *" + orderNumber + "* has been accepted! "
                + "Our kitchen is getting ready to prepare your meal 🍳";
            case PREPARING ->
                "\uD83C\uDF73 Great news! Your order *" + orderNumber + "* is being prepared. "
                + "Our chefs are cooking your meal fresh just for you \uD83D\uDD25";
            case READY_FOR_PICKUP ->
                "📦 Your order *" + orderNumber + "* is ready and packed! "
                + "Our rider will pick it up and head your way shortly 🚀";
            case OUT_FOR_DELIVERY ->
                "🚗 Your order *" + orderNumber + "* is out for delivery! "
                + "Your food is on its way. Please be available at your address 📍";
            case DELIVERED ->
                "🎉 Your order *" + orderNumber + "* has been delivered! "
                + "Enjoy your meal 😋 Thank you for choosing us. Rate us on the app!";
            case CANCELLED ->
                "❌ Your order *" + orderNumber + "* has been cancelled. "
                + "If this was unexpected, please contact our support team.";
            case REFUNDED ->
                "💰 Your order *" + orderNumber + "* has been refunded. "
                + "The amount will reflect in your account within 3-5 business days.";
        };
    }
}
