package com.openiot.platform.service;

import com.openiot.platform.config.DeviceProperties;
import com.openiot.platform.domain.CommandRecord;
import com.openiot.platform.domain.CommandRecordRepository;
import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.mqtt.MqttClientService;
import com.openiot.platform.support.JsonCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 命令闭环：下发目标状态、等待设备确认、超时标记为结果未知。
 *
 * <p>关键约定：Broker 接收发布不等于设备已执行。只有能回报实际状态的设备，
 * 在收到确认后才更新实际状态；红外网关这类设备始终停留在「已发送，未确认」。
 */
@Service
public class CommandService {

  private static final Logger log = LoggerFactory.getLogger(CommandService.class);

  private final CommandRecordRepository commands;
  private final DeviceRepository devices;
  private final MqttClientService mqtt;
  private final DeviceEventService deviceEvents;
  private final DeviceProperties deviceProperties;

  public CommandService(
      CommandRecordRepository commands,
      DeviceRepository devices,
      MqttClientService mqtt,
      DeviceEventService deviceEvents,
      DeviceProperties deviceProperties) {
    this.commands = commands;
    this.devices = devices;
    this.mqtt = mqtt;
    this.deviceEvents = deviceEvents;
    this.deviceProperties = deviceProperties;
  }

  @Transactional
  public CommandRecord send(String deviceId, Map<String, Object> target) {
    if (target == null || target.isEmpty()) {
      throw new IllegalArgumentException("命令目标状态不能为空");
    }
    Device device = devices.findById(deviceId)
        .orElseThrow(() -> new IllegalArgumentException("设备不存在：" + deviceId));

    Instant now = Instant.now();
    CommandRecord command = new CommandRecord();
    command.setId(UUID.randomUUID().toString());
    command.setDeviceId(device.getId());
    command.setDeviceName(device.getName());
    command.setTarget(JsonCodec.write(target));
    command.setCreatedAt(now);
    command.setUpdatedAt(now);
    command.setConfirmation(device.getConfirmation());

    if (!device.isEnabled()) {
      command.setStatus("unreachable");
      command.setMessage("设备已停用，命令未下发");
    } else if (!device.isOnline()) {
      command.setStatus("unreachable");
      command.setMessage("设备当前离线，命令未下发");
    } else {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", "command");
      payload.put("commandId", command.getId());
      payload.put("deviceCode", device.getCode());
      payload.put("ts", now.toString());
      payload.put("target", target);

      boolean published = mqtt.publish(
          mqtt.getProperties().commandTopic(device.getCode()),
          JsonCodec.write(payload));

      if (!published) {
        command.setStatus("unknown");
        command.setMessage("MQTT 未连接，命令未下发");
      } else if ("gateway".equals(device.getConfirmation())) {
        command.setStatus("sent_unconfirmed");
        command.setMessage("红外网关已发送，目标设备状态未确认");
      } else {
        command.setStatus("pending");
        command.setMessage("等待设备执行确认");
      }

      if (published) {
        Map<String, Object> merged = new LinkedHashMap<>(device.getTargetState());
        merged.putAll(target);
        device.setTargetState(merged);
        devices.save(device);
      }
    }

    commands.save(command);
    deviceEvents.record(device.getId(), "command", command.getMessage());
    return command;
  }

  /** 处理设备回报的命令结果。 */
  @Transactional
  public void onAck(
      String deviceCode,
      String commandId,
      String ackStatus,
      String ackMessage,
      Map<String, Object> reportedState) {
    if (commandId == null || commandId.isBlank()) {
      return;
    }
    CommandRecord command = commands.findById(commandId).orElse(null);
    if (command == null) {
      log.warn("收到未知命令 {} 的回执，已忽略", commandId);
      return;
    }
    Device device = devices.findByCode(deviceCode).orElse(null);
    String label = CommandLabels.labelFor(JsonCodec.readMap(command.getTarget()));
    Instant now = Instant.now();

    if ("gateway".equals(command.getConfirmation())) {
      // 网关回执只能证明红外指令已发出，家电实际状态依旧未知。
      command.setUpdatedAt(now);
      command.setMessage("网关已回执：" + (ackMessage == null || ackMessage.isBlank() ? "已发送" : ackMessage)
          + "（家电实际状态未确认）");
      commands.save(command);
      if (device != null) {
        deviceEvents.record(device.getId(), "command", command.getMessage());
      }
      return;
    }

    if (!"pending".equals(command.getStatus())) {
      log.debug("命令 {} 已处于 {} 状态，忽略重复回执", commandId, command.getStatus());
      return;
    }

    if ("confirmed".equals(ackStatus)) {
      command.setStatus("confirmed");
      command.setMessage("设备已确认" + label);
      command.setUpdatedAt(now);
      if (device != null) {
        Map<String, Object> actual = reportedState == null || reportedState.isEmpty()
            ? JsonCodec.readMap(command.getTarget())
            : reportedState;
        Map<String, Object> merged = new LinkedHashMap<>(device.getState());
        merged.putAll(actual);
        device.setState(merged);
        device.setLastSeen(now);
        devices.save(device);
        deviceEvents.record(device.getId(), "command", command.getMessage());
      }
    } else if ("rejected".equals(ackStatus)) {
      command.setStatus("rejected");
      command.setMessage(ackMessage == null || ackMessage.isBlank() ? "设备拒绝执行" : ackMessage);
      command.setUpdatedAt(now);
      if (device != null) {
        deviceEvents.record(device.getId(), "command", command.getMessage());
      }
    }
    commands.save(command);
  }

  /** 超时仍未确认的命令标记为「结果未知」，不直接判定失败。 */
  @Transactional
  public int expirePending() {
    Instant cutoff = Instant.now().minus(Duration.ofSeconds(deviceProperties.getCommandTimeoutSeconds()));
    List<CommandRecord> pending = commands.findByStatus("pending");
    int expired = 0;
    for (CommandRecord command : pending) {
      if (command.getCreatedAt() != null && command.getCreatedAt().isBefore(cutoff)) {
        command.setStatus("unknown");
        command.setMessage("等待超时，执行结果未知，可重发目标状态命令");
        command.setUpdatedAt(Instant.now());
        commands.save(command);
        deviceEvents.record(command.getDeviceId(), "command", command.getMessage());
        expired++;
      }
    }
    return expired;
  }

  public List<CommandRecord> listCommands() {
    return commands.findTop100ByOrderByCreatedAtDesc();
  }
}
