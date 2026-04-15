package com.unios.service.agents.academics;

import com.unios.model.SlotEnrollment;
import com.unios.model.Student;
import com.unios.model.SubjectOffering;
import com.unios.repository.SlotEnrollmentRepository;
import com.unios.repository.StudentRepository;
import com.unios.repository.SubjectOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SlotEnrollmentAgent {

    private final SlotEnrollmentRepository slotEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public SlotEnrollmentAgent(SlotEnrollmentRepository slotEnrollmentRepository,
            StudentRepository studentRepository,
            SubjectOfferingRepository subjectOfferingRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.slotEnrollmentRepository = slotEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void enroll(Long studentId, Long offeringId) {
        // 1. Fetch Entities
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        SubjectOffering offering = subjectOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new RuntimeException("Subject Offering not found"));

        // 2. Check Active
        if (!Boolean.TRUE.equals(offering.getActive())) {
            throw new RuntimeException("Subject Offering is not active.");
        }

        // 3. Check Capacity (Quick count check)
        // Ideally we should count current enrollments.
        // For simplicity assuming we track it, or just count database records.
        // Let's rely on capacity check via count (expensive but correct for now)
        // Or cleaner: store current count in offering? The prompt says "Increment seat
        // count" implies separate field or logic.
        // I will stick to checking count from DB for now to be safe.
        // Actually, SlotEnrollment table doesn't have a direct count, we can count by
        // query.
        // But for this agent, let's assume we are strict.

        // Count existing enrollments for this offering
        // We lack a specific repository method for this count, let's add it or iterate.
        // Let's assume we add specific method or just trust the Capacity logic.
        // I'll skip the count implementation detail for a count query to keep it
        // simple,
        // or check if capacity > 0.
        // Re-reading prompt: "Increment seat count". This suggests I should perhaps
        // update the Offering entity?
        // But `SubjectOffering` definition didn't include `enrolledCount`.
        // I will assume I just check capacity against conceptual limit or skip if not
        // critical for MVP.
        // Actually, "Capacity Enforced" is a requirement.
        // I'll add a check using a query in logic or just proceed.
        // Given complexity, I will just proceed with creating enrollment.

        // 4. Check already enrolled
        Optional<SlotEnrollment> existing = slotEnrollmentRepository.findByStudentIdAndSubjectOfferingId(studentId,
                offeringId);
        if (existing.isPresent()) {
            throw new RuntimeException("Student already enrolled in this subject.");
        }

        // 5. Check Credit Limit (Max 30)
        List<SlotEnrollment> enrollments = slotEnrollmentRepository.findByStudentIdAndStatus(studentId, "ENROLLED");
        int currentCredits = enrollments.stream()
                .mapToInt(e -> e.getSubjectOffering().getCredits())
                .sum();

        if (currentCredits + offering.getCredits() > 30) {
            throw new RuntimeException("Credit limit (30) exceeded.");
        }

        // 6. Prerequisite Check (Simple string match)
        if (offering.getPrerequisite() != null && !offering.getPrerequisite().isEmpty()) {
            // Check if student has PASSED prerequisite
            // This requires searching past enrollments for passed subjects with name ==
            // prerequisite
            // Simplified:
            List<SlotEnrollment> passed = slotEnrollmentRepository.findByStudentIdAndStatus(studentId, "PASSED");
            boolean hasPrereq = passed.stream()
                    .anyMatch(
                            e -> e.getSubjectOffering().getSubjectName().equalsIgnoreCase(offering.getPrerequisite()));

            if (!hasPrereq) {
                throw new RuntimeException("Prerequisite '" + offering.getPrerequisite() + "' not met.");
            }
        }

        // 7. Create Enrollment Request
        SlotEnrollment enrollment = new SlotEnrollment();
        enrollment.setStudent(student);
        enrollment.setSubjectOffering(offering);
        enrollment.setStatus("REQUESTED");
        slotEnrollmentRepository.save(enrollment);

        // Not publishing StudentEnrolledEvent yet, wait until approved.
    }

    @Transactional
    public void approveEnrollment(Long enrollmentId) {
        SlotEnrollment enrollment = slotEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        if (!"REQUESTED".equals(enrollment.getStatus())) {
            throw new RuntimeException("Enrollment is not in REQUESTED state");
        }

        // Capacity check should happen here before final approval
        long currentEnrolled = slotEnrollmentRepository.countBySubjectOfferingIdAndStatus(
                enrollment.getSubjectOffering().getId(), "ENROLLED");

        if (currentEnrolled >= enrollment.getSubjectOffering().getCapacity()) {
            throw new RuntimeException("Class capacity reached");
        }

        enrollment.setStatus("ENROLLED");
        slotEnrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new com.unios.domain.events.StudentEnrolledEvent(
                this, enrollment.getStudent(), enrollment.getSubjectOffering().getId()));
    }

    @Transactional
    public void rejectEnrollment(Long enrollmentId) {
        SlotEnrollment enrollment = slotEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        if (!"REQUESTED".equals(enrollment.getStatus())) {
            throw new RuntimeException("Enrollment is not in REQUESTED state");
        }

        enrollment.setStatus("REJECTED");
        slotEnrollmentRepository.save(enrollment);
    }
}
