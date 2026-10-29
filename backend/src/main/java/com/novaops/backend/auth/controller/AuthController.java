package com.novaops.backend.auth.controller;

import com.novaops.backend.auth.dto.AuthTokenResponse;
import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.LoginResponse;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.RegisterRequest;
import com.novaops.backend.auth.dto.RegisterResponse;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.RoleSaveRequest;
import com.novaops.backend.auth.dto.PermissionOption;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.novaops.backend.auth.dto.UserProfileResponse;
import com.novaops.backend.auth.dto.UserListQuery;
import com.novaops.backend.auth.dto.UserListItemResponse;
import com.novaops.backend.auth.dto.UserOptionResponse;
import com.novaops.backend.auth.dto.UpdateUserStatusRequest;
import com.novaops.backend.auth.dto.UpdateUserRoleRequest;
import com.novaops.backend.auth.dto.ResetPasswordRequest;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.ApiResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.security.RequestContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request), "登录成功");
  }

  @PostMapping("/register")
  public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.success(authService.register(request), "注册成功");
  }

  @GetMapping("/verify")
  public ApiResponse<Void> verify(@org.springframework.web.bind.annotation.RequestParam("token") String token) {
    authService.verify(token);
    return ApiResponse.success(null, "激活成功，请登录");
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(authService.refresh(request), "刷新成功");
  }

  @GetMapping("/me")
  public ApiResponse<UserProfileResponse> me() {
    return ApiResponse.success(authService.me(RequestContext.getRequired()));
  }

  @GetMapping("/menu")
  public ApiResponse<MenuDataResponse> menu() {
    return ApiResponse.success(authService.menu(RequestContext.getRequired()));
  }

  @GetMapping("/roles")
  public ApiResponse<List<RoleResponse>> roles() {
    authService.assertAdmin(RequestContext.getRequired());
    return ApiResponse.success(authService.listRoles());
  }

  @GetMapping("/permissions")
  public ApiResponse<List<PermissionOption>> permissions() {
    return ApiResponse.success(authService.permissionCatalog(RequestContext.getRequired()));
  }

  @PostMapping("/roles")
  public ApiResponse<Void> createRole(@Valid @RequestBody RoleSaveRequest request) {
    authService.createRole(RequestContext.getRequired(), request);
    return ApiResponse.success(null, "角色已创建");
  }

  @PutMapping("/roles/{id}")
  public ApiResponse<Void> saveRole(@PathVariable String id, @Valid @RequestBody RoleSaveRequest request) {
    authService.updateRole(RequestContext.getRequired(), id, request);
    return ApiResponse.success(null, "角色已更新");
  }

  @DeleteMapping("/roles/{id}")
  public ApiResponse<Void> deleteRole(@PathVariable String id) {
    authService.deleteRole(RequestContext.getRequired(), id);
    return ApiResponse.success(null, "角色已删除");
  }

  @GetMapping("/users")
  public ApiResponse<PageResult<UserListItemResponse>> users(@Valid UserListQuery query) {
    return ApiResponse.success(authService.listUsers(RequestContext.getRequired(), query));
  }

  // 启用用户选项（工单创建/指派/转派、资产领用共用）
  @GetMapping("/user-options")
  public ApiResponse<List<UserOptionResponse>> userOptions() {
    authService.requireAnyPermission(
        RequestContext.getRequired(),
        "ticket:create",
        "ticket:assign",
        "ticket:transfer",
        "asset:claim"
    );
    return ApiResponse.success(authService.listUserOptions());
  }

  @PutMapping("/users/{id}/status")
  public ApiResponse<Void> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateUserStatusRequest request) {
    authService.updateUserStatus(RequestContext.getRequired(), id, request.getEnabled());
    return ApiResponse.success(null, "用户状态已更新");
  }

  @PutMapping("/users/{id}/role")
  public ApiResponse<Void> updateRole(@PathVariable String id, @Valid @RequestBody UpdateUserRoleRequest request) {
    authService.updateUserRole(RequestContext.getRequired(), id, request.getRoleId());
    return ApiResponse.success(null, "用户身份已更新");
  }

  @PutMapping("/users/{id}/password")
  public ApiResponse<Void> resetPassword(@PathVariable String id, @Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(RequestContext.getRequired(), id, request.getPassword());
    return ApiResponse.success(null, "密码已重置");
  }
}
