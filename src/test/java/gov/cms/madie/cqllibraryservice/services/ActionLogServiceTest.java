package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.models.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionLogServiceTest {

  @Mock CqlLibraryActionLogRepository cqlLibraryHistoryRepository;

  @Mock LibrarySetActionLogRepository librarySetActionLogRepository;

  @InjectMocks ActionLogService actionLogService;

  @Captor private ArgumentCaptor<Action> actionArgumentCaptor;
  @Captor private ArgumentCaptor<AccessControlAction> accessControlActionArgumentCaptor;

  @Captor private ArgumentCaptor<String> stringArgumentCaptor;

  @Test
  void testLogActionReturnsTrue() {
    when(cqlLibraryHistoryRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(true);
    boolean output =
        actionLogService.logAction("TARGET_ID", ActionType.CREATED, "firstUser", "actionLog");
    assertThat(output, is(true));
    verify(cqlLibraryHistoryRepository, times(1))
        .pushEvent(stringArgumentCaptor.capture(), actionArgumentCaptor.capture(), anyString());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(value.getPerformedBy(), is(equalTo("firstUser")));
  }

  @Test
  void testLogActionReturnsFalse() {
    when(cqlLibraryHistoryRepository.pushEvent(anyString(), any(Action.class), anyString()))
        .thenReturn(false);
    boolean output =
        actionLogService.logAction(
            "TARGET_ID", ActionType.VERSIONED_MAJOR, "secondUser", "actionLog");
    assertThat(output, is(false));
    verify(cqlLibraryHistoryRepository, times(1))
        .pushEvent(stringArgumentCaptor.capture(), actionArgumentCaptor.capture(), anyString());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.VERSIONED_MAJOR)));
    assertThat(value.getPerformedBy(), is(equalTo("secondUser")));
  }

  @Test
  void testLogAccessControlActionReturnsTrue() {
    when(librarySetActionLogRepository.pushEvent(anyString(), any(AccessControlAction.class)))
        .thenReturn(true);
    boolean output =
        actionLogService.logShareAccessControlAction(
            "TARGET_ID", ActionType.SHARED, "firstUser", "sharedWith");
    assertThat(output, is(true));
    verify(librarySetActionLogRepository, times(1))
        .pushEvent(stringArgumentCaptor.capture(), accessControlActionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    assertThat(accessControlActionArgumentCaptor.getValue(), instanceOf(AccessControlAction.class));
    AccessControlAction value = (AccessControlAction) accessControlActionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.SHARED)));
    assertThat(value.getPerformedBy(), is(equalTo("firstUser")));
    assertThat(value.getSharedWith(), is(equalTo("sharedWith")));
  }

  @Test
  void testLogAccessControlActionReturnsFalse() {
    when(librarySetActionLogRepository.pushEvent(anyString(), any(AccessControlAction.class)))
        .thenReturn(false);
    boolean output =
        actionLogService.logShareAccessControlAction(
            "TARGET_ID", ActionType.SHARED, "secondUser", "sharedWith");
    assertThat(output, is(false));
    verify(librarySetActionLogRepository, times(1))
        .pushEvent(stringArgumentCaptor.capture(), accessControlActionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    assertThat(accessControlActionArgumentCaptor.getValue(), instanceOf(AccessControlAction.class));
    AccessControlAction value = (AccessControlAction) accessControlActionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.SHARED)));
    assertThat(value.getPerformedBy(), is(equalTo("secondUser")));
    assertThat(value.getSharedWith(), is(equalTo("sharedWith")));
  }

  @Test
  void returnsCombinedCreatedActionLogWhenBothRepositoriesHaveCreatedLogs() {
    String cqlLibraryId = "cqlLibraryId";
    String librarySetId = "librarySetId";

    LibraryActionLog libraryActionLog = new LibraryActionLog();
    libraryActionLog.setActions(
        List.of(
            Action.builder()
                .actionType(ActionType.CREATED)
                .performedAt(Instant.parse("2025-12-18T21:17:25.549Z"))
                .build(),
            Action.builder()
                .actionType(ActionType.UPDATED)
                .performedAt(Instant.parse("2026-12-18T21:17:25.549Z"))
                .build()));

    LibrarySetActionLog librarySetActionLog =
        LibrarySetActionLog.builder()
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .actionType(ActionType.SHARED)
                        .performedAt(Instant.parse("2024-12-18T21:17:25.549Z"))
                        .build(),
                    AccessControlAction.builder()
                        .actionType(ActionType.CREATED)
                        .performedAt(Instant.parse("2023-12-18T21:17:25.549Z"))
                        .build()))
            .build();

    when(cqlLibraryHistoryRepository.findByTargetId(cqlLibraryId))
        .thenReturn(Optional.of(libraryActionLog));
    when(librarySetActionLogRepository.findByTargetId(librarySetId))
        .thenReturn(Optional.of(librarySetActionLog));

    List<Action> result = actionLogService.findCqlLibraryHistory(cqlLibraryId, librarySetId);

    assertThat(result.size(), is(3));
    assertThat(result.get(0).getPerformedAt(), is(Instant.parse("2026-12-18T21:17:25.549Z")));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.CREATED), is(true));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.UPDATED), is(true));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.SHARED), is(true));
  }

  @Test
  void returnsEmptyListWhenBothRepositoriesReturnEmpty() {
    String cqlLibraryId = "cqlLibraryId";
    String librarySetId = "librarySetId";

    when(cqlLibraryHistoryRepository.findByTargetId(cqlLibraryId)).thenReturn(Optional.empty());
    when(librarySetActionLogRepository.findByTargetId(librarySetId)).thenReturn(Optional.empty());

    List<Action> result = actionLogService.findCqlLibraryHistory(cqlLibraryId, librarySetId);

    assertThat(result.isEmpty(), is(true));
  }

  @Test
  void returnsOnlyLibraryActionsWhenLibrarySetRepositoryReturnsEmpty() {
    String cqlLibraryId = "cqlLibraryId";
    String librarySetId = "librarySetId";

    LibraryActionLog libraryActionLog = new LibraryActionLog();
    libraryActionLog.setActions(
        List.of(
            Action.builder()
                .actionType(ActionType.CREATED)
                .performedAt(Instant.parse("2024-12-18T21:17:25.549Z"))
                .build(),
            Action.builder()
                .actionType(ActionType.UPDATED)
                .performedAt(Instant.parse("2025-12-18T21:17:25.549Z"))
                .build()));

    when(cqlLibraryHistoryRepository.findByTargetId(cqlLibraryId))
        .thenReturn(Optional.of(libraryActionLog));
    when(librarySetActionLogRepository.findByTargetId(librarySetId)).thenReturn(Optional.empty());

    List<Action> result = actionLogService.findCqlLibraryHistory(cqlLibraryId, librarySetId);

    assertThat(result.size(), is(2));
    assertThat(result.get(0).getPerformedAt(), is(Instant.parse("2025-12-18T21:17:25.549Z")));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.CREATED), is(true));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.UPDATED), is(true));
  }

  @Test
  void returnsOnlyLibrarySetActionsWhenLibraryRepositoryReturnsEmpty() {
    String cqlLibraryId = "cqlLibraryId";
    String librarySetId = "librarySetId";

    LibrarySetActionLog librarySetActionLog =
        LibrarySetActionLog.builder()
            .actions(
                List.of(
                    AccessControlAction.builder().actionType(ActionType.SHARED).build(),
                    AccessControlAction.builder().actionType(ActionType.CREATED).build()))
            .build();

    when(cqlLibraryHistoryRepository.findByTargetId(cqlLibraryId)).thenReturn(Optional.empty());
    when(librarySetActionLogRepository.findByTargetId(librarySetId))
        .thenReturn(Optional.of(librarySetActionLog));

    List<Action> result = actionLogService.findCqlLibraryHistory(cqlLibraryId, librarySetId);

    assertThat(result.size(), is(1));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.SHARED), is(true));
    assertThat(
        result.stream().anyMatch(action -> action.getActionType() == ActionType.CREATED),
        is(false));
  }
}
