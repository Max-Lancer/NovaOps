package com.novaops.backend.common.exception;

import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.web.RequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception, HttpServletRequest request) {
    request.setAttribute(RequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
    return ResponseEntity.ok(ApiResponse.failure(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Object>> handleValidationException(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    request.setAttribute(RequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
    String message = exception.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("；"));
    return ResponseEntity.ok(ApiResponse.failure(400, message.isBlank() ? "请求参数不合法" : message));
  }

  // @ModelAttribute + @Valid 的 query 参数校验失败走 BindException（如分页超上限）
  @ExceptionHandler(org.springframework.validation.BindException.class)
  public ResponseEntity<ApiResponse<Object>> handleBindException(
      org.springframework.validation.BindException exception, HttpServletRequest request) {
    request.setAttribute(RequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
    String message = exception.getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("；"));
    return ResponseEntity.ok(ApiResponse.failure(400, message.isBlank() ? "请求参数不合法" : message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    request.setAttribute(RequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
    return ResponseEntity.ok(ApiResponse.failure(400, exception.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(
      MaxUploadSizeExceededException exception, HttpServletRequest request) {
    request.setAttribute(RequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
    return ResponseEntity.ok(ApiResponse.failure(400, "上传文件超过允许的大小上限"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleException(Exception exception, HttpServletRequest request) {
    // 未知异常必须留堆栈，否则线上问题无法排查；响应契约保持 HTTP 200 + body code
    request.setAttribute(RequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
    log.error("unhandled exception", exception);
    return ResponseEntity.ok(ApiResponse.failure(500, "服务异常，请稍后重试"));
  }
}
