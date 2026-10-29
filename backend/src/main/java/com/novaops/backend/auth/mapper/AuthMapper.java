package com.novaops.backend.auth.mapper;

import com.novaops.backend.auth.dto.RoleResponse;
import com.novaops.backend.auth.dto.UserListItemResponse;
import com.novaops.backend.auth.dto.UserOptionResponse;
import com.novaops.backend.auth.model.EmailVerificationRecord;
import com.novaops.backend.auth.model.MenuRecord;
import com.novaops.backend.auth.model.RefreshTokenRecord;
import com.novaops.backend.auth.model.UserRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AuthMapper {

  UserRecord findUserByUsername(@Param("username") String username);

  UserRecord findUserById(@Param("userId") String userId);

  List<String> listRolesByUserId(@Param("userId") String userId);

  List<String> listPermissions(@Param("userId") String userId);

  List<MenuRecord> listMenusByScope(@Param("menuScope") String menuScope);

  void insertRefreshToken(
      @Param("token") String token,
      @Param("userId") String userId,
      @Param("expiresAt") LocalDateTime expiresAt
  );

  RefreshTokenRecord findRefreshToken(@Param("token") String token);

  void insertUser(UserRecord user);

  List<RoleResponse> listRoles();

  RoleResponse findRoleById(@Param("roleId") String roleId);

  List<String> listPermissionCodes();
  void insertRole(@Param("id") String id, @Param("code") String code, @Param("name") String name, @Param("description") String description);
  void updateRole(@Param("id") String id, @Param("name") String name, @Param("description") String description);
  void deleteRole(@Param("id") String id);
  void deleteRolePermissions(@Param("id") String id);
  int insertRolePermission(@Param("id") String id, @Param("code") String code);
  long countUsersByRole(@Param("id") String id);
  RoleResponse lockRole(@Param("id") String id);

  List<String> listPermissionsByRoleId(@Param("roleId") String roleId);

  List<UserListItemResponse> listUsers(
      @Param("keyword") String keyword,
      @Param("roleId") String roleId,
      @Param("enabled") Boolean enabled,
      @Param("offset") int offset,
      @Param("pageSize") int pageSize
  );

  long countUsers(
      @Param("keyword") String keyword,
      @Param("roleId") String roleId,
      @Param("enabled") Boolean enabled
  );

  List<UserOptionResponse> listEnabledUserOptions();

  void updateUserStatus(@Param("userId") String userId, @Param("enabled") Boolean enabled);
  void updateUserRole(@Param("userId") String userId, @Param("roleId") String roleId);
  void updateUserPassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash);
  void revokeRefreshTokens(@Param("userId") String userId);
  void revokeRefreshToken(@Param("token") String token);
  int deleteStaleRefreshTokens();

  // --- 邮箱验证 / 注册激活 ---

  UserRecord findUserByEmail(@Param("email") String email);

  void insertEmailVerification(
      @Param("token") String token,
      @Param("userId") String userId,
      @Param("purpose") String purpose,
      @Param("expiresAt") LocalDateTime expiresAt
  );

  EmailVerificationRecord findEmailVerification(@Param("token") String token);

  void markEmailVerificationUsed(@Param("token") String token);

  void activateUser(@Param("userId") String userId);
}
