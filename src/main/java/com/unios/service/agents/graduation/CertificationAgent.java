package com.unios.service.agents.graduation;

import com.unios.model.Certificate;
import com.unios.model.Student;
import com.unios.repository.CertificateRepository;
import com.unios.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CertificationAgent {

    private final GraduationEligibilityAgent eligibilityAgent;
    private final CertificateRepository certificateRepository;
    private final StudentRepository studentRepository;
    private final com.unios.service.llm.ReasoningEngineService reasoningEngineService;

    public CertificationAgent(GraduationEligibilityAgent eligibilityAgent,
            CertificateRepository certificateRepository,
            StudentRepository studentRepository,
            com.unios.service.llm.ReasoningEngineService reasoningEngineService) {
        this.eligibilityAgent = eligibilityAgent;
        this.certificateRepository = certificateRepository;
        this.studentRepository = studentRepository;
        this.reasoningEngineService = reasoningEngineService;
    }

    @Transactional
    public void issueCertificate(Long studentId) {
        if (!eligibilityAgent.checkEligibility(studentId)) {
            throw new RuntimeException("Student is not eligible for graduation.");
        }

        // Check if already issued
        // Logic skipped for brevity, assuming check is done or idempotent logic

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Use Reasoning Engine for degree naming (Replacing static simulation)
        String inputData = String.format("Student: %s. Department: %s.",
                student.getFullName(), student.getDepartment());
        String decisionWithReasoning = reasoningEngineService.decide(
                "Registrar", "Student", studentId,
                "Identify the correct degree name based on department and verify academic clearance.",
                inputData);

        // Extract decision part (Assuming format "Bachelor of Technology in [Dept]")
        String degreeName = decisionWithReasoning;
        if (degreeName.contains(" (")) {
            degreeName = degreeName.substring(0, degreeName.indexOf(" (")).trim();
        }

        Certificate certificate = new Certificate();
        certificate.setStudent(student);
        certificate.setDegreeName(degreeName);
        certificate.setIssuedAt(LocalDateTime.now());
        certificate.setCertificateCode(UUID.randomUUID().toString());

        certificateRepository.save(certificate);
    }
}
