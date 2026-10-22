package com.novaops.backend.system.web;

import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.common.web.RequestAttributes;
import com.novaops.backend.system.model.AccessLogRecord;
import com.novaops.backend.system.service.AccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

  private final AccessLogService accessLogService;

  public AccessLogFilter(AccessLogService accessLogService) {
    this.accessLogService = accessLogService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || !path.startsWith("/api/")) {
      return true;
    }
    return path.startsWith("/api/system/health")
        || path.startsWith("/api/system/logs")
        || "/api/auth/login".equals(path)
        || "/api/auth/refresh".equals(path);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    String requestId = request.getHeader("X-Request-Id");
    if (!StringUtils.hasText(requestId) || requestId.length() > 64) {
      requestId = IdGenerator.randomId("req");
    }
    response.setHeader("X-Request-Id", requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      writeLog(request, response, requestId, started);
    }
  }

  private void writeLog(HttpServletRequest request, HttpServletResponse response, String requestId, long started) {
    try {
      AccessLogRecord record = new AccessLogRecord();
      record.setRequestId(requestId);
      record.setMethod(request.getMethod());
      record.setPath(request.getRequestURI());
      record.setHttpStatus(response.getStatus());
      record.setDurationMs((int) Math.min((System.nanoTime() - started) / 1_000_000, Integer.MAX_VALUE));
      Object username = request.getAttribute(RequestAttributes.USERNAME);
      record.setUsername(username instanceof String value ? value : null);
      Object code = request.getAttribute(RequestAttributes.RESULT_CODE);
      record.setResultCode(code instanceof Integer value ? value : null);
      Object message = request.getAttribute(RequestAttributes.RESULT_MESSAGE);
      record.setResultMessage(message instanceof String value ? value : null);
      Object exceptionType = request.getAttribute(RequestAttributes.EXCEPTION_TYPE);
      record.setExceptionType(exceptionType instanceof String value ? value : null);
      accessLogService.record(record);
    } catch (Exception exception) {
      log.warn("write access log failed", exception);
    }
  }
}
