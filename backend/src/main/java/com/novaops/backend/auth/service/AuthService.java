package com.novaops.backend.auth.service;

import com.novaops.backend.auth.dto.AuthTokenResponse;
import com.novaops.backend.auth.dto.LoginRequest;
import com.novaops.backend.auth.dto.LoginResponse;
import com.novaops.backend.auth.dto.MenuDataResponse;
import com.novaops.backend.auth.dto.MenuItemResponse;
import com.novaops.backend.auth.dto.RefreshTokenRequest;
import com.novaops.backend.auth.dto.RegisterRequest;
import com.novaops.backend.auth.dto.RegisterResponse;
import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.UserProfileResponse;
import com.novaops.backend.auth.dto.UserListQuery;
import com.novaops.backend.auth.dto.UserListItemResponse;
import com.novaops.backend.auth.dto.UserOptionResponse;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.auth.mail.EmailSender;
import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.model.EmailVerificationRecord;
import com.novaops.backend.auth.model.MenuRecord;
import com.novaops.backend.auth.model.RefreshTokenRecord;
import com.novaops.backend.auth.model.UserRecord;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.security.JwtService;
import com.novaops.backend.common.util.IdGenerator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

  private final AuthMapper authMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LoginFailureGuard loginFailureGuard;
  private final EmailSender emailSender;

  public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, JwtService jwtService, LoginFailureGuard loginFailureGuard, EmailSender emailSender) {
    this.authMapper = authMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.loginFailureGuard = loginFailureGuard;
    this.emailSender = emailSender;
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    loginFailureGuard.assertNotLocked(request.getUsername());

    UserRecord user = authMapper.findUserByUsername(request.getUsername());

    if (user == null) {
      // 用户不存在 → 不再自助注册，统一返回模糊错误避免账号枚举
      loginFailureGuard.recordFailure(request.getUsername());
      throw new BusinessException(403, "账号或密码错误");
    }

    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(403, "账号未激活或已被禁用");
    }
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      loginFailureGuard.recordFailure(request.getUsername());
      throw new BusinessException(403, "账号或密码错误");
    }

    loginFailureGuard.reset(request.getUsername());
    return issueLoginSession(user);
  }

  /** 显式注册：创建未激活账号（enabled=0），生成验证 token 并发送激活邮件。 */
  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    String username = request.getUsername().trim();
    String email = request.getEmail().trim().toLowerCase();

    if (authMapper.findUserByUsername(username) != null) {
      throw new BusinessException(409, "账号已存在");
    }
    if (authMapper.findUserByEmail(email) != null) {
      throw new BusinessException(409, "邮箱已被注册");
    }

    RoleResponse memberRole = authMapper.findRoleById("role-member");
    if (memberRole == null) {
      throw new BusinessException(500, "默认身份未配置");
    }

    UserRecord user = new UserRecord();
    user.setId(IdGenerator.randomId("usr"));
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setDisplayName(username);
    user.setRoleId(memberRole.getId());
    authMapper.insertUser(user);

    String token = IdGenerator.randomId("ev");
    authMapper.insertEmailVerification(token, user.getId(), "register", LocalDateTime.now().plusHours(24));
    emailSender.sendVerificationEmail(email, token);
    // log 降级模式：token 直接交回前端跳转激活页；smtp 模式为 null，仅走邮件
    return new RegisterResponse(emailSender.activationTokenFor(token));
  }

  /** 邮箱激活：校验 token 后启用账号并强制首次改密。 */
  @Transactional
  public void verify(String token) {
    EmailVerificationRecord record = authMapper.findEmailVerification(token);
    if (record == null || Boolean.TRUE.equals(record.getUsed())) {
      throw new BusinessException(400, "激活链接无效或已使用");
    }
    if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException(400, "激活链接已过期");
    }
    UserRecord user = authMapper.findUserById(record.getUserId());
    if (user == null) {
      throw new BusinessException(404, "用户不存在");
    }
    if (Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(400, "账号已激活，请直接登录");
    }
    authMapper.activateUser(user.getId());
    authMapper.markEmailVerificationUsed(token);
  }

  public AuthTokenResponse refresh(RefreshTokenRequest request) {
    RefreshTokenRecord record = authMapper.findRefreshToken(request.getRefreshToken());
    if (record == null || Boolean.TRUE.equals(record.getRevoked()) || record.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException(401, "refresh token 已失效");
    }

    UserRecord user = authMapper.findUserById(record.getUserId());
    if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
      throw new BusinessException(401, "refresh token 已失效");
    }

    // 轮换：旧的 refresh token 立即作废，签发新 token，降低泄露后的重放窗口
    authMapper.revokeRefreshToken(record.getToken());
    String rotatedRefreshToken = IdGenerator.randomId("rt");
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtService.getRefreshTokenExpireDays());
    authMapper.insertRefreshToken(rotatedRefreshToken, user.getId(), expiresAt);
    return buildTokenResponse(user, rotatedRefreshToken);
  }

  public UserProfileResponse me(CurrentSession session) {
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null) {
      throw new BusinessException(401, "token 无效");
    }

    UserProfileResponse response = new UserProfileResponse();
    response.setId(user.getId());
    response.setUsername(user.getUsername());
    response.setDisplayName(user.getDisplayName());
    response.setRoles(authMapper.listRolesByUserId(user.getId()));
    response.setPermissions(authMapper.listPermissions(user.getId()));
    return response;
  }

  public MenuDataResponse menu(CurrentSession session) {
    UserRecord user = authMapper.findUserById(session.getUserId());
    if (user == null) {
      throw new BusinessException(401, "token 无效");
    }

    List<String> permissions = authMapper.listPermissions(user.getId());
    List<String> roles = authMapper.listRolesByUserId(user.getId());
    String menuScope = roles.isEmpty() ? "guest" : resolveMenuScopeByRole(roles.get(0));
    List<MenuRecord> menuRecords = authMapper.listMenusByScope(menuScope);
    MenuDataResponse response = new MenuDataResponse();
    response.setPermissions(permissions);
    response.setMenus(buildMenuTree(menuRecords));
    return response;
  }

  public List<RoleResponse> listRoles() {
    List<RoleResponse> roles = authMapper.listRoles();
    roles.forEach(role -> role.setPermissions(authMapper.listPermissionsByRoleId(role.getId())));
    return roles;
  }

  public List<UserOptionResponse> listUserOptions() {
    return authMapper.listEnabledUserOptions();
  }

  public PageResult<UserListItemResponse> listUsers(CurrentSession session, UserListQuery query) {
    requireAdmin(session);
    int offset = (query.getPage() - 1) * query.getPageSize();
    List<UserListItemResponse> list = authMapper.listUsers(query.getKeyword(), query.getRoleId(), query.getEnabled(), offset, query.getPageSize());
    long total = authMapper.countUsers(query.getKeyword(), query.getRoleId(), query.getEnabled());
    return new PageResult<>(list, query.getPage(), query.getPageSize(), total);
  }

  public void updateUserStatus(CurrentSession session, String userId, boolean enabled) {
    requireAdmin(session);
    if (session.getUserId().equals(userId) && !enabled) throw new BusinessException(400, "不能禁用当前登录账号");
    requireUser(userId);
    authMapper.updateUserStatus(userId, enabled);
    authMapper.revokeRefreshTokens(userId);
  }

  public void updateUserRole(CurrentSession session, String userId, String roleId) {
    requireAdmin(session);
    if (authMapper.findRoleById(roleId) == null) throw new BusinessException(400, "身份不存在");
    requireUser(userId);
    authMapper.updateUserRole(userId, roleId);
    authMapper.revokeRefreshTokens(userId);
  }

  public void resetPassword(CurrentSession session, String userId, String password) {
    requireAdmin(session);
    requireUser(userId);
    authMapper.updateUserPassword(userId, passwordEncoder.encode(password));
    authMapper.revokeRefreshTokens(userId);
  }

  public UserRecord requireEnabledUser(String userId) {
    UserRecord user = requireUser(userId);
    if (!Boolean.TRUE.equals(user.getEnabled())) throw new BusinessException(403, "账号已被禁用");
    return user;
  }

  private UserRecord requireUser(String userId) {
    UserRecord user = authMapper.findUserById(userId);
    if (user == null) throw new BusinessException(404, "用户不存在");
    return user;
  }

  private void requireAdmin(CurrentSession session) {
    UserRecord user = requireEnabledUser(session.getUserId());
    RoleResponse role = authMapper.findRoleById(user.getRoleId());
    if (role == null || !"admin".equals(role.getCode())) throw new BusinessException(403, "仅系统管理员可操作");
  }

  public void assertAdmin(CurrentSession session) {
    requireAdmin(session);
  }

  /** 校验当前会话是否持有权限码，供无法用注解表达的场景（如工单按动作细分） */
  public void requirePermission(CurrentSession session, String code) {
    if (!authMapper.listPermissions(session.getUserId()).contains(code)) {
      throw new BusinessException(403, "无权限执行该操作");
    }
  }

  /** 调用者持有任意一个权限码即可，用于多个业务共用的只读接口。 */
  public void requireAnyPermission(CurrentSession session, String... codes) {
    List<String> granted = authMapper.listPermissions(session.getUserId());
    for (String code : codes) {
      if (granted.contains(code)) {
        return;
      }
    }
    throw new BusinessException(403, "无权限执行该操作");
  }

  private LoginResponse issueLoginSession(UserRecord user) {
    String refreshToken = IdGenerator.randomId("rt");
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(jwtService.getRefreshTokenExpireDays());
    authMapper.insertRefreshToken(refreshToken, user.getId(), expiresAt);

    AuthTokenResponse tokenResponse = buildTokenResponse(user, refreshToken);
    LoginResponse response = new LoginResponse();
    response.setAccessToken(tokenResponse.getAccessToken());
    response.setRefreshToken(tokenResponse.getRefreshToken());
    response.setExpiresIn(tokenResponse.getExpiresIn());
    return response;
  }

  private AuthTokenResponse buildTokenResponse(UserRecord user, String refreshToken) {
    CurrentSession session = new CurrentSession(user.getId(), user.getUsername(), user.getDisplayName());
    AuthTokenResponse response = new AuthTokenResponse();
    response.setAccessToken(jwtService.createAccessToken(session));
    response.setRefreshToken(refreshToken);
    response.setExpiresIn(jwtService.getAccessTokenExpireSeconds());
    return response;
  }

  private String resolveMenuScopeByRole(String roleCode) {
    if ("guest".equals(roleCode)) {
      return "guest";
    }
    if ("member".equals(roleCode)) {
      return "member";
    }
    if ("staff".equals(roleCode)) {
      return "staff";
    }
    return "full";
  }

  private List<MenuItemResponse> buildMenuTree(List<MenuRecord> menuRecords) {
    Map<String, MenuItemResponse> menuMap = new LinkedHashMap<>();
    for (MenuRecord record : menuRecords) {
      MenuItemResponse response = new MenuItemResponse();
      response.setId(record.getId());
      response.setTitle(record.getTitle());
      response.setName(record.getName());
      response.setPath(record.getPath());
      response.setComponent(record.getComponent());
      response.setIcon(record.getIcon());
      response.setPermission(record.getPermissionCode());
      response.setKeepAlive(record.getKeepAlive());
      response.setChildren(new ArrayList<>());
      menuMap.put(record.getId(), response);
    }

    List<MenuItemResponse> roots = new ArrayList<>();
    for (MenuRecord record : menuRecords) {
      MenuItemResponse current = menuMap.get(record.getId());
      if (!StringUtils.hasText(record.getParentId())) {
        roots.add(current);
        continue;
      }
      MenuItemResponse parent = menuMap.get(record.getParentId());
      if (parent != null) {
        parent.getChildren().add(current);
      }
    }

    roots.forEach(this::normalizeChildren);
    return roots;
  }

  private void normalizeChildren(MenuItemResponse menu) {
    if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
      menu.setChildren(null);
      return;
    }
    menu.getChildren().forEach(this::normalizeChildren);
  }
}
