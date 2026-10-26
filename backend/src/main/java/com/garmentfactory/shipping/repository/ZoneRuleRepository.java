package com.garmentfactory.shipping.repository;

import com.garmentfactory.shipping.entity.ZoneRule;
import com.garmentfactory.shipping.entity.ZoneRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ZoneRuleRepository extends JpaRepository<ZoneRule, Long> {

    Optional<ZoneRule> findByRuleTypeAndMatchValue(ZoneRuleType ruleType, String matchValue);

    /** For PIN lookup: pass prefixes of a PIN (e.g. "122001","12200","1220","122","12","1"). */
    List<ZoneRule> findByRuleTypeAndMatchValueIn(ZoneRuleType ruleType, Collection<String> matchValues);

    List<ZoneRule> findByZoneId(Long zoneId);
}