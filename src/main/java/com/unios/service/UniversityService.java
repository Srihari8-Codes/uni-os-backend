package com.unios.service;

import com.unios.dto.UniversityRegistrationRequest;
import com.unios.model.JoinRequest;
import com.unios.model.Role;
import com.unios.model.University;
import com.unios.model.User;
import com.unios.repository.JoinRequestRepository;
import com.unios.repository.UniversityRepository;
import com.unios.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UniversityService {

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JoinRequestRepository joinRequestRepository;
    private final com.unios.repository.FacultyRepository facultyRepository;

    public UniversityService(UniversityRepository universityRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             JoinRequestRepository joinRequestRepository,
                             com.unios.repository.FacultyRepository facultyRepository) {
        this.universityRepository = universityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.joinRequestRepository = joinRequestRepository;
        this.facultyRepository = facultyRepository;
    }

    @Transactional
    public University registerUniversity(UniversityRegistrationRequest request) {
        // 1. Check if admin email already exists
        if (userRepository.findByEmail(request.getAdminEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }
        
        // 2. Check if university name or subdomain exists
        if (universityRepository.findByName(request.getUniversityName()).isPresent()) {
            throw new IllegalArgumentException("University name already taken");
        }
        if (request.getSubdomain() != null && universityRepository.findBySubdomain(request.getSubdomain()).isPresent()) {
             throw new IllegalArgumentException("Subdomain already taken");
        }

        // 3. Create University
        University university = new University();
        university.setName(request.getUniversityName());
        university.setSubdomain(request.getSubdomain());
        university.setDescription(request.getDescription());
        university.setAdminEmail(request.getAdminEmail());
        university = universityRepository.save(university);

        // 4. Create Admin User
        User adminUser = new User();
        adminUser.setEmail(request.getAdminEmail());
        adminUser.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setRole(Role.ADMIN);
        adminUser.setUniversity(university);
        userRepository.save(adminUser);

        return university;
    }

    public List<University> getAllUniversities() {
        return universityRepository.findAll();
    }

    @Transactional
    public JoinRequest requestToJoin(Long universityId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        University university = universityRepository.findById(universityId)
                .orElseThrow(() -> new IllegalArgumentException("University not found"));
        
        boolean alreadyRequested = joinRequestRepository.findByUniversityId(universityId).stream()
                .anyMatch(req -> req.getUser().getId().equals(user.getId()));
        if (alreadyRequested) {
            throw new IllegalArgumentException("Join request already submitted");
        }

        JoinRequest request = new JoinRequest();
        request.setUser(user);
        request.setUniversity(university);
        request.setStatus(JoinRequest.Status.PENDING);
        return joinRequestRepository.save(request);
    }

    public List<JoinRequest> getPendingRequests(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        if (admin.getUniversity() == null) {
            throw new IllegalArgumentException("Admin is not associated with any university");
        }
        return joinRequestRepository.findByUniversityIdAndStatus(admin.getUniversity().getId(), JoinRequest.Status.PENDING);
    }

    @Transactional
    public JoinRequest approveRequest(Long requestId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        JoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        
        if (admin.getUniversity() == null || !request.getUniversity().getId().equals(admin.getUniversity().getId())) {
            throw new IllegalArgumentException("Unauthorized");
        }

        request.setStatus(JoinRequest.Status.APPROVED);
        User user = request.getUser();
        user.setUniversity(request.getUniversity());
        user.setRole(Role.COUNSELOR); // Default approved joiners to Counselor role
        userRepository.save(user);
        
        return joinRequestRepository.save(request);
    }

    @Transactional
    public User createCounselorAccount(String adminEmail, String counselorEmail, String password, String fullName) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        if (admin.getUniversity() == null) {
            throw new IllegalArgumentException("Admin is not associated with any university");
        }
        
        if (userRepository.findByEmail(counselorEmail).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setEmail(counselorEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.COUNSELOR);
        user.setUniversity(admin.getUniversity());
        return userRepository.save(user);
    }

    @Transactional
    public User createFacultyAccount(String adminEmail, String facultyEmail, String facultyPassword, String fullName) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        if (admin.getUniversity() == null) {
            throw new IllegalArgumentException("Admin is not associated with any university");
        }
        
        if (userRepository.findByEmail(facultyEmail).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setEmail(facultyEmail);
        user.setPassword(passwordEncoder.encode(facultyPassword));
        user.setRole(Role.FACULTY);
        user.setUniversity(admin.getUniversity());
        user = userRepository.save(user);

        com.unios.model.Faculty faculty = new com.unios.model.Faculty();
        faculty.setUser(user);
        faculty.setUniversity(admin.getUniversity());
        faculty.setFullName(fullName != null ? fullName : facultyEmail.split("@")[0]);
        facultyRepository.save(faculty);

        return user;
    }
    @Transactional
    public University updateBranding(String adminEmail, String newName, String logoUrl) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        University university = admin.getUniversity();
        if (university == null) {
            throw new IllegalArgumentException("Admin is not associated with any university");
        }

        if (newName != null && !newName.isBlank()) {
            university.setName(newName);
        }
        if (logoUrl != null) {
            university.setLogoUrl(logoUrl);
        }
        return universityRepository.save(university);
    }
}
