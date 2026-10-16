package com.openiot.platform.web;

import com.openiot.platform.domain.AlarmRule;
import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.service.AlarmService;
import com.openiot.platform.service.StateAssembler;
import com.openiot.platform.web.dto.RuleEnabledRequest;
import com.openiot.platform.web.dto.RuleRequest;
import com.openiot.platform.web.dto.StateResponse;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

  private static final Set<String> OPERATORS = Set.of("gt", "lt", "offline");
  private static final Set<String> LEVELS = Set.of("critical", "warning", "info");

  private final AlarmService alarms;
  private final StateAssembler assembler;
  private final CurrentUserService currentUser;

  public RuleController(
      AlarmService alarms,
      StateAssembler assembler,
      CurrentUserService currentUser) {
    this.alarms = alarms;
    this.assembler = assembler;
    this.currentUser = currentUser;
  }

  @PostMapping
  public StateResponse save(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @RequestBody RuleRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("规则名称不能为空");
    }
    String operator = request.operator() == null ? "gt" : request.operator();
    if (!OPERATORS.contains(operator)) {
      throw new IllegalArgumentException("不支持的比较条件：" + operator);
    }
    if (!"offline".equals(operator) && request.threshold() == null) {
      throw new IllegalArgumentException("阈值不能为空");
    }
    String level = request.level() == null ? "warning" : request.level();
    if (!LEVELS.contains(level)) {
      throw new IllegalArgumentException("不支持的告警等级：" + level);
    }

    AlarmRule rule = new AlarmRule();
    rule.setId(request.id() == null || request.id().isBlank() ? UUID.randomUUID().toString() : request.id());
    rule.setName(request.name().trim());
    rule.setDeviceId(request.deviceId() == null || request.deviceId().isBlank() ? "all" : request.deviceId());
    rule.setProperty(request.property() == null ? "temperature" : request.property());
    rule.setOperator(operator);
    rule.setThreshold("offline".equals(operator) ? null : request.threshold());
    rule.setLevel(level);
    rule.setEnabled(request.enabled() == null || request.enabled());
    alarms.saveRule(rule);
    return assembler.build(currentUser.toUser(email));
  }

  @PatchMapping("/{id}/enabled")
  public StateResponse setEnabled(
      @RequestAttribute(AuthInterceptor.EMAIL_ATTRIBUTE) String email,
      @PathVariable String id,
      @RequestBody RuleEnabledRequest request) {
    alarms.toggleRule(id, Boolean.TRUE.equals(request.enabled()));
    return assembler.build(currentUser.toUser(email));
  }
}
