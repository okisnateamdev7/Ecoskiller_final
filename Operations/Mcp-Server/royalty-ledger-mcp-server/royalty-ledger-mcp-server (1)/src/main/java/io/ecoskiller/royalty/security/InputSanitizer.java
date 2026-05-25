package io.ecoskiller.royalty.security;

import java.util.Arrays;
import java.util.regex.Pattern;

public final class InputSanitizer {

    private static final Pattern ID_PATTERN      = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9\\-_]{0,63}$");
    private static final Pattern UUID_PATTERN    = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IP_TYPE_PATTERN = Pattern.compile("^(problem|interview_question|discussion_scenario|idea|premium_content)$");
    private static final Pattern HASH_PATTERN    = Pattern.compile("^(sha256:|sha512:|md5:)[a-fA-F0-9]{32,128}$");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^(creator_balance|platform_earnings|tax_withholding|disputed_balance|payout_pending|payout_completed|refund)$");
    private static final Pattern CREATOR_TYPE    = Pattern.compile("^(individual|foreign|corporate|nonprofit|government)$");
    private static final Pattern TIER_PATTERN    = Pattern.compile("^(tier1|tier2|tier3)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN    = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private InputSanitizer() {}

    public static void validateId(String id, String field) {
        if (id == null || (!ID_PATTERN.matcher(id).matches() && !UUID_PATTERN.matcher(id).matches()))
            throw new SecurityException("Invalid " + field + " format: " + sanitizeForLog(id));
    }

    public static void validateIpType(String type) {
        if (type == null || !IP_TYPE_PATTERN.matcher(type).matches())
            throw new SecurityException("Invalid ip_type '" + sanitizeForLog(type) + "'. Allowed: problem|interview_question|discussion_scenario|idea|premium_content");
    }

    public static void validateContentHash(String hash) {
        if (hash == null || !HASH_PATTERN.matcher(hash).matches())
            throw new SecurityException("Invalid content_hash format. Expected sha256:/sha512: prefix + hex digest");
    }

    public static void validateAccountType(String type) {
        if (type == null || !ACCOUNT_PATTERN.matcher(type).matches())
            throw new SecurityException("Invalid account_type '" + sanitizeForLog(type) + "'");
    }

    public static void validateCreatorType(String type) {
        if (type == null || !CREATOR_TYPE.matcher(type).matches())
            throw new SecurityException("Invalid creator_type '" + sanitizeForLog(type) + "'. Allowed: individual|foreign|corporate|nonprofit|government");
    }

    public static void validateTier(String tier) {
        if (tier == null || !TIER_PATTERN.matcher(tier).matches())
            throw new SecurityException("Invalid tier '" + sanitizeForLog(tier) + "'. Allowed: tier1|tier2|tier3");
    }

    public static void validateDate(String date, String field) {
        if (date != null && !date.isBlank() && !DATE_PATTERN.matcher(date).matches())
            throw new IllegalArgumentException(field + " must be YYYY-MM-DD format, got: " + sanitizeForLog(date));
    }

    public static void validatePositiveAmount(double amount, String field) {
        if (amount < 0 || amount > 100_000_000)
            throw new IllegalArgumentException(field + " must be between 0 and ₹10 crore, got: " + amount);
    }

    public static void validateSplitPercentage(double pct, String field) {
        if (pct <= 0 || pct > 100)
            throw new IllegalArgumentException(field + " must be 0-100%, got: " + pct);
    }

    public static void validateRange(int v, int min, int max, String field) {
        if (v < min || v > max) throw new IllegalArgumentException(field + " must be " + min + "-" + max + ", got: " + v);
    }

    public static void requireNonBlank(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Required field '" + field + "' is missing");
    }

    public static void validateEnum(String v, String field, String... allowed) {
        for (String a : allowed) if (a.equalsIgnoreCase(v)) return;
        throw new IllegalArgumentException("Invalid '" + field + "': " + sanitizeForLog(v) + ". Allowed: " + Arrays.toString(allowed));
    }

    public static boolean isValidToolName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 100 && name.matches("^[a-z_]+$");
    }

    /** Sanitize free-text: strip control chars, truncate, HTML-escape. */
    public static String sanitizeText(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() > maxLen) text = text.substring(0, maxLen);
        return text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
            .replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", "");
    }

    public static String sanitizeForLog(String v) {
        if (v == null) return "<null>";
        return v.replaceAll("[\\r\\n\\t]", " ").substring(0, Math.min(v.length(), 80));
    }
}
