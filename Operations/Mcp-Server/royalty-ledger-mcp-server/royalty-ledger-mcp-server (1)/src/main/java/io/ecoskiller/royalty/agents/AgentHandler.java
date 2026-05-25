package io.ecoskiller.royalty.agents;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentHandler {
    JsonNode getToolDefinition();
    JsonNode execute(JsonNode args) throws Exception;
}
