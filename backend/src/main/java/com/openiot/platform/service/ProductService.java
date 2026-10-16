package com.openiot.platform.service;

import com.openiot.platform.domain.DeviceRepository;
import com.openiot.platform.domain.Product;
import com.openiot.platform.domain.ProductProperty;
import com.openiot.platform.domain.ProductRepository;
import com.openiot.platform.web.dto.ProductRequest;
import com.openiot.platform.web.dto.PropertyRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository products;
  private final DeviceRepository devices;

  public ProductService(ProductRepository products, DeviceRepository devices) {
    this.products = products;
    this.devices = devices;
  }

  public List<Product> list() {
    return products.findAllByOrderByCreatedAtDesc();
  }

  public Product require(String id) {
    return products.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("产品不存在：" + id));
  }

  @Transactional
  public Product create(ProductRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("产品名称不能为空");
    }
    Product product = new Product();
    product.setId(UUID.randomUUID().toString());
    product.setCreatedAt(Instant.now());
    product.setProtocol("MQTT");
    apply(product, request);
    return products.save(product);
  }

  @Transactional
  public Product update(String id, ProductRequest request) {
    Product product = require(id);
    apply(product, request);
    return products.save(product);
  }

  // 产品仍绑定设备时不允许删除。
  @Transactional
  public void delete(String id) {
    require(id);
    if (devices.existsByProductId(id)) {
      throw new IllegalStateException("该产品仍绑定设备，无法删除");
    }
    products.deleteById(id);
  }

  private void apply(Product product, ProductRequest request) {
    if (request.name() != null && !request.name().isBlank()) {
      product.setName(request.name().trim());
    }
    if (request.category() != null && !request.category().isBlank()) {
      product.setCategory(request.category());
    }
    product.setDescription(request.description() == null ? "" : request.description());
    product.setStatus("active".equals(request.status()) ? "active" : "draft");

    List<ProductProperty> properties = new ArrayList<>();
    if (request.properties() != null) {
      for (PropertyRequest item : request.properties()) {
        if (item == null || item.key() == null || item.key().isBlank()) {
          continue;
        }
        ProductProperty property = new ProductProperty();
        property.setId(item.id() == null || item.id().isBlank() ? UUID.randomUUID().toString() : item.id());
        property.setKey(item.key().trim());
        property.setName(item.name() == null || item.name().isBlank() ? item.key().trim() : item.name());
        property.setType(item.type() == null ? "number" : item.type());
        property.setUnit(item.unit() == null ? "" : item.unit());
        property.setMin(item.min());
        property.setMax(item.max());
        property.setOptions(item.options() == null ? null : String.join(",", item.options()));
        properties.add(property);
      }
    }
    if (properties.isEmpty()) {
      throw new IllegalArgumentException("产品至少需要一个属性");
    }
    product.getProperties().clear();
    product.getProperties().addAll(properties);
  }
}
