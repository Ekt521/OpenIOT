package com.openiot.platform.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MqttSettingRepository extends JpaRepository<MqttSetting, String> {
}
