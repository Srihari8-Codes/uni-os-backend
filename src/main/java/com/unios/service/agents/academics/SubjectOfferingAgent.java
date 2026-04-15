package com.unios.service.agents.academics;

import com.unios.optimizer.domain.Room;
import com.unios.optimizer.domain.SubjectClass;
import com.unios.optimizer.service.AllocationSolverService;
import com.unios.model.Batch;
import com.unios.model.Faculty;
import com.unios.model.SubjectOffering;
import com.unios.repository.BatchRepository;
import com.unios.repository.FacultyRepository;
import com.unios.repository.SubjectOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubjectOfferingAgent {

    private final SubjectOfferingRepository subjectOfferingRepository;
    private final BatchRepository batchRepository;
    private final FacultyRepository facultyRepository;
    private final AllocationSolverService allocationSolverService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public SubjectOfferingAgent(SubjectOfferingRepository subjectOfferingRepository,
            BatchRepository batchRepository,
            FacultyRepository facultyRepository,
            AllocationSolverService allocationSolverService,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.batchRepository = batchRepository;
        this.facultyRepository = facultyRepository;
        this.allocationSolverService = allocationSolverService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SubjectOffering createOffering(String subjectName, String slot, Integer capacity, Integer credits,
            String prerequisite, Long facultyId, Long batchId, String userEmail) {

        // Validate Batch
        Batch batch;
        if (batchId != null) {
            batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new RuntimeException("Batch not found"));
        } else {
            // Find active batch or just the first batch available
            batch = batchRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("No batch available in system."));
        }

        // Validate Faculty
        Faculty faculty;
        if (facultyId != null) {
            faculty = facultyRepository.findById(facultyId)
                    .orElseThrow(() -> new RuntimeException("Faculty not found"));
        } else if (userEmail != null) {
            faculty = facultyRepository.findByUserEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Faculty profile not found for user: " + userEmail));
        } else {
            throw new RuntimeException("Faculty ID and User Email cannot both be null");
        }

        Optional<SubjectOffering> existing = subjectOfferingRepository.findByBatchIdAndSlot(batch.getId(), slot);
        // User wants "Subject slots" allocated.
        // So maybe `createOffering` should NOT take a slot?
        // Converting `createOffering` to not require slot might break other things.
        // I will keep `createOffering` as is for "legacy/manual" but add
        // `optimizeSchedule` to re-assign.

        if (existing.isPresent()) {
            throw new RuntimeException("Slot " + slot + " is already occupied for this batch.");
        }

        SubjectOffering offering = new SubjectOffering();
        offering.setSubjectName(subjectName);
        offering.setSlot(slot);
        offering.setCapacity(capacity);
        offering.setCredits(credits);
        offering.setPrerequisite(prerequisite);
        offering.setFaculty(faculty);
        offering.setBatch(batch);
        offering.setActive(false); // Default inactive until approved
        offering.setStatus("PENDING_APPROVAL");

        SubjectOffering saved = subjectOfferingRepository.save(offering);

        eventPublisher.publishEvent(new com.unios.domain.events.SubjectOfferedEvent(this, saved.getId(), batchId));

        return saved;
    }

    @Transactional
    public void optimizeSchedule(Long batchId) {
        List<SubjectOffering> offerings = subjectOfferingRepository.findByBatchId(batchId);

        // 1. Prepare Data
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("R1", "Hall A", 60));
        rooms.add(new Room("R2", "Hall B", 60));
        rooms.add(new Room("R3", "Hall C", 60));
        rooms.add(new Room("R4", "Hall D", 60));
        rooms.add(new Room("R5", "Hall E", 60));

        List<String> slots = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P",
                "Q", "R", "S", "T");

        List<SubjectClass> planningClasses = offerings.stream()
                .map(o -> new SubjectClass(o.getId(), o.getSubjectName(), o.getCapacity())) // Assuming Capacity =
                                                                                            // Enrolled for now? Or 0?
                // Using capacity as proxy for enrolled count for optimization (worst case)
                .collect(Collectors.toList());

        // 2. Solve
        List<SubjectClass> optimized = allocationSolverService.solveSubjectAllocation(rooms, planningClasses, slots);

        // 3. Persist
        for (SubjectClass cls : optimized) {
            SubjectOffering offering = subjectOfferingRepository.findById(cls.getSubjectOfferingId()).orElseThrow();
            if (cls.getRoom() != null) {
                offering.setRoom(cls.getRoom().getId());
            }
            if (cls.getSlot() != null) {
                offering.setSlot(cls.getSlot());
            }
            subjectOfferingRepository.save(offering);
        }
    }

    @Transactional
    public void approveOffering(Long offeringId) {
        SubjectOffering offering = subjectOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new RuntimeException("Offering not found"));
        offering.setActive(true);
        offering.setStatus("APPROVED");
        subjectOfferingRepository.save(offering);

        eventPublisher.publishEvent(
                new com.unios.domain.events.SubjectOfferedEvent(this, offeringId, offering.getBatch().getId()));
    }

    @Transactional
    public void rejectOffering(Long offeringId) {
        SubjectOffering offering = subjectOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new RuntimeException("Offering not found"));
        offering.setActive(false);
        offering.setStatus("REJECTED");
        subjectOfferingRepository.save(offering);
    }

}
