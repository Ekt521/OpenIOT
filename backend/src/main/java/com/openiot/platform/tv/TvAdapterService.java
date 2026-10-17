package com.openiot.platform.tv;

import com.openiot.platform.config.TvProperties;
import com.openiot.platform.domain.Device;
import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.domain.Product;
import com.openiot.platform.domain.ProductRepository;
import com.openiot.platform.service.DeviceEventService;
import com.openiot.platform.service.DeviceService;
import com.openiot.platform.service.ProductService;
import com.openiot.platform.web.dto.DeviceRequest;
import com.openiot.platform.web.dto.ProductRequest;
import com.openiot.platform.web.dto.PropertyRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 把局域网里的小米电视接成平台设备。
// 适配器负责探活与转发按键，不经过 MQTT：电视只会说 HTTP，
// 强行要求它走 MQTT 反而要多维护一个桥接进程。
@Service
public class TvAdapterService {

  private static final Logger log = LoggerFactory.getLogger(TvAdapterService.class);

  private final TvProperties properties;
  private final XiaomiTvClient client;
  private final DeviceRepository devices;
  private final ProductRepository products;
  private final ProductService productService;
  private final DeviceService deviceService;
  private final DeviceEventService deviceEvents;

  public TvAdapterService(
      TvProperties properties,
      XiaomiTvClient client,
      DeviceRepository devices,
      ProductRepository products,
      ProductService productService,
      DeviceService deviceService,
      DeviceEventService deviceEvents) {
    this.properties = properties;
    this.client = client;
    this.devices = devices;
    this.products = products;
    this.productService = productService;
    this.deviceService = deviceService;
    this.deviceEvents = deviceEvents;
  }

  // 启动时确保电视对应的产品和设备已存在，避免手工录入出错。
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void provision() {
    if (!properties.isEnabled()) {
      return;
    }
    Product product = findOrCreateProduct();
    Device device = devices.findByCode(properties.getCode()).orElse(null);
    if (device == null) {
      deviceService.register(new DeviceRequest(
          properties.getName(),
          product.getId(),
          properties.getCode(),
          properties.getLocation(),
          "小米电视局域网适配器自动接入，仅确认指令已送达。"));
      log.info("已自动登记电视设备 {}（{}）", properties.getName(), properties.getCode());
    } else if (!"tv".equals(device.getControlProfile())) {
      device.setControlProfile("tv");
      devices.save(device);
    }
    log.info("电视适配器已启用：{} @ {}:{}", properties.getName(), properties.getIp(), properties.getPort());
  }

  private Product findOrCreateProduct() {
    return products.findAllByOrderByCreatedAtDesc().stream()
        .filter(item -> properties.getProductName().equals(item.getName()))
        .findFirst()
        .orElseGet(() -> productService.create(new ProductRequest(
            properties.getProductName(),
            "电视",
            "局域网可遥控的小米电视，只能确认指令已送达。",
            "active",
            List.of(new PropertyRequest(null, "网络可达", "online", "boolean", "", null, null, List.of())))));
  }

  @Scheduled(
      fixedDelayString = "${openiot.tv.poll-interval-millis:15000}",
      initialDelayString = "${openiot.tv.poll-interval-millis:15000}")
  @Transactional
  public void poll() {
    if (!properties.isEnabled()) {
      return;
    }
    Device device = devices.findByCode(properties.getCode()).orElse(null);
    if (device == null) {
      return;
    }
    boolean alive = client.isAlive();
    boolean changed = device.isOnline() != alive;
    device.setOnline(alive);
    if (alive) {
      device.setLastSeen(Instant.now());
    }
    Map<String, Object> state = new LinkedHashMap<>(device.getState());
    state.put("online", alive);
    device.setState(state);
    devices.save(device);
    if (changed) {
      deviceEvents.record(device.getId(), alive ? "online" : "offline",
          alive ? "电视网络可达" : "电视不可达，可能是关机或 IP 变化");
    }
  }

  // 供命令服务调用，把平台命令转成电视按键。
  public boolean sendKey(String keyCode) {
    boolean sent = client.sendKey(keyCode);
    if (!sent) {
      log.warn("电视 {} 按键 {} 发送失败，请检查 IP 与电视是否开机", properties.getIp(), keyCode);
    }
    return sent;
  }
}
