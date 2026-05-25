package io.ecoskiller.royalty.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public class RequestValidator {
    private static final int MAX_BYTES = 65536;

    public boolean isValidToolName(String n) { return InputSanitizer.isValidToolName(n); }

    public List<String> validate(String tool, JsonNode args) {
        List<String> errors = new ArrayList<>();
        if (args == null) return errors;
        String raw = args.toString();
        if (raw.length() > MAX_BYTES) errors.add("Payload (" + raw.length() + " bytes) exceeds 64KB limit");
        if (raw.contains("\u0000")) errors.add("Payload contains null bytes — potential injection");
        return errors;
    }
}
