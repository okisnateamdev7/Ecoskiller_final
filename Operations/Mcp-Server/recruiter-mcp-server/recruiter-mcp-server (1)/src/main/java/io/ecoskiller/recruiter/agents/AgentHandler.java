package io.ecoskiller.recruiter.agents;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contract for all Recruiter MCP agents.
 */
public interface AgentHandler {
    JsonNode getToolDefinition();
    JsonNode execute(JsonNode args) throws Exception;
}
