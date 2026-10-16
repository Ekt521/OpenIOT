package com.openiot.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.openiot.platform.domain.AlarmRecord;
import com.openiot.platform.domain.AlarmRecordRepository;
import com.openiot.platform.domain.AlarmRule;
import com.openiot.platform.domain.CommandRecord;
import com.openiot.platform.domain.CommandRecordRepository;
import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.domain.Product;
import com.openiot.platform.service.AlarmService;
import com.openiot.platform.service.CommandService;
import com.openiot.platform.service.DeviceService;
import com.openiot.platform.service.ProductService;
import com.openiot.platform.support.JsonCodec;
import com.openiot.platform.web.dto.DeviceRequest;
import com.openiot.platform.web.dto.ProductRequest;
import com.openiot.platform.web.dto.PropertyRequest;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 真实链路集成测试：连接本机 Mosquitto，用独立 MQTT 客户端扮演设备，
 * 验证「设备上报 → 平台入库 → 命令下发 → 设备回执」的完整闭环。
 */
@SpringBootTest
@ActiveProfiles("test")
class PlatformFlowIntegrationTest {

  private static final String BROKER = "tcp://localhost:1883";

  private static boolean brokerAvailable;

  @Autowired
  private ProductService productService;

  @Autowired
  private DeviceService deviceService;

  @Autowired
  private CommandService commandService;

  @Autowired
  private AlarmService alarmService;

  @Autowired
  private DeviceRepository devices;

  @Autowired
  private CommandRecordRepository commands;

  @Autowired
  private AlarmRecordRepository alarms;

  private final List<MqttClient> testClients = new ArrayList<>();

