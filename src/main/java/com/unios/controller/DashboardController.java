package com.unios.controller;

import com.unios.dto.DashboardMetricsDTO;
import com.unios.model.User;
import com.unios.repository.ApplicationRepository;
import com.unios.repository.FacultyRepository;
import com.unios.repository.StudentRepository;
import com.unios.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public DashboardController(StudentRepository studentRepository, 
                               FacultyRepository facultyRepository, 
                               ApplicationRepository applicationRepository,
                               UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsDTO> getMetrics(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getUniversity() == null) {
            return ResponseEntity.ok(new DashboardMetricsDTO(0, 0, 0, "No University", null));
        }

        Long universityId = user.getUniversity().getId();
        
        long studentCount = studentRepository.countByUniversityId(universityId);
        long facultyCount = facultyRepository.countByUniversityId(universityId);
        long applicationCount = applicationRepository.countByUniversityId(universityId);

        return ResponseEntity.ok(new DashboardMetricsDTO(
                studentCount,
                facultyCount,
                applicationCount,
                user.getUniversity().getName(),
                user.getUniversity().getLogoUrl()
        ));
    }
}
