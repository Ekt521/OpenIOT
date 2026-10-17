package com.openiot.platform.service;

import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.domain.Product;
import com.openiot.platform.domain.ProductProperty;
import com.openiot.platform.domain.ProductRepository;
import com.openiot.platform.web.dto.DeviceRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final DeviceRepository devices;
  private final ProductRepository products;
  private final DeviceEventService deviceEvents;

  public DeviceService(
      DeviceRepository devices,
      ProductRepository products,
      DeviceEventService deviceEvents) {
    this.devices = devices;
    this.products = products;
    this.deviceEvents = deviceEvents;
  }

  public List<Device> list() {
    return devices.findAllByOrderByNameAsc();
  }

  public Device require(String id) {
    return devices.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("设备不存在：" + id));
  }

  @Transactional
  public Device register(DeviceRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("设备名称不能为空");
    }
    if (request.code() == null || request.code().isBlank()) {
      throw new IllegalArgumentException("设备编号不能为空，编号同时用于 MQTT 主题");
    }
    String code = request.code().trim();
    if (devices.existsByCode(code)) {
      throw new IllegalArgumentException("设备编号已存在：" + code);
    }
    String productId = request.productId() == null ? "" : request.productId();
    Product product = products.findById(productId)
        .orElseThrow(() -> new IllegalArgumentException("产品不存在，请先创建产品"));

    Device device = new Device();
    device.setId(UUID.randomUUID().toString());
    device.setProductId(product.getId());
    device.setCode(code);
    device.setName(request.name().trim());
    device.setLocation(request.location() == null ? "" : request.location());
    device.setDescription(request.description() == null ? "" : request.description());
    device.setEnabled(true);
    device.setOnline(false);
    // 红外网关和电视都只能证明「已发送」，其余设备才标记为可确认状态。
    switch (product.getCategory() == null ? "" : product.getCategory()) {
      case "红外网关" -> {
        device.setConfirmation("gateway");
        device.setControlProfile("ir");
      }
      case "电视" -> {
        device.setConfirmation("gateway");
        device.setControlProfile("tv");
      }
      default -> {
        device.setConfirmation("state");
        device.setControlProfile("switch");
      }
    }
    device.setCapabilities(String.join(",",
        product.getProperties().stream().map(ProductProperty::getName).toList()));
    device.setState(initialState(product));
    device.setTargetState(new LinkedHashMap<>());
    device.setCredential("oi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    devices.save(device);

    deviceEvents.record(device.getId(), "config", "设备已创建，等待首次连接");
    return device;
  }

  private Map<String, Object> initialState(Product product) {
    Map<String, Object> state = new LinkedHashMap<>();
    for (ProductProperty property : product.getProperties()) {
      String type = property.getType() == null ? "number" : property.getType();
      switch (type) {
        case "boolean" -> state.put(property.getKey(), false);
        case "number" -> state.put(property.getKey(), 0);
        default -> state.put(property.getKey(), "");
      }
    }
    return state;
  }

  @Transactional
  public Device setEnabled(String deviceId, boolean enabled) {
    Device device = require(deviceId);
    device.setEnabled(enabled);
    if (!enabled) {
      device.setOnline(false);
    }
    devices.save(device);
    deviceEvents.record(device.getId(), "config", enabled ? "设备已启用" : "设备已停用");
    return device;
  }

  // 处理设备通过 MQTT 上报的上下线状态，包含遗嘱消息。
  @Transactional
  public void onStatus(String deviceCode, boolean online) {
    Device device = devices.findByCode(deviceCode).orElse(null);
    if (device == null) {
      return;
    }
    boolean changed = device.isOnline() != online;
    device.setOnline(online && device.isEnabled());
    device.setLastSeen(Instant.now());
    devices.save(device);
    if (changed) {
      deviceEvents.record(device.getId(), online ? "online" : "offline",
          online ? "设备已上线" : "设备已离线");
    }
  }
}
