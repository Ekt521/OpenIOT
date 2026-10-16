package com.openiot.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 校验 Authorization: Bearer <token>，为 /api/** 提供最基础的访问控制。
@Component
public class AuthInterceptor implements HandlerInterceptor {

  public static final String EMAIL_ATTRIBUTE = "openiot.email";

  private final TokenStore tokens;

  public AuthInterceptor(TokenStore tokens) {
    this.tokens = tokens;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String token = bearerToken(request);
    var email = tokens.resolve(token);
    if (email.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"message\":\"登录状态已失效，请重新登录\"}");
      return false;
    }
    request.setAttribute(EMAIL_ATTRIBUTE, email.get());
    return true;
  }

  public static String bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring("Bearer ".length()).trim();
    }
    return null;
  }
}
