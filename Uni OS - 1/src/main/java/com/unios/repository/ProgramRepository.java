package com.unios.repository;

import com.unios.model.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    Optional<Program> findByName(String name);
}
