package com.openiot.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openiot.mqtt")
public class MqttProperties {

  private String brokerUrl = "tcp://localhost:1883";
  private String clientId = "openiot-platform";
  private String username = "";
  private String password = "";
  private String telemetryTopic = "openiot/+/telemetry";
  private String commandAckTopic = "openiot/+/command/ack";
  private String statusTopic = "openiot/+/status";
  private String commandTopicTemplate = "openiot/%s/command";
  private String deviceStatusTopicTemplate = "openiot/%s/status";
  private String deviceCommandAckTopicTemplate = "openiot/%s/command/ack";
  private String deviceTelemetryTopicTemplate = "openiot/%s/telemetry";
  private int keepAliveSeconds = 30;
  private int connectionTimeoutSeconds = 10;

  public String getBrokerUrl() {
    return brokerUrl;
  }

  public void setBrokerUrl(String brokerUrl) {
    this.brokerUrl = brokerUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getTelemetryTopic() {
    return telemetryTopic;
  }

  public void setTelemetryTopic(String telemetryTopic) {
    this.telemetryTopic = telemetryTopic;
  }

  public String getCommandAckTopic() {
    return commandAckTopic;
  }

  public void setCommandAckTopic(String commandAckTopic) {
    this.commandAckTopic = commandAckTopic;
  }

  public String getStatusTopic() {
    return statusTopic;
  }

  public void setStatusTopic(String statusTopic) {
    this.statusTopic = statusTopic;
  }

  public String getCommandTopicTemplate() {
    return commandTopicTemplate;
  }

  public void setCommandTopicTemplate(String commandTopicTemplate) {
    this.commandTopicTemplate = commandTopicTemplate;
  }

  public int getKeepAliveSeconds() {
    return keepAliveSeconds;
  }

  public void setKeepAliveSeconds(int keepAliveSeconds) {
    this.keepAliveSeconds = keepAliveSeconds;
  }

  public int getConnectionTimeoutSeconds() {
    return connectionTimeoutSeconds;
  }

  public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
    this.connectionTimeoutSeconds = connectionTimeoutSeconds;
  }

  public String commandTopic(String deviceCode) {
    return String.format(commandTopicTemplate, deviceCode);
  }

  public String deviceStatusTopic(String deviceCode) {
    return String.format(deviceStatusTopicTemplate, deviceCode);
  }

  public String deviceCommandAckTopic(String deviceCode) {
    return String.format(deviceCommandAckTopicTemplate, deviceCode);
  }

  public String telemetryTopicFor(String deviceCode) {
    return String.format(deviceTelemetryTopicTemplate, deviceCode);
  }

  public String getDeviceTelemetryTopicTemplate() {
    return deviceTelemetryTopicTemplate;
  }

  public void setDeviceTelemetryTopicTemplate(String deviceTelemetryTopicTemplate) {
    this.deviceTelemetryTopicTemplate = deviceTelemetryTopicTemplate;
  }

  public String getDeviceStatusTopicTemplate() {
    return deviceStatusTopicTemplate;
  }

  public void setDeviceStatusTopicTemplate(String deviceStatusTopicTemplate) {
    this.deviceStatusTopicTemplate = deviceStatusTopicTemplate;
  }

  public String getDeviceCommandAckTopicTemplate() {
    return deviceCommandAckTopicTemplate;
  }

  public void setDeviceCommandAckTopicTemplate(String deviceCommandAckTopicTemplate) {
    this.deviceCommandAckTopicTemplate = deviceCommandAckTopicTemplate;
  }
}
