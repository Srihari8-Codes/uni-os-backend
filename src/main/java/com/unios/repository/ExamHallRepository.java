package com.unios.repository;

import com.unios.model.ExamHall;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExamHallRepository extends JpaRepository<ExamHall, Long> {
    Optional<ExamHall> findByName(String name);
}
