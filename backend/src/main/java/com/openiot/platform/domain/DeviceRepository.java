package com.openiot.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, String> {

  Optional<Device> findByCode(String code);

  boolean existsByCode(String code);

  boolean existsByProductId(String productId);

  List<Device> findAllByOrderByNameAsc();
}
