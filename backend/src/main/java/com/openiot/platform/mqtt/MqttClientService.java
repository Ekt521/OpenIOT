package com.openiot.platform.mqtt;

import com.openiot.platform.config.MqttProperties;
import com.openiot.platform.domain.MqttSetting;
import com.openiot.platform.domain.MqttSettingRepository;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 平台与真实 MQTT Broker 的连接。
 *
 * <p>连接参数优先取自数据库中的设置（界面上可修改），首次启动时用配置文件初始化。
 * 消息收到后不在这里处理业务，只转成 Spring 事件，避免和命令下发形成循环依赖。
 */
@Service
public class MqttClientService {

  private static final Logger log = LoggerFactory.getLogger(MqttClientService.class);

  private final MqttProperties properties;
  private final MqttSettingRepository settingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Object clientLock = new Object();

  private volatile MqttClient client;
  private volatile boolean connected;
  private volatile String lastError;
  private volatile Instant lastCheck = Instant.now();
  private volatile String host;
  private volatile int port;
  private volatile String clientId;

  public MqttClientService(
      MqttProperties properties,
      MqttSettingRepository settingRepository,
      ApplicationEventPublisher eventPublisher) {
    this.properties = properties;
    this.settingRepository = settingRepository;
    this.eventPublisher = eventPublisher;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    MqttSetting setting = settingRepository.findById(MqttSetting.SINGLETON_ID).orElseGet(this::createDefaultSetting);
    this.host = setting.getHost();
    this.port = setting.getPort();
    this.clientId = setting.getClientId();
    connect();
  }

  private MqttSetting createDefaultSetting() {
    String url = properties.getBrokerUrl();
    String hostPart = url.replaceFirst("^tcp://", "");
    int colon = hostPart.lastIndexOf(':');
    String hostValue = colon > 0 ? hostPart.substring(0, colon) : hostPart;
    int portValue = colon > 0 ? Integer.parseInt(hostPart.substring(colon + 1)) : 1883;

    MqttSetting setting = new MqttSetting();
    setting.setId(MqttSetting.SINGLETON_ID);
    setting.setHost(hostValue);
    setting.setPort(portValue);
    setting.setClientId(properties.getClientId());
    setting.setLastCheck(Instant.now());
    return settingRepository.save(setting);
  }

  private String brokerUrl() {
    return "tcp://" + host + ":" + port;
  }

  /** 建立连接并订阅设备上报主题。连接失败不抛异常，交给定时任务重试。 */
  public void connect() {
    synchronized (clientLock) {
      closeQuietly();
      String url = brokerUrl();
      try {
        MqttClient newClient = new MqttClient(url, clientId, new MemoryPersistence());
        newClient.setCallback(new MqttCallbackExtended() {
          @Override
          public void connectComplete(boolean reconnect, String serverURI) {
            connected = true;
            lastError = null;
            lastCheck = Instant.now();
            log.info("已连接 MQTT Broker {}（{}）", serverURI, reconnect ? "重连" : "首次连接");
            subscribeAll();
          }

          @Override
          public void connectionLost(Throwable cause) {
            connected = false;
            lastError = cause == null ? "连接中断" : cause.getMessage();
            lastCheck = Instant.now();
            log.warn("MQTT 连接中断：{}", lastError);
          }

          @Override
          public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            eventPublisher.publishEvent(new MqttMessageEvent(topic, payload));
          }

          @Override
          public void deliveryComplete(IMqttDeliveryToken token) {
            // QoS 1 送达确认，命令结果以设备 ack 为准，这里不处理。
          }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
          options.setUserName(properties.getUsername());
          options.setPassword(properties.getPassword().toCharArray());
        }

        newClient.connect(options);
        this.client = newClient;
      } catch (Exception ex) {
        connected = false;
        lastError = ex.getMessage();
        lastCheck = Instant.now();
        log.warn("连接 MQTT Broker {} 失败：{}", url, ex.getMessage());
      }
    }
  }

  private void subscribeAll() {
    try {
      MqttClient current = client;
      if (current == null) {
        return;
      }
      current.subscribe(properties.getTelemetryTopic(), 1);
      current.subscribe(properties.getCommandAckTopic(), 1);
      current.subscribe(properties.getStatusTopic(), 1);
      log.info("已订阅设备主题：{}、{}、{}",
          properties.getTelemetryTopic(), properties.getCommandAckTopic(), properties.getStatusTopic());
    } catch (MqttException ex) {
      lastError = ex.getMessage();
      log.warn("订阅设备主题失败：{}", ex.getMessage());
    }
  }

  /** 未连接时按固定间隔重试，保证 Broker 晚于平台启动也能自动恢复。 */
  @Scheduled(fixedDelay = 15_000L, initialDelay = 15_000L)
  public void ensureConnected() {
    if (connected) {
      return;
    }
    log.debug("MQTT 未连接，尝试重新连接 {}:{}", host, port);
    connect();
  }

  /** 向设备下发命令，QoS 1。返回是否成功交给 Broker。 */
  public boolean publish(String topic, String payload) {
    synchronized (clientLock) {
      MqttClient current = client;
      if (current == null || !current.isConnected()) {
        lastError = "MQTT 未连接，命令未下发";
        return false;
      }
      try {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        current.publish(topic, message);
        return true;
      } catch (Exception ex) {
        lastError = ex.getMessage();
        log.warn("发布到 {} 失败：{}", topic, ex.getMessage());
        return false;
      }
    }
  }

  /** 更新 Broker 连接参数并立即重连。 */
  public void applySettings(String newHost, int newPort, String newClientId) {
    MqttSetting setting = settingRepository.findById(MqttSetting.SINGLETON_ID).orElseGet(MqttSetting::new);
    setting.setId(MqttSetting.SINGLETON_ID);
    setting.setHost(newHost);
    setting.setPort(newPort);
    setting.setClientId(newClientId);
    setting.setLastCheck(Instant.now());
    settingRepository.save(setting);

    this.host = newHost;
    this.port = newPort;
    this.clientId = newClientId;
    connect();
  }

  /** 仅重连，不改参数。 */
  public void reconnect() {
    connect();
  }

  @PreDestroy
  public void shutdown() {
    synchronized (clientLock) {
      closeQuietly();
    }
  }

  private void closeQuietly() {
    MqttClient current = client;
    client = null;
    connected = false;
    if (current == null) {
      return;
    }
    try {
      if (current.isConnected()) {
        current.disconnect(1000);
      }
      current.close(true);
    } catch (Exception ex) {
      log.debug("关闭 MQTT 客户端时忽略异常：{}", ex.getMessage());
    }
  }

  public boolean isConnected() {
    MqttClient current = client;
    return connected && current != null && current.isConnected();
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getLastCheck() {
    return lastCheck;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getClientId() {
    return clientId;
  }

  public MqttProperties getProperties() {
    return properties;
  }
}