  @BeforeAll
  static void detectBroker() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("localhost", 1883), 500);
      brokerAvailable = true;
    } catch (Exception ex) {
      brokerAvailable = false;
    }
  }

  @BeforeEach
  void requireBroker() {
    Assumptions.assumeTrue(brokerAvailable, "未检测到本机 MQTT Broker(1883)，跳过真实链路测试");
  }

  @AfterEach
  void closeClients() {
    for (MqttClient client : testClients) {
      try {
        if (client.isConnected()) {
          client.disconnect(500);
        }
        client.close(true);
      } catch (Exception ignored) {
        // 清理阶段忽略异常。
      }
    }
    testClients.clear();
  }

  @Test
  @DisplayName("设备上报遥测后状态入库、在线可见，并触发阈值告警")
  void telemetryUpdatesStateAndCreatesAlarm() throws Exception {
    Product product = productService.create(new ProductRequest(
        "温度传感器-" + suffix(),
        "环境监测",
        "集成测试产品",
        "active",
        List.of(new PropertyRequest(null, "温度", "temperature", "number", "°C", -20.0, 80.0, List.of()))));
    Device device = deviceService.register(new DeviceRequest(
        "测试温度计-" + suffix(), product.getId(), "OI-TEST-" + suffix(), "测试房间", "集成测试设备"));

    AlarmRule rule = new AlarmRule();
    rule.setId(UUID.randomUUID().toString());
    rule.setName("集成测试-温度过高");
    rule.setDeviceId(device.getId());
    rule.setProperty("temperature");
    rule.setOperator("gt");
    rule.setThreshold(30.0);
    rule.setLevel("warning");
    rule.setEnabled(true);
    alarmService.saveRule(rule);

    MqttClient client = connectDevice(device.getCode(), false);
    publishTelemetry(client, device.getCode(), Map.of("temperature", 31.5));

    Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      Device stored = devices.findById(device.getId()).orElseThrow();
      assertThat(stored.isOnline()).isTrue();
      assertThat(stored.getState()).containsEntry("temperature", 31.5);
      assertThat(stored.getLastSeen()).isNotNull();
    });

    Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      List<AlarmRecord> deviceAlarms = alarms.findAllByOrderByCreatedAtDesc().stream()
          .filter(alarm -> alarm.getDeviceId().equals(device.getId()))
          .toList();
      assertThat(deviceAlarms).hasSize(1);
      assertThat(deviceAlarms.get(0).getLevel()).isEqualTo("warning");
      assertThat(deviceAlarms.get(0).getStatus()).isEqualTo("active");
      assertThat(deviceAlarms.get(0).getMessage()).contains("temperature");
    });
  }

  @Test
  @DisplayName("能回报状态的设备：命令下发后被确认，并更新实际状态")
  void commandIsConfirmedByDeviceAck() throws Exception {
    Product product = productService.create(new ProductRequest(
        "智能继电器-" + suffix(),
        "开关控制",
        "集成测试产品",
        "active",
        List.of(new PropertyRequest(null, "开关状态", "power", "boolean", "", null, null, List.of()))));
    Device device = deviceService.register(new DeviceRequest(
        "测试继电器-" + suffix(), product.getId(), "OI-TEST-" + suffix(), "测试房间", "集成测试设备"));

    MqttClient client = connectDevice(device.getCode(), true);
    publishTelemetry(client, device.getCode(), Map.of("power", false));
    Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(devices.findById(device.getId()).orElseThrow().isOnline()).isTrue());

    CommandRecord command = commandService.send(device.getId(), Map.of("power", true));
    assertThat(command.getStatus()).isEqualTo("pending");

    Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      CommandRecord stored = commands.findById(command.getId()).orElseThrow();
      assertThat(stored.getStatus()).isEqualTo("confirmed");
      assertThat(stored.getMessage()).contains("已确认");
      Device updated = devices.findById(device.getId()).orElseThrow();
      assertThat(updated.getState()).containsEntry("power", true);
      assertThat(updated.getTargetState()).containsEntry("power", true);
    });
  }

  @Test
  @DisplayName("红外网关：只记录已发送，不把回执当作家电实际状态")
  void gatewayCommandStaysUnconfirmed() throws Exception {
    Product product = productService.create(new ProductRequest(
        "红外网关-" + suffix(),
        "红外网关",
        "集成测试产品",
        "active",
        List.of(new PropertyRequest(null, "温度", "temperature", "number", "°C", 16.0, 30.0, List.of()))));
    Device device = deviceService.register(new DeviceRequest(
        "测试红外-" + suffix(), product.getId(), "OI-TEST-" + suffix(), "测试房间", "集成测试设备"));
    assertThat(device.getConfirmation()).isEqualTo("gateway");

    MqttClient client = connectDevice(device.getCode(), true);
    publishTelemetry(client, device.getCode(), Map.of("temperature", 26.0));
    Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(devices.findById(device.getId()).orElseThrow().isOnline()).isTrue());

    CommandRecord command = commandService.send(device.getId(), Map.of("temperature", 24, "mode", "制冷"));
    assertThat(command.getStatus()).isEqualTo("sent_unconfirmed");
    assertThat(command.getMessage()).contains("未确认");

    // 网关即使回了 ack，平台也不能据此改写家电实际状态。
    Thread.sleep(3000);
    CommandRecord stored = commands.findById(command.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo("sent_unconfirmed");
    Device unchanged = devices.findById(device.getId()).orElseThrow();
    assertThat(unchanged.getState()).containsEntry("temperature", 26.0);
    assertThat(unchanged.getTargetState()).containsEntry("temperature", 24);
  }

  @Test
  @DisplayName("设备离线时不下发命令，直接表明不可达")
  void offlineDeviceCommandIsRejected() {
    Product product = productService.create(new ProductRequest(
        "离线设备-" + suffix(),
        "开关控制",
        "集成测试产品",
        "active",
        List.of(new PropertyRequest(null, "开关状态", "power", "boolean", "", null, null, List.of()))));
    Device device = deviceService.register(new DeviceRequest(
        "测试离线设备-" + suffix(), product.getId(), "OI-TEST-" + suffix(), "测试房间", "集成测试设备"));

    CommandRecord command = commandService.send(device.getId(), Map.of("power", true));

    assertThat(command.getStatus()).isEqualTo("unreachable");
    assertThat(command.getMessage()).contains("离线");
    assertThat(devices.findById(device.getId()).orElseThrow().getTargetState()).isEmpty();
  }

  private MqttClient connectDevice(String code, boolean autoAck) throws Exception {
    MqttClient client = new MqttClient(BROKER, "openiot-test-" + code + "-" + suffix(), new MemoryPersistence());
    MqttConnectOptions options = new MqttConnectOptions();
    options.setCleanSession(true);
    client.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(Throwable cause) {
        // 测试期间不需要处理。
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) {
        if (!autoAck || !topic.endsWith("/command")) {
          return;
        }
        try {
          Map<String, Object> command = JsonCodec.readMap(new String(message.getPayload(), StandardCharsets.UTF_8));
          Map<String, Object> ack = new LinkedHashMap<>();
          ack.put("type", "commandAck");
          ack.put("deviceCode", code);
          ack.put("commandId", command.get("commandId"));
          ack.put("status", "confirmed");
          ack.put("message", "设备已执行");
          ack.put("state", command.get("target"));
          ack.put("ts", Instant.now().toString());
          MqttMessage reply = new MqttMessage(JsonCodec.write(ack).getBytes(StandardCharsets.UTF_8));
          reply.setQos(1);
          client.publish("openiot/" + code + "/command/ack", reply);
        } catch (Exception ignored) {
          // 测试辅助代码，忽略回执异常。
        }
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
        // 不需要处理。
      }
    });
    client.connect(options);
    client.subscribe("openiot/" + code + "/command", 1);
    testClients.add(client);
    return client;
  }

  private void publishTelemetry(MqttClient client, String code, Map<String, Object> state) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "telemetry");
    payload.put("deviceCode", code);
    payload.put("ts", Instant.now().toString());
    payload.put("state", state);
    MqttMessage message = new MqttMessage(JsonCodec.write(payload).getBytes(StandardCharsets.UTF_8));
    message.setQos(1);
    client.publish("openiot/" + code + "/telemetry", message);
  }

  private String suffix() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
