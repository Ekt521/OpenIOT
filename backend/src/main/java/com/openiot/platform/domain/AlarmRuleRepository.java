package com.openiot.platform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRuleRepository extends JpaRepository<AlarmRule, String> {

  List<AlarmRule> findAllByOrderByNameAsc();

  List<AlarmRule> findByEnabledTrue();
}
