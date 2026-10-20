package com.openiot.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 小米电视局域网接入参数。电视 IP 由路由器 DHCP 分配，可能变化，
// 所以放在配置文件里，改完重启后端即可。
@ConfigurationProperties(prefix = "openiot.tv")
public class TvProperties {

  private boolean enabled = false;
  private String ip = "127.0.0.1";
  private int port = 6095;
  private String code = "";
  private String name = "电视";
  private String productName = "电视";
  private String location = "";
  private int timeoutMillis = 3000;
  private long pollIntervalMillis = 15000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public int getTimeoutMillis() {
    return timeoutMillis;
  }

  public void setTimeoutMillis(int timeoutMillis) {
    this.timeoutMillis = timeoutMillis;
  }

  public long getPollIntervalMillis() {
    return pollIntervalMillis;
  }

  public void setPollIntervalMillis(long pollIntervalMillis) {
    this.pollIntervalMillis = pollIntervalMillis;
  }
}
