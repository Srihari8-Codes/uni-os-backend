package com.unios.optimizer;

import com.unios.optimizer.domain.ExamSession;
import com.unios.optimizer.domain.Room;
import com.unios.optimizer.domain.SubjectClass;
import com.unios.optimizer.service.AllocationSolverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class UniversityOptimizerTest {

    @Autowired
    private AllocationSolverService solverService;

    @Test
    public void testExamAllocation() {
        // 1. Setup Data: 300 Students, 5 Rooms (60 cap each)
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("R1", "Hall A", 60));
        rooms.add(new Room("R2", "Hall B", 60));
        rooms.add(new Room("R3", "Hall C", 60));
        rooms.add(new Room("R4", "Hall D", 60));
        rooms.add(new Room("R5", "Hall E", 60));

        List<ExamSession> sessions = new ArrayList<>();
        for (long i = 1; i <= 300; i++) {
            sessions.add(new ExamSession(i, i, i, "Computer Science"));
        }

        List<LocalTime> timeSlots = List.of(LocalTime.of(10, 0));

        // 2. Solve
        List<ExamSession> result = solverService.solveExamAllocation(rooms, sessions, timeSlots);

        // 3. Verify
        assertNotNull(result);
        assertEquals(300, result.size());

        // Check assigned rooms
        int assignedCount = 0;
        for (ExamSession s : result) {
            if (s.getAssignedRoom() != null) {
                assignedCount++;
                assertTrue(s.getAssignedRoom().getCapacity() >= 1); // just valid room
            }
        }
        assertEquals(300, assignedCount, "All students should be assigned a room");

        // Check capacity constraint logic
        // Group by room
        var byRoom = result.stream().collect(Collectors.groupingBy(s -> s.getAssignedRoom().getId()));
        for (var entry : byRoom.entrySet()) {
            int count = entry.getValue().size();
            System.out.println("Room " + entry.getKey() + " has " + count + " students.");
            assertTrue(count <= 60, "Room " + entry.getKey() + " capacity exceeded!");
        }
    }

    @Test
    public void testSubjectAllocation() {
        // 1. Setup: 5 Subjects, 3 Rooms, Slots
        List<Room> rooms = new ArrayList<>();
        rooms.add(new Room("R1", "Class A", 30));
        rooms.add(new Room("R2", "Class B", 30));

        List<SubjectClass> classes = new ArrayList<>();
        classes.add(new SubjectClass(1L, "Math", 30));
        classes.add(new SubjectClass(2L, "Physics", 30));
        classes.add(new SubjectClass(3L, "Chemistry", 30));

        List<String> slots = List.of("Slot A", "Slot B");

        // 2. Solve
        List<SubjectClass> result = solverService.solveSubjectAllocation(rooms, classes, slots);

        // 3. Verify
        for (SubjectClass c : result) {
            assertNotNull(c.getRoom(), "Subject " + c.getSubjectName() + " should have a room");
            assertNotNull(c.getSlot(), "Subject " + c.getSubjectName() + " should have a slot");
            System.out.println(c.getSubjectName() + " -> " + c.getSlot() + " in " + c.getRoom().getName());
        }

        // Check hard conflict: No two classes in same room at same slot
        Set<String> roomSlotPairs = new HashSet<>();
        for (SubjectClass c : result) {
            if (c.getRoom() != null && c.getSlot() != null) {
                String key = c.getRoom().getId() + "-" + c.getSlot();
                if (roomSlotPairs.contains(key)) {
                    fail("Room/Slot conflict detected: " + key);
                }
                roomSlotPairs.add(key);
            }
        }
    }
}
