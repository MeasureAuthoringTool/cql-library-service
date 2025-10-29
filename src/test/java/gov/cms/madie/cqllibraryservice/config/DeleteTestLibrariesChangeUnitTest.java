package gov.cms.madie.cqllibraryservice.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import gov.cms.madie.cqllibraryservice.repositories.ActionLogRepositoryImpl;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibraryActionLog;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;

@ExtendWith(MockitoExtension.class)
public class DeleteTestLibrariesChangeUnitTest {

  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock private ActionLogRepositoryImpl actionLogRepository;
  @Mock private LibrarySetActionLogRepository librarySetActionLogRepository;

  @InjectMocks private DeleteTestLibrariesChangeUnit changeUnit;

  private final List<String> users = Arrays.asList("testUser");
  private final Set<String> userSet = new HashSet<>(users);
  private final List<String> WHITE_LISTED_LIBRARYSET_IDS =
      Arrays.asList("librarySetId1", "librarySetId2");
  private Action action =
      Action.builder().actionType(ActionType.CREATED).performedBy("testUser").build();
  private LibraryActionLog actionLog1 =
      LibraryActionLog.builder()
          .id("testLibraryActionLogId1")
          .targetId("testCqlLibraryId")
          .actions(List.of(action))
          .build();
  private LibraryActionLog actionLog2 =
      LibraryActionLog.builder()
          .id("testLibraryActionLogId2")
          .targetId("librarySetId1")
          .actions(List.of(action))
          .build();
  private AccessControlAction accessControlAction =
      AccessControlAction.builder().actionType(ActionType.CREATED).performedBy("testUser").build();
  private LibrarySetActionLog librarySetActionLog =
      LibrarySetActionLog.builder()
          .id("testLibrarySetActionLogId")
          .targetId("testCqlLibraryId")
          .actions(List.of(accessControlAction))
          .build();

  private LibrarySet librarySet1 =
      LibrarySet.builder().librarySetId("testLibrarySetId1").owner("testUser").build();
  private LibrarySet librarySet2 =
      LibrarySet.builder().librarySetId("testLibrarySetId2").owner("anotherUser").build();
  private LibrarySet librarySetWhiteListed =
      LibrarySet.builder().librarySetId("librarySetId1").owner("testUser").build();
  private CqlLibrary cqlLibrary =
      CqlLibrary.builder().id("testCqlLibraryId").librarySetId("testLibrarySetId").build();

  @BeforeEach
  void init() {
    ReflectionTestUtils.setField(changeUnit, "userSet", userSet);
    ReflectionTestUtils.setField(changeUnit, "users", users);
    ReflectionTestUtils.setField(
        changeUnit, "WHITE_LISTED_LIBRARYSET_IDS", WHITE_LISTED_LIBRARYSET_IDS);
    ReflectionTestUtils.setField(changeUnit, "users", users);
    ReflectionTestUtils.setField(changeUnit, "filteredActionLogs", List.of(actionLog1, actionLog2));
    ReflectionTestUtils.setField(
        changeUnit, "filteredLibrarySetActionLogs", List.of(librarySetActionLog));
    ReflectionTestUtils.setField(changeUnit, "filteredLibraries", List.of(cqlLibrary));
    ReflectionTestUtils.setField(
        changeUnit, "filteredLibrarySets", List.of(librarySet1, librarySet2));
  }

  @Test
  void testDeleteLibraries() {
    doNothing().when(cqlLibraryRepository).deleteAll(anyList());
    changeUnit.deleteLibraries(cqlLibraryRepository, List.of(cqlLibrary));
    verify(cqlLibraryRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteLibrarySets() {
    doNothing().when(librarySetRepository).deleteAll(anyList());
    changeUnit.deleteLibrarySets(librarySetRepository, List.of(librarySet1, librarySet2));
    verify(librarySetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestLibraries() {
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet1, librarySetWhiteListed));
    when(cqlLibraryRepository.findByLibrarySetIdIn(anyList())).thenReturn(List.of(cqlLibrary));

    doNothing().when(cqlLibraryRepository).deleteAll(anyList());

    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(actionLog1, actionLog2));
    doNothing().when(actionLogRepository).deleteByTargetIds(anyList(), anyString());

    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));
    doNothing().when(librarySetActionLogRepository).deleteByTargetIds(anyList(), anyString());

    doNothing().when(librarySetRepository).deleteAll(anyList());

