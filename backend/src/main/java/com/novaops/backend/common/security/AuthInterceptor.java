package com.novaops.backend.common.security;

import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.web.RequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import com.novaops.backend.auth.service.AuthService;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

  private final JwtService jwtService;
  private final AuthService authService;

  public AuthInterceptor(JwtService jwtService, AuthService authService) {
    this.jwtService = jwtService;
    this.authService = authService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 防御：容器线程池复用时，先清掉可能残留的上一请求会话
    RequestContext.clear();

    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(401, "token 无效");
    }

    String token = authorization.substring("Bearer ".length()).trim();
    CurrentSession session = jwtService.parseAccessToken(token);
    authService.requireEnabledUser(session.getUserId());
    RequestContext.set(session);
    request.setAttribute(RequestAttributes.USERNAME, session.getUsername());
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    RequestContext.clear();
  }
}
