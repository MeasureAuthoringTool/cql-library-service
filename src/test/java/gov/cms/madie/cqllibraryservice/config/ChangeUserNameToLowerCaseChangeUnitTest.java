package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.LibraryActionLog;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.common.Action;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ChangeUserNameToLowerCaseChangeUnitTest {
  @Mock CqlLibraryRepository cqlLibraryRepository;
  @Mock LibrarySetRepository librarySetRepository;
  @Mock ActionLogRepositoryImpl actionLogRepository;
  @Mock LibrarySetActionLogRepository librarySetActionLogRepository;
  @Mock CqlLibraryLockRepository cqlLibraryLockRepository;
  @InjectMocks ChangeUserNameToLowerCaseChangeUnit changeUnit;

  private final String LOWER_CASE_USER_NAME = "user1";
  private final String UPPER_CASE_USER_NAME = "User1";
  private final CqlLibrary library =
      CqlLibrary.builder()
          .id("library1")
          .createdBy(UPPER_CASE_USER_NAME)
          .lastModifiedBy(UPPER_CASE_USER_NAME)
          .build();
  private final LibrarySet librarySet =
      LibrarySet.builder()
          .id("librarySet1")
          .owner(UPPER_CASE_USER_NAME)
          .acls(List.of(AclSpecification.builder().userId(UPPER_CASE_USER_NAME).build()))
          .build();
  private final LibraryActionLog libraryActionLog =
      LibraryActionLog.builder()
          .id("actionLog1")
          .actions(List.of(Action.builder().performedBy(UPPER_CASE_USER_NAME).build()))
          .build();
  private final LibrarySetActionLog librarySetActionLog =
      LibrarySetActionLog.builder()
          .id("librarySetActionLog1")
          .actions(List.of(AccessControlAction.builder().performedBy(UPPER_CASE_USER_NAME).build()))
          .build();
  private final CqlLibraryLock libraryLock =
      CqlLibraryLock.builder().lockedBy(UPPER_CASE_USER_NAME).build();

  @Test
  void changeUserNameToLowerCase() {
    when(cqlLibraryRepository.findAll()).thenReturn(List.of(library));
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(libraryActionLog));
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));
    when(cqlLibraryLockRepository.findAll()).thenReturn(List.of(libraryLock));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraries().size());
    assertEquals(1, changeUnit.getUpdatedLibraries().size());
    CqlLibrary updatedLibrary = changeUnit.getUpdatedLibraries().get(0);
    assertEquals(LOWER_CASE_USER_NAME, updatedLibrary.getCreatedBy());
    assertEquals(LOWER_CASE_USER_NAME, updatedLibrary.getLastModifiedBy());
    assertEquals(1, changeUnit.getOriginalLibrarySets().size());
    assertEquals(1, changeUnit.getUpdatedLibrarySets().size());
    LibrarySet updatedLibrarySet = changeUnit.getUpdatedLibrarySets().get(0);
    assertEquals(LOWER_CASE_USER_NAME, updatedLibrarySet.getOwner());
    assertEquals(LOWER_CASE_USER_NAME, updatedLibrarySet.getAcls().get(0).getUserId());
    assertEquals(1, changeUnit.getOriginalLibraryActionLogs().size());
    assertEquals(1, changeUnit.getUpdatedLibraryActionLogs().size());
    LibraryActionLog updatedActionLog = changeUnit.getUpdatedLibraryActionLogs().get(0);
    assertEquals(LOWER_CASE_USER_NAME, updatedActionLog.getActions().get(0).getPerformedBy());
    assertEquals(1, changeUnit.getOriginalLibrarySetActionLogs().size());
    assertEquals(1, changeUnit.getUpdatedLibrarySetActionLogs().size());
    LibrarySetActionLog updatedLibrarySetActionLog =
        changeUnit.getUpdatedLibrarySetActionLogs().get(0);
    assertEquals(
        LOWER_CASE_USER_NAME, updatedLibrarySetActionLog.getActions().get(0).getPerformedBy());
    assertEquals(1, changeUnit.getOriginalLibraryLocks().size());
    assertEquals(1, changeUnit.getUpdatedLibraryLocks().size());
    CqlLibraryLock updatedLibraryLock = changeUnit.getUpdatedLibraryLocks().get(0);
    assertEquals(LOWER_CASE_USER_NAME, updatedLibraryLock.getLockedBy());
  }

  @Test
  void testNoChangeWhenUserNameIsAlreadyLowerCase() {
    library.setCreatedBy(LOWER_CASE_USER_NAME);
    library.setLastModifiedBy(LOWER_CASE_USER_NAME);
    when(cqlLibraryRepository.findAll()).thenReturn(List.of(library));

    librarySet.setOwner(LOWER_CASE_USER_NAME);
    librarySet.setAcls(List.of(AclSpecification.builder().userId(LOWER_CASE_USER_NAME).build()));
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    libraryActionLog.getActions().get(0).setPerformedBy(LOWER_CASE_USER_NAME);
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(libraryActionLog));

    librarySetActionLog.getActions().get(0).setPerformedBy(LOWER_CASE_USER_NAME);
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));

    libraryLock.setLockedBy(LOWER_CASE_USER_NAME);
    when(cqlLibraryLockRepository.findAll()).thenReturn(List.of(libraryLock));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraries().size());
    assertEquals(0, changeUnit.getUpdatedLibraries().size());
    assertTrue(changeUnit.getUpdatedLibraries().isEmpty());
    assertEquals(1, changeUnit.getOriginalLibrarySets().size());
    assertTrue(changeUnit.getUpdatedLibrarySets().isEmpty());
    assertEquals(1, changeUnit.getOriginalLibraryActionLogs().size());
    assertTrue(changeUnit.getUpdatedLibraryActionLogs().isEmpty());
    assertEquals(1, changeUnit.getOriginalLibrarySetActionLogs().size());
    assertTrue(changeUnit.getUpdatedLibrarySetActionLogs().isEmpty());
    assertEquals(1, changeUnit.getOriginalLibraryLocks().size());
    assertTrue(changeUnit.getUpdatedLibraryLocks().isEmpty());
  }

  @Test
  void testNoChangeWhenNoLibraries() {
    when(cqlLibraryRepository.findAll()).thenReturn(List.of());
    when(librarySetRepository.findAll()).thenReturn(List.of());
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of());
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of());
    when(cqlLibraryLockRepository.findAll()).thenReturn(List.of());

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertTrue(changeUnit.getOriginalLibraries().isEmpty());
    assertTrue(changeUnit.getUpdatedLibraries().isEmpty());
    assertTrue(changeUnit.getOriginalLibrarySets().isEmpty());
    assertTrue(changeUnit.getUpdatedLibraryLocks().isEmpty());
    assertTrue(changeUnit.getOriginalLibraryActionLogs().isEmpty());
    assertTrue(changeUnit.getUpdatedLibraryActionLogs().isEmpty());
    assertTrue(changeUnit.getOriginalLibrarySetActionLogs().isEmpty());
    assertTrue(changeUnit.getUpdatedLibrarySetActionLogs().isEmpty());
    assertTrue(changeUnit.getOriginalLibraryLocks().isEmpty());
  }

  @Test
  void testUpdateLibrariesCreatedByLastModifiedByNull() {
    library.setCreatedBy(null);
    library.setLastModifiedBy(null);
    when(cqlLibraryRepository.findAll()).thenReturn(List.of(library));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraries().size());
    assertTrue(changeUnit.getUpdatedLibraries().isEmpty());
  }

  @Test
  void testUpdatedLibrarySetsOwnerAclsNull() {
    librarySet.setOwner(null);
    librarySet.setAcls(null);
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibrarySets().size());
    assertTrue(changeUnit.getUpdatedLibrarySets().isEmpty());
  }

  @Test
  void testUpdateLibraryActionLogsActionsNull() {
    libraryActionLog.setActions(null);
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(libraryActionLog));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraryActionLogs().size());
    assertTrue(changeUnit.getUpdatedLibraryActionLogs().isEmpty());
  }

  @Test
  void testUpdateLibraryActionLogsPerformedByNull() {
    libraryActionLog.setActions(List.of(Action.builder().performedBy(null).build()));
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(libraryActionLog));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraryActionLogs().size());
    assertTrue(changeUnit.getUpdatedLibraryActionLogs().isEmpty());
  }

  @Test
  void testUpdateLibrarySetActionLogsActionsNull() {
    librarySetActionLog.setActions(null);
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibrarySetActionLogs().size());
    assertTrue(changeUnit.getUpdatedLibrarySetActionLogs().isEmpty());
  }

  @Test
  void testUpdateLibrarySetActionLogsPerformedByNull() {
    librarySetActionLog.setActions(
        List.of(AccessControlAction.builder().performedBy(null).build()));
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibrarySetActionLogs().size());
    assertTrue(changeUnit.getUpdatedLibrarySetActionLogs().isEmpty());
  }

  @Test
  void testUpdateLibraryLocksLockedByNull() {
    libraryLock.setLockedBy(null);
    when(cqlLibraryLockRepository.findAll()).thenReturn(List.of(libraryLock));

    changeUnit.changeAllUserNamesToLowerCase(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraryLocks().size());
    assertTrue(changeUnit.getUpdatedLibraryLocks().isEmpty());
  }

  @Test
  void testRollbackChanges() {
    ReflectionTestUtils.setField(changeUnit, "originalLibraries", List.of(library));
    ReflectionTestUtils.setField(changeUnit, "originalLibrarySets", List.of(librarySet));
    ReflectionTestUtils.setField(
        changeUnit, "originalLibraryActionLogs", List.of(libraryActionLog));
    ReflectionTestUtils.setField(
        changeUnit, "originalLibrarySetActionLogs", List.of(librarySetActionLog));
    ReflectionTestUtils.setField(changeUnit, "originalLibraryLocks", List.of(libraryLock));

    changeUnit.rollbackChanges(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertEquals(1, changeUnit.getOriginalLibraries().size());
    assertEquals(1, changeUnit.getOriginalLibrarySets().size());
    assertEquals(1, changeUnit.getOriginalLibraryActionLogs().size());
    assertEquals(1, changeUnit.getOriginalLibrarySetActionLogs().size());
    assertEquals(1, changeUnit.getOriginalLibraryLocks().size());

    assertDoesNotThrow(
        () -> {
          changeUnit.rollbackChanges(
              cqlLibraryRepository,
              librarySetRepository,
              actionLogRepository,
              librarySetActionLogRepository,
              cqlLibraryLockRepository);
        });
  }

  @Test
  void testRollbackNoOriginalData() {
    ReflectionTestUtils.setField(changeUnit, "originalLibraries", List.of());
    ReflectionTestUtils.setField(changeUnit, "originalLibrarySets", List.of());
    ReflectionTestUtils.setField(changeUnit, "originalLibraryActionLogs", List.of());
    ReflectionTestUtils.setField(changeUnit, "originalLibrarySetActionLogs", List.of());
    ReflectionTestUtils.setField(changeUnit, "originalLibraryLocks", List.of());

    changeUnit.rollbackChanges(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository,
        cqlLibraryLockRepository);

    assertTrue(changeUnit.getOriginalLibraries().isEmpty());
    assertTrue(changeUnit.getOriginalLibrarySets().isEmpty());
    assertTrue(changeUnit.getOriginalLibraryActionLogs().isEmpty());
    assertTrue(changeUnit.getOriginalLibrarySetActionLogs().isEmpty());
    assertTrue(changeUnit.getOriginalLibraryLocks().isEmpty());
  }
}
