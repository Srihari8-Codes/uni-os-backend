package com.unios.optimizer.service;

import com.unios.optimizer.domain.ExamSession;
import com.unios.optimizer.domain.Room;
import com.unios.optimizer.domain.SubjectClass;
import com.unios.optimizer.domain.UniversitySchedule;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class AllocationSolverService {

    @Autowired
    private SolverManager<UniversitySchedule, UUID> solverManager;

    @Autowired
    private com.unios.service.policy.PolicyEngineService policyEngineService;

    /**
     * Solves the exam allocation problem.
     * 
     * @param rooms        List of available rooms
     * @param examSessions List of exam sessions (uninitialized or partially
     *                     initialized)
     * @param timeSlots    List of available time slots
     * @return Optimized list of exam sessions
     */
    public List<ExamSession> solveExamAllocation(List<Room> rooms, List<ExamSession> examSessions,
            List<LocalTime> timeSlots) {

        // Apply Policy: Max Room Utilization
        Double utilizationRate = policyEngineService.getPolicyValue("MAX_ROOM_UTILIZATION", 1.0,
                "Fraction of room capacity to use (0.0 - 1.0)");

        // Create effective rooms
        List<Room> effectiveRooms = new ArrayList<>();
        for (Room r : rooms) {
            Room er = new Room(r.getId(), r.getName(), (int) (r.getCapacity() * utilizationRate));
            effectiveRooms.add(er);
        }

        UUID problemId = UUID.randomUUID();

        // Create the problem
        UniversitySchedule problem = new UniversitySchedule(
                rooms,
                timeSlots,
                java.util.Collections.singletonList("UNUSED_SLOT"), // OptaPlanner requires non-empty value ranges
                examSessions,
                new ArrayList<>() // No subject classes
        );

        // Submit the problem to the solver
        SolverJob<UniversitySchedule, UUID> solverJob = solverManager.solve(problemId, problem);
        UniversitySchedule solution;
        try {
            solution = solverJob.getFinalBestSolution();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Solving failed", e);
        }

        return solution.getExamSessionList();
    }

    /**
     * Solves the subject class allocation problem.
     * 
     * @param rooms          List of available rooms
     * @param subjectClasses List of subject classes
     * @param slots          List of available slots (e.g. "A", "B", "C")
     * @return Optimized list of subject classes
     */
    public List<SubjectClass> solveSubjectAllocation(List<Room> rooms, List<SubjectClass> subjectClasses,
            List<String> slots) {
        UUID problemId = UUID.randomUUID();

        // Create the problem
        UniversitySchedule problem = new UniversitySchedule(
                rooms,
                java.util.Collections.singletonList(java.time.LocalTime.MIDNIGHT), // OptaPlanner requires non-empty value ranges
                slots,
                new ArrayList<>(), // No exam sessions
                subjectClasses);

        // Submit the problem to the solver
        SolverJob<UniversitySchedule, UUID> solverJob = solverManager.solve(problemId, problem);
        UniversitySchedule solution;
        try {
            solution = solverJob.getFinalBestSolution();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Solving failed", e);
        }

        return solution.getSubjectClassList();
    }
}
