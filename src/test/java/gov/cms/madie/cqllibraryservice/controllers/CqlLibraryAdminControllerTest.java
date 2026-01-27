package gov.cms.madie.cqllibraryservice.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryAdminControllerTest {

  @InjectMocks private CqlLibraryAdminController controller;
  @Mock private CqlLibraryLockService cqlLibraryLockService;
  @Mock private CqlLibraryService cqlLibraryService;
  @Mock Principal principal;

  @Test
  public void testUnlockAllByUser() {
    String msg1 = "Delete library locks for harpId: test.user";
    String msg2 = "Deleted library lock for Id: cqlLibrayId";
    when(cqlLibraryLockService.unlockByUser(anyString())).thenReturn(List.of(msg1, msg2));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");

    ResponseEntity<List<String>> response =
        controller.unlockAllByUser(request, "key", "test.user", principal);
    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().get(0).contains(msg1));
    assertTrue(response.getBody().get(1).contains(msg2));
  }

  @Test
  public void testChangeOwnerShipSuccess() {
    when(principal.getName()).thenReturn("admin");
    when(cqlLibraryLockService.findByCqlLibraryId(anyString())).thenReturn(null);
    when(cqlLibraryService.transferLibraries(
            any(List.class), anyString(), any(Boolean.class), anyString()))
        .thenReturn(Collections.emptyList());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");

    ResponseEntity<List<String>> response =
        controller.changeOwnership(
            request, "key", List.of("testCqlLibraryId"), "newUser", true, principal);

    assertTrue(response.getBody().size() == 1);
    assertTrue(response.getStatusCode().equals(HttpStatusCode.valueOf(200)));
    assertEquals("testCqlLibraryId", response.getBody().get(0));
  }

  @Test
  public void testChangeOwnerShipReturnsBadRequest() {
    when(principal.getName()).thenReturn("admin");

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");

    ResponseEntity<List<String>> response =
        controller.changeOwnership(
            request, "key", Collections.emptyList(), "newUser", true, principal);

    assertTrue(response.getStatusCode().equals(HttpStatusCode.valueOf(400)));
  }

  @Test
  public void testChangeOwnerShipReturnsMultiStatus() {
    when(principal.getName()).thenReturn("admin");
    when(cqlLibraryLockService.findByCqlLibraryId(anyString()))
        .thenReturn(CqlLibraryLock.builder().build());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");

    ResponseEntity<List<String>> response =
        controller.changeOwnership(
            request, "key", List.of("testCqlLibraryId"), "newUser", true, principal);

    assertTrue(response.getStatusCode().equals(HttpStatusCode.valueOf(207)));
    assertEquals("testCqlLibraryId", response.getBody().get(0));
  }
}
