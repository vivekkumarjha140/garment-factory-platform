package com.garmentfactory.shared.numbering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-free counters backed by the number_sequences table. The row is locked until the caller's
 * transaction ends, so a rollback hands the number back and two callers can never get the same one.
 */
@Component
public class NumberSequences {

    private final JdbcTemplate jdbc;

    public NumberSequences(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String key) {
        jdbc.update("INSERT INTO number_sequences (seq_key, next_value) VALUES (?, 1) "
                + "ON DUPLICATE KEY UPDATE seq_key = seq_key", key);
        Long value = jdbc.queryForObject(
                "SELECT next_value FROM number_sequences WHERE seq_key = ? FOR UPDATE", Long.class, key);
        jdbc.update("UPDATE number_sequences SET next_value = next_value + 1 WHERE seq_key = ?", key);
        return value;
    }
}