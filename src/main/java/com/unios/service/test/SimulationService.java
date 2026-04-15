package com.unios.service.test;

import com.unios.model.Application;
import com.unios.model.Batch;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class SimulationService {

    private final ApplicationRepository applicationRepository;
    private final BatchRepository batchRepository;
    private final Random random = new Random();

    public SimulationService(ApplicationRepository applicationRepository, BatchRepository batchRepository) {
        this.applicationRepository = applicationRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public void generateApplicants(Long batchId, int count) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        int eligibleCount = (int) (count * 0.6);
        int ineligibleCount = count - eligibleCount;

        List<Application> apps = new ArrayList<>();

        // Generate Eligible (60%)
        for (int i = 0; i < eligibleCount; i++) {
            apps.add(createDummyApp(batch, true, i));
        }

        // Generate Ineligible (40%)
        for (int i = 0; i < ineligibleCount; i++) {
            apps.add(createDummyApp(batch, false, eligibleCount + i));
        }

        applicationRepository.saveAll(apps);
    }

    private Application createDummyApp(Batch batch, boolean eligible, int index) {
        Application app = new Application();
        app.setFullName("Simulated Student " + index);
        app.setEmail("student" + index + "@unios.demo");
        app.setBatch(batch);
        app.setStatus("SUBMITTED");
        app.setDocumentsVerified(true);
        app.setAcademicScore(eligible ? 70.0 + random.nextDouble() * 25.0 : 40.0 + random.nextDouble() * 15.0);
        app.setSchoolMarks(app.getAcademicScore());
        app.setApplicationData("{}");
        return app;
    }

    @Transactional
    public void simulateExamScores(Long batchId) {
        List<Application> apps = applicationRepository.findByBatchIdAndStatus(batchId, "EXAM_SCHEDULED");
        
        int total = apps.size();
        if (total == 0) return;

        int passCount = (int) (total * 0.75);
        int waitlistCount = (int) (total * 0.15);
        int failCount = total - passCount - waitlistCount;

        for (int i = 0; i < total; i++) {
            Application app = apps.get(i);
            if (i < passCount) {
                app.setStatus("EXAM_PASSED");
                app.setNeetScore(70.0 + random.nextDouble() * 25.0);
            } else if (i < passCount + waitlistCount) {
                app.setStatus("WAITLISTED");
                app.setNeetScore(50.0 + random.nextDouble() * 10.0);
            } else {
                app.setStatus("EXAM_FAILED");
                app.setNeetScore(30.0 + random.nextDouble() * 15.0);
            }
        }
        applicationRepository.saveAll(apps);
    }
}
