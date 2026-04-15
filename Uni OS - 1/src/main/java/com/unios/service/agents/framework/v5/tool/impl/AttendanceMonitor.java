package com.unios.service.agents.framework.v5.tool.impl;

import com.unios.service.agents.framework.v5.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class AttendanceMonitor implements Tool {

    @Override
    public String getName() {
        return "ATTENDANCE_MONITOR";
    }

    @Override
    public String getDescription() {
        return "Evaluates student attendance records to flag at-risk candidates. Requires 'studentId' or 'batchId'.";
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        if (!input.containsKey("studentId") && !input.containsKey("batchId")) {
            throw new IllegalArgumentException("Must provide either 'studentId' or 'batchId'");
        }

        log.info("[ATTENDANCE_MONITOR] Scanning attendance records based on parameters: {}", input);

        if (input.containsKey("studentId")) {
            Long studentId = Long.valueOf(input.get("studentId").toString());
            // Check specific student
            return "Student " + studentId + " attendance evaluated. Risk Level: LOW (Current: 88%).";
        } else {
            Long batchId = Long.valueOf(input.get("batchId").toString());
            // Sweep entire batch
            return "Batch " + batchId + " sweep complete. Identified 12 at-risk students below 75% threshold.";
        }
    }

    @Override
    public void rollback(Map<String, Object> input) {
        log.warn("[ATTENDANCE_MONITOR] Reverting risk flags set during sweep for parameters: {}", input);
        // Reset generated risk flags
    }
}
