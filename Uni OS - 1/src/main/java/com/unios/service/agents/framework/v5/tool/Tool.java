package com.unios.service.agents.framework.v5.tool;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    
    /**
     * Executes the tool logic.
     * @param input Data required for execution.
     * @return Output string on success.
     * @throws Exception on failure.
     */
    String execute(Map<String, Object> input) throws Exception;
    
    /**
     * Strategy to rollback or revert partial execution if applicable.
     */
    void rollback(Map<String, Object> input);
}
