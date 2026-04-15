package com.unios.controller;

import com.unios.service.agents.admissions.EligibilityAgent;
import com.unios.service.agents.admissions.EnrollmentAgent;
import com.unios.service.agents.admissions.ExamSchedulerAgent;
import com.unios.service.agents.admissions.RankingAgent;
import com.unios.service.agents.academics.SemesterExamAgent;
import com.unios.service.agents.academics.CourseExamSchedulerAgent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/automation-trigger")
public class AutomationTriggerController {

    private final EligibilityAgent eligibilityAgent;
    private final EnrollmentAgent enrollmentAgent;
    private final ExamSchedulerAgent examSchedulerAgent;
    private final RankingAgent rankingAgent;
    private final SemesterExamAgent semesterExamAgent;
    private final CourseExamSchedulerAgent courseExamSchedulerAgent;

    public AutomationTriggerController(EligibilityAgent eligibilityAgent,
                                        EnrollmentAgent enrollmentAgent,
                                        ExamSchedulerAgent examSchedulerAgent,
                                        RankingAgent rankingAgent,
                                        SemesterExamAgent semesterExamAgent,
                                        CourseExamSchedulerAgent courseExamSchedulerAgent) {
        this.eligibilityAgent = eligibilityAgent;
        this.enrollmentAgent = enrollmentAgent;
        this.examSchedulerAgent = examSchedulerAgent;
        this.rankingAgent = rankingAgent;
        this.semesterExamAgent = semesterExamAgent;
        this.courseExamSchedulerAgent = courseExamSchedulerAgent;
    }

    @PostMapping("/run-academics-cycle")
    public ResponseEntity<String> runAcademicsCycle(@RequestParam Long batchId) {
        semesterExamAgent.processSemester(batchId);
        return ResponseEntity.ok("Academics cycle triggered for batch " + batchId);
    }
}
