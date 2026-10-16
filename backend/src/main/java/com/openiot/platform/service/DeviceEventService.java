package com.openiot.platform.service;

import com.openiot.platform.domain.DeviceEvent;
import com.openiot.platform.domain.DeviceEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 记录设备关键事件，并只保留最近 30 条。 */
@Service
public class DeviceEventService {

  private static final int MAX_EVENTS_PER_DEVICE = 30;

  private final DeviceEventRepository events;

  public DeviceEventService(DeviceEventRepository events) {
    this.events = events;
  }

  @Transactional
  public void record(String deviceId, String type, String message) {
    DeviceEvent event = new DeviceEvent();
    event.setId(UUID.randomUUID().toString());
    event.setDeviceId(deviceId);
    event.setType(type);
    event.setMessage(message);
    event.setOccurredAt(Instant.now());
    events.save(event);

    List<DeviceEvent> recent = events.findTop30ByDeviceIdOrderByOccurredAtDesc(deviceId);
    if (recent.size() >= MAX_EVENTS_PER_DEVICE) {
      Instant oldestKept = recent.get(MAX_EVENTS_PER_DEVICE - 1).getOccurredAt();
      events.deleteByDeviceIdAndOccurredAtLessThan(deviceId, oldestKept);
    }
  }
}
