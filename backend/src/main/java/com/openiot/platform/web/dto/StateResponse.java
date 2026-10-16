package com.openiot.platform.web.dto;

import java.util.List;

/** 前端一次拉取的完整平台状态快照。 */
public record StateResponse(
    UserDto user,
    List<ProductDto> products,
    List<DeviceDto> devices,
    List<CommandDto> commands,
    List<RuleDto> rules,
    List<AlarmDto> alarms,
    MqttDto mqtt) {
}
