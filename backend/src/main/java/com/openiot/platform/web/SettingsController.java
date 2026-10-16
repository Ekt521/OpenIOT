package com.openiot.platform.web;

import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.mqtt.MqttClientService;
import com.openiot.platform.service.StateAssembler;
import com.openiot.platform.web.dto.MqttSettingsRequest;
import com.openiot.platform.web.dto.StateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

  private final MqttClientService mqtt;
  private final StateAssembler assembler;
  private final CurrentUserService currentUser;

  public SettingsController(
      MqttClientService mqtt,
      StateAssembler assembler,
      CurrentUserService currentUser) {
    this.mqtt = mqtt;
    this.assembler = assembler;
    this.currentUser = currentUser;
  }

  @PutMapping("/mqtt")
  public StateResponse saveMqtt(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @RequestBody MqttSettingsRequest request) {
    if (request.host() == null || request.host().isBlank()) {
      throw new IllegalArgumentException("Broker 地址不能为空");
    }
    int port = request.port() == null ? 1883 : request.port();
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("端口需要在 1 到 65535 之间");
    }
    String clientId = request.clientId() == null || request.clientId().isBlank()
        ? "openiot-platform"
        : request.clientId().trim();
    mqtt.applySettings(request.host().trim(), port, clientId);
    return assembler.build(currentUser.toUser(email));
  }

  @PostMapping("/mqtt/reconnect")
  public StateResponse reconnect(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email) {
    mqtt.reconnect();
    return assembler.build(currentUser.toUser(email));
  }
}
