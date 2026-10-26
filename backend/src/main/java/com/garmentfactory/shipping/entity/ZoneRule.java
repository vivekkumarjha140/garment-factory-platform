package com.garmentfactory.shipping.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "zone_rules")
public class ZoneRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false, updatable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rule_type", nullable = false, length = 20, updatable = false)
    private ZoneRuleType ruleType;

    @Column(name = "match_value", nullable = false, length = 64, updatable = false)
    private String matchValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ZoneRule() {
    }

    public ZoneRule(Long zoneId, ZoneRuleType ruleType, String matchValue) {
        this.zoneId = zoneId;
        this.ruleType = ruleType;
        this.matchValue = matchValue;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getZoneId() { return zoneId; }
    public ZoneRuleType getRuleType() { return ruleType; }
    public String getMatchValue() { return matchValue; }
    public Instant getCreatedAt() { return createdAt; }
}