package com.unios.repository;

import com.unios.model.StrategicLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StrategicLessonRepository extends JpaRepository<StrategicLesson, Long> {
    
    @Query("SELECT s FROM StrategicLesson s ORDER BY s.createdAt DESC")
    List<StrategicLesson> findRecentLessons();
}
