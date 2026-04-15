package com.unios.service.communication;

import com.unios.model.Student;
import com.unios.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParentCallService {
    private final StudentRepository studentRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${retell.api.key:key_d4a7b81d12f68174f23dce76e624}")
    private String apiKey;

    @Value("${retell.agent.id:agent_6dfc97c8514e66144b8ea5f15d}")
    private String agentId;

    @Value("${retell.from.number:}")
    private String fromNumber;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void callParent(Long studentId, String subject) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            log.error("[PARENT CALL] Student not found: {}", studentId);
            return;
        }

        String parentPhone = student.getParentPhone();
        if (parentPhone == null || parentPhone.trim().isEmpty() || parentPhone.equalsIgnoreCase("NULL") || parentPhone.equalsIgnoreCase("MISSING")) {
            log.warn("[PARENT CALL] Skipped: No phone number for student {}", student.getFullName());
            System.out.println("[RETELL ERROR] Cannot call student " + student.getFullName() + ": Phone number is missing or NULL.");
            return;
        }

        log.info("[PARENT CALL] Initiating Retell AI call to {} for student {} (Subject: {})", 
                parentPhone, student.getFullName(), subject);

        try {
            String url = "https://api.retellai.com/v2/create-phone-call";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("student_name", student.getFullName());
            metadata.put("subject_name", subject);
            metadata.put("university_name", student.getUniversity() != null ? student.getUniversity().getName() : "the University");
            metadata.put("parent_name", student.getParentName() != null ? student.getParentName() : "Parent");
            metadata.put("student_id", student.getId());

            String toNumber = parentPhone.trim();
            // Basic normalization: if it's 10 digits, assume +91 (India) as per user's locale
            if (!toNumber.startsWith("+")) {
                String cleaned = toNumber.replaceAll("[^0-9]", "");
                if (cleaned.length() == 10) {
                    toNumber = "+91" + cleaned;
                } else if (!cleaned.isEmpty()) {
                    toNumber = "+" + cleaned;
                }
            }

            Map<String, Object> body = new HashMap<>();
            body.put("agent_id", agentId);
            body.put("to_number", toNumber);
            
            if (fromNumber == null || fromNumber.trim().isEmpty()) {
                System.err.println("[RETELL ERROR] 'from_number' is NOT CONFIGURED. V2 API requires a valid outbound number.");
                System.err.println("[RETELL ERROR] Please add 'retell.from.number=+1234567890' to application.properties.");
                log.error("[PARENT CALL] Aborting call: retell.from.number is missing in application.properties.");
                return;
            }
            body.put("from_number", fromNumber.trim());
            
            // Phase V5: Name Synthesis via Dynamic Variables
            Map<String, String> dynamicVariables = new HashMap<>();
            dynamicVariables.put("student_name", student.getFullName());
            dynamicVariables.put("subject_name", subject);
            dynamicVariables.put("parent_name", student.getParentName() != null ? student.getParentName() : "Parent");
            dynamicVariables.put("university_name", student.getUniversity() != null ? student.getUniversity().getName() : "the University");
            dynamicVariables.put("date", java.time.LocalDate.now().toString());
            
            // Try both keys for maximum compatibility with different Retell V2 builds
            body.put("dynamic_variables", dynamicVariables);
            body.put("retell_llm_dynamic_variables", dynamicVariables);
            
            body.put("metadata", metadata);

            System.out.println("[RETELL DEBUG] Request URL: " + url);
            System.out.println("[RETELL DEBUG] Body: " + body);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("[RETELL SUCCESS] Response: " + response.getBody());
                log.info("[PARENT CALL] Successfully initiated call for student {}. ID: {}", student.getFullName(), response.getBody());
            } else {
                System.out.println("[RETELL FAILURE] Status: " + response.getStatusCode() + " | Body: " + response.getBody());
                log.error("[PARENT CALL] Failed to initiate call. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            System.err.println("[RETELL EXCEPTION] Error: " + e.getMessage());
            log.error("[PARENT CALL] Error during Retell API call: {}", e.getMessage());
        }
    }
}
