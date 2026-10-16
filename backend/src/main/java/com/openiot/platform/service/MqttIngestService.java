package com.openiot.platform.service;

import com.openiot.platform.mqtt.CommandAckPayload;
import com.openiot.platform.mqtt.DeviceStatusPayload;
import com.openiot.platform.mqtt.MqttMessageEvent;
import com.openiot.platform.mqtt.TelemetryPayload;
import com.openiot.platform.support.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

// 把 Broker 收到的设备消息按主题分派给业务处理。
// 主题约定：
//   openiot/{deviceCode}/telemetry    设备上报状态与遥测
//   openiot/{deviceCode}/command/ack  设备回报命令执行结果
//   openiot/{deviceCode}/status       设备上下线，含遗嘱消息
@Service
public class MqttIngestService {

  private static final Logger log = LoggerFactory.getLogger(MqttIngestService.class);

  private final TelemetryService telemetryService;
  private final CommandService commandService;
  private final DeviceService deviceService;

  public MqttIngestService(
      TelemetryService telemetryService,
      CommandService commandService,
      DeviceService deviceService) {
    this.telemetryService = telemetryService;
    this.commandService = commandService;
    this.deviceService = deviceService;
  }

  @EventListener
  public void onMessage(MqttMessageEvent event) {
    try {
      dispatch(event.topic(), event.payload());
    } catch (Exception ex) {
      // 单条设备消息出错不能中断 MQTT 接收线程。
      log.warn("处理设备消息失败，主题={}：{}", event.topic(), ex.getMessage());
    }
  }

  private void dispatch(String topic, String payload) {
    String[] parts = topic.split("/");
    if (parts.length < 3) {
      return;
    }
    String topicDeviceCode = parts[1];

    if (topic.endsWith("/command/ack")) {
      CommandAckPayload ack = JsonCodec.read(payload, CommandAckPayload.class);
      commandService.onAck(
          ack.deviceCode() == null || ack.deviceCode().isBlank() ? topicDeviceCode : ack.deviceCode(),
          ack.commandId(),
          ack.status(),
          ack.message(),
          ack.state());
      return;
    }
    if (topic.endsWith("/telemetry")) {
      TelemetryPayload telemetry = JsonCodec.read(payload, TelemetryPayload.class);
      telemetryService.onTelemetry(
          telemetry.deviceCode() == null || telemetry.deviceCode().isBlank() ? topicDeviceCode : telemetry.deviceCode(),
          telemetry.state(),
          telemetry.ts());
      return;
    }
    if (topic.endsWith("/status")) {
      DeviceStatusPayload status = JsonCodec.read(payload, DeviceStatusPayload.class);
      if (status.online() != null) {
        deviceService.onStatus(
            status.deviceCode() == null || status.deviceCode().isBlank() ? topicDeviceCode : status.deviceCode(),
            status.online());
      }
    }
  }
}
