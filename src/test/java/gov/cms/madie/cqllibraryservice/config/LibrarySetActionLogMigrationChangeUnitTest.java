package gov.cms.madie.cqllibraryservice.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class LibrarySetActionLogMigrationChangeUnitTest {
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock CqlLibraryActionLogRepository libraryActionLogRepository;
  @Mock LibrarySetActionLogRepository librarySetActionLogRepository;
  @InjectMocks private LibrarySetActionLogMigrationChangeUnit changeUnit;

  private ActionLog actionLog;
  Instant instant = Instant.parse("2025-04-06T21:06:00Z");
  private LibrarySet librarySet;
  LibrarySetActionLog librarySetActionLog = null;

  @BeforeEach
  void setUp() {
    actionLog = new ActionLog();
    actionLog.setId("action1");
    actionLog.setTargetId("librarySetId");
    Action action1 =
        Action.builder()
            .actionType(ActionType.CREATED)
            .additionalActionMessage("message1")
            .performedAt(instant)
            .performedBy("user1")
            .build();

    actionLog.setActions(List.of(action1));

    librarySet = LibrarySet.builder().id("librarySetId").librarySetId("librarySet1").build();

    List<AccessControlAction> accessControlActions =
        actionLog.getActions().stream()
            .map(
                action -> {
                  return AccessControlAction.builder()
                      .actionType(action.getActionType())
                      .additionalActionMessage(action.getAdditionalActionMessage())
                      .performedAt(action.getPerformedAt())
                      .performedBy(action.getPerformedBy())
                      .build();
                })
            .collect(Collectors.toList());
    librarySetActionLog =
        LibrarySetActionLog.builder()
            .id(actionLog.getId())
            .targetId("librarySet1")
            .actions(accessControlActions)
            .build();
  }

  @Test
  void testMigrateLibrarySetActionLogEmptyLibraryActionLog() {
    when(libraryActionLogRepository.findAll()).thenReturn(List.of());

    changeUnit.migrateLibrarySetActionLog(
        librarySetRepository, libraryActionLogRepository, librarySetActionLogRepository);

    verify(libraryActionLogRepository, new Times(1)).findAll();
    verifyNoInteractions(librarySetRepository);
    verifyNoInteractions(librarySetActionLogRepository);
  }

  @Test
  void testMigrateLibrarySetActionLogNotInLibrarySet() {
    when(libraryActionLogRepository.findAll()).thenReturn(List.of(actionLog));
    when(librarySetRepository.findById(anyString())).thenReturn(Optional.empty());

    changeUnit.migrateLibrarySetActionLog(
        librarySetRepository, libraryActionLogRepository, librarySetActionLogRepository);

    verify(libraryActionLogRepository, new Times(1)).findAll();
    verify(librarySetRepository, new Times(1)).findById("librarySetId");
    verifyNoInteractions(librarySetActionLogRepository);
  }

  @Test
  void testMigrateLibrarySetActionLog() {
    when(libraryActionLogRepository.findAll()).thenReturn(List.of(actionLog));
    when(librarySetRepository.findById(anyString())).thenReturn(Optional.of(librarySet));

    changeUnit.migrateLibrarySetActionLog(
        librarySetRepository, libraryActionLogRepository, librarySetActionLogRepository);

    verify(libraryActionLogRepository, new Times(1)).findAll();
    verify(librarySetRepository, new Times(1)).findById("librarySetId");
    verify(librarySetActionLogRepository, new Times(1)).saveAll(List.of(librarySetActionLog));
    verify(libraryActionLogRepository, new Times(1)).deleteAllById(List.of("action1"));
  }

  @Test
  public void testRollbackExecution() {

    ReflectionTestUtils.setField(changeUnit, "actionLogsToBeMigrated", List.of(actionLog));
    ReflectionTestUtils.setField(changeUnit, "librarySetActionLogIds", List.of("action1"));

    changeUnit.rollbackExecution(libraryActionLogRepository, librarySetActionLogRepository);

    verify(libraryActionLogRepository, new Times(1)).saveAll(List.of(actionLog));
    verify(librarySetActionLogRepository, new Times(1)).deleteAllById(List.of("action1"));
  }

  @Test
  public void testRollbackExecutionNoActionLogs() {

    ReflectionTestUtils.setField(changeUnit, "actionLogsToBeMigrated", Collections.emptyList());
    ReflectionTestUtils.setField(changeUnit, "librarySetActionLogIds", Collections.emptyList());

    changeUnit.rollbackExecution(libraryActionLogRepository, librarySetActionLogRepository);

    verifyNoInteractions(libraryActionLogRepository);
    verifyNoInteractions(librarySetActionLogRepository);
  }
}
