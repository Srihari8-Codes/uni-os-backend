package com.unios.dto.admissions;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfoDTO {
    private String id;
    private String label;
    private String type;
    private boolean required;
    private String conversationalPrompt;
    private List<String> options; // For select types
    private Map<String, Object> validation;
}
