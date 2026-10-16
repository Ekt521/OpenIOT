package com.openiot.platform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandRecordRepository extends JpaRepository<CommandRecord, String> {

  List<CommandRecord> findTop100ByOrderByCreatedAtDesc();

  List<CommandRecord> findTop6ByDeviceIdOrderByCreatedAtDesc(String deviceId);

  List<CommandRecord> findByStatus(String status);
}
