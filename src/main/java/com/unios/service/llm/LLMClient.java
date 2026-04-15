package com.unios.service.llm;

public interface LLMClient {
    /**
     * Sends a prompt to the LLM and returns the response text.
     * 
     * @param systemPrompt Context or role definition for the LLM.
     * @param userPrompt   The specific task or input data.
     * @return The LLM's response.
     */
    String generateResponse(String systemPrompt, String userPrompt);

    /**
     * Sends a prompt and an image (base64) to the LLM and returns the response.
     * 
     * @param systemPrompt Character definition.
     * @param userPrompt   The specific task instruction.
     * @param base64Image  The image data (excluding data:image header).
     * @return The LLM's response.
     */
    String generateResponseWithImage(String systemPrompt, String userPrompt, String base64Image);
}
