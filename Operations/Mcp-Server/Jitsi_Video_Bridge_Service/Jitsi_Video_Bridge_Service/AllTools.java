package io.ecoskiller.mcp.jitsi;

import java.time.Instant;
import java.util.*;

import static io.ecoskiller.mcp.jitsi.ConferenceCreateTool.*;

// ═════════════════════════════════════════════════════════════════════════════
// 2. conference_terminate
// ═════════════════════════════════════════════════════════════════════════════
class ConferenceTerminateTool implements ToolHandler {
    @Override public String getName() { return "conference_terminate"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Terminate an active Jitsi conference. Stops all media streams, " +
                           "finalises recording upload to S3/GCS, and cleans up bridge resources. " +
                           "Emits 'conference_ended' to Kafka topic assessment.participants.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id", Map.of("type","string","description","Conference ID returned by conference_create"),
                    "reason",        Map.of("type","string","description","Termination reason (normal_exit|timeout|admin_action)","enum",List.of("normal_exit","timeout","admin_action"))
                ),
                "required", List.of("conference_id")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId = require(args, "conference_id");
        String reason = getString(args, "reason", "admin_action");
        validateConfId(confId);

        boolean existed = ConferenceStore.terminate(confId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",         existed ? "terminated" : "not_found");
        r.put("conference_id",  confId);
        r.put("reason",         reason);
        r.put("recording_finalised", existed);
        r.put("kafka_event",    "conference_ended → assessment.participants");
        r.put("terminated_at",  Instant.now().toString());
        return r;
    }

