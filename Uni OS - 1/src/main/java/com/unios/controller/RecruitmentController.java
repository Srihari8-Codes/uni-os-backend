package com.unios.controller;

import com.unios.model.Candidate;
import com.unios.repository.CandidateRepository;
import com.unios.service.agents.hr.RecruitmentAgent;
import com.unios.service.documents.FileStorageService;
import com.unios.service.documents.PdfExtractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/recruitment")
@CrossOrigin(origins = "*") // Allows web requests
public class RecruitmentController {

    private final CandidateRepository candidateRepository;
    private final FileStorageService fileStorageService;
    private final PdfExtractionService pdfExtractionService;
    private final RecruitmentAgent recruitmentAgent;

    public RecruitmentController(CandidateRepository candidateRepository,
                                 FileStorageService fileStorageService,
                                 PdfExtractionService pdfExtractionService,
                                 RecruitmentAgent recruitmentAgent) {
        this.candidateRepository = candidateRepository;
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.recruitmentAgent = recruitmentAgent;
    }

    /**
     * POST /api/recruitment/apply
     * Expects multipart form data with candidate details and a resume PDF file.
     */
    @PostMapping("/apply")
    public ResponseEntity<?> applyForFacultyPosition(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("department") String department,
            @RequestParam("resume") MultipartFile resumeFile) {

        try {
            // 1. Create and save candidate entry
            Candidate candidate = new Candidate();
            candidate.setFullName(fullName);
            candidate.setEmail(email);
            candidate.setDepartment(department);
            candidate.setStatus("APPLIED");
            candidate = candidateRepository.save(candidate);

            // 2. Store the PDF to disk
            String storedFilePath = fileStorageService.storeFile(resumeFile, candidate.getId());
            candidate.setResumeUrl(storedFilePath);

            // 3. Extract text synchronously for immediate downstream reasoning
            String extractedText = pdfExtractionService.extractText(resumeFile);
            candidate.setExtractedText(extractedText);
            
            candidateRepository.save(candidate);

            final Long finalCandidateId = candidate.getId();
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    recruitmentAgent.processCandidate(finalCandidateId);
                } catch (Exception e) {
                    System.err.println("[RECRUITMENT API] Failed to process candidate async: " + e.getMessage());
                }
            });

            return ResponseEntity.ok(Map.of(
                    "message", "Application submitted successfully. Our AI will review your resume.",
                    "candidateId", candidate.getId(),
                    "extractedDataLength", extractedText.length()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Error processing application: " + e.getMessage()));
        }
    }
}
