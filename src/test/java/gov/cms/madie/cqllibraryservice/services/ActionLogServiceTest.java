package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    when(cqlLibraryHistoryRepository.pushEvent(anyString(), any(Action.class))).thenReturn(true);
    boolean output = actionLogService.logAction("TARGET_ID", ActionType.CREATED, "firstUser");
    assertThat(output, is(true));
    verify(cqlLibraryHistoryRepository, times(1))
        .pushEvent(stringArgumentCaptor.capture(), actionArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue(), is(equalTo("TARGET_ID")));
    Action value = actionArgumentCaptor.getValue();
    assertThat(value, is(notNullValue()));
    assertThat(value.getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(value.getPerformedBy(), is(equalTo("firstUser")));
  }

  @Test
  void testLogActionReturnsFalse() {
    when(cqlLibraryHistoryRepository.pushEvent(anyString(), any(Action.class))).thenReturn(false);
    boolean output =
        actionLogService.logAction("TARGET_ID", ActionType.VERSIONED_MAJOR, "secondUser");
    assertThat(output, is(false));
    verify(cqlLibraryHistoryRepository, times(1))
        .pushEvent(stringArgumentCaptor.capture(), actionArgumentCaptor.capture());
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
}
