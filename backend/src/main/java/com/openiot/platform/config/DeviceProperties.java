package com.openiot.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openiot.device")
public class DeviceProperties {

  /** 超过该秒数没有任何上报，设备即判定为离线。 */
  private int offlineThresholdSeconds = 90;

  /** 命令下发后等待设备确认的秒数，超时记为「结果未知」。 */
  private int commandTimeoutSeconds = 15;

  public int getOfflineThresholdSeconds() {
    return offlineThresholdSeconds;
  }

  public void setOfflineThresholdSeconds(int offlineThresholdSeconds) {
    this.offlineThresholdSeconds = offlineThresholdSeconds;
  }

  public int getCommandTimeoutSeconds() {
    return commandTimeoutSeconds;
  }

  public void setCommandTimeoutSeconds(int commandTimeoutSeconds) {
    this.commandTimeoutSeconds = commandTimeoutSeconds;
  }
}
