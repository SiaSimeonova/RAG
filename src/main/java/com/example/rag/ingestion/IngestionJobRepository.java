package com.example.rag.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IngestionJobRepository {

    private final JdbcTemplate jdbc;

    public IngestionJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String create(String source) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO ingestion_jobs (id, source, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, source, IngestionJobStatus.QUEUED.name(), now, now
        );
        return id;
    }

    public void updateStatus(String id, IngestionJobStatus status, String errorMessage) {
        jdbc.update(
                "UPDATE ingestion_jobs SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status.name(), errorMessage, LocalDateTime.now(), id
        );
    }

    public Optional<IngestionJob> findById(String id) {
        List<IngestionJob> results = jdbc.query(
                "SELECT id, source, status, error_message, created_at, updated_at FROM ingestion_jobs WHERE id = ?",
                (rs, row) -> new IngestionJob(
                        rs.getString("id"),
                        rs.getString("source"),
                        IngestionJobStatus.valueOf(rs.getString("status")),
                        rs.getString("error_message"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ),
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
