package io.ecoskiller.mcp.jitsi;

import java.util.Map;

/**
 * Contract every Jitsi MCP tool must implement.
 */
public interface ToolHandler {

    /** Unique snake_case tool name — matched against MCP tools/call requests. */
    String getName();

    /** Returns the MCP-compliant JSON Schema descriptor for this tool. */
    Map<String, Object> getSchema();

    /**
     * Execute the tool with the provided arguments.
     *
     * @param args validated, deserialized arguments (auth token already stripped)
     * @return an object that will be JSON-serialised into the response content
     * @throws IllegalArgumentException for validation / business-logic errors
     */
    Object execute(Map<String, Object> args);
}
