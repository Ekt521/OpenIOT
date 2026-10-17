package com.openiot.platform.service;

import com.openiot.platform.domain.AlarmRecord;
import com.openiot.platform.domain.AlarmRule;
import com.openiot.platform.domain.CommandRecord;
import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceEvent;
import com.openiot.platform.domain.DeviceEventRepository;
import com.openiot.platform.domain.Product;
import com.openiot.platform.domain.ProductProperty;
import com.openiot.platform.domain.ProductRepository;
import com.openiot.platform.domain.TelemetryRecord;
import com.openiot.platform.domain.TelemetryRecordRepository;
import com.openiot.platform.mqtt.MqttClientService;
import com.openiot.platform.support.JsonCodec;
import com.openiot.platform.web.dto.AlarmDto;
import com.openiot.platform.web.dto.CommandDto;
import com.openiot.platform.web.dto.DeviceDto;
import com.openiot.platform.web.dto.EventDto;
import com.openiot.platform.web.dto.MqttDto;
import com.openiot.platform.web.dto.ProductDto;
import com.openiot.platform.web.dto.PropertyDto;
import com.openiot.platform.web.dto.RuleDto;
import com.openiot.platform.web.dto.StateResponse;
import com.openiot.platform.web.dto.UserDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// 把数据库中的实体组装成前端直接可用的状态快照。
@Service
public class StateAssembler {

  private final ProductRepository products;
  private final com.openiot.platform.domain.DeviceRepository devices;
  private final TelemetryRecordRepository telemetry;
  private final DeviceEventRepository events;
  private final AlarmService alarmService;
  private final CommandService commandService;
  private final MqttClientService mqtt;

  public StateAssembler(
      ProductRepository products,
      com.openiot.platform.domain.DeviceRepository devices,
      TelemetryRecordRepository telemetry,
      DeviceEventRepository events,
      AlarmService alarmService,
      CommandService commandService,
      MqttClientService mqtt) {
    this.products = products;
    this.devices = devices;
    this.telemetry = telemetry;
    this.events = events;
    this.alarmService = alarmService;
    this.commandService = commandService;
    this.mqtt = mqtt;
  }

  public StateResponse build(UserDto user) {
    List<ProductDto> productDtos = products.findAllByOrderByCreatedAtDesc().stream()
        .map(this::toProduct)
        .toList();
    List<DeviceDto> deviceDtos = devices.findAllByOrderByNameAsc().stream()
        .map(this::toDevice)
        .toList();
    List<CommandDto> commandDtos = commandService.listCommands().stream()
        .map(this::toCommand)
        .toList();
    List<RuleDto> ruleDtos = alarmService.listRules().stream()
        .map(this::toRule)
        .toList();
    List<AlarmDto> alarmDtos = alarmService.listAlarms().stream()
        .map(this::toAlarm)
        .toList();
    return new StateResponse(user, productDtos, deviceDtos, commandDtos, ruleDtos, alarmDtos, toMqtt());
  }

  public MqttDto toMqtt() {
    return new MqttDto(
        mqtt.getHost(),
        mqtt.getPort(),
        mqtt.getClientId(),
        mqtt.isConnected(),
        iso(mqtt.getLastCheck()),
        mqtt.getLastError());
  }

  public ProductDto toProduct(Product product) {
    List<PropertyDto> properties = product.getProperties().stream()
        .map(this::toProperty)
        .toList();
    return new ProductDto(
        product.getId(),
        product.getName(),
        product.getCategory(),
        product.getDescription(),
        product.getStatus(),
        product.getProtocol(),
        iso(product.getCreatedAt()),
        properties);
  }

  private PropertyDto toProperty(ProductProperty property) {
    List<String> options = property.getOptions() == null || property.getOptions().isBlank()
        ? List.of()
        : Arrays.stream(property.getOptions().split(",")).filter(item -> !item.isBlank()).toList();
    return new PropertyDto(
        property.getId(),
        property.getName(),
        property.getKey(),
        property.getType(),
        property.getUnit(),
        property.getMin(),
        property.getMax(),
        options);
  }

  public DeviceDto toDevice(Device device) {
    List<String> capabilities = device.getCapabilities() == null || device.getCapabilities().isBlank()
        ? List.of()
        : Arrays.stream(device.getCapabilities().split(",")).filter(item -> !item.isBlank()).toList();

    List<TelemetryRecord> records = new ArrayList<>(
        telemetry.findTop240ByDeviceIdOrderByRecordedAtDesc(device.getId()));
    Collections.reverse(records);
    List<Map<String, Object>> history = new ArrayList<>();
    for (TelemetryRecord record : records) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("timestamp", iso(record.getRecordedAt()));
      point.putAll(JsonCodec.readMap(record.getState()));
      history.add(point);
    }

    List<EventDto> eventDtos = events.findTop30ByDeviceIdOrderByOccurredAtDesc(device.getId()).stream()
        .map(this::toEvent)
        .toList();

    return new DeviceDto(
        device.getId(),
        device.getProductId(),
        device.getName(),
        device.getCode(),
        device.getLocation(),
        device.getDescription(),
        device.isEnabled(),
        device.isOnline(),
        iso(device.getLastSeen()),
        device.getConfirmation(),
        device.getControlProfile(),
        capabilities,
        device.getState(),
        device.getTargetState(),
        history,
        eventDtos,
        device.getCredential());
  }

  private EventDto toEvent(DeviceEvent event) {
    return new EventDto(event.getId(), event.getType(), event.getMessage(), iso(event.getOccurredAt()));
  }

  public CommandDto toCommand(CommandRecord command) {
    Map<String, Object> target = JsonCodec.readMap(command.getTarget());
    return new CommandDto(
        command.getId(),
        command.getDeviceId(),
        command.getDeviceName(),
        CommandLabels.labelFor(target),
        target,
        iso(command.getCreatedAt()),
        command.getStatus(),
        command.getConfirmation(),
        command.getMessage());
  }

  public RuleDto toRule(AlarmRule rule) {
    return new RuleDto(
        rule.getId(),
        rule.getName(),
        rule.getDeviceId(),
        rule.getProperty(),
        rule.getOperator(),
        rule.getThreshold(),
        rule.getLevel(),
        rule.isEnabled());
  }

  public AlarmDto toAlarm(AlarmRecord alarm) {
    return new AlarmDto(
        alarm.getId(),
        alarm.getRuleId(),
        alarm.getRuleName(),
        alarm.getDeviceId(),
        alarm.getDeviceName(),
        alarm.getMessage(),
        alarm.getLevel(),
        alarm.getStatus(),
        iso(alarm.getCreatedAt()),
        iso(alarm.getAcknowledgedAt()),
        iso(alarm.getClosedAt()),
        alarm.getNote());
  }

  private String iso(Instant instant) {
    return instant == null ? null : instant.toString();
  }
}
