package com.novaops.backend.kb.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.novaops.backend.auth.mapper.AuthMapper;
import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.kb.dto.KbDocumentQuery;
import com.novaops.backend.kb.mapper.KbMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KbPermissionTest {
  @Test
  void readOnlyRoleCanListButCannotModify() {
    AuthMapper authMapper = mock(AuthMapper.class);
    when(authMapper.listPermissions("user-1")).thenReturn(List.of("kb:view"));
    AuthService auth = new AuthService(authMapper, null, null, null, null);
    KbMapper mapper = mock(KbMapper.class);
    KbDocumentService service = new KbDocumentService(mapper, auth, null, null, null);
    CurrentSession session = new CurrentSession("user-1", "reader", "Reader");

    service.list(session, new KbDocumentQuery());
    verify(mapper).listDocuments(null, null, null, 0, 10);
    assertThatThrownBy(() -> service.updateTitle(session, "doc", "new"))
        .isInstanceOf(BusinessException.class).hasMessageContaining("无权限");
    verify(mapper, never()).updateTitle(anyString(), anyString());
  }

  @Test
  void customRoleWithEditPermissionCanModifyDocument() {
    AuthMapper authMapper = mock(AuthMapper.class);
    when(authMapper.listPermissions("user-2")).thenReturn(List.of("kb:edit"));
    AuthService auth = new AuthService(authMapper, null, null, null, null);
    KbMapper mapper = mock(KbMapper.class);
    when(mapper.findDocument("doc")).thenReturn(new com.novaops.backend.kb.model.KbDocumentRecord());
    KbDocumentService service = new KbDocumentService(mapper, auth, null, null, null);
    service.updateTitle(new CurrentSession("user-2", "editor", "Editor"), "doc", "new");
    verify(mapper).updateTitle("doc", "new");
  }
}
