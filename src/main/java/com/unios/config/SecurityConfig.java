package com.unios.config;

import org.springframework.http.HttpMethod;
import com.unios.security.JwtFilter;
import com.unios.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtFilter jwtFilter,
            CustomUserDetailsService userDetailsService,
            CorsConfigurationSource corsConfigurationSource) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS using the CorsConfig bean
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // --- Auth ---
                        .requestMatchers("/auth/signup", "/auth/login", "/auth/signup-applicant").permitAll()
                        // --- Public student application ---
                        .requestMatchers(HttpMethod.POST, "/applications").permitAll()
                        .requestMatchers(HttpMethod.GET, "/applications/*/pdf").permitAll()
                        // --- Public batch info (needed by public StudentApply page) ---
                        .requestMatchers(HttpMethod.GET, "/batches/active", "/batches/public/**").permitAll()
                        // --- Applicant Portal ---
                        .requestMatchers("/api/applicant/**").hasAuthority("ROLE_APPLICANT")
                        // --- Enrollment (student only) ---
                        .requestMatchers("/enroll").hasAuthority("ROLE_STUDENT")
                        // --- Attendance (faculty only) ---
                        .requestMatchers(HttpMethod.POST, "/attendance").hasAuthority("ROLE_FACULTY")
                        // --- Offerings: GET open to STUDENT,FACULTY,ADMIN; POST/activate restricted to
                        // FACULTY,ADMIN ---
                        .requestMatchers(HttpMethod.GET, "/offerings")
                        .hasAnyAuthority("ROLE_STUDENT", "ROLE_FACULTY", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/offerings").hasAnyAuthority("ROLE_ADMIN", "ROLE_FACULTY")
                        .requestMatchers("/offerings/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_FACULTY")
                        // --- Recruitment & Graduation ---
                        .requestMatchers(HttpMethod.POST, "/recruitment").permitAll()
                        .requestMatchers("/recruitment/**", "/graduation/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/batches/nuke-all").permitAll()
                        // --- Batches: COUNSELOR can read, ADMIN can do everything ---
                        .requestMatchers(HttpMethod.GET, "/batches", "/batches/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_COUNSELOR")
                        .requestMatchers("/batches", "/batches/**", "/admissions/**").hasAuthority("ROLE_ADMIN")
                        // --- Counseling access for Admin & Counselor ---
                        .requestMatchers("/api/counseling/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_COUNSELOR")
                        // --- Exam portal (student only) ---
                        .requestMatchers("/api/exam/**").hasAuthority("ROLE_STUDENT")
                        // --- Student portal ---
                        .requestMatchers("/api/student/**").hasAuthority("ROLE_STUDENT")
                        // --- Faculty portal ---
                        .requestMatchers("/api/faculty/**").authenticated()
                        // --- Governance & Supervisor Control ---
                        .requestMatchers("/api/governance/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERVISOR")
                        .requestMatchers("/supervisor/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPERVISOR")
                        .requestMatchers("/api/simulation/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/entrance-exam/**").permitAll()
                        .requestMatchers("/api/exams/**").permitAll()
                        .requestMatchers("/retell/**", "/api/retell-webhook").permitAll()
                        .requestMatchers("/admission-chat/**", "/conversational-admissions/**").permitAll()
                        
                        // --- Multi-Tenancy Registration (Public) ---
                        .requestMatchers(HttpMethod.POST, "/api/universities/register").permitAll()
                        .requestMatchers("/api/dev/**", "/api/debug/ocr/**", "/ocr-debug.html").permitAll()
                        .requestMatchers("/api/universities/**").authenticated()

                        // --- Everything else requires authentication ---
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
