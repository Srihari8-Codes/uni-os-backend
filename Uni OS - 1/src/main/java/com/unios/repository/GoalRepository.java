package com.unios.repository;

import com.unios.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {
    
    List<Goal> findByStatus(Goal.GoalStatus status);
    
    List<Goal> findByTypeAndStatus(Goal.GoalType type, Goal.GoalStatus status);

    @Query("SELECT g FROM Goal g WHERE g.status = 'ACTIVE' ORDER BY (g.priority * g.urgencyScore) DESC")
    List<Goal> findTopActiveGoalsByWeight();

    @Query("SELECT g FROM Goal g WHERE g.status = :status ORDER BY g.priority DESC, g.urgencyScore DESC")
    List<Goal> findByStatusOrderByPriorityDescUrgencyScoreDesc(Goal.GoalStatus status);

    @Modifying
    @Query("UPDATE Goal g SET g.status = 'TIMEOUT' WHERE g.status = 'ACTIVE' AND g.deadline < :now")
    int markExpiredGoalsAsTimeout(ZonedDateTime now);
}
