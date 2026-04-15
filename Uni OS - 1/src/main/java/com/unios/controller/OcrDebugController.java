package com.unios.controller;

import com.unios.service.agents.framework.v5.tool.impl.DocumentAnalyzerTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/ocr")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OcrDebugController {

    private final DocumentAnalyzerTool documentAnalyzerTool;

    @PostMapping("/test")
    public Map<String, Object> testOcr(@RequestParam("file") MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String fileName = file.getOriginalFilename();
        
        return documentAnalyzerTool.scanDocument(bytes, fileName);
    }
}
