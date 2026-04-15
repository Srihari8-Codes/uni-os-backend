package com.unios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseTemplate {
    private String name;
    private Integer credits;
    private List<String> prerequisites;
    private Boolean mandatory;
}
