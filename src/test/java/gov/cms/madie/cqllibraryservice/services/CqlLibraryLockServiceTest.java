package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryLockRepository;
import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryLockServiceTest {
  @InjectMocks private CqlLibraryLockService service;
  @Mock private CqlLibraryLockRepository repository;

  private CqlLibraryLock lock;
  private Instant instant = Instant.parse("2025-08-20T14:13:00Z");

  @BeforeEach
  public void setUp() {
    lock =
        CqlLibraryLock.builder()
            .cqlLibraryId("libraryId")
            .lockedAt(instant)
            .lockedBy("test.user")
            .build();
  }

  @Test
  public void testLockCqlLibrarySuccess() {
    when(repository.insert(any(CqlLibraryLock.class))).thenReturn(lock);

    LockInfo lockInfo = service.lockCqlLibrary("libraryId", "test.user");

    assertEquals("libraryId", lockInfo.getLockedId());
    assertEquals("test.user", lockInfo.getLockedBy());
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testLockCqlLibraryThrowsDuplicateKeyException() {
    when(repository.insert(any(CqlLibraryLock.class))).thenThrow(DuplicateKeyException.class);
    when(repository.findByCqlLibraryId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.lockCqlLibrary("libraryId", "test.user");

    assertEquals("libraryId", lockInfo.getLockedId());
    assertEquals("test.user", lockInfo.getLockedBy());
    assertTrue(lockInfo.isLocked());
  }

  @Test
  public void testDuplicateKeyExceptionNoExistingLock() {
    when(repository.insert(any(CqlLibraryLock.class))).thenThrow(DuplicateKeyException.class);
    when(repository.findByCqlLibraryId(anyString())).thenReturn(Optional.empty());

    LockInfo lockInfo = service.lockCqlLibrary("libraryId", "test.user");

    assertNull(lockInfo);
  }

  @Test
  public void testUnlockCqlLibraryNotFound() {
    when(repository.findByCqlLibraryId(anyString())).thenReturn(Optional.empty());

    LockInfo lockInfo = service.unlockCqlLibrary("libraryId", "test.user");

    assertNull(lockInfo);
  }

  @Test
  public void testUnlockCqlLibraryLockFoundForSameUser() {
    when(repository.findByCqlLibraryId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.unlockCqlLibrary("libraryId", "test.user");

    assertNotNull(lockInfo);
    assertFalse(lockInfo.isLocked());
    assertNull(lockInfo.getLockedBy());
    assertNull(lockInfo.getLockedId());
  }

  @Test
  public void testUnlockCqlLibraryLockFoundForDifferentUser() {
    when(repository.findByCqlLibraryId(anyString())).thenReturn(Optional.of(lock));

    LockInfo lockInfo = service.unlockCqlLibrary("libraryId", "test.user2");

    assertNotNull(lockInfo);
    assertTrue(lockInfo.isLocked());
    assertEquals("libraryId", lockInfo.getLockedId());
    assertEquals("test.user", lockInfo.getLockedBy());
  }

  @Test
  public void testUnlockByUser() {
    CqlLibraryLock libraryLock =
        CqlLibraryLock.builder().cqlLibraryId("cqlLibraryId").lockedBy("test.user").build();
    when(repository.findAllByLockedBy(anyString())).thenReturn(List.of(libraryLock));

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete library locks for harpId: test.user";
    String msg2 = "Deleted library lock: cqlLibraryId";
    List<String> expected = List.of(msg1, msg2);
    assertEquals(expected, results);
  }

  @Test
  public void testUnlockByUserLocksNotFound() {
    when(repository.findAllByLockedBy(anyString())).thenReturn(Collections.emptyList());

    List<String> results = service.unlockByUser("test.user");

    String msg1 = "Delete library locks for harpId: test.user";
    String msg2 = "No library locks found for harpId: test.user";
    List<String> expected = List.of(msg1, msg2);
    assertEquals(expected, results);
  }
}
