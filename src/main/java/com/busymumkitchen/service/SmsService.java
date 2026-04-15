package com.busymumkitchen.service;

import com.busymumkitchen.model.mongo.NotificationLog;
import com.busymumkitchen.repository.mongo.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

@Service
@Slf4j
public class SmsService {

    private final SnsClient snsClient;
    private final NotificationLogRepository notificationLogRepository;

    @Autowired
    public SmsService(@Autowired(required = false) SnsClient snsClient,
                      NotificationLogRepository notificationLogRepository) {
        this.snsClient = snsClient;
        this.notificationLogRepository = notificationLogRepository;
    }

    @Value("${aws.sns.sender-id}")
    private String senderId;

    @Value("${aws.sns.enabled:true}")
    private boolean snsEnabled;

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
            if (!snsEnabled || snsClient == null) {
            // In local/dev mode when SNS is disabled or no credentials configured, log OTP/message to console
            log.info("[SNS DISABLED] Would send SMS to {}: {}", maskPhone(phoneNumber), message);
            logEntry.setStatus("SIMULATED");
            logEntry.setProviderMessageId("SIMULATED");
            notificationLogRepository.save(logEntry);
            return;
            }

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
            log.info("SMS sent to {} - MessageId: {}", maskPhone(phoneNumber), response.messageId());

        } catch (Exception e) {
            logEntry.setStatus("FAILED");
            logEntry.setFailureReason(e.getMessage());
            log.error("Failed to send SMS to {}: {}", maskPhone(phoneNumber), e.getMessage());
        }

        notificationLogRepository.save(logEntry);
    }

    private String maskPhone(String phone) {
        if (phone.length() <= 4) return "****";
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
