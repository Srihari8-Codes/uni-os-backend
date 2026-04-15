package com.unios.optimizer.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@PlanningEntity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubjectClass {

    @PlanningId
    private Long subjectOfferingId;

    private String subjectName;
    private int enrolledCount;

    @PlanningVariable(valueRangeProviderRefs = "roomRange")
    private Room room;

    @PlanningVariable(valueRangeProviderRefs = "slotRange")
    private String slot;

    public SubjectClass(Long subjectOfferingId, String subjectName, int enrolledCount) {
        this.subjectOfferingId = subjectOfferingId;
        this.subjectName = subjectName;
        this.enrolledCount = enrolledCount;
    }
}
