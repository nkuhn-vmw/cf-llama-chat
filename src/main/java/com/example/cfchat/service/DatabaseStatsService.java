package com.example.cfchat.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Service
@Slf4j
public class DatabaseStatsService {

    @PersistenceContext
    private EntityManager entityManager;

    private volatile Boolean postgresDetected = null;

    @Transactional(readOnly = true)
    public boolean isPostgres() {
        if (postgresDetected != null) {
            return postgresDetected;
        }
        try {
            Object result = entityManager.createNativeQuery("SELECT version()").getSingleResult();
            String version = result != null ? result.toString().toLowerCase() : "";
            postgresDetected = version.contains("postgresql");
            log.info("Database type detected: {}", postgresDetected ? "PostgreSQL" : "Non-PostgreSQL (" + result + ")");
            return postgresDetected;
        } catch (Exception e) {
            postgresDetected = false;
            log.info("Database type detected: H2/other (not PostgreSQL)");
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTableStats() {
        List<Map<String, Object>> stats = new ArrayList<>();

        if (isPostgres()) {
            return getPostgresTableStats();
        } else {
            return getH2TableStats();
        }
    }

    private List<Map<String, Object>> getPostgresTableStats() {
        List<Map<String, Object>> stats = new ArrayList<>();
        try {
            // Get table sizes and row counts for PostgreSQL
            String sql = """
                SELECT
                    schemaname,
                    relname as table_name,
                    n_live_tup as row_count,
                    pg_size_pretty(pg_total_relation_size(relid)) as total_size,
                    pg_total_relation_size(relid) as size_bytes,
                    pg_size_pretty(pg_indexes_size(relid)) as index_size,
                    n_tup_ins as inserts,
                    n_tup_upd as updates,
                    n_tup_del as deletes,
                    last_vacuum,
                    last_autovacuum,
                    last_analyze
                FROM pg_stat_user_tables
                ORDER BY pg_total_relation_size(relid) DESC
                """;

            Query query = entityManager.createNativeQuery(sql);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            for (Object[] row : results) {
                Map<String, Object> tableInfo = new LinkedHashMap<>();
                tableInfo.put("schema", row[0]);
                tableInfo.put("tableName", row[1]);
                tableInfo.put("rowCount", toLong(row[2]));
                tableInfo.put("totalSize", row[3]);
                tableInfo.put("sizeBytes", toLong(row[4]));
                tableInfo.put("indexSize", row[5]);
                tableInfo.put("inserts", toLong(row[6]));
                tableInfo.put("updates", toLong(row[7]));
                tableInfo.put("deletes", toLong(row[8]));
                tableInfo.put("lastVacuum", row[9]);
                tableInfo.put("lastAutovacuum", row[10]);
                tableInfo.put("lastAnalyze", row[11]);
                stats.add(tableInfo);
            }
        } catch (Exception e) {
            log.error("Error getting PostgreSQL table stats", e);
        }
        return stats;
    }

    private List<Map<String, Object>> getH2TableStats() {
        List<Map<String, Object>> stats = new ArrayList<>();
        try {
            // For H2, get basic table information
            String sql = """
                SELECT
                    TABLE_SCHEMA,
                    TABLE_NAME,
                    ROW_COUNT_ESTIMATE
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC'
                ORDER BY ROW_COUNT_ESTIMATE DESC
                """;

            Query query = entityManager.createNativeQuery(sql);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            for (Object[] row : results) {
                Map<String, Object> tableInfo = new LinkedHashMap<>();
                tableInfo.put("schema", row[0]);
                tableInfo.put("tableName", row[1]);
                tableInfo.put("rowCount", toLong(row[2]));
                tableInfo.put("totalSize", "N/A (H2)");
                tableInfo.put("sizeBytes", 0L);
                tableInfo.put("indexSize", "N/A");
                tableInfo.put("inserts", 0L);
                tableInfo.put("updates", 0L);
                tableInfo.put("deletes", 0L);
                tableInfo.put("lastVacuum", null);
                tableInfo.put("lastAutovacuum", null);
                tableInfo.put("lastAnalyze", null);
                stats.add(tableInfo);
            }
        } catch (Exception e) {
            log.error("Error getting H2 table stats", e);
        }
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDatabaseOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        if (isPostgres()) {
            return getPostgresDatabaseOverview();
        } else {
            return getH2DatabaseOverview();
        }
    }

    private Map<String, Object> getPostgresDatabaseOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        try {
            // Database size
            Query sizeQuery = entityManager.createNativeQuery("SELECT pg_size_pretty(pg_database_size(current_database()))");
            overview.put("databaseSize", sizeQuery.getSingleResult());

            // Database name
            Query nameQuery = entityManager.createNativeQuery("SELECT current_database()");
            overview.put("databaseName", nameQuery.getSingleResult());

            // PostgreSQL version
            Query versionQuery = entityManager.createNativeQuery("SELECT version()");
            overview.put("version", versionQuery.getSingleResult());

            // Connection count
            Query connQuery = entityManager.createNativeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()");
            overview.put("activeConnections", toLong(connQuery.getSingleResult()));

            // Table count
            Query tableCountQuery = entityManager.createNativeQuery("SELECT count(*) FROM pg_stat_user_tables");
            overview.put("tableCount", toLong(tableCountQuery.getSingleResult()));

            // Total rows
            Query totalRowsQuery = entityManager.createNativeQuery("SELECT SUM(n_live_tup) FROM pg_stat_user_tables");
            Object totalRows = totalRowsQuery.getSingleResult();
            overview.put("totalRows", totalRows != null ? toLong(totalRows) : 0L);

            overview.put("databaseType", "PostgreSQL");
        } catch (Exception e) {
            log.error("Error getting PostgreSQL overview", e);
            overview.put("error", e.getMessage());
        }
        return overview;
    }

    private Map<String, Object> getH2DatabaseOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        try {
            overview.put("databaseSize", "In-Memory");
            overview.put("databaseName", "H2 Database");

            // H2 version
            Query versionQuery = entityManager.createNativeQuery("SELECT H2VERSION()");
            overview.put("version", "H2 " + versionQuery.getSingleResult());

            // Table count
            Query tableCountQuery = entityManager.createNativeQuery(
                "SELECT count(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'"
            );
            overview.put("tableCount", toLong(tableCountQuery.getSingleResult()));

            overview.put("activeConnections", 1L);
            overview.put("totalRows", 0L);
            overview.put("databaseType", "H2 (Development)");
        } catch (Exception e) {
            log.error("Error getting H2 overview", e);
            overview.put("error", e.getMessage());
        }
        return overview;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSlowQueries() {
        List<Map<String, Object>> queries = new ArrayList<>();

        if (!isPostgres()) {
            return queries; // H2 doesn't have pg_stat_statements
        }

        try {
            // Check if pg_stat_statements extension is available
            String checkSql = "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')";
            Query checkQuery = entityManager.createNativeQuery(checkSql);
            Boolean hasExtension = (Boolean) checkQuery.getSingleResult();

            if (!hasExtension) {
                Map<String, Object> notice = new LinkedHashMap<>();
                notice.put("notice", "pg_stat_statements extension not installed");
                queries.add(notice);
                return queries;
            }

            String sql = """
                SELECT
                    queryid,
                    query,
                    calls,
                    total_exec_time,
                    mean_exec_time,
                    rows
                FROM pg_stat_statements
                WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
                ORDER BY total_exec_time DESC
                LIMIT 20
                """;

            Query query = entityManager.createNativeQuery(sql);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            for (Object[] row : results) {
                Map<String, Object> queryInfo = new LinkedHashMap<>();
                queryInfo.put("queryId", row[0]);
                queryInfo.put("query", truncateQuery((String) row[1]));
                queryInfo.put("calls", toLong(row[2]));
                queryInfo.put("totalTimeMs", toDouble(row[3]));
                queryInfo.put("meanTimeMs", toDouble(row[4]));
                queryInfo.put("rows", toLong(row[5]));
                queries.add(queryInfo);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve slow queries: {}", e.getMessage());
        }
        return queries;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getIndexStats() {
        List<Map<String, Object>> indexes = new ArrayList<>();

        if (!isPostgres()) {
            return indexes;
        }

        try {
            String sql = """
                SELECT
                    schemaname,
                    relname as table_name,
                    indexrelname as index_name,
                    idx_scan as scans,
                    idx_tup_read as tuples_read,
                    idx_tup_fetch as tuples_fetched,
                    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
                FROM pg_stat_user_indexes
                ORDER BY idx_scan DESC
                LIMIT 30
                """;

            Query query = entityManager.createNativeQuery(sql);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            for (Object[] row : results) {
                Map<String, Object> indexInfo = new LinkedHashMap<>();
                indexInfo.put("schema", row[0]);
                indexInfo.put("tableName", row[1]);
                indexInfo.put("indexName", row[2]);
                indexInfo.put("scans", toLong(row[3]));
                indexInfo.put("tuplesRead", toLong(row[4]));
                indexInfo.put("tuplesFetched", toLong(row[5]));
                indexInfo.put("indexSize", row[6]);
                indexes.add(indexInfo);
            }
        } catch (Exception e) {
            log.error("Error getting index stats", e);
        }
        return indexes;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveConnections() {
        List<Map<String, Object>> connections = new ArrayList<>();

        if (!isPostgres()) {
            return connections;
        }

        try {
            String sql = """
                SELECT
                    pid,
                    usename,
                    application_name,
                    client_addr,
                    state,
                    query_start,
                    state_change,
                    LEFT(query, 100) as query_preview
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND pid != pg_backend_pid()
                ORDER BY query_start DESC NULLS LAST
                LIMIT 20
                """;

            Query query = entityManager.createNativeQuery(sql);
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            for (Object[] row : results) {
                Map<String, Object> connInfo = new LinkedHashMap<>();
                connInfo.put("pid", row[0]);
                connInfo.put("username", row[1]);
                connInfo.put("applicationName", row[2]);
                connInfo.put("clientAddr", row[3] != null ? row[3].toString() : "local");
                connInfo.put("state", row[4]);
                connInfo.put("queryStart", row[5]);
                connInfo.put("stateChange", row[6]);
                connInfo.put("queryPreview", row[7]);
                connections.add(connInfo);
            }
        } catch (Exception e) {
            log.error("Error getting active connections", e);
        }
        return connections;
    }

    private Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof BigInteger) return ((BigInteger) value).longValue();
        if (value instanceof BigDecimal) return ((BigDecimal) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof BigDecimal) return ((BigDecimal) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private String truncateQuery(String query) {
        if (query == null) return "";
        // Remove excessive whitespace and truncate
        String cleaned = query.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned;
    }
}
