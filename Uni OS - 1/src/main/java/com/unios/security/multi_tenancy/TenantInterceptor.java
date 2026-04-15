package com.unios.security.multi_tenancy;

import com.unios.model.University;
import com.unios.repository.UniversityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Interceptor to resolve the tenant from the custom hostname/subdomain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    private final UniversityRepository universityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String host = request.getHeader("Host");
        log.debug("Resolving tenant for host: {}", host);

        if (host != null) {
            String subdomain = extractSubdomain(host);
            if (subdomain != null && !subdomain.equalsIgnoreCase("localhost") && !subdomain.equalsIgnoreCase("www")) {
                Optional<University> university = universityRepository.findBySubdomain(subdomain.toLowerCase());
                university.ifPresent(u -> {
                    TenantContext.setCurrentTenant(u.getId());
                    // Enable Hibernate Filter
                    Session session = entityManager.unwrap(Session.class);
                    session.enableFilter("tenantFilter").setParameter("tenantId", u.getId());
                    log.debug("Enabled tenantFilter for tenantId: {}", u.getId());
                });
            }
        }
        
        return true; // Continue regardless, downstream logic can check if tenant is required
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractSubdomain(String host) {
        // Simple extraction logic: tenant1.localhost:8080 -> tenant1
        String[] parts = host.split("\\.");
        if (parts.length > 1) {
            return parts[0];
        }
        return null;
    }
}
