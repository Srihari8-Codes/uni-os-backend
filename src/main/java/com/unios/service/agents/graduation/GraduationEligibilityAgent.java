package com.unios.service.agents.graduation;

import com.unios.model.StudentCredit;
import com.unios.repository.StudentCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationEligibilityAgent {

    private final StudentCreditRepository studentCreditRepository;
    private final com.unios.repository.GraduationCandidateRepository graduationCandidateRepository;
    private final com.unios.repository.StudentRepository studentRepository;

    public GraduationEligibilityAgent(
            StudentCreditRepository studentCreditRepository,
            com.unios.repository.GraduationCandidateRepository graduationCandidateRepository,
            com.unios.repository.StudentRepository studentRepository) {
        this.studentCreditRepository = studentCreditRepository;
        this.graduationCandidateRepository = graduationCandidateRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public boolean checkEligibility(Long studentId) {
        StudentCredit credit = studentCreditRepository.findByStudentId(studentId).orElse(null);
        if (credit == null)
            return false;

        // Requirement: 160 credits
        return credit.getEarnedCredits() >= 160;
    }

    @Transactional
    public void markAsCandidate(Long studentId) {
        StudentCredit credit = studentCreditRepository.findByStudentId(studentId)
                .orElseThrow(() -> new RuntimeException("Credit record not found"));

        if (credit.getEarnedCredits() < 160) {
            return;
        }

        com.unios.model.GraduationCandidate candidate = graduationCandidateRepository.findByStudentId(studentId)
                .orElseGet(() -> {
                    com.unios.model.GraduationCandidate newCandidate = new com.unios.model.GraduationCandidate();
                    newCandidate.setStudent(studentRepository.findById(studentId).get());
                    newCandidate.setIdentifiedAt(java.time.LocalDateTime.now());
                    newCandidate.setStatus("IDENTIFIED");
                    return newCandidate;
                });

        candidate.setCreditsAchieved(credit.getEarnedCredits());
        graduationCandidateRepository.save(candidate);

        System.out.println("[GRADUATION] Student " + studentId + " marked as graduation candidate with "
                + credit.getEarnedCredits() + " credits.");
    }
}
