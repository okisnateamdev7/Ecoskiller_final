package io.ecoskiller.mcp.jitsi;

import java.time.Instant;
import java.util.*;

/**
 * Tool: conference_create
 *
 * Creates a new Jitsi Video Bridge conference room for an Ecoskiller assessment.
 * Supports Group Discussion (GD), Technical Interview (TI), Panel Interview, and
 * Case Study Discussion formats.
 *
 * Security: requires valid JWT (_auth_token) from Assessment Orchestration Service.
 */
public class ConferenceCreateTool implements ToolHandler {

    @Override
    public String getName() { return "conference_create"; }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name",        getName());
        schema.put("description", "Create a new Jitsi Video Bridge conference for an Ecoskiller assessment session. " +
                "Supports Group Discussion (4-8 participants), Technical Interview (2-3), Panel Interview (4-7), " +
                "and Case Study Discussion formats. Returns conference room details including JWT tokens for participants.");

        Map<String, Object> props = new LinkedHashMap<>();

        props.put("assessment_id", prop("string",
                "Unique assessment identifier (e.g. gd-12345, ti-67890)"));

        props.put("assessment_type", propEnum("string",
                "Type of synchronous assessment requiring video bridge",
                List.of("group_discussion", "technical_interview", "panel_interview", "case_study_discussion")));

        props.put("participants", prop("array",
                "List of participants. Each entry: {id, name, role (candidate|interviewer|moderator), email}"));

        props.put("max_duration_minutes", prop("integer",
                "Maximum conference duration in minutes (default 60). Bridge auto-terminates at limit."));

        props.put("recording_enabled", prop("boolean",
                "Enable Jibri recording to S3/GCS for AI scoring analysis (default true)"));

        props.put("codec_preference", propEnum("string",
                "Preferred video codec (default vp8)",
                List.of("vp8", "h264", "vp9")));

        props.put("simulcast_enabled", prop("boolean",
                "Enable multi-bitrate simulcast for adaptive streaming (default true)"));

        schema.put("inputSchema", Map.of(
            "type",       "object",
            "properties", props,
            "required",   List.of("assessment_id", "assessment_type", "participants")
        ));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> args) {
        // ── Validation ─────────────────────────────────────────────────────
        String assessmentId   = require(args, "assessment_id");
        String assessmentType = require(args, "assessment_type");

        validateAssessmentId(assessmentId);
        validateAssessmentType(assessmentType);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participants =
            (List<Map<String, Object>>) args.getOrDefault("participants", List.of());

        validateParticipants(participants, assessmentType);

        boolean recordingEnabled  = getBool(args, "recording_enabled", true);
        boolean simulcastEnabled  = getBool(args, "simulcast_enabled", true);
        int     maxDurationMin    = getInt(args, "max_duration_minutes", 60);
        String  codecPreference   = getString(args, "codec_preference", "vp8");

        // ── Create conference ──────────────────────────────────────────────
        String roomName     = "assessment-" + assessmentId;
        String conferenceId = "conf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String recordingId  = recordingEnabled
                ? "rec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 9)
                : null;

        // Build per-participant join tokens
        List<Map<String, Object>> participantTokens = new ArrayList<>();
        for (Map<String, Object> p : participants) {
            String pid  = (String) p.get("id");
            String role = (String) p.getOrDefault("role", "candidate");
            participantTokens.add(Map.of(
                "participant_id", pid,
                "role",           role,
                "join_url",       "https://jitsi.ecoskiller.io/" + roomName + "?jwt=<generated_for_" + pid + ">",
                "jwt_expiry",     Instant.now().plusSeconds(maxDurationMin * 60L + 300).toString()
            ));
        }

        // Register in in-memory store
        ConferenceStore.create(conferenceId, assessmentId, assessmentType, participants);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",            "created");
        result.put("conference_id",     conferenceId);
        result.put("assessment_id",     assessmentId);
        result.put("room_name",         roomName);
        result.put("jitsi_host",        "jitsi.ecoskiller.io");
        result.put("xmpp_room_jid",     roomName + "@conference.jitsi.ecoskiller.io");
        result.put("assessment_type",   assessmentType);
        result.put("codec",             codecPreference);
        result.put("simulcast",         simulcastEnabled);
        result.put("recording_enabled", recordingEnabled);
        if (recordingId != null) result.put("recording_id", recordingId);
        result.put("max_duration_min",  maxDurationMin);
        result.put("participant_count", participants.size());
        result.put("participant_tokens", participantTokens);
        result.put("ports", Map.of(
            "rtp_udp",       10000,
            "xmpp",          5347,
            "xmpp_secure",   5349,
            "health_http",   8080,
            "prometheus",    8888
        ));
        result.put("created_at",        Instant.now().toString());
        result.put("expires_at",        Instant.now().plusSeconds(maxDurationMin * 60L + 600).toString());
        result.put("kafka_topic",       "assessment.participants");
        result.put("recording_topic",   "assessment.recordings");
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateAssessmentId(String id) {
        if (!id.matches("^[a-zA-Z0-9\\-]{3,64}$")) {
            throw new IllegalArgumentException(
                "assessment_id must be 3-64 alphanumeric characters (hyphens allowed)");
        }
    }

    private void validateAssessmentType(String type) {
        Set<String> valid = Set.of("group_discussion","technical_interview","panel_interview","case_study_discussion");
        if (!valid.contains(type)) {
            throw new IllegalArgumentException("Invalid assessment_type: " + type);
        }
    }

    private void validateParticipants(List<Map<String, Object>> participants, String type) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("At least 1 participant required");
        }
        int max = switch (type) {
            case "group_discussion"   -> 8;
            case "technical_interview"-> 3;
            case "panel_interview"    -> 7;
            default                   -> 12;
        };
        if (participants.size() > max) {
            throw new IllegalArgumentException(
                "Max " + max + " participants for " + type + "; got " + participants.size());
        }
        for (Map<String, Object> p : participants) {
            if (!p.containsKey("id") || !p.containsKey("name")) {
                throw new IllegalArgumentException("Each participant must have 'id' and 'name'");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private static Map<String, Object> prop(String type, String desc) {
        return Map.of("type", type, "description", desc);
    }

    private static Map<String, Object> propEnum(String type, String desc, List<String> values) {
        return Map.of("type", type, "description", desc, "enum", values);
    }

    static String require(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required argument: " + key);
        return v.toString();
    }

    static String getString(Map<String, Object> args, String key, String def) {
        Object v = args.get(key);
        return (v != null) ? v.toString() : def;
    }

    static boolean getBool(Map<String, Object> args, String key, boolean def) {
        Object v = args.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String)  return Boolean.parseBoolean((String) v);
        return def;
    }

    static int getInt(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { if (v != null) return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }
}
