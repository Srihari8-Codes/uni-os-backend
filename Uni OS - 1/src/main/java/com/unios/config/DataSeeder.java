package com.unios.config;

import com.unios.model.*;
import com.unios.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final RoomRepository roomRepository;
    private final ProgramRepository programRepository;
    private final BatchProgramRepository batchProgramRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(UserRepository userRepository,
                      FacultyRepository facultyRepository,
                      StudentRepository studentRepository,
                      BatchRepository batchRepository,
                      RoomRepository roomRepository,
                      ProgramRepository programRepository,
                      BatchProgramRepository batchProgramRepository,
                      ApplicationRepository applicationRepository,
                      PasswordEncoder passwordEncoder,
                      JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.roomRepository = roomRepository;
        this.programRepository = programRepository;
        this.batchProgramRepository = batchProgramRepository;
        this.applicationRepository = applicationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("🚀 [CONSOLIDATED SEEDER] Starting database initialization...");

        // 0. Clean up constraints (Postgres specific if needed)
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        } catch (Exception e) {
            // Might not be postgres or constraint doesn't exist
        }

        // 1. Seed Programs (Aligned with CourseTemplateService)
        if (programRepository.count() == 0) {
            System.out.println("-> Seeding Programs...");
            programRepository.saveAll(Arrays.asList(
                new Program(null, "B.E/B.Tech Computer Science", "CS"),
                new Program(null, "B.E/B.Tech Artificial Intelligence", "AI"),
                new Program(null, "B.E/B.Tech Electronics & Communication", "ECE"),
                new Program(null, "B.E/B.Tech Information Technology", "IT")
            ));
        } else {
            // MIGRATION: Rename B.S. to B.E/B.Tech if they exist
            System.out.println("-> Checking for Program Nomenclature Updates...");
            programRepository.findAll().forEach(p -> {
                if (p.getName().startsWith("B.S.")) {
                    String newName = p.getName().replace("B.S.", "B.E/B.Tech");
                    System.out.println("   [Migration] Renaming: " + p.getName() + " -> " + newName);
                    p.setName(newName);
                    programRepository.save(p);
                }
            });
        }

        // 2. Seed a single platform-level SUPER_ADMIN account (no university association).
        //    ------------------------------------------------------------------
        //    ALL real university admins onboard via the registration flow:
        //      POST /api/universities/register  (LandingPage.jsx)
        //    This creates a University + its first ADMIN User in one transaction.
        //    Counselors, faculty, and supervisors are then invited by that admin.
        //    ------------------------------------------------------------------
        seedUser("superadmin@unios.com", "password", Role.ADMIN);

        // 5. Seed Rooms
        if (roomRepository.count() < 10) {
            System.out.println("-> Seeding Entrance Exam Halls...");
            for (int i = 1; i <= 10; i++) {
                String roomName = "Exam Hall " + i;
                if (!roomRepository.existsByName(roomName)) {
                    Room r = new Room();
                    r.setName(roomName);
                    r.setCapacity(100);
                    roomRepository.save(r);
                }
            }
        }

        // 6. Testing Batch Seed Removed per user request


        // 7. Seed Applications
        long appCount = applicationRepository.count();
//        if (appCount < 50) {
//            System.out.println("-> Seeding Dummy Applications...");
//            List<Application> apps = new ArrayList<>();
//            for (int i = 1; i <= 50; i++) {
//                Application a = new Application();
//                a.setBatch(batch);
//                a.setFullName("Dummy Student " + i);
//                a.setEmail("student" + i + "@unios.email");
//                a.setAcademicScore(70.0 + Math.random() * 20.0);
//                a.setDocumentsVerified(true);
//                a.setStatus("SUBMITTED");
//                apps.add(a);
//            }
//            applicationRepository.saveAll(apps);
//        }

        System.out.println("✅ [CONSOLIDATED SEEDER] Database initialization complete.");
        System.out.println("   Super Admin (dev only): superadmin@unios.com / password");
        System.out.println("   ⚠️  Real university admins must register via the frontend landing page.");
    }

    private void seedUser(String email, String rawPassword, Role role) {
        User u = userRepository.findByEmail(email).orElse(new User());
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole(role);
        userRepository.save(u);
        System.out.println("-> Seeded/Updated " + role + ": " + email);
    }
}
