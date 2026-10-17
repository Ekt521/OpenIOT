package com.openiot.platform.tv;

import static org.assertj.core.api.Assertions.assertThat;

import com.openiot.platform.config.TvProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XiaomiTvClientTest {

  private HttpServer server;
  private final AtomicReference<String> lastPath = new AtomicReference<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      lastPath.set(exchange.getRequestURI().toString());
      byte[] body = "{\"status\":0,\"msg\":\"success\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void probesAliveAndSendsKeyCodeOverHttp() {
    TvProperties properties = new TvProperties();
    properties.setIp("127.0.0.1");
    properties.setPort(server.getAddress().getPort());
    properties.setTimeoutMillis(2000);
    XiaomiTvClient client = new XiaomiTvClient(properties);

    assertThat(client.isAlive()).isTrue();
    assertThat(lastPath.get()).contains("action=isalive");

    assertThat(client.sendKey("volumeup")).isTrue();
    assertThat(lastPath.get()).contains("keycode=volumeup");

    assertThat(client.sendKey(" ")).isFalse();
  }
}
