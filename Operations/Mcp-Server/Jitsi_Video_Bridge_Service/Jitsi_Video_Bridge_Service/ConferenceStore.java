package io.ecoskiller.mcp.jitsi;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conference registry.
 *
 * Tracks active conferences and their participants.
 * In production this would be backed by Redis for multi-pod consistency.
 */
public class ConferenceStore {

    private static final ConcurrentHashMap<String, Map<String, Object>> STORE = new ConcurrentHashMap<>();

    public static void create(String confId, String assessmentId,
                              String assessmentType,
                              List<Map<String, Object>> participants) {
        Map<String, Object> entry = new ConcurrentHashMap<>();
        entry.put("conference_id",    confId);
        entry.put("assessment_id",    assessmentId);
        entry.put("assessment_type",  assessmentType);
        entry.put("status",           "active");
        entry.put("participant_count", participants.size());
        entry.put("created_epoch",    System.currentTimeMillis());

        // Store participant IDs
        List<String> pids = new ArrayList<>();
        for (Map<String, Object> p : participants) pids.add((String) p.get("id"));
        entry.put("participant_ids",  pids);

        STORE.put(confId, entry);
    }

    public static Map<String, Object> get(String confId) {
        return STORE.get(confId);
    }

    /** @return true if conference existed and was terminated */
    public static boolean terminate(String confId) {
        Map<String, Object> conf = STORE.remove(confId);
        return conf != null;
    }

    public static void addParticipant(String confId, String participantId,
                                      String name, String role) {
        Map<String, Object> conf = STORE.get(confId);
        if (conf == null) return; // best-effort; conference might be on another pod
        @SuppressWarnings("unchecked")
        List<String> pids = (List<String>) conf.get("participant_ids");
        if (pids != null && !pids.contains(participantId)) {
            pids.add(participantId);
            conf.put("participant_count", pids.size());
        }
    }

    public static void removeParticipant(String confId, String participantId) {
        Map<String, Object> conf = STORE.get(confId);
        if (conf == null) return;
        @SuppressWarnings("unchecked")
        List<String> pids = (List<String>) conf.get("participant_ids");
        if (pids != null) {
            pids.remove(participantId);
            conf.put("participant_count", pids.size());
        }
    }

    public static int size() { return STORE.size(); }
}