    private void validateConfId(String id) {
        if (!id.matches("^conf-[a-f0-9]{12}$"))
            throw new IllegalArgumentException("Invalid conference_id format");
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. conference_status
// ═════════════════════════════════════════════════════════════════════════════
class ConferenceStatusTool implements ToolHandler {
    @Override public String getName() { return "conference_status"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Retrieve live status of a Jitsi conference: active participants, " +
                           "current speaker, RTP stats (packet loss, jitter, bitrate), recording state.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id", Map.of("type","string","description","Conference ID to query")
                ),
                "required", List.of("conference_id")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId = require(args, "conference_id");
        Map<String, Object> conf = ConferenceStore.get(confId);

        if (conf == null) {
            throw new IllegalArgumentException("Conference not found: " + confId);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conference_id",       confId);
        r.put("status",              conf.get("status"));
        r.put("assessment_id",       conf.get("assessment_id"));
        r.put("assessment_type",     conf.get("assessment_type"));
        r.put("participant_count",   conf.get("participant_count"));
        r.put("active_speaker",      "participant-001");           // simulated
        r.put("recording_active",    true);
        r.put("rtp_stats", Map.of(
            "packet_loss_pct",   1.2,
            "jitter_ms",         12.4,
            "avg_bitrate_kbps",  1280,
            "rtt_ms",            34
        ));
        r.put("uptime_seconds",      (System.currentTimeMillis() - (Long) conf.get("created_epoch")) / 1000);
        r.put("queried_at",          Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. participant_join
// ═════════════════════════════════════════════════════════════════════════════
class ParticipantJoinTool implements ToolHandler {
    @Override public String getName() { return "participant_join"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Register a participant joining an active Jitsi conference. " +
                           "Validates participant identity against assessment roster, emits " +
                           "'participant_joined' to Kafka topic assessment.participants.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id",   Map.of("type","string","description","Target conference"),
                    "participant_id",  Map.of("type","string","description","Unique participant identifier"),
                    "participant_name",Map.of("type","string","description","Display name"),
                    "role",            Map.of("type","string","enum",List.of("candidate","interviewer","moderator")),
                    "audio_enabled",   Map.of("type","boolean","description","Initial audio state (default true)"),
                    "video_enabled",   Map.of("type","boolean","description","Initial video state (default true)"),
                    "jitsi_endpoint_id",Map.of("type","string","description","Internal Jitsi endpoint identifier")
                ),
                "required", List.of("conference_id","participant_id","participant_name","role")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId  = require(args, "conference_id");
        String pid     = require(args, "participant_id");
        String name    = require(args, "participant_name");
        String role    = getString(args, "role", "candidate");

        // Sanitise display name to prevent XSS in XMPP presence stanzas
        name = name.replaceAll("[<>&\"']", "");
        if (name.length() > 64) throw new IllegalArgumentException("participant_name too long (max 64)");

        boolean audio   = getBool(args, "audio_enabled", true);
        boolean video   = getBool(args, "video_enabled", true);
        String  epId    = getString(args, "jitsi_endpoint_id", "ep-" + pid.hashCode());

        ConferenceStore.addParticipant(confId, pid, name, role);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",              "joined");
        r.put("conference_id",       confId);
        r.put("participant_id",      pid);
        r.put("participant_name",    name);
        r.put("role",                role);
        r.put("jitsi_endpoint_id",   epId);
        r.put("audio_enabled",       audio);
        r.put("video_enabled",       video);
        r.put("xmpp_jid",            pid + "@conference.jitsi.ecoskiller.io");
        r.put("kafka_event",         Map.of("topic","assessment.participants","event_type","participant_joined"));
        r.put("joined_at",           Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. participant_remove
// ═════════════════════════════════════════════════════════════════════════════
class ParticipantRemoveTool implements ToolHandler {
    @Override public String getName() { return "participant_remove"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Remove (kick) a participant from an active Jitsi conference. " +
                           "Moderator action. Emits 'participant_left' to Kafka.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id",  Map.of("type","string"),
                    "participant_id", Map.of("type","string"),
                    "reason",         Map.of("type","string","enum",List.of("admin_removal","timeout","violation","connection_lost"))
                ),
                "required", List.of("conference_id","participant_id","reason")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId = require(args, "conference_id");
        String pid    = require(args, "participant_id");
        String reason = require(args, "reason");

        ConferenceStore.removeParticipant(confId, pid);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",          "removed");
        r.put("conference_id",   confId);
        r.put("participant_id",  pid);
        r.put("reason",          reason);
        r.put("kafka_event",     Map.of("topic","assessment.participants","event_type","participant_left","reason",reason));
        r.put("removed_at",      Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 6. active_speaker_detection
// ═════════════════════════════════════════════════════════════════════════════
class ActiveSpeakerDetectionTool implements ToolHandler {
    @Override public String getName() { return "active_speaker_detection"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Query or configure Active Speaker Detection (ASD) for a conference. " +
                           "ASD analyses audio energy levels and updates the highlighted speaker every 500ms. " +
                           "Returns current speaker, speaking durations, and interruption counts for all participants.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id",       Map.of("type","string","description","Target conference"),
                    "action",              Map.of("type","string","enum",List.of("get_current","get_stats","configure"),"description","ASD action"),
                    "update_interval_ms",  Map.of("type","integer","description","ASD polling interval (default 500ms, range 100-2000)")
                ),
                "required", List.of("conference_id","action")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId  = require(args, "conference_id");
        String action  = require(args, "action");
        int interval   = getInt(args, "update_interval_ms", 500);

        if (interval < 100 || interval > 2000)
            throw new IllegalArgumentException("update_interval_ms must be 100-2000");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conference_id",  confId);
        r.put("action",         action);
        r.put("asd_enabled",    true);
        r.put("update_interval_ms", interval);

        if ("get_current".equals(action) || "get_stats".equals(action)) {
            r.put("current_speaker", Map.of(
                "participant_id",   "cand-001",
                "name",             "Alice Smith",
                "speaking_since",   Instant.now().minusSeconds(8).toString(),
                "audio_energy_db",  -18.4
            ));
            r.put("speaker_stats", List.of(
                Map.of("participant_id","cand-001","total_speaking_sec",180,"interruptions",3,"turns",12),
                Map.of("participant_id","cand-002","total_speaking_sec",120,"interruptions",1,"turns",8),
                Map.of("participant_id","intvr-001","total_speaking_sec",90,"interruptions",0,"turns",15)
            ));
        }
        r.put("kafka_topic",    "assessment.speakers");
        r.put("queried_at",     Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 7. bandwidth_management
// ═════════════════════════════════════════════════════════════════════════════
class BandwidthManagementTool implements ToolHandler {
    @Override public String getName() { return "bandwidth_management"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Monitor and control bandwidth for a conference. Reports REMB " +
                           "(Receiver Estimated Max Bitrate) per participant and allows overriding " +
                           "video quality constraints for poor-network participants.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id",        Map.of("type","string"),
                    "action",               Map.of("type","string","enum",List.of("get_stats","set_max_bitrate","force_low_quality")),
                    "participant_id",       Map.of("type","string","description","Target participant (null = all)"),
                    "max_video_bitrate_kbps",Map.of("type","integer","description","Cap outgoing video bitrate (64-2500 kbps)"),
                    "target_resolution",    Map.of("type","string","enum",List.of("360p","480p","720p","1080p"))
                ),
                "required", List.of("conference_id","action")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId  = require(args, "conference_id");
        String action  = require(args, "action");

        int maxBitrate = getInt(args, "max_video_bitrate_kbps", 2500);
        if (maxBitrate < 64 || maxBitrate > 2500)
            throw new IllegalArgumentException("max_video_bitrate_kbps must be 64-2500");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conference_id",  confId);
        r.put("action",         action);

        if ("get_stats".equals(action)) {
            r.put("participants_bandwidth", List.of(
                Map.of("participant_id","cand-001","remb_kbps",1800,"packet_loss_pct",0.5,"jitter_ms",8,"rtt_ms",28),
                Map.of("participant_id","cand-002","remb_kbps",320,"packet_loss_pct",8.2,"jitter_ms",45,"rtt_ms",180),
                Map.of("participant_id","intvr-001","remb_kbps",2100,"packet_loss_pct",0.1,"jitter_ms",5,"rtt_ms",15)
            ));
            r.put("bridge_total_bandwidth_mbps", 4.2);
        } else {
            r.put("applied_max_bitrate_kbps", maxBitrate);
            r.put("applied_to", args.getOrDefault("participant_id", "all"));
            r.put("resolution",  getString(args,"target_resolution","720p"));
        }
        r.put("updated_at", Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 8. recording_control
// ═════════════════════════════════════════════════════════════════════════════
class RecordingControlTool implements ToolHandler {
    @Override public String getName() { return "recording_control"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Start, stop, or pause Jibri recording for a conference. " +
                           "Recordings are stored to S3/GCS with SSE-KMS encryption for AI scoring analysis. " +
                           "Emits recording events to Kafka topic assessment.recordings.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "conference_id",   Map.of("type","string"),
                    "action",          Map.of("type","string","enum",List.of("start","stop","pause","resume")),
                    "output_format",   Map.of("type","string","enum",List.of("webm","mp4"),"description","Recording format (default webm)"),
                    "storage_path",    Map.of("type","string","description","S3/GCS destination path prefix"),
                    "notify_participants", Map.of("type","boolean","description","Notify participants recording state changed (GDPR compliance)")
                ),
                "required", List.of("conference_id","action")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String confId = require(args, "conference_id");
        String action = require(args, "action");
        String format = getString(args, "output_format", "webm");
        String path   = getString(args, "storage_path", "s3://ecoskiller-recordings/auto/");
        boolean notify = getBool(args, "notify_participants", true);

        // Validate S3/GCS path format
        if (!path.startsWith("s3://") && !path.startsWith("gs://"))
            throw new IllegalArgumentException("storage_path must start with s3:// or gs://");

        String recordingId = "rec-" + Integer.toHexString(confId.hashCode()).substring(0,8);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",           "recording_" + action + "ed");
        r.put("conference_id",    confId);
        r.put("recording_id",     recordingId);
        r.put("action",           action);
        r.put("output_format",    format);
        r.put("storage_path",     path + confId + "-" + Instant.now().getEpochSecond() + "." + format);
        r.put("encryption",       "SSE-KMS (AWS KMS customer-managed key)");
        r.put("participants_notified", notify);
        r.put("jibri_pod",        "jibri-0");
        r.put("kafka_event",      Map.of(
            "topic",      "assessment.recordings",
            "event_type", "recording_" + action + "ed"
        ));
        r.put("timestamp", Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 9. recording_status
// ═════════════════════════════════════════════════════════════════════════════
class RecordingStatusTool implements ToolHandler {
    @Override public String getName() { return "recording_status"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Check recording status and retrieve S3/GCS path for completed recordings.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "recording_id",  Map.of("type","string","description","Recording ID from recording_control"),
                    "conference_id", Map.of("type","string","description","Conference ID (alternative lookup)")
                )
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String recId   = getString(args, "recording_id", null);
        String confId  = getString(args, "conference_id", null);

        if (recId == null && confId == null)
            throw new IllegalArgumentException("Provide recording_id or conference_id");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("recording_id",        recId != null ? recId : "rec-" + confId);
        r.put("status",              "completed");
        r.put("duration_seconds",    2700);
        r.put("file_size_mb",        850);
        r.put("codec",               "VP8");
        r.put("resolution",          "720p");
        r.put("recording_path",      "s3://ecoskiller-recordings/2026/03/" + (confId != null ? confId : recId) + ".webm");
        r.put("encryption",          "SSE-KMS");
        r.put("checksum_sha256",     "a3f8b2...truncated");
        r.put("iam_access_roles",    List.of("ai-scoring-service", "archive-service"));
        r.put("retention_delete_at", Instant.now().plusSeconds(90L * 24 * 3600).toString());
        r.put("queried_at",          Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 10. bridge_health
// ═════════════════════════════════════════════════════════════════════════════
class BridgeHealthTool implements ToolHandler {
    @Override public String getName() { return "bridge_health"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Query health and Prometheus metrics for all Jitsi Video Bridge nodes. " +
                           "Returns conference count, CPU, memory, packet loss, and Jicofo/Jibri status.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "node",   Map.of("type","string","description","Specific bridge pod name, or 'all' for cluster overview"),
                    "metric", Map.of("type","string","enum",List.of("all","cpu","memory","conferences","packet_loss","bitrate"))
                )
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String node   = getString(args, "node", "all");
        String metric = getString(args, "metric", "all");

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            nodes.add(Map.of(
                "pod",            "jitsi-pod-" + i,
                "status",         i < 7 ? "healthy" : "degraded",
                "cpu_pct",        30 + i * 5,
                "memory_mb",      2048 + i * 256,
                "conferences",    8 + i,
                "participants",   (8 + i) * 4,
                "packet_loss_pct",i < 7 ? 0.5 + i * 0.1 : 12.3,
                "avg_bitrate_kbps",1200 + i * 80,
                "health_url",     "http://jitsi-pod-" + i + ".jitsi.default.svc:8080/about/health",
                "metrics_url",    "http://jitsi-pod-" + i + ".jitsi.default.svc:8888/metrics"
            ));
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cluster_status",  "operational");
        r.put("total_nodes",     8);
        r.put("healthy_nodes",   7);
        r.put("jicofo_status",   "healthy");
        r.put("jibri_status",    "healthy");
        r.put("prosody_status",  "healthy");
        r.put("total_conferences", 72);
        r.put("total_participants", 288);
        r.put("nodes",           "all".equals(node) ? nodes : nodes.subList(0, 1));
        r.put("metric_filter",   metric);
        r.put("prometheus_endpoint", "http://grafana.ecoskiller.local:3000");
        r.put("queried_at",      Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 11. bridge_scaling
// ═════════════════════════════════════════════════════════════════════════════
class BridgeScalingTool implements ToolHandler {
    @Override public String getName() { return "bridge_scaling"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Scale Jitsi Video Bridge deployment up or down via Kubernetes. " +
                           "HPA rules: scale up when CPU >70% for 3 min; scale down when CPU <40% for 5 min. " +
                           "Min replicas: 4, Max replicas: 20.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "action",    Map.of("type","string","enum",List.of("scale_up","scale_down","set_replicas","get_hpa_status")),
                    "replicas",  Map.of("type","integer","description","Target replica count (4-20)"),
                    "reason",    Map.of("type","string","description","Reason for manual scaling action")
                ),
                "required", List.of("action")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String action   = require(args, "action");
        int    replicas = getInt(args, "replicas", 8);

        if ("set_replicas".equals(action)) {
            if (replicas < 4 || replicas > 20)
                throw new IllegalArgumentException("Replicas must be 4-20 (HPA limits)");
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("action",          action);
        r.put("current_replicas", 8);
        r.put("target_replicas",  "set_replicas".equals(action) ? replicas : ("scale_up".equals(action) ? 10 : 6));
        r.put("min_replicas",    4);
        r.put("max_replicas",    20);
        r.put("hpa_trigger",     Map.of("scale_up_cpu_pct",70,"scale_down_cpu_pct",40));
        r.put("estimated_ready_seconds", 150);
        r.put("kubectl_command", "kubectl scale deployment jitsi --replicas=" + replicas);
        r.put("zero_downtime",   true);
        r.put("reason",          getString(args,"reason","manual"));
        r.put("initiated_at",    Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 12. kafka_event_emit
// ═════════════════════════════════════════════════════════════════════════════
class KafkaEventEmitTool implements ToolHandler {
    @Override public String getName() { return "kafka_event_emit"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Emit structured events to Kafka topics for downstream consumption by " +
                           "AI Scoring, Engagement Analytics, Report Generator, and Archive services. " +
                           "Supported topics: assessment.participants, assessment.speakers, assessment.recordings.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "topic",      Map.of("type","string","enum",List.of("assessment.participants","assessment.speakers","assessment.recordings")),
                    "event_type", Map.of("type","string","description","Event type (e.g. participant_joined, active_speaker_changed, recording_completed)"),
                    "payload",    Map.of("type","object","description","Event payload object"),
                    "assessment_id", Map.of("type","string","description","Associated assessment identifier")
                ),
                "required", List.of("topic","event_type","payload","assessment_id")
            )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args) {
        String topic        = require(args, "topic");
        String eventType    = require(args, "event_type");
        String assessmentId = require(args, "assessment_id");
        Map<String, Object> payload = (Map<String, Object>) args.getOrDefault("payload", Map.of());

        // Validate event type per topic
        Map<String, List<String>> validEvents = Map.of(
            "assessment.participants", List.of("participant_joined","participant_left","conference_started","conference_ended"),
            "assessment.speakers",     List.of("active_speaker_changed","speaker_timeout","mute_change"),
            "assessment.recordings",   List.of("recording_started","recording_paused","recording_resumed","recording_completed")
        );
        List<String> allowed = validEvents.getOrDefault(topic, List.of());
        if (!allowed.isEmpty() && !allowed.contains(eventType))
            throw new IllegalArgumentException("Invalid event_type '" + eventType + "' for topic '" + topic + "'");

        String messageKey = assessmentId + "-" + System.currentTimeMillis();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",         "emitted");
        r.put("topic",          topic);
        r.put("event_type",     eventType);
        r.put("assessment_id",  assessmentId);
        r.put("message_key",    messageKey);
        r.put("partition",      Math.abs(messageKey.hashCode()) % 12);
        r.put("payload_fields", payload.keySet());
        r.put("timestamp",      Instant.now().toString());
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 13. jwt_generate
// ═════════════════════════════════════════════════════════════════════════════
class JwtGenerateTool implements ToolHandler {
    @Override public String getName() { return "jwt_generate"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Generate a signed JWT token for Jitsi participant authentication. " +
                           "Uses HMAC-SHA256 (HS256). Claims include assessmentId, room, role, expiry. " +
                           "JWT secret is rotated every 30 days per security policy.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "assessment_id",   Map.of("type","string"),
                    "participant_id",  Map.of("type","string"),
                    "participant_name",Map.of("type","string"),
                    "email",           Map.of("type","string","description","Participant email for identity binding"),
                    "role",            Map.of("type","string","enum",List.of("candidate","interviewer","moderator")),
                    "is_moderator",    Map.of("type","boolean","description","Grant moderator privileges"),
                    "ttl_minutes",     Map.of("type","integer","description","Token TTL in minutes (default 120, max 480)")
                ),
                "required", List.of("assessment_id","participant_id","participant_name","role")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String assessmentId   = require(args, "assessment_id");
        String participantId  = require(args, "participant_id");
        String name           = require(args, "participant_name");
        String role           = require(args, "role");
        String email          = getString(args, "email", "");
        boolean isModerator   = getBool(args, "is_moderator", "moderator".equals(role));
        int ttl               = getInt(args, "ttl_minutes", 120);

        // Validate email if provided
        if (!email.isEmpty() && !email.matches("^[^@]+@[^@]+\\.[^@]+$"))
            throw new IllegalArgumentException("Invalid email format");

        if (ttl > 480) throw new IllegalArgumentException("ttl_minutes cannot exceed 480");

        long now    = Instant.now().getEpochSecond();
        long expiry = now + ttl * 60L;

        // Build claims (actual signing done by JwtValidator in production)
        Map<String, Object> header  = Map.of("typ","JWT","alg","HS256");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aud",         "jitsi");
        payload.put("iss",         "ecoskiller");
        payload.put("sub",         "assessor-service");
        payload.put("room",        "assessment-" + assessmentId);
        payload.put("name",        name);
        payload.put("email",       email);
        payload.put("iat",         now);
        payload.put("exp",         expiry);
        payload.put("moderator",   isModerator);
        payload.put("affiliation", isModerator ? "owner" : "member");
        payload.put("context", Map.of(
            "user", Map.of("id", participantId, "name", name, "email", email),
            "group", assessmentId
        ));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",          "generated");
        r.put("algorithm",       "HS256");
        r.put("token",           "<signed_jwt_header.payload.signature>");
        r.put("header",          header);
        r.put("claims",          payload);
        r.put("issued_at",       Instant.ofEpochSecond(now).toString());
        r.put("expires_at",      Instant.ofEpochSecond(expiry).toString());
        r.put("secret_rotation", "Every 30 days (next: 2026-04-20)");
        r.put("join_url",        "https://jitsi.ecoskiller.io/assessment-" + assessmentId + "?jwt=<token>");
        return r;
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 14. network_diagnostics
// ═════════════════════════════════════════════════════════════════════════════
class NetworkDiagnosticsTool implements ToolHandler {
    @Override public String getName() { return "network_diagnostics"; }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
            "name",        getName(),
            "description", "Run network diagnostics for WebRTC connectivity issues. " +
                           "Checks STUN/TURN server availability, ICE candidate gathering, " +
                           "JWT validity, Jicofo responsiveness, and Jibri health.",
            "inputSchema", Map.of(
                "type",       "object",
                "properties", Map.of(
                    "diagnostic_type", Map.of("type","string","enum",
                        List.of("stun_turn_check","ice_connectivity","jwt_validation","jicofo_health","jibri_health","full_check")),
                    "conference_id",   Map.of("type","string","description","Optional: scope diagnostics to a conference"),
                    "participant_id",  Map.of("type","string","description","Optional: scope to a specific participant's connection")
                ),
                "required", List.of("diagnostic_type")
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> args) {
        String diagType  = require(args, "diagnostic_type");
        String confId    = getString(args, "conference_id", null);
        String pid       = getString(args, "participant_id", null);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("diagnostic_type", diagType);
        if (confId != null) r.put("conference_id", confId);
        if (pid    != null) r.put("participant_id", pid);

        if ("stun_turn_check".equals(diagType) || "full_check".equals(diagType)) {
            r.put("stun", Map.of(
                "host",    "stun.ecoskiller.io:3478",
                "status",  "reachable",
                "latency_ms", 12
            ));
            r.put("turn", Map.of(
                "host",     "turn.ecoskiller.io:3478",
                "status",   "reachable",
                "transport", "udp+tcp",
                "auth",     "ephemeral_credentials_1h_ttl"
            ));
        }
        if ("ice_connectivity".equals(diagType) || "full_check".equals(diagType)) {
            r.put("ice_state",       "connected");
            r.put("selected_pair",   "host:192.168.1.x <-> srflx:203.0.x.x");
            r.put("candidate_types", List.of("host","srflx","relay"));
        }
        if ("jicofo_health".equals(diagType) || "full_check".equals(diagType)) {
            r.put("jicofo_status",   "healthy");
            r.put("jicofo_heap_mb",  512);
            r.put("xmpp_connected",  true);
            r.put("bridges_known",   8);
        }
        if ("jibri_health".equals(diagType) || "full_check".equals(diagType)) {
            r.put("jibri_status",    "healthy");
            r.put("jibri_pod",       "jibri-0");
            r.put("s3_writable",     true);
            r.put("disk_free_gb",    85.4);
        }
        if ("jwt_validation".equals(diagType) || "full_check".equals(diagType)) {
            r.put("jwt_secret_age_days",  15);
            r.put("jwt_next_rotation",    "2026-04-20");
            r.put("prosody_auth_enabled", true);
        }

        r.put("overall_health", "healthy");
        r.put("recommendations", List.of(
            "jitsi-pod-7 showing elevated packet loss (12.3%) - consider draining",
            "JWT secret rotation due in 15 days"
        ));
        r.put("runbook", "https://wiki.ecoskiller.io/runbooks/jitsi");
        r.put("checked_at", Instant.now().toString());
        return r;
    }
}
