package com.unios.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final com.unios.repository.EmailLogRepository emailLogRepository;
    private final com.unios.service.test.FailureInjectionService failureInjectionService;

    public EmailService(JavaMailSender javaMailSender, 
                        com.unios.repository.EmailLogRepository emailLogRepository,
                        com.unios.service.test.FailureInjectionService failureInjectionService) {
        this.javaMailSender = javaMailSender;
        this.emailLogRepository = emailLogRepository;
        this.failureInjectionService = failureInjectionService;
    }

    public void sendEmailWithAttachment(String to, String subject, String text, String attachmentFilename,
            byte[] attachmentData) {
        
        // Save initial log
        com.unios.model.EmailLog logEntity = com.unios.model.EmailLog.builder()
                .recipient(to)
                .subject(subject)
                .body(text)
                .status("PENDING")
                .retryCount(0)
                .build();
        final com.unios.model.EmailLog savedLog = emailLogRepository.save(logEntity);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (to.endsWith("@unios.demo")) {
                    System.out.println("📧 [SIMULATION] Skipping real email for demo address: " + to);
                    savedLog.setStatus("SENT");
                    savedLog.setSentAt(java.time.LocalDateTime.now());
                    emailLogRepository.save(savedLog);
                    return;
                }
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);

                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(text);

                if (attachmentData != null && attachmentFilename != null) {
                    helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentData));
                }

                javaMailSender.send(message);
                
                savedLog.setStatus("SENT");
                savedLog.setSentAt(java.time.LocalDateTime.now());
                emailLogRepository.save(savedLog);
                
                System.out.println("✅ Real Email sent successfully to: " + to);
            } catch (Exception e) {
                savedLog.setStatus("FAILED");
                savedLog.setErrorMessage(e.getMessage());
                emailLogRepository.save(savedLog);
                System.err.println("❌ Failed to send email to " + to + ". Logged to DB for retry.");
            }
        });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000) // Retry every minute
    public void retryFailedEmails() {
        java.util.List<com.unios.model.EmailLog> failed = emailLogRepository.findByStatus("FAILED");
        for (com.unios.model.EmailLog log : failed) {
            if (log.getRetryCount() < 3) {
                log.setRetryCount(log.getRetryCount() + 1);
                log.setStatus("PENDING");
                emailLogRepository.save(log);
                
                // Re-attempt non-attachment emails for now (simpler logic)
                sendEmail(log.getRecipient(), log.getSubject(), log.getBody());
            }
        }
    }

    public void sendEmail(String to, String subject, String text) {
        sendEmailWithAttachment(to, subject, text, null, null);
    }
}
