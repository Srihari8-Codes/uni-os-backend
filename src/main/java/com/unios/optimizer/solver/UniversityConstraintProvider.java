package com.unios.optimizer.solver;

import com.unios.optimizer.domain.ExamSession;
import com.unios.optimizer.domain.Room;
import com.unios.optimizer.domain.SubjectClass;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

public class UniversityConstraintProvider implements ConstraintProvider {

        @Override
        public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
                return new Constraint[] {
                                // Hard Constraints
                                roomCapacityConflictExam(constraintFactory),
                                roomCapacityConflictSubject(constraintFactory),
                                roomConflictSubject(constraintFactory),
                                // Mixing exams and subjects in same room/time is also a conflict if they share
                                // resource,
                                // but usually exams and classes are scheduled separately or time slots are
                                // distinct.
                                // Assuming they might share rooms but maybe not times?
                                // For safety, let's treat them as separate problems or ensure keys match.
                                // Since user said "subjects" and "exams", I will implement constraints for
                                // both.

                                studentExamConflict(constraintFactory),

                                // Soft Constraints
                                minimizeUnusedSeatsExam(constraintFactory),
                                minimizeUnusedSeatsSubject(constraintFactory)
                                // minimizeRoomsUsed is harder to model directly as a simple stream without
                                // extra facts,
                                // but we can penalize each used room? Or just rely on unused seats to compact
                                // them.
                };
        }

        // Hard: Room capacity must not be exceeded (Exams)
        // We count number of exams in a room at a time and ensure it <= capacity
        // Actually, ExamSession is 1 student. So we group by Room+Time and count.

        // Changing approach: If ExamSession is 1 student, we need to count how many
        // sessions are in (Room, Time).
        // If count > room.capacity -> penalize.

        Constraint roomCapacityConflictExam(ConstraintFactory constraintFactory) {
                return constraintFactory.forEach(ExamSession.class)
                                .groupBy(ExamSession::getAssignedRoom, ExamSession::getTimeSlot,
                                                org.optaplanner.core.api.score.stream.ConstraintCollectors.count())
                                .filter((room, time, count) -> count > room.getCapacity())
                                .penalize(HardSoftScore.ONE_HARD, (room, time, count) -> count - room.getCapacity())
                                .asConstraint("Exam Room capacity exceeded");
        }

        // Hard: Room capacity must not be exceeded (SubjectClass)
        // SubjectClass has 'enrolledCount'.
        Constraint roomCapacityConflictSubject(ConstraintFactory constraintFactory) {
                return constraintFactory.forEach(SubjectClass.class)
                                .filter(subject -> subject.getRoom() != null
                                                && subject.getEnrolledCount() > subject.getRoom().getCapacity())
                                .penalize(HardSoftScore.ONE_HARD,
                                                (subject) -> subject.getEnrolledCount()
                                                                - subject.getRoom().getCapacity())
                                .asConstraint("Subject Room capacity exceeded");
        }

        // Hard: No two sessions in same room + same timeSlot
        // For ExamSession (1 student per session), multiple sessions CAN be in same
        // room (just not same seat, but we ignore seats for now).
        // The previous constraint handles capacity.
        // Wait, "No two sessions in same room + same timeSlot" usually refers to
        // *Classes*.
        // Two *different* SubjectClasses cannot be in same Room at same Slot.

        Constraint roomConflictSubject(ConstraintFactory constraintFactory) {
                return constraintFactory.forEachUniquePair(SubjectClass.class,
                                Joiners.equal(SubjectClass::getRoom),
                                Joiners.equal(SubjectClass::getSlot))
                                .penalize(HardSoftScore.ONE_HARD)
                                .asConstraint("Subject Room conflict");
        }

        // For exams, "No two sessions" might mean "No two DIFFERENT exams" ?
        // Usually many students take same exam in same room.
        // If "Session" means "Sitting of a single student", they CAN share a room.
        // If "Session" means "An exam event for a course", then they can't.
        // User said: "ExamSchedulerAgent... naive sequential logic... 300 students...
        // system auto-distributes them".
        // Entities: "ExamSession... applicationId... seatNumber".
        // This implies ExamSession = 1 Student's seat.
        // So multiple ExamSessions CAN share a Room+Time.
        // "No two sessions in same room + same timeSlot" might be a misunderstanding of
        // terms or implies "Seat conflict"?
        // "No two sessions in same room + same timeSlot" -> If this refers to the *same
        // seat*?
        // PROMPT: "No two sessions in same room + same timeSlot".
        // Since `seatNumber` is a field, maybe it means strictly (Room, Time, Seat)
        // must be unique.

        // Let's assume we don't optimize Seat yet (as I decided in entity), so we can't
        // check seat conflict.
        // The "Capacity" constraint covers the "too many students in room".
        // I will skip "Room conflict" for Exams regarding *sharing the room*, because
        // that's the point of an exam hall.
        // BUT, we must ensure a student doesn't have 2 exams at same time.

        Constraint studentExamConflict(ConstraintFactory constraintFactory) {
                return constraintFactory.forEachUniquePair(ExamSession.class,
                                Joiners.equal(ExamSession::getStudentId),
                                Joiners.equal(ExamSession::getTimeSlot))
                                .penalize(HardSoftScore.ONE_HARD)
                                .asConstraint("Student has overlapping exams");
        }

        // Soft: Minimize unused seats (Compactness)
        // For Exams: Penalize (Capacity - Count) for every used room?
        // Or reward used seats?
        // Let's penalize unused seats per occupied room.
        Constraint minimizeUnusedSeatsExam(ConstraintFactory constraintFactory) {
                return constraintFactory.forEach(ExamSession.class)
                                .groupBy(ExamSession::getAssignedRoom, ExamSession::getTimeSlot,
                                                org.optaplanner.core.api.score.stream.ConstraintCollectors.count())
                                .penalize(HardSoftScore.ONE_SOFT, (room, time, count) -> room.getCapacity() - count)
                                .asConstraint("Minimize unused seats (Exam)");
        }

        Constraint minimizeUnusedSeatsSubject(ConstraintFactory constraintFactory) {
                return constraintFactory.forEach(SubjectClass.class)
                                .filter(subject -> subject.getRoom() != null)
                                .penalize(HardSoftScore.ONE_SOFT,
                                                (subject) -> subject.getRoom().getCapacity()
                                                                - subject.getEnrolledCount())
                                .asConstraint("Minimize unused seats (Subject)");
        }

        // Constraint: Minimize total number of rooms used.
        // We can penalize *every* assignment calculation? No, that's minimal
        // *assignments*.
        // We want to penalize *distinct* rooms used.
        // It's hard to do "distinct count" directly in simple streams without overhead.
        // But "Minimize unused seats" implicitly pushes for fewer rooms (because empty
        // rooms have "capacity" unused seats? No, empty rooms aren't in groupBy).
        // Actually, if we pack 100 students into 1 room (cap 100), unused=0.
        // If we pack 100 students into 2 rooms (cap 100 each), 50 each,
        // unused=50+50=100.
        // So "Minimize unused seats" IS "Minimize rooms used" effectively for fixed
        // capacity rooms.
        // If rooms have variable capacity, it tries to fit into smallest rooms.

}
