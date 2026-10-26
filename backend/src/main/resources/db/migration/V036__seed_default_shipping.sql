-- SAMPLE DATA. Replace charges, thresholds and coverage with real courier rates before go-live.

INSERT INTO delivery_zones (code, name, free_shipping_above, active, created_at, updated_at) VALUES
                                                                                                 ('NCR',           'Delhi NCR',     1999.00, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                                                                                                 ('REST_OF_INDIA', 'Rest of India', 2999.00, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Longest matching PIN prefix wins, so the 3-digit NCR prefixes beat the 1-digit national ones.
INSERT INTO zone_rules (zone_id, rule_type, match_value, created_at)
SELECT z.id, 'PIN_PREFIX', p.prefix, UTC_TIMESTAMP(6)
FROM delivery_zones z
         CROSS JOIN (SELECT '110' AS prefix UNION ALL SELECT '121' UNION ALL SELECT '122' UNION ALL SELECT '201') p
WHERE z.code = 'NCR';

INSERT INTO zone_rules (zone_id, rule_type, match_value, created_at)
SELECT z.id, 'PIN_PREFIX', p.prefix, UTC_TIMESTAMP(6)
FROM delivery_zones z
         CROSS JOIN (SELECT '1' AS prefix UNION ALL SELECT '2' UNION ALL SELECT '3' UNION ALL SELECT '4'
                     UNION ALL SELECT '5' UNION ALL SELECT '6' UNION ALL SELECT '7' UNION ALL SELECT '8') p
WHERE z.code = 'REST_OF_INDIA';

-- Weight slabs are [min, max) in grams.
INSERT INTO shipping_rates (zone_id, min_weight_grams, max_weight_grams, charge,
                            estimated_days_min, estimated_days_max, active, created_at, updated_at)
SELECT z.id, s.min_g, s.max_g, s.charge, 1, 3, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM delivery_zones z
         CROSS JOIN (SELECT 0 AS min_g, 500 AS max_g, 50.00 AS charge
                     UNION ALL SELECT 500, 1000, 70.00
                     UNION ALL SELECT 1000, 2000, 100.00
                     UNION ALL SELECT 2000, 5000, 160.00
                     UNION ALL SELECT 5000, 20000, 320.00) s
WHERE z.code = 'NCR';

INSERT INTO shipping_rates (zone_id, min_weight_grams, max_weight_grams, charge,
                            estimated_days_min, estimated_days_max, active, created_at, updated_at)
SELECT z.id, s.min_g, s.max_g, s.charge, 3, 7, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM delivery_zones z
         CROSS JOIN (SELECT 0 AS min_g, 500 AS max_g, 80.00 AS charge
                     UNION ALL SELECT 500, 1000, 110.00
                     UNION ALL SELECT 1000, 2000, 160.00
                     UNION ALL SELECT 2000, 5000, 260.00
                     UNION ALL SELECT 5000, 20000, 520.00) s
WHERE z.code = 'REST_OF_INDIA';