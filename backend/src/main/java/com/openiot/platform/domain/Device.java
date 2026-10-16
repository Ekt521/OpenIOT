package com.openiot.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "device")
public class Device {

  @Id
  private String id;

  private String productId;

  /** 设备编号，同时是 MQTT 主题中的设备标识，必须唯一 */
  @Column(nullable = false, unique = true)
  private String code;

  private String name;

  private String location;

  @Column(length = 2000)
  private String description;

  private boolean enabled;

  private boolean online;

  private Instant lastSeen;

  /** state：设备能回报实际状态；gateway：只能证明网关已发送 */
  private String confirmation;

  /** 能力名称列表，逗号分隔 */
  private String capabilities;

  @Convert(converter = JsonMapConverter.class)
  @Column(columnDefinition = "text")
  private Map<String, Object> state = new LinkedHashMap<>();

  @Convert(converter = JsonMapConverter.class)
  @Column(columnDefinition = "text")
  private Map<String, Object> targetState = new LinkedHashMap<>();

  private String credential;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
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

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isOnline() {
    return online;
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  public Instant getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(Instant lastSeen) {
    this.lastSeen = lastSeen;
  }

  public String getConfirmation() {
    return confirmation;
  }

  public void setConfirmation(String confirmation) {
    this.confirmation = confirmation;
  }

  public String getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(String capabilities) {
    this.capabilities = capabilities;
  }

  public Map<String, Object> getState() {
    return state;
  }

  public void setState(Map<String, Object> state) {
    this.state = state == null ? new LinkedHashMap<>() : state;
  }

  public Map<String, Object> getTargetState() {
    return targetState;
  }

  public void setTargetState(Map<String, Object> targetState) {
    this.targetState = targetState == null ? new LinkedHashMap<>() : targetState;
  }

  public String getCredential() {
    return credential;
  }

  public void setCredential(String credential) {
    this.credential = credential;
  }
}
