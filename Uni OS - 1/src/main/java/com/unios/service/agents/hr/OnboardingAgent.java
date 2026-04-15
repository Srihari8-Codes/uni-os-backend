package com.unios.service.agents.hr;

import com.unios.model.Candidate;
import com.unios.model.Staff;
import com.unios.repository.CandidateRepository;
import com.unios.repository.StaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.unios.repository.UserRepository;
import com.unios.repository.FacultyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class OnboardingAgent {

    private final CandidateRepository candidateRepository;
    private final StaffRepository staffRepository;
    private final com.unios.repository.UserRepository userRepository;
    private final com.unios.repository.FacultyRepository facultyRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public OnboardingAgent(CandidateRepository candidateRepository,
            StaffRepository staffRepository,
            com.unios.repository.UserRepository userRepository,
            com.unios.repository.FacultyRepository facultyRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.candidateRepository = candidateRepository;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void onboard(Long candidateId, String role, Double salary) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        if (!"SELECTED".equals(candidate.getStatus())) {
            throw new RuntimeException("Candidate is not in SELECTED status.");
        }

        // 1. Create or Find User
        com.unios.model.User user = userRepository.findByEmail(candidate.getEmail()).orElse(null);
        if (user == null) {
            String rawPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
            user = new com.unios.model.User();
            user.setEmail(candidate.getEmail());
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(com.unios.model.Role.FACULTY);
            userRepository.save(user);
        } else {
            // Update role if user already exists
            user.setRole(com.unios.model.Role.FACULTY);
            userRepository.save(user);
        }

        // 2. Create Staff/Faculty record
        Staff staff = new Staff();
        staff.setFullName(candidate.getFullName());
        staff.setEmail(candidate.getEmail());
        staff.setDepartment(candidate.getDepartment());
        staff.setRole(role != null ? role : "FACULTY");
        staff.setActive(true);
        staff.setSalary(salary); // Adding salary if field exists, otherwise just
        // logging
        staffRepository.save(staff);

        // Also create Faculty record if it's a faculty role
        if ("FACULTY".equalsIgnoreCase(role) || role == null) {
            com.unios.model.Faculty faculty = new com.unios.model.Faculty();
            faculty.setUser(user);
            faculty.setFullName(candidate.getFullName());
            faculty.setDepartment(candidate.getDepartment());
            facultyRepository.save(faculty);
        }

        candidate.setStatus("ONBOARDED");
        candidateRepository.save(candidate);

        System.out.println("[ONBOARDING] Candidate " + candidate.getFullName() + " onboarded as " + role
                + " with salary " + salary);
    }
}
