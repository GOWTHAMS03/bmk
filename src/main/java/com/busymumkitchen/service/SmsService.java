package com.busymumkitchen.service;

import com.busymumkitchen.model.mongo.NotificationLog;
import com.busymumkitchen.repository.mongo.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class SmsService {

    private final SnsClient snsClient;
    private final NotificationLogRepository notificationLogRepository;

    @Autowired
    public SmsService(@Autowired(required = false) SnsClient snsClient,
                      @Autowired(required = false) NotificationLogRepository notificationLogRepository) {
        this.snsClient = snsClient;
        this.notificationLogRepository = notificationLogRepository;
    }

    @Value("${aws.sns.sender-id}")
    private String senderId;

    @Value("${aws.sns.enabled:true}")
    private boolean snsEnabled;

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.sms-from:}")
    private String twilioSmsFrom;

    @Value("${twilio.sms-enabled:false}")
    private boolean twilioSmsEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendOtp(String phoneNumber, String otp) {
        // When SNS is disabled (dev mode), print OTP prominently so developers can find it
        if (!snsEnabled || snsClient == null) {
            log.warn("\n=====================================================\n" +
                     "  [DEV MODE] OTP CONSOLE DELIVERY — SNS is disabled  \n" +
                     "  Phone  : {}                                         \n" +
                     "  OTP    : {}                                         \n" +
                     "  Expiry : 5 minutes                                  \n" +
                     "  → Enter this OTP in the app login form              \n" +
                     "  To switch to real SMS: set aws.sns.enabled=true     \n" +
                     "=====================================================\n",
                     phoneNumber, otp);
        }
        String message = "Your BusyMumKitchen verification code is: " + otp
                + ". Valid for 5 minutes. Do not share this code.";
        sendSms(phoneNumber, message, "OTP");
    }

    public void sendOrderConfirmation(String phoneNumber, String orderNumber, String totalAmount) {
        String message = "Your order " + orderNumber + " has been placed! Amount: ₹" + totalAmount
                + ". Track your order on BusyMumKitchen.";
        sendSms(phoneNumber, message, "ORDER_CONFIRMATION");
    }

    public void sendOrderStatusUpdate(String phoneNumber, String orderNumber, String status) {
        String message = "Your order " + orderNumber + " is now: " + status
                + ". Track on BusyMumKitchen.";
        sendSms(phoneNumber, message, "ORDER_STATUS");
    }

    private void sendSms(String phoneNumber, String message, String templateName) {
        NotificationLog logEntry = NotificationLog.builder()
                .type("SMS")
                .recipient(phoneNumber)
                .templateName(templateName)
                .message(message)
                .build();

        try {
            // 1. Try Twilio SMS
            if (twilioSmsEnabled && twilioAccountSid != null && !twilioAccountSid.isBlank()) {
                sendViaTwilioSms(phoneNumber, message);
                logEntry.setStatus("SENT");
                logEntry.setProviderMessageId("TWILIO_SMS");
                log.info("SMS sent via Twilio to {}", maskPhone(phoneNumber));
                saveLog(logEntry);
                return;
            }

            // 2. Try AWS SNS
            if (snsEnabled && snsClient != null) {
                PublishRequest request = PublishRequest.builder()
                        .phoneNumber(phoneNumber)
                        .message(message)
                        .messageAttributes(Map.of(
                                "AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                                        .stringValue(senderId)
                                        .dataType("String")
                                        .build(),
                                "AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                                        .stringValue("Transactional")
                                        .dataType("String")
                                        .build()
                        ))
                        .build();

                PublishResponse response = snsClient.publish(request);
                logEntry.setStatus("SENT");
                logEntry.setProviderMessageId(response.messageId());
                log.info("SMS sent via SNS to {} - MessageId: {}", maskPhone(phoneNumber), response.messageId());
                saveLog(logEntry);
                return;
            }

            // 3. Dev mode fallback — log to console
            log.info("[DEV MODE] Would send SMS to {}: {}", maskPhone(phoneNumber), message);
            logEntry.setStatus("SIMULATED");
            logEntry.setProviderMessageId("SIMULATED");
            saveLog(logEntry);

        } catch (Exception e) {
            logEntry.setStatus("FAILED");
            logEntry.setFailureReason(e.getMessage());
            log.error("Failed to send SMS to {}: {}", maskPhone(phoneNumber), e.getMessage());
            saveLog(logEntry);
        }
    }

    private void sendViaTwilioSms(String to, String message) {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String credentials = twilioAccountSid + ":" + twilioAuthToken;
        headers.set("Authorization", "Basic " +
                Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", to);
        body.add("From", twilioSmsFrom);
        body.add("Body", message);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }

    private void saveLog(NotificationLog logEntry) {
        if (notificationLogRepository != null) {
            try {
                notificationLogRepository.save(logEntry);
            } catch (Exception mongoEx) {
                log.warn("MongoDB unavailable — skipping notification log: {}", mongoEx.getMessage());
            }
        }
    }

    private String maskPhone(String phone) {
        if (phone.length() <= 4) return "****";
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
