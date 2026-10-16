package com.openiot.platform.service;

import com.openiot.platform.config.DeviceProperties;
import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 兜底监测：长时间没有上报的设备判定为离线，等待过久的命令标记为结果未知。
@Service
public class DeviceMonitorService {

  private static final Logger log = LoggerFactory.getLogger(DeviceMonitorService.class);

  private final DeviceRepository devices;
  private final DeviceEventService deviceEvents;
  private final AlarmService alarmService;
  private final CommandService commandService;
  private final DeviceProperties deviceProperties;

  public DeviceMonitorService(
      DeviceRepository devices,
      DeviceEventService deviceEvents,
      AlarmService alarmService,
      CommandService commandService,
      DeviceProperties deviceProperties) {
    this.devices = devices;
    this.deviceEvents = deviceEvents;
    this.alarmService = alarmService;
    this.commandService = commandService;
    this.deviceProperties = deviceProperties;
  }

  @Scheduled(fixedDelay = 10_000L, initialDelay = 20_000L)
  @Transactional
  public void markStaleDevicesOffline() {
    Instant cutoff = Instant.now().minus(Duration.ofSeconds(deviceProperties.getOfflineThresholdSeconds()));
    for (Device device : devices.findAll()) {
      if (!device.isEnabled() || !device.isOnline()) {
        continue;
      }
      if (device.getLastSeen() != null && device.getLastSeen().isAfter(cutoff)) {
        continue;
      }
      device.setOnline(false);
      devices.save(device);
      deviceEvents.record(device.getId(), "offline", "超过 "
          + deviceProperties.getOfflineThresholdSeconds() + " 秒未上报，判定为离线");
      alarmService.evaluate(device);
      log.info("设备 {} 长时间未上报，已标记离线", device.getName());
    }
  }

  @Scheduled(fixedDelay = 5_000L, initialDelay = 10_000L)
  public void expireStaleCommands() {
    int expired = commandService.expirePending();
    if (expired > 0) {
      log.info("有 {} 条命令等待超时，已标记为结果未知", expired);
    }
  }
}
