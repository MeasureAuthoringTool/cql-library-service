package gov.cms.madie.cqllibraryservice.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryLockControllerTest {

  @InjectMocks private CqlLibraryLockController controller;
  @Mock private CqlLibraryLockService service;

  private LockInfo lockInfo =
      LockInfo.builder().isLocked(false).lockedBy("test.user").lockedId("cqlLibrayId").build();

  @Test
  public void testAddCqlLibraryLock() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(service.lockCqlLibrary(anyString(), anyString())).thenReturn(lockInfo);

    ResponseEntity<LockInfo> response = controller.addCqlLibraryLock("cqlLibrayId", principal);
    assertNotNull(response);
    assertEquals("test.user", response.getBody().getLockedBy());
    assertFalse(response.getBody().isLocked());
    assertEquals("cqlLibrayId", response.getBody().getLockedId());
  }

  @Test
  public void testUnlockCqlLibrary() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    when(service.unlockCqlLibrary(anyString(), anyString())).thenReturn(lockInfo);

    ResponseEntity<LockInfo> response = controller.unlockCqlLibrary("cqlLibrayId", principal);
    assertNotNull(response);
    assertEquals("test.user", response.getBody().getLockedBy());
    assertFalse(response.getBody().isLocked());
    assertEquals("cqlLibrayId", response.getBody().getLockedId());
  }

  @Test
  public void testUnlockAllByUser() {
    Principal principal = mock(Principal.class);
    //    when(principal.getName()).thenReturn("test.user");
    String msg1 = "Delete library locks for harpId: test.user";
    String msg2 = "Deleted library lock: cqlLibrayId";
    when(service.unlockByUser(anyString())).thenReturn(List.of(msg1, msg2));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");

    ResponseEntity<List<String>> response =
        controller.unlockAllByUser(request, "key", "test.user", principal);
    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().get(0).contains(msg1));
    assertTrue(response.getBody().get(1).contains(msg2));
  }
}
