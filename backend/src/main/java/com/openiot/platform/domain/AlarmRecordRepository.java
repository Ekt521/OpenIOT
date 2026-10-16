package com.openiot.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRecordRepository extends JpaRepository<AlarmRecord, String> {

  List<AlarmRecord> findAllByOrderByCreatedAtDesc();

  Optional<AlarmRecord> findFirstByRuleIdAndDeviceIdAndStatusNot(String ruleId, String deviceId, String status);
}
