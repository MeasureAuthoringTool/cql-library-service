package gov.cms.madie.cqllibraryservice.repositories;

import com.mongodb.client.result.UpdateResult;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.LibraryActionLog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ActionLogRepositoryImplTest {

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks ActionLogRepositoryImpl actionLogRepository;

  @Test
  void returnsFalseForNullTargetId() {
    boolean output = actionLogRepository.pushEvent(null, Action.builder().build(), "actionLog");
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForEmptyTargetId() {
    boolean output = actionLogRepository.pushEvent("", Action.builder().build(), "actionLog");
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForNullAction() {
    boolean output = actionLogRepository.pushEvent("TARGET_ID", null);
    assertThat(output, is(false));
  }

  @Test
  void returnsTrueForValidInputs() {
    when(mongoTemplate.upsert(any(Query.class), any(Update.class), anyString()))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    boolean output =
        actionLogRepository.pushEvent("TARGET_ID", Action.builder().build(), "librarySetActionLog");
    assertThat(output, is(true));
  }

  @Test
  void returnsFalseForValidInputsNoUpsert() {
    when(mongoTemplate.upsert(any(Query.class), any(Update.class), anyString()))
        .thenReturn(UpdateResult.acknowledged(1, 0L, null));
    boolean output =
        actionLogRepository.pushEvent("TARGET_ID", Action.builder().build(), "librarySetActionLog");
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForNullTargetIdWhenPushingIntoLibrarySetActionLog() {
    boolean output = actionLogRepository.pushEvent(null, AccessControlAction.builder().build());
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForEmptyTargetIdWhenPushingIntoLibrarySetActionLog() {
    boolean output = actionLogRepository.pushEvent("", AccessControlAction.builder().build());
    assertThat(output, is(false));
  }

  @Test
  void returnsFalseForNullActionWhenPushingIntoLibrarySetActionLog() {
    boolean output = actionLogRepository.pushEvent("TARGET_ID", null);
    assertThat(output, is(false));
  }

  @Test
  void returnsTrueForValidInputsWhenPushingIntoLibrarySetActionLog() {
    when(mongoTemplate.upsert(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    boolean output =
        actionLogRepository.pushEvent("TARGET_ID", AccessControlAction.builder().build());
    assertThat(output, is(true));
  }

  @Test
  void returnsFalseForValidInputsNoUpsertWhenPushingIntoLibrarySetActionLog() {
    when(mongoTemplate.upsert(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 0L, null));
    boolean output =
        actionLogRepository.pushEvent("TARGET_ID", AccessControlAction.builder().build());
    assertThat(output, is(false));
  }

  @Test
  void testFindAllActionLogs() {
    actionLogRepository.findAllActionLogs();
    verify(mongoTemplate).findAll(LibraryActionLog.class, "actionLog");
  }

  @Test
  void testSaveAllActionLogs() {
    LibraryActionLog actionLog =
        LibraryActionLog.builder().id("testActionLogId").targetId("testTargetId").build();
    actionLogRepository.saveAllActionLogs(List.of(actionLog));
    verify(mongoTemplate).insert(anyList(), eq("actionLog"));
  }

  @Test
  void testRemovesActionsAndDeletesEmptyLogs() {
    UpdateResult updateResult = UpdateResult.acknowledged(2, 2L, null);
    when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), anyString()))
        .thenReturn(updateResult);

    actionLogRepository.removeActionsByUsers(Arrays.asList("testUser1", "testUser2"), "actionLog");

    // Verify updateMulti called
    verify(mongoTemplate)
        .updateMulti(
            argThat(q -> q.getQueryObject().toString().contains("actions.performedBy")),
            any(Update.class),
            eq("actionLog"));

    // Verify remove called for empty actions
    verify(mongoTemplate)
        .remove(
            argThat(
                q ->
                    q.getQueryObject().toString().contains("actions")
                        && q.getQueryObject().toString().contains("$size")),
            eq("actionLog"));
  }

  @Test
  void testUpdateAllActionLogs() {
    LibraryActionLog actionLog1 = LibraryActionLog.builder().id("log1").targetId("target1").build();
    LibraryActionLog actionLog2 = LibraryActionLog.builder().id("log2").targetId("target2").build();
    List<LibraryActionLog> actionLogs = List.of(actionLog1, actionLog2);

    actionLogRepository.updateAllActionLogs(actionLogs);

    verify(mongoTemplate).save(actionLog1);
    verify(mongoTemplate).save(actionLog2);
  }
}