    changeUnit.deleteTestLibraries(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verify(cqlLibraryRepository, times(1)).deleteAll(anyList());

    verify(actionLogRepository, times(1)).findAllActionLogs();
    verify(actionLogRepository, times(1)).deleteByTargetIds(anyList(), anyString());

    verify(librarySetActionLogRepository, times(1)).findAll();
    verify(librarySetActionLogRepository, times(2)).deleteByTargetIds(anyList(), anyString());

    verify(librarySetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestLibrariesNoLibrarySets() {
    when(librarySetRepository.findAll()).thenReturn(Collections.emptyList());

    changeUnit.deleteTestLibraries(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verify(cqlLibraryRepository, times(0)).deleteAll(anyList());
    verify(librarySetRepository, times(0)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestLibrariesNoLibraries() {
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet1, librarySet2));
    when(cqlLibraryRepository.findByLibrarySetIdIn(anyList())).thenReturn(Collections.emptyList());

    changeUnit.deleteTestLibraries(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verify(cqlLibraryRepository, times(0)).deleteAll(anyList());
    verify(librarySetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteActionLogsFilteredLibrarySetIdsContainsTargetId() {
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(actionLog1));

    changeUnit.deleteActionLogs(
        actionLogRepository, Arrays.asList("cqlLibraryId"), Arrays.asList("testCqlLibraryId"));

    verify(actionLogRepository, times(1)).findAllActionLogs();
    verify(actionLogRepository, times(1)).deleteByTargetIds(anyList(), anyString());
  }

  @Test
  void testDeleteActionLogsNoDelete() {
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(actionLog2));

    changeUnit.deleteActionLogs(
        actionLogRepository,
        Arrays.asList("newCqlLibraryId"),
        Arrays.asList("newTestCqlLibrarySetId"));

    verify(actionLogRepository, times(1)).findAllActionLogs();
    verify(actionLogRepository, times(0)).deleteByTargetIds(anyList(), anyString());
  }

  @Test
  void testDeleteLibrarySetActionLogsFilteredLibrarySetIdsContainsTargetId() {
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));
    doNothing().when(librarySetActionLogRepository).deleteByTargetIds(anyList(), any());

    changeUnit.deleteLibrarySetActionLogs(
        librarySetActionLogRepository,
        Arrays.asList("cqlLibraryId"),
        Arrays.asList("testCqlLibraryId"));

    verify(librarySetActionLogRepository, times(1)).findAll();
    verify(librarySetActionLogRepository, times(2)).deleteByTargetIds(anyList(), any());
  }

  @Test
  void testDeleteLibrarySetActionLogsNoDelete() {
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));

    changeUnit.deleteLibrarySetActionLogs(
        librarySetActionLogRepository,
        Arrays.asList("newCqlLibraryId"),
        Arrays.asList("newTestCqlLibrarySetId"));

    verify(librarySetActionLogRepository, times(1)).findAll();
    verify(librarySetActionLogRepository, times(0)).deleteByTargetIds(anyList(), any());
  }

  @Test
  void testRollBackExecutionEmptyLists() {
    ReflectionTestUtils.setField(changeUnit, "filteredActionLogs", Collections.emptyList());
    ReflectionTestUtils.setField(
        changeUnit, "filteredLibrarySetActionLogs", Collections.emptyList());
    ReflectionTestUtils.setField(changeUnit, "filteredLibraries", Collections.emptyList());
    ReflectionTestUtils.setField(changeUnit, "filteredLibrarySets", Collections.emptyList());

    changeUnit.rollbackExecution(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verifyNoInteractions(actionLogRepository);
    verifyNoInteractions(librarySetActionLogRepository);
    verifyNoInteractions(cqlLibraryRepository);
    verifyNoInteractions(librarySetRepository);
  }

  @Test
  void testRollBackExecution() {
    when(actionLogRepository.saveAllActionLogs(anyList())).thenReturn(List.of(actionLog1));
    when(librarySetActionLogRepository.saveAll(anyList())).thenReturn(List.of(librarySetActionLog));
    when(cqlLibraryRepository.saveAll(anyList())).thenReturn(List.of(cqlLibrary));
    when(librarySetRepository.saveAll(anyList())).thenReturn(List.of(librarySet1));

    changeUnit.rollbackExecution(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verify(actionLogRepository, times(1)).saveAllActionLogs(anyList());
    verify(librarySetActionLogRepository, times(1)).saveAll(anyList());
    verify(librarySetRepository, times(1)).saveAll(anyList());
    verify(cqlLibraryRepository, times(1)).saveAll(anyList());
  }

  @Test
  void testRollBackActionLogsSavedNull() {
    when(actionLogRepository.saveAllActionLogs(anyList())).thenReturn(null);
    int result = changeUnit.rollBackActionLogs(actionLogRepository);
    assertTrue(result == 0);
  }

  @Test
  void testRollBackLibrarySetActionLogSaveNull() {
    when(librarySetActionLogRepository.saveAll(anyList())).thenReturn(null);
    int result = changeUnit.rollBackLibrarySetActionLog(librarySetActionLogRepository);
    assertTrue(result == 0);
  }

  @Test
  void testRollBackCqlLibraries() {
    when(cqlLibraryRepository.saveAll(anyList())).thenReturn(null);
    int result = changeUnit.rollBackCqlLibraries(cqlLibraryRepository);
    assertTrue(result == 0);
  }

  @Test
  void testRollBackCqlLibrarySets() {
    when(librarySetRepository.saveAll(anyList())).thenReturn(null);
    int result = changeUnit.rollBackCqlLibrarySets(librarySetRepository);
    assertTrue(result == 0);
  }
}
