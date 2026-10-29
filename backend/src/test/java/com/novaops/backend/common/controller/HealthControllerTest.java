package com.novaops.backend.common.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novaops.backend.auth.controller.AuthController;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.config.WebMvcConfig;
import com.novaops.backend.common.exception.GlobalExceptionHandler;
import com.novaops.backend.common.security.AuthInterceptor;
import com.novaops.backend.common.security.JwtService;
import com.novaops.backend.common.security.PermissionInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HealthController.class, AuthController.class})
@ContextConfiguration(classes = {HealthController.class, AuthController.class, GlobalExceptionHandler.class})
@Import({WebMvcConfig.class, AuthInterceptor.class, PermissionInterceptor.class})
class HealthControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean AuthService authService;
  @MockitoBean AuthMapper authMapper;
  @MockitoBean JwtService jwtService;

  @Test
  void healthIsPublicButRolesRemainProtected() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/auth/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(401));
  }
}
