package com.unios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unios.service.agents.admissions.ChatAdmissionAgent.LlmExtractionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestJsonController {
    @GetMapping("/test-json")
    public String testJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = "{\n  \"reply\": \"Hello from test\",\n  \"extractedFields\": {}\n}";
            LlmExtractionResult res = mapper.readValue(json, LlmExtractionResult.class);
            return "Reply=" + res.getReply() + " mapping=" + (res.getExtractedFields() != null);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
