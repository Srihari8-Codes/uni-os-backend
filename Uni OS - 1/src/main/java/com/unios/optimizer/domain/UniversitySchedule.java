package com.unios.optimizer.domain;

import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@PlanningSolution
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UniversitySchedule {

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "roomRange")
    private List<Room> roomList;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeSlotRange")
    private List<LocalTime> timeSlotList;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "slotRange")
    private List<String> subjectSlotList;

    @PlanningEntityCollectionProperty
    private List<ExamSession> examSessionList;

    @PlanningEntityCollectionProperty
    private List<SubjectClass> subjectClassList;

    @PlanningScore
    private HardSoftScore score;

    public UniversitySchedule(List<Room> roomList, List<LocalTime> timeSlotList, List<String> subjectSlotList,
            List<ExamSession> examSessionList, List<SubjectClass> subjectClassList) {
        this.roomList = roomList;
        this.timeSlotList = timeSlotList;
        this.subjectSlotList = subjectSlotList;
        this.examSessionList = examSessionList;
        this.subjectClassList = subjectClassList;
    }
}
