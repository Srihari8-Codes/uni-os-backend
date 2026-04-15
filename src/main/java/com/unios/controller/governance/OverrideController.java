package com.unios.controller.governance;

import com.unios.service.governance.OverrideDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/governance/overrides")
@RequiredArgsConstructor
public class OverrideController {

    private final OverrideDecisionService overrideService;

    @PostMapping("/{taskId}")
    public void override(@PathVariable String taskId, @RequestBody Map<String, String> request) {
        overrideService.provideOverride(
            taskId, 
            request.get("action"), 
            request.get("reasoning"), 
            request.get("parametersJson")
        );
    }
}
