package com.unios.repository;

import com.unios.model.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
}
