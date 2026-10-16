package com.openiot.platform.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// 会话令牌存储。MVP 阶段放在内存里，平台重启后需要重新登录；
// 接入正式用户体系时应替换为数据库或统一认证服务。
@Component
public class TokenStore {

  private static final Duration TTL = Duration.ofHours(12);

  private record Session(String email, Instant expiresAt) {
  }

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  public String issue(String email) {
    String token = UUID.randomUUID().toString().replace("-", "");
    sessions.put(token, new Session(email, Instant.now().plus(TTL)));
    return token;
  }

  public Optional<String> resolve(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    Session session = sessions.get(token);
    if (session == null) {
      return Optional.empty();
    }
    if (session.expiresAt().isBefore(Instant.now())) {
      sessions.remove(token);
      return Optional.empty();
    }
    return Optional.of(session.email());
  }

  public void revoke(String token) {
    if (token != null) {
      sessions.remove(token);
    }
  }
}
