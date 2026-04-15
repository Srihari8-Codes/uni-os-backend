package com.unios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsDTO {
    private long totalStudents;
    private long activeFaculty;
    private long pendingApplications;
    private String universityName;
    private String logoUrl;
}
