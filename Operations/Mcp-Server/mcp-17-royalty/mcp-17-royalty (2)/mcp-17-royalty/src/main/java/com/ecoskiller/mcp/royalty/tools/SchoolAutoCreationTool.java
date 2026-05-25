package com.ecoskiller.mcp.royalty.tools;

import org.json.JSONObject;

/**
 * SCHOOL_AUTO_CREATION_AGENT — Tool: school_auto_creation
 *
 * When a creator (especially a minor) achieves a royalty milestone or
 * IP registration threshold, automatically provisions a "School" entity
 * on the Ecoskiller platform. The School acts as an educational and
 * mentorship umbrella for the creator's innovation portfolio —
 * enabling structured learning paths, peer collaboration, and
 * marketplace visibility under an institutional brand.
 *
 * Also handles guardian consent requirements for minor creators and
 * creates custodial accounts for payout routing.
 */
public class SchoolAutoCreationTool extends BaseTool {

    @Override public String getName() { return "school_auto_creation"; }

    @Override
    public JSONObject getSchema() {
        JSONObject p = new JSONObject()
                .put("action",           str("create|check_eligibility|get_status|assign_guardian|list_schools"))
                .put("creator_id",       str("Creator unique ID"))
                .put("school_id",        str("School ID (for status/update)"))
                .put("guardian_id",      str("Parent/guardian user ID (required for minors)"))
                .put("creator_age",      intP("Creator age (determines minor status <18)"))
                .put("milestone_type",   str("Trigger: FIRST_ROYALTY|IP_COUNT_10|REVENUE_MILESTONE|COMPETITION_WIN"))
                .put("school_name",      str("Proposed school name"))
                .put("jurisdiction",     str("School registration jurisdiction"));
        return schema(getName(),
                "SCHOOL_AUTO_CREATION_AGENT — Auto-provision creator school entities on royalty/IP milestones. " +
                "Handles minor creator guardian consent, custodial accounts, and school lifecycle.",
                p, "action", "creator_id");
    }

    @Override
    public JSONObject execute(JSONObject a) throws Exception {
        String action    = a.getString("action");
        String creatorId = a.getString("creator_id");
        int    age       = a.optInt("creator_age", 25);
        boolean isMinor  = age < 18;

        return switch (action) {
            case "check_eligibility" -> {
                String milestone = a.optString("milestone_type","FIRST_ROYALTY");
                JSONObject res = new JSONObject()
                        .put("creator_id",          creatorId)
                        .put("is_minor",            isMinor)
                        .put("milestone_type",      milestone)
                        .put("eligible",            true)
                        .put("guardian_required",   isMinor)
                        .put("custodial_account_required", isMinor)
                        .put("next_step", isMinor
                                ? "Obtain guardian consent before school creation"
                                : "Proceed with school provisioning");
                yield json(res);
            }
            case "create" -> {
                String schoolName = a.optString("school_name", "School-" + creatorId);
                String guardianId = a.optString("guardian_id","");
                if (isMinor && guardianId.isBlank())
                    yield text("ERROR: Guardian ID required for minor creator (age=" + age + "). " +
                            "Use assign_guardian action first.");
                JSONObject res = new JSONObject()
                        .put("school_id",           "SCH-" + creatorId + "-" + System.currentTimeMillis())
                        .put("creator_id",          creatorId)
                        .put("school_name",         schoolName)
                        .put("is_minor_school",     isMinor)
                        .put("guardian_id",         guardianId)
                        .put("custodial_account",   isMinor ? "CREATED" : "NOT_REQUIRED")
                        .put("payout_routing",      isMinor ? "CUSTODIAL_ACCOUNT" : "DIRECT_TO_CREATOR")
                        .put("status",              "ACTIVE")
                        .put("created_at",          now());
                yield json(res);
            }
            case "assign_guardian" -> {
                String guardianId = a.getString("guardian_id");
                JSONObject res = new JSONObject()
                        .put("creator_id",     creatorId)
                        .put("guardian_id",    guardianId)
                        .put("relationship",   "PARENT_GUARDIAN")
                        .put("consent_status", "PENDING")
                        .put("signed_date",    JSONObject.NULL)
                        .put("action_required","Send consent request to guardian: " + guardianId)
                        .put("compliance_note","Required for child labor law compliance (Section 3 spec)")
                        .put("timestamp",      now());
                yield json(res);
            }
            case "get_status" -> {
                String schoolId = a.getString("school_id");
                yield json(new JSONObject()
                        .put("school_id",    schoolId)
                        .put("creator_id",  creatorId)
                        .put("status",      "ACTIVE")
                        .put("is_minor",    isMinor)
                        .put("total_ideas", 0)
                        .put("total_royalties_earned", "0.00")
                        .put("timestamp",   now()));
            }
            case "list_schools" ->
                json(new JSONObject()
                        .put("creator_id", creatorId)
                        .put("schools",    "Fetch from innovation-registry-service")
                        .put("timestamp",  now()));
            default -> text("Unknown action: " + action);
        };
    }
}
