package com.openiot.platform.service;

import com.openiot.platform.domain.AlarmRecord;
import com.openiot.platform.domain.AlarmRecordRepository;
import com.openiot.platform.domain.AlarmRule;
import com.openiot.platform.domain.AlarmRuleRepository;
import com.openiot.platform.domain.Device;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 告警规则判定与告警生命周期。 */
@Service
public class AlarmService {

  private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

  private final AlarmRuleRepository rules;
  private final AlarmRecordRepository alarms;
  private final DeviceEventService deviceEvents;

  public AlarmService(
      AlarmRuleRepository rules,
      AlarmRecordRepository alarms,
      DeviceEventService deviceEvents) {
    this.rules = rules;
    this.alarms = alarms;
    this.deviceEvents = deviceEvents;
  }

  /** 根据设备当前状态判断是否需要新建告警。同一规则同一设备只保留一条未关闭告警。 */
  @Transactional
  public void evaluate(Device device) {
    for (AlarmRule rule : rules.findByEnabledTrue()) {
      if (!"all".equals(rule.getDeviceId()) && !rule.getDeviceId().equals(device.getId())) {
        continue;
      }
      String message = triggeredMessage(rule, device);
      if (message == null) {
        continue;
      }
      if (alarms.findFirstByRuleIdAndDeviceIdAndStatusNot(rule.getId(), device.getId(), "closed").isPresent()) {
        continue;
      }
      AlarmRecord alarm = new AlarmRecord();
      alarm.setId(UUID.randomUUID().toString());
      alarm.setRuleId(rule.getId());
      alarm.setRuleName(rule.getName());
      alarm.setDeviceId(device.getId());
      alarm.setDeviceName(device.getName());
      alarm.setMessage(message);
      alarm.setLevel(rule.getLevel());
      alarm.setStatus("active");
      alarm.setCreatedAt(Instant.now());
      alarm.setNote("");
      alarms.save(alarm);
      deviceEvents.record(device.getId(), "config", "触发告警：" + rule.getName());
      log.info("设备 {} 触发告警规则 {}：{}", device.getName(), rule.getName(), message);
    }
  }

  /** 返回触发原因，未触发返回 null。 */
  private String triggeredMessage(AlarmRule rule, Device device) {
    if ("offline".equals(rule.getOperator())) {
      return device.isOnline() ? null : "设备已离线";
    }
    Object raw = device.getState().get(rule.getProperty());
    if (!(raw instanceof Number value) || rule.getThreshold() == null) {
      return null;
    }
    double actual = value.doubleValue();
    double threshold = rule.getThreshold();
    if ("gt".equals(rule.getOperator()) && actual > threshold) {
      return rule.getProperty() + " " + actual + " 高于阈值 " + threshold;
    }
    if ("lt".equals(rule.getOperator()) && actual < threshold) {
      return rule.getProperty() + " " + actual + " 低于阈值 " + threshold;
    }
    return null;
  }

  @Transactional
  public AlarmRecord acknowledge(String alarmId, String note) {
    AlarmRecord alarm = alarms.findById(alarmId)
        .orElseThrow(() -> new IllegalArgumentException("告警不存在：" + alarmId));
    if (!"active".equals(alarm.getStatus())) {
      return alarm;
    }
    alarm.setStatus("acknowledged");
    alarm.setAcknowledgedAt(Instant.now());
    if (note != null && !note.isBlank()) {
      alarm.setNote(note.trim());
    }
    return alarms.save(alarm);
  }

  @Transactional
  public AlarmRecord close(String alarmId, String note) {
    AlarmRecord alarm = alarms.findById(alarmId)
        .orElseThrow(() -> new IllegalArgumentException("告警不存在：" + alarmId));
    if ("closed".equals(alarm.getStatus())) {
      return alarm;
    }
    alarm.setStatus("closed");
    alarm.setClosedAt(Instant.now());
    if (note != null && !note.isBlank()) {
      alarm.setNote(note.trim());
    }
    return alarms.save(alarm);
  }

  @Transactional
  public AlarmRule saveRule(AlarmRule rule) {
    return rules.save(rule);
  }

  @Transactional
  public AlarmRule toggleRule(String ruleId, boolean enabled) {
    AlarmRule rule = rules.findById(ruleId)
        .orElseThrow(() -> new IllegalArgumentException("告警规则不存在：" + ruleId));
    rule.setEnabled(enabled);
    return rules.save(rule);
  }

  public List<AlarmRule> listRules() {
    return rules.findAllByOrderByNameAsc();
  }

  public List<AlarmRecord> listAlarms() {
    return alarms.findAllByOrderByCreatedAtDesc();
  }
}
