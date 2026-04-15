package com.unios.dto.admissions;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityResult {
    private Long applicationId;
    private boolean isEligible;
    private String status; // ELIGIBLE, INELIGIBLE, DATA_MISSING
    private String reason;
    private Map<String, String> checks;
}
