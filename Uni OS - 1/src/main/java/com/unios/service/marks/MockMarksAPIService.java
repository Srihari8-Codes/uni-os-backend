package com.unios.service.marks;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Mock API Service simulating a real tertiary exam evaluation system.
 * Designed to be replaced with a real REST/SOAP client later.
 */
@Service
public class MockMarksAPIService {

    /**
     * Simulates fetching a score from the college's external grading system.
     * Uses a cryptographic hash to ensure the structural score is 100% deterministic
     * and consistent given the exact same student and exam, acting like a real API.
     * 
     * @param studentId The ID of the student/applicant
     * @param examId The ID of the exam/batch
     * @return A deterministic score between 35.0 and 95.0
     */
    public double getMarksFromCollegeSystem(Long studentId, Long examId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = "STUDENT:" + studentId + "_EXAM:" + examId + "_SECRET_SALT_2024";
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert first 4 bytes of hash to an integer
            int hashInt = ((hash[0] & 0xFF) << 24) |
                          ((hash[1] & 0xFF) << 16) |
                          ((hash[2] & 0xFF) << 8)  |
                           (hash[3] & 0xFF);
                           
            // Map integer accurately to a score between 35.0 and 95.0
            double normalized = (Math.abs(hashInt) % 600) / 10.0; // 0.0 to 59.9
            return 35.0 + normalized;
            
        } catch (NoSuchAlgorithmException e) {
            // Fallback if SHA-256 is somehow missing
            long pseudoRandom = (studentId * 31 + examId * 17) % 60;
            return 40.0 + pseudoRandom;
        }
    }
}
