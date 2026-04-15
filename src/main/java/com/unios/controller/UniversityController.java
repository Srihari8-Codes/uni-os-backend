package com.unios.controller;

import com.unios.dto.UniversityRegistrationRequest;
import com.unios.model.JoinRequest;
import com.unios.model.University;
import com.unios.service.UniversityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/universities")
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUniversity(@RequestBody UniversityRegistrationRequest request) {
        try {
            University university = universityService.registerUniversity(request);
            return ResponseEntity.ok(university);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred during registration");
        }
    }

    @GetMapping
    public ResponseEntity<List<University>> getAllUniversities() {
        return ResponseEntity.ok(universityService.getAllUniversities());
    }

    @PostMapping("/{universityId}/join")
    public ResponseEntity<?> requestToJoin(@PathVariable Long universityId, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            JoinRequest request = universityService.requestToJoin(universityId, principal.getName());
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/join-requests")
    public ResponseEntity<?> getJoinRequests(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            List<JoinRequest> requests = universityService.getPendingRequests(principal.getName());
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<?> approveRequest(@PathVariable Long requestId, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            JoinRequest request = universityService.approveRequest(requestId, principal.getName());
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/faculty")
    public ResponseEntity<?> createFacultyAccount(@RequestBody com.unios.dto.FacultyCreationRequest request, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            com.unios.model.User faculty = universityService.createFacultyAccount(principal.getName(), request.getEmail(), request.getPassword(), request.getName());
            return ResponseEntity.ok(java.util.Map.of("message", "Faculty created successfully", "email", faculty.getEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/counselor")
    public ResponseEntity<?> createCounselorAccount(@RequestBody com.unios.dto.FacultyCreationRequest request, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            com.unios.model.User counselor = universityService.createCounselorAccount(principal.getName(), request.getEmail(), request.getPassword(), request.getName());
            return ResponseEntity.ok(java.util.Map.of("message", "Counselor created successfully", "email", counselor.getEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PutMapping("/branding")
    public ResponseEntity<?> updateBranding(@RequestBody java.util.Map<String, String> payload, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        try {
            String newName = payload.get("name");
            String logoUrl = payload.get("logoUrl");
            University university = universityService.updateBranding(principal.getName(), newName, logoUrl);
            return ResponseEntity.ok(university);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
