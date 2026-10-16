package com.openiot.platform.mqtt;

/** Broker 收到设备消息后，交给业务层处理的事件。 */
public record MqttMessageEvent(String topic, String payload) {
}
