package com.unios.service.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FailureInjectionService {

    private boolean llmDown = false;
    private boolean smtpDown = false;
    private long dbLatencyMs = 0;

    public void setLlmDown(boolean down) {
        this.llmDown = down;
        log.info("[QA] LLM Failure Simulation: {}", down ? "ACTIVE" : "INACTIVE");
    }

    public void setSmtpDown(boolean down) {
        this.smtpDown = down;
        log.info("[QA] SMTP Failure Simulation: {}", down ? "ACTIVE" : "INACTIVE");
    }

    public void setDbLatency(long ms) {
        this.dbLatencyMs = ms;
        log.info("[QA] DB Latency Simulation: {}ms", ms);
    }

    public void checkLlm() {
        if (llmDown) {
            throw new RuntimeException("Simulated LLM Downtime Error (QA)");
        }
    }

    public void checkSmtp() {
        if (smtpDown) {
            throw new RuntimeException("Simulated SMTP Server Error (QA)");
        }
    }

    public void applyDbLatency() {
        if (dbLatencyMs > 0) {
            try {
                Thread.sleep(dbLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isLlmDown() { return llmDown; }
    public boolean isSmtpDown() { return smtpDown; }
}
