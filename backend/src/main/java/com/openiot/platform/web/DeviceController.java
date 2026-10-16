package com.openiot.platform.web;

import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.service.CommandService;
import com.openiot.platform.service.DeviceService;
import com.openiot.platform.service.StateAssembler;
import com.openiot.platform.web.dto.CommandRequest;
import com.openiot.platform.web.dto.DeviceEnabledRequest;
import com.openiot.platform.web.dto.DeviceRequest;
import com.openiot.platform.web.dto.StateResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

  private final DeviceService devices;
  private final CommandService commands;
  private final StateAssembler assembler;
  private final CurrentUserService currentUser;

  public DeviceController(
      DeviceService devices,
      CommandService commands,
      StateAssembler assembler,
      CurrentUserService currentUser) {
    this.devices = devices;
    this.commands = commands;
    this.assembler = assembler;
    this.currentUser = currentUser;
  }

  @PostMapping
  public StateResponse create(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @RequestBody DeviceRequest request) {
    devices.register(request);
    return assembler.build(currentUser.toUser(email));
  }

  @PatchMapping("/{id}/enabled")
  public StateResponse setEnabled(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id,
      @RequestBody DeviceEnabledRequest request) {
    devices.setEnabled(id, Boolean.TRUE.equals(request.enabled()));
    return assembler.build(currentUser.toUser(email));
  }

  @PostMapping("/{id}/commands")
  public StateResponse sendCommand(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id,
      @RequestBody CommandRequest request) {
    commands.send(id, request.target());
    return assembler.build(currentUser.toUser(email));
  }
}
