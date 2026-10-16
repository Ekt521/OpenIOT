package com.openiot.platform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryRecordRepository extends JpaRepository<TelemetryRecord, String> {

  List<TelemetryRecord> findTop240ByDeviceIdOrderByRecordedAtDesc(String deviceId);

  long countByDeviceId(String deviceId);

  void deleteByDeviceIdAndRecordedAtLessThan(String deviceId, java.time.Instant cutoff);
}
