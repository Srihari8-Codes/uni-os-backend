package com.unios.controller;

import com.unios.model.Application;
import com.unios.model.User;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applicant")
@RequiredArgsConstructor
public class ApplicantController {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    /**
     * Returns all applications submitted by the logged-in applicant user.
     */
    @GetMapping("/applications")
    public ResponseEntity<List<Map<String, Object>>> getMyApplications() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Application> apps = applicationRepository.findByApplicantUserId(user.getId());

        List<Map<String, Object>> result = apps.stream().map(app -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", app.getId());
            m.put("fullName", app.getFullName());
            m.put("email", app.getEmail());
            m.put("status", app.getStatus());
            m.put("schoolMarks", app.getSchoolMarks());
            m.put("createdAt", app.getCreatedAt());
            m.put("batchId", app.getBatch() != null ? app.getBatch().getId() : null);
            m.put("batchCode", app.getBatch() != null ? app.getBatch().getBatchCode() : null);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
