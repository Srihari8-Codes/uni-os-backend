package com.unios.service.llm;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service("ollamaClient")
@Primary
public class OllamaClient implements LLMClient {

    private final RestTemplate restTemplate;
    
    @Value("${ollama.api.url:http://localhost:11434/api/generate}")
    private String apiUrl;

    @Value("${ollama.model:llama3.2}")
    private String modelName;

    @Value("${ollama.debug.enabled:false}")
    private boolean debugEnabled;

    public OllamaClient(RestTemplate restTemplate, @Value("${ollama.api.url:http://localhost:11434/api/generate}") String apiUrl) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
    }

    @Override
    public String generateResponse(String systemPrompt, String userPrompt) {
        logToOrch("[OLLAMA] Calling Model: " + modelName);
        Map<String, Object> request = new HashMap<>();
        request.put("model", modelName);
        request.put("system", systemPrompt);
        request.put("prompt", userPrompt);
        request.put("stream", false);
        request.put("format", "json");

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            
            org.springframework.http.HttpEntity<Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(request, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(apiUrl, requestEntity, Map.class);
            if (response != null && response.containsKey("response")) {
                String resText = (String) response.get("response");
                logToOrch("[OLLAMA] Success. Response received.");
                
                if (debugEnabled) {
                    try {
                        java.nio.file.Path logDir = java.nio.file.Paths.get("logs");
                        java.nio.file.Files.createDirectories(logDir);
                        java.nio.file.Files.writeString(
                            logDir.resolve("ollama-debug.log"),
                            "==== RAW RESPONSE ====\n" + resText
                        );
                    } catch (Exception e) {
                        System.err.println("FAILED TO WRITE DEBUG LOG: " + e.getMessage());
                    }
                }
                
                return resText;
            }
            logToOrch("[OLLAMA] ERROR: No response field in result.");
            return "{\"error\": \"No response field\"}";
        } catch (Exception e) {
            logToOrch("[OLLAMA] ERROR: " + e.getMessage());
            if (debugEnabled) {
                try {
                    java.nio.file.Path logDir = java.nio.file.Paths.get("logs");
                    java.nio.file.Files.createDirectories(logDir);
                    java.nio.file.Files.writeString(
                        logDir.resolve("ollama-debug.log"),
                        "==== ERROR RESPONSE ====\nMESSAGE: " + e.getMessage()
                    );
                } catch (Exception ex) {
                     System.err.println("FAILED TO WRITE ERROR LOG: " + ex.getMessage());
                }
            }
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private void logToOrch(String msg) {
        com.unios.service.orchestrator.InstitutionalOrchestrator.ACTIVITY_LOG.add("[LLM] " + java.time.LocalDateTime.now() + ": " + msg);
    }

    @Override
    public String generateResponseWithImage(String systemPrompt, String userPrompt, String base64Image) {
        return null; // Local Ollama 3.2 3B doesn't support vision out-of-the-box in this setup.
    }
}
