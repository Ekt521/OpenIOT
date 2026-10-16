package com.openiot.platform.web;

import com.openiot.platform.config.SecurityProperties;
import com.openiot.platform.security.AuthInterceptor;
import com.openiot.platform.security.CurrentUserService;
import com.openiot.platform.security.TokenStore;
import com.openiot.platform.web.dto.LoginRequest;
import com.openiot.platform.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final SecurityProperties security;
  private final TokenStore tokens;
  private final CurrentUserService currentUser;

  public AuthController(
      SecurityProperties security,
      TokenStore tokens,
      CurrentUserService currentUser) {
    this.security = security;
    this.tokens = tokens;
    this.currentUser = currentUser;
  }

  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    String email = request.email() == null ? "" : request.email().trim();
    String password = request.password() == null ? "" : request.password();
    if (!email.equalsIgnoreCase(security.getAdminEmail()) || !password.equals(security.getAdminPassword())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "邮箱或密码不正确");
    }
    String token = tokens.issue(email);
    return new LoginResponse(token, currentUser.toUser(email));
  }

  @PostMapping("/logout")
  public void logout(HttpServletRequest request) {
    tokens.revoke(AuthInterceptor.bearerToken(request));
  }
}
