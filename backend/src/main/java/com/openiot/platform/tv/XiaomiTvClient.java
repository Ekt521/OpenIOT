package com.openiot.platform.tv;

import com.openiot.platform.config.TvProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

// 小米电视 6095 端口的 HTTP 遥控协议。
// 探活：GET /request?action=isalive
// 按键：GET /controller?action=keyevent&keycode=power
// 这台电视只能证明指令已送达，读不到真实开关与音量，
// 所以调用方必须把结果标成未确认，不能假设电视已经执行。
@Component
public class XiaomiTvClient {

  private final TvProperties properties;
  private final HttpClient httpClient;

  public XiaomiTvClient(TvProperties properties) {
    this.properties = properties;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
        .build();
  }

  public boolean isAlive() {
    return get("/request?action=isalive");
  }

  public boolean sendKey(String keyCode) {
    if (keyCode == null || keyCode.isBlank()) {
      return false;
    }
    String encoded = URLEncoder.encode(keyCode.trim(), StandardCharsets.UTF_8);
    return get("/controller?action=keyevent&keycode=" + encoded);
  }

  private boolean get(String path) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
        .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
        .GET()
        .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() >= 200 && response.statusCode() < 300;
    } catch (Exception ex) {
      return false;
    }
  }

  private String baseUrl() {
    return "http://" + properties.getIp() + ":" + properties.getPort();
  }
}
