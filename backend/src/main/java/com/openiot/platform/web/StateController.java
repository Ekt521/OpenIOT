package com.openiot.platform.web;

import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.service.StateAssembler;
import com.openiot.platform.web.dto.StateResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StateController {

  private final StateAssembler assembler;
  private final CurrentUserService currentUser;

  public StateController(StateAssembler assembler, CurrentUserService currentUser) {
    this.assembler = assembler;
    this.currentUser = currentUser;
  }

  @GetMapping("/state")
  public StateResponse state(@RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email) {
    return assembler.build(currentUser.toUser(email));
  }
}
