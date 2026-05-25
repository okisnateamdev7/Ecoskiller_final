package io.ecoskiller.recruiter.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public class RequestValidator {
    private static final int MAX_PAYLOAD = 65536; // 64KB

    public boolean isValidToolName(String name) { return InputSanitizer.isValidToolName(name); }

    public List<String> validate(String toolName, JsonNode args) {
        List<String> errors = new ArrayList<>();
        if (args == null) return errors;
        String raw = args.toString();
        if (raw.length() > MAX_PAYLOAD)
            errors.add("Payload (" + raw.length() + " bytes) exceeds 64KB limit");
        if (raw.contains("\u0000"))
            errors.add("Payload contains null bytes — potential injection attack");
        return errors;
    }
}
