package com.openiot.platform.simulator;

import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.mqtt.MqttClientService;
import com.openiot.platform.support.JsonCodec;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 开发用设备模拟器：以 profile=simulator 启动。
 *
 * <p>它不直接调用平台接口，而是像真实设备一样连接 Broker、按主题上报遥测、
 * 订阅命令并回执。用于在没有硬件时验证整条链路；接入真实设备后不必启动。
 */
@Component
@ConditionalOnProperty(name = "openiot.simulator.enabled", havingValue = "true")
public class DeviceSimulator {

  private static final Logger log = LoggerFactory.getLogger(DeviceSimulator.class);
  private static final long TELEMETRY_INTERVAL_MILLIS = 5_000L;

  private final DeviceRepository devices;
  private final MqttClientService mqtt;
  private final Map<String, SimulatedDevice> running = new ConcurrentHashMap<>();

  public DeviceSimulator(DeviceRepository devices, MqttClientService mqtt) {
    this.devices = devices;
    this.mqtt = mqtt;
    log.info("设备模拟器已启用：平台创建设备后会自动开始上报，关闭请去掉 simulator 配置。");
  }

  @Scheduled(fixedDelay = TELEMETRY_INTERVAL_MILLIS, initialDelay = 3_000L)
  public void tick() {
    for (Device device : devices.findAll()) {
      if (!device.isEnabled()) {
        stop(device.getId());
        continue;
      }
      SimulatedDevice simulated = running.get(device.getId());
      if (simulated == null) {
        simulated = new SimulatedDevice(device);
        if (simulated.start()) {
          running.put(device.getId(), simulated);
        }
        continue;
      }
      simulated.publishTelemetry();
    }
  }

  private void stop(String deviceId) {
    SimulatedDevice simulated = running.remove(deviceId);
    if (simulated != null) {
      simulated.close();
    }
  }

  @PreDestroy
  public void shutdown() {
    List<String> ids = new ArrayList<>(running.keySet());
    ids.forEach(this::stop);
  }

  /** 单个被模拟的设备，持有自己的 MQTT 连接，与真实设备行为一致。 */
  private final class SimulatedDevice {

    private final String code;
    private final String name;
    private final String confirmation;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private MqttClient client;

    private SimulatedDevice(Device device) {
      this.code = device.getCode();
      this.name = device.getName();
      this.confirmation = device.getConfirmation();
      this.values.putAll(device.getState());
    }

    private boolean start() {
      try {
        MqttClient newClient = new MqttClient(
            "tcp://" + mqtt.getHost() + ":" + mqtt.getPort(),
            "openiot-sim-" + code,
            new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        // 设备掉线时由 Broker 代发离线状态，平台据此更新在线状态。
        options.setWill(
            mqtt.getProperties().deviceStatusTopic(code),
            statusPayload(false).getBytes(StandardCharsets.UTF_8),
            1,
            true);

        newClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallback() {
          @Override
          public void connectionLost(Throwable cause) {
            // 交给自动重连处理。
          }

          @Override
          public void messageArrived(String topic, MqttMessage message) {
            handleCommand(new String(message.getPayload(), StandardCharsets.UTF_8));
          }

          @Override
          public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
            // 不需要处理。
          }
        });

        newClient.connect(options);
        this.client = newClient;
        newClient.subscribe(mqtt.getProperties().commandTopic(code), 1);
        publish(mqtt.getProperties().deviceStatusTopic(code), statusPayload(true), true);
        log.info("模拟设备 {} 已上线", name);
        publishTelemetry();
        return true;
      } catch (Exception ex) {
        log.warn("模拟设备 {} 启动失败：{}", name, ex.getMessage());
        return false;
      }
    }

    private String statusPayload(boolean online) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", "status");
      payload.put("deviceCode", code);
      payload.put("online", online);
      payload.put("ts", Instant.now().toString());
      return JsonCodec.write(payload);
    }

    private void publishTelemetry() {
      Map<String, Object> state = new LinkedHashMap<>();
      values.forEach((key, value) -> state.put(key, drift(value)));
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", "telemetry");
      payload.put("deviceCode", code);
      payload.put("ts", Instant.now().toString());
      payload.put("state", state);
      publish(mqtt.getProperties().telemetryTopicFor(code), JsonCodec.write(payload), false);
    }

    // 数值型属性做小幅随机游走，其余保持原值，模拟真实传感器波动。
    private Object drift(Object value) {
      if (value instanceof Number number) {
        double next = number.doubleValue() + (Math.random() - 0.5) * 0.6;
        return Math.round(next * 10.0) / 10.0;
      }
      return value;
    }

    private void handleCommand(String payload) {
      try {
        Map<String, Object> command = JsonCodec.readMap(payload);
        String commandId = String.valueOf(command.get("commandId"));
        Object rawTarget = command.get("target");
        Map<String, Object> target = new LinkedHashMap<>();
        if (rawTarget instanceof Map<?, ?> map) {
          map.forEach((key, value) -> target.put(String.valueOf(key), value));
        }

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "commandAck");
        ack.put("deviceCode", code);
        ack.put("commandId", commandId);
        ack.put("status", "confirmed");
        ack.put("ts", Instant.now().toString());

        if ("gateway".equals(confirmation)) {
          // 红外网关只能证明指令已发出，无法回报家电实际状态。
          ack.put("message", "红外指令已发送");
        } else {
          values.putAll(target);
          ack.put("message", "设备已执行");
          ack.put("state", target);
        }
        publish(mqtt.getProperties().deviceCommandAckTopic(code), JsonCodec.write(ack), false);
      } catch (Exception ex) {
        log.warn("模拟设备 {} 处理命令失败：{}", name, ex.getMessage());
      }
    }

    private void publish(String topic, String payload, boolean retained) {
      MqttClient current = client;
      try {
        if (current == null) {
          return;
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(retained);
        current.publish(topic, message);
      } catch (Exception ex) {
        log.warn("模拟设备 {} 发布到 {} 失败：{}", name, topic, ex.getMessage());
      }
    }

    private void close() {
      MqttClient current = client;
      client = null;
      if (current == null) {
        return;
      }
      try {
        if (current.isConnected()) {
          MqttMessage offline = new MqttMessage(statusPayload(false).getBytes(StandardCharsets.UTF_8));
          offline.setQos(1);
          offline.setRetained(true);
          current.publish(mqtt.getProperties().deviceStatusTopic(code), offline);
          current.disconnect(1000);
        }
        current.close(true);
        log.info("模拟设备 {} 已下线", name);
      } catch (Exception ex) {
        log.debug("关闭模拟设备 {} 时忽略异常：{}", name, ex.getMessage());
      }
    }
  }
}
