package com.openiot.platform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, String> {

  List<DeviceEvent> findTop30ByDeviceIdOrderByOccurredAtDesc(String deviceId);

  void deleteByDeviceIdAndOccurredAtLessThan(String deviceId, java.time.Instant cutoff);
}
