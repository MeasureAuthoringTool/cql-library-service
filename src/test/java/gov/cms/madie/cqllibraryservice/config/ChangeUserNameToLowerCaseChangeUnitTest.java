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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
  private CqlLibrary library =
      CqlLibrary.builder()
          .id("library1")
          .createdBy(UPPER_CASE_USER_NAME)
          .lastModifiedBy(UPPER_CASE_USER_NAME)
          .build();
  private LibrarySet librarySet =
      LibrarySet.builder()
          .id("librarySet1")
          .owner(UPPER_CASE_USER_NAME)
          .acls(List.of(AclSpecification.builder().userId(UPPER_CASE_USER_NAME).build()))
          .build();
  private LibraryActionLog libraryActionLog =
      LibraryActionLog.builder()
          .id("actionLog1")
          .actions(List.of(Action.builder().performedBy(UPPER_CASE_USER_NAME).build()))
          .build();
  private LibrarySetActionLog librarySetActionLog =
      LibrarySetActionLog.builder()
          .id("librarySetActionLog1")
          .actions(List.of(AccessControlAction.builder().performedBy(UPPER_CASE_USER_NAME).build()))
          .build();
  private CqlLibraryLock libraryLock =
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

    assert changeUnit.getOriginalLibraries().size() == 1;
    assert changeUnit.getUpdatedLibraries().size() == 1;
    CqlLibrary updatedLibrary = changeUnit.getUpdatedLibraries().get(0);
    assert updatedLibrary.getCreatedBy().equals(LOWER_CASE_USER_NAME);
    assert updatedLibrary.getLastModifiedBy().equals(LOWER_CASE_USER_NAME);
    assert changeUnit.getOriginalLibrarySets().size() == 1;
    assert changeUnit.getUpdatedLibrarySets().size() == 1;
    LibrarySet updatedLibrarySet = changeUnit.getUpdatedLibrarySets().get(0);
    assert updatedLibrarySet.getOwner().equals(LOWER_CASE_USER_NAME);
    assert updatedLibrarySet.getAcls().get(0).getUserId().equals(LOWER_CASE_USER_NAME);
    assert changeUnit.getOriginalLibraryActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibraryActionLogs().size() == 1;
    LibraryActionLog updatedActionLog = changeUnit.getUpdatedLibraryActionLogs().get(0);
    assert updatedActionLog.getActions().get(0).getPerformedBy().equals(LOWER_CASE_USER_NAME);
    assert changeUnit.getOriginalLibrarySetActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibrarySetActionLogs().size() == 1;
    LibrarySetActionLog updatedLibrarySetActionLog =
        changeUnit.getUpdatedLibrarySetActionLogs().get(0);
    assert updatedLibrarySetActionLog
        .getActions()
        .get(0)
        .getPerformedBy()
        .equals(LOWER_CASE_USER_NAME);
    assert changeUnit.getOriginalLibraryLocks().size() == 1;
    assert changeUnit.getUpdatedLibraryLocks().size() == 1;
    CqlLibraryLock updatedLibraryLock = changeUnit.getUpdatedLibraryLocks().get(0);
    assert updatedLibraryLock.getLockedBy().equals(LOWER_CASE_USER_NAME);
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

    assert changeUnit.getOriginalLibraries().size() == 1;
    assert changeUnit.getUpdatedLibraries().isEmpty();
    assert changeUnit.getOriginalLibrarySets().size() == 1;
    assert changeUnit.getUpdatedLibrarySets().isEmpty();
    assert changeUnit.getOriginalLibraryActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibraryActionLogs().isEmpty();
    assert changeUnit.getOriginalLibrarySetActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibrarySetActionLogs().isEmpty();
    assert changeUnit.getOriginalLibraryLocks().size() == 1;
    assert changeUnit.getUpdatedLibraryLocks().isEmpty();
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

    assert changeUnit.getOriginalLibraries().isEmpty();
    assert changeUnit.getUpdatedLibraries().isEmpty();
    assert changeUnit.getOriginalLibrarySets().isEmpty();
    assert changeUnit.getUpdatedLibrarySets().isEmpty();
    assert changeUnit.getOriginalLibraryActionLogs().isEmpty();
    assert changeUnit.getUpdatedLibraryActionLogs().isEmpty();
    assert changeUnit.getOriginalLibrarySetActionLogs().isEmpty();
    assert changeUnit.getUpdatedLibrarySetActionLogs().isEmpty();
    assert changeUnit.getOriginalLibraryLocks().isEmpty();
    assert changeUnit.getUpdatedLibraryLocks().isEmpty();
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

    assert changeUnit.getOriginalLibraries().size() == 1;
    assert changeUnit.getUpdatedLibraries().isEmpty();
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

    assert changeUnit.getOriginalLibrarySets().size() == 1;
    assert changeUnit.getUpdatedLibrarySets().isEmpty();
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

    assert changeUnit.getOriginalLibraryActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibraryActionLogs().isEmpty();
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

    assert changeUnit.getOriginalLibraryActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibraryActionLogs().isEmpty();
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

    assert changeUnit.getOriginalLibrarySetActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibrarySetActionLogs().isEmpty();
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

    assert changeUnit.getOriginalLibrarySetActionLogs().size() == 1;
    assert changeUnit.getUpdatedLibrarySetActionLogs().isEmpty();
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

    assert changeUnit.getOriginalLibraryLocks().size() == 1;
    assert changeUnit.getUpdatedLibraryLocks().isEmpty();
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

    assert changeUnit.getOriginalLibraries().size() == 1;
    assert changeUnit.getOriginalLibrarySets().size() == 1;
    assert changeUnit.getOriginalLibraryActionLogs().size() == 1;
    assert changeUnit.getOriginalLibrarySetActionLogs().size() == 1;
    assert changeUnit.getOriginalLibraryLocks().size() == 1;

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

    assert changeUnit.getOriginalLibraries().isEmpty();
    assert changeUnit.getOriginalLibrarySets().isEmpty();
    assert changeUnit.getOriginalLibraryActionLogs().isEmpty();
    assert changeUnit.getOriginalLibrarySetActionLogs().isEmpty();
    assert changeUnit.getOriginalLibraryLocks().isEmpty();
  }
}
