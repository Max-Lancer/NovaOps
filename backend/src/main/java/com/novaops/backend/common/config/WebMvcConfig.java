package com.novaops.backend.common.config;

import com.novaops.backend.common.security.AuthInterceptor;
import com.novaops.backend.common.security.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final PermissionInterceptor permissionInterceptor;

  public WebMvcConfig(AuthInterceptor authInterceptor, PermissionInterceptor permissionInterceptor) {
    this.authInterceptor = authInterceptor;
    this.permissionInterceptor = permissionInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/health", "/api/auth/login", "/api/auth/refresh", "/api/auth/register", "/api/auth/verify", "/api/mcp");
    // 权限拦截器必须注册在认证拦截器之后：先解析会话，再按注解校验权限码
    registry.addInterceptor(permissionInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/health", "/api/auth/login", "/api/auth/refresh", "/api/auth/register", "/api/auth/verify", "/api/mcp");
  }
}
