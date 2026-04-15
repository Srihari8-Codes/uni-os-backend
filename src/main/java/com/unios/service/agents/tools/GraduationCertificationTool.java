package com.unios.service.agents.tools;

import com.unios.service.agents.framework.AgentTool;
import com.unios.service.agents.framework.ToolContext;
import com.unios.service.agents.framework.ToolResult;
import com.unios.service.agents.graduation.CertificationAgent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GraduationCertificationTool implements AgentTool {

    private final CertificationAgent certificationAgent;

    public GraduationCertificationTool(CertificationAgent certificationAgent) {
        this.certificationAgent = certificationAgent;
    }

    @Override
    public String name() {
        return "GraduationCertificationTool";
    }

    @Override
    public String description() {
        return "Issues digital graduation certificates and updates student status to GRADUATED. Input: {studentId: Long}";
    }

    @Override
    public ToolResult executeWithContext(ToolContext context) {
        Map<String, Object> input = context.getParameters();
        Long studentId = Long.valueOf(input.get("studentId").toString());
        certificationAgent.issueCertificate(studentId);
        
        return ToolResult.builder()
                .summary("Certificate issued for student " + studentId)
                .status("SUCCESS")
                .confidence(1.0)
                .build();
    }
}
