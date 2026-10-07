package kr.boothrock.api.common.repository;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.boothrock.api.common.service.ApiException;
import kr.boothrock.api.common.service.Rules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Repository;

@Repository
public class SqlStore {
    private final JdbcTemplate jdbc;
    public SqlStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.query(sql, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String key = rs.getMetaData().getColumnLabel(i);
                String property = key.contains("_") ? JdbcUtils.convertUnderscoreNameToPropertyName(key) : key;
                row.put(Character.toLowerCase(property.charAt(0)) + property.substring(1), value(rs.getObject(i)));
            }
            return row;
        }, parameters(args));
    }
    public Map<String, Object> optional(String sql, Object... args) {
        List<Map<String, Object>> rows = list(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public Map<String, Object> one(String sql, Object... args) {
        Map<String, Object> row = optional(sql, args);
        if (row == null) throw ApiException.notFound();
        return row;
    }
    public int update(String sql, Object... args) { return jdbc.update(sql, parameters(args)); }
    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, parameters(args));
        return value == null ? 0 : value;
    }
    public Map<String, Object> page(String selectSql, String countSql, int page, int size, Object... args) {
        Rules.page(page, size);
        long total = count(countSql, args);
        List<Object> values = new ArrayList<>(Arrays.asList(args));
        values.add(size);
        values.add((long) page * size);
        return Rules.result("content", list(selectSql + " LIMIT ? OFFSET ?", values.toArray()),
                "page", page, "size", size, "totalElements", total, "totalPages", (total + size - 1) / size);
    }
    public static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
    public static long revision(Map<String, Object> row) { return ((Number) row.get("revision")).longValue(); }
    private Object[] parameters(Object[] args) {
        return Arrays.stream(args).map(value -> {
            if (value instanceof Instant time) return Timestamp.from(time);
            if (value instanceof OffsetDateTime time) return Timestamp.from(time.toInstant());
            if (value instanceof Enum<?> code) return code.name();
            return value;
        }).toArray();
    }
    private Object value(Object value) throws SQLException {
        if (value instanceof Timestamp time) return time.toInstant();
        if (value instanceof OffsetDateTime time) return time.toInstant();
        if (value instanceof java.sql.Array array) {
            try { return Arrays.asList((Object[]) array.getArray()); }
            finally { array.free(); }
        }
        return value;
    }
}
