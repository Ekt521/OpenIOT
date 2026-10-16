package com.openiot.platform.security;

import com.openiot.platform.config.SecurityProperties;
import com.openiot.platform.web.dto.UserDto;
import org.springframework.stereotype.Component;

// 把当前会话映射成平台用户信息。
@Component
public class CurrentUserService {

  private final SecurityProperties security;

  public CurrentUserService(SecurityProperties security) {
    this.security = security;
  }

  public UserDto toUser(String email) {
    return new UserDto("user-admin", security.getAdminName(), email, "管理员");
  }
}
