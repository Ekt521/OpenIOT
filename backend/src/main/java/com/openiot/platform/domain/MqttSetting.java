package com.openiot.platform.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 平台侧保存的 Broker 连接参数，只有一行。 */
@Entity
@Table(name = "mqtt_setting")
public class MqttSetting {

  public static final String SINGLETON_ID = "default";

  @Id
  private String id = SINGLETON_ID;

  private String host;

  private int port;

  private String clientId;

  private Instant lastCheck;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public Instant getLastCheck() {
    return lastCheck;
  }

  public void setLastCheck(Instant lastCheck) {
    this.lastCheck = lastCheck;
  }
}
