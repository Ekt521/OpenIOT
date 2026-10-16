package com.openiot.platform.service;

import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.domain.TelemetryRecord;
import com.openiot.platform.domain.TelemetryRecordRepository;
import com.openiot.platform.support.JsonCodec;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 处理设备上报的遥测与状态，并驱动告警判定。 */
@Service
public class TelemetryService {

  private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);
  private static final int MAX_HISTORY_PER_DEVICE = 240;

  private final DeviceRepository devices;
  private final TelemetryRecordRepository telemetry;
  private final AlarmService alarmService;
  private final DeviceEventService deviceEvents;

  public TelemetryService(
      DeviceRepository devices,
      TelemetryRecordRepository telemetry,
      AlarmService alarmService,
      DeviceEventService deviceEvents) {
    this.devices = devices;
    this.telemetry = telemetry;
    this.alarmService = alarmService;
    this.deviceEvents = deviceEvents;
  }

  @Transactional
  public void onTelemetry(String deviceCode, Map<String, Object> reportedState, String timestamp) {
    Device device = devices.findByCode(deviceCode).orElse(null);
    if (device == null) {
      log.warn("收到未注册设备 {} 的上报，已忽略。请先在平台创建设备。", deviceCode);
      return;
    }
    if (reportedState == null || reportedState.isEmpty()) {
      return;
    }

    boolean wasOnline = device.isOnline();
    Map<String, Object> merged = new LinkedHashMap<>(device.getState());
    merged.putAll(reportedState);
    device.setState(merged);
    device.setLastSeen(parseInstant(timestamp));
    device.setOnline(device.isEnabled());
    devices.save(device);

    TelemetryRecord record = new TelemetryRecord();
    record.setId(UUID.randomUUID().toString());
    record.setDeviceId(device.getId());
    record.setRecordedAt(device.getLastSeen());
    record.setState(JsonCodec.write(reportedState));
    telemetry.save(record);
    trimHistory(device.getId());

    if (!wasOnline && device.isOnline()) {
      deviceEvents.record(device.getId(), "online", "设备已上线并上报数据");
    }
    if (device.isEnabled()) {
      alarmService.evaluate(device);
    }
  }

  private void trimHistory(String deviceId) {
    List<TelemetryRecord> recent = telemetry.findTop240ByDeviceIdOrderByRecordedAtDesc(deviceId);
    if (recent.size() >= MAX_HISTORY_PER_DEVICE) {
      Instant oldestKept = recent.get(MAX_HISTORY_PER_DEVICE - 1).getRecordedAt();
      telemetry.deleteByDeviceIdAndRecordedAtLessThan(deviceId, oldestKept);
    }
  }

  private Instant parseInstant(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return Instant.now();
    }
    try {
      return Instant.parse(timestamp);
    } catch (Exception ex) {
      return Instant.now();
    }
  }
}
