package com.openiot.platform.mqtt;

import java.util.Map;

/**
 * 设备回报的命令执行结果。
 * {"type":"commandAck","deviceCode":"OI-240018","commandId":"...","status":"confirmed","state":{"power":true}}
 */
public record CommandAckPayload(
    String type,
    String deviceCode,
    String commandId,
    String status,
    String message,
    Map<String, Object> state) {
}
