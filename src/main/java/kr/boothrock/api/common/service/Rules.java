package kr.boothrock.api.common.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Rules {
    private Rules() {}
    public static void revision(long requested, Map<String, Object> row) {
        require(requested >= 0 && requested == ((Number) row.get("revision")).longValue(),
                "REVISION_CONFLICT", "Reload the latest version before saving.");
    }
    public static void require(boolean ok, String code, String message) {
        if (!ok) throw new ApiException(409, code, message);
    }
    public static void input(boolean ok, String message) {
        if (!ok) throw new ApiException(400, "VALIDATION_ERROR", message);
    }
    public static void page(int page, int size) {
        input(page >= 0 && size >= 1 && size <= 100, "page must be >= 0; size must be 1..100.");
    }
    public static void category(String code) { enumValue(code, "FOOD", "BEVERAGE", "EXPERIENCE", "GAME", "GOODS", "OTHER"); }
    public static void enumValue(String value, String... allowed) {
        input(value != null && Arrays.asList(allowed).contains(value), "Unsupported value: " + value);
    }
    public static String text(String value) { return value == null ? "" : value; }
    public static String like(String value) {
        String q = text(value);
        input(q.length() <= 100, "Search text must be at most 100 characters.");
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
    public static Map<String, Object> result(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Expected key/value pairs");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
