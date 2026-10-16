package com.openiot.platform.web;

import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.service.AlarmService;
import com.openiot.platform.service.StateAssembler;
import com.openiot.platform.web.dto.NoteRequest;
import com.openiot.platform.web.dto.StateResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

  private final AlarmService alarms;
  private final StateAssembler assembler;
  private final CurrentUserService currentUser;

  public AlarmController(
      AlarmService alarms,
      StateAssembler assembler,
      CurrentUserService currentUser) {
    this.alarms = alarms;
    this.assembler = assembler;
    this.currentUser = currentUser;
  }

  @PostMapping("/{id}/acknowledge")
  public StateResponse acknowledge(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id,
      @RequestBody(required = false) NoteRequest request) {
    alarms.acknowledge(id, request == null ? null : request.note());
    return assembler.build(currentUser.toUser(email));
  }

  @PostMapping("/{id}/close")
  public StateResponse close(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id,
      @RequestBody(required = false) NoteRequest request) {
    alarms.close(id, request == null ? null : request.note());
    return assembler.build(currentUser.toUser(email));
  }
}
