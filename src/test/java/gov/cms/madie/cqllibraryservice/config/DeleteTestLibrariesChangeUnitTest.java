package gov.cms.madie.cqllibraryservice.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
  private Action action =
      Action.builder().actionType(ActionType.CREATED).performedBy("testUser").build();
  private LibraryActionLog actionLog =
      LibraryActionLog.builder()
          .id("testLibraryActionLogId")
          .targetId("testTargetId")
          .actions(List.of(action))
          .build();
  private AccessControlAction accessControlAction =
      AccessControlAction.builder().actionType(ActionType.CREATED).performedBy("testUser").build();
  private LibrarySetActionLog librarySetActionLog =
      LibrarySetActionLog.builder()
          .id("testLibrarySetActionLogId")
          .targetId("testTargetId2")
          .actions(List.of(accessControlAction))
          .build();

  private LibrarySet librarySet =
      LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build();
  private CqlLibrary cqlLibrary = CqlLibrary.builder().librarySetId("testLibrarySetId").build();

  @BeforeEach
  void init() {
    ReflectionTestUtils.setField(changeUnit, "userSet", userSet);

    ReflectionTestUtils.setField(changeUnit, "filteredActionLogs", List.of(actionLog));
    ReflectionTestUtils.setField(
        changeUnit, "filteredLibrarySetActionLogs", List.of(librarySetActionLog));
    ReflectionTestUtils.setField(changeUnit, "filteredLibraries", List.of(cqlLibrary));
    ReflectionTestUtils.setField(changeUnit, "filteredLibrarySets", List.of(librarySet));
  }

  @Test
  void testDeleteActionLogs() {
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    changeUnit.deleteActionLogs(actionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs();
    verify(actionLogRepository, times(1)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteLibrarySetActionLogs() {
    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));
    doNothing().when(librarySetActionLogRepository).removeActionsByUsers(anyList(), any());

    changeUnit.deleteLibrarySetActionLogs(librarySetActionLogRepository);

    verify(librarySetActionLogRepository, times(1)).findAll();
    verify(librarySetActionLogRepository, times(1)).removeActionsByUsers(anyList(), any());
  }

  @Test
  void testDeleteLibraries() {
    doNothing().when(cqlLibraryRepository).deleteAll(anyList());
    changeUnit.deleteLibraries(cqlLibraryRepository, List.of(cqlLibrary));
    verify(cqlLibraryRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteLibrariesEmptyList() {
    changeUnit.deleteLibraries(cqlLibraryRepository, Collections.emptyList());
    verify(cqlLibraryRepository, times(0)).deleteAll(anyList());
  }

  @Test
  void testDeleteLibrarySets() {

    doNothing().when(librarySetRepository).deleteAll(anyList());
    changeUnit.deleteLibrarySets(librarySetRepository, List.of(librarySet));
    verify(librarySetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteLibrarySetsEmptyList() {
    changeUnit.deleteLibrarySets(librarySetRepository, Collections.emptyList());
    verify(librarySetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestLibraries() {
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));
    doNothing().when(librarySetActionLogRepository).removeActionsByUsers(anyList(), any());

    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));
    when(cqlLibraryRepository.findByLibrarySetIdIn(anyList())).thenReturn(List.of(cqlLibrary));

    doNothing().when(cqlLibraryRepository).deleteAll(anyList());
    doNothing().when(librarySetRepository).deleteAll(anyList());

    changeUnit.deleteTestLibraries(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs();
    verify(actionLogRepository, times(1)).removeActionsByUsers(anyList(), any());

    verify(cqlLibraryRepository, times(1)).deleteAll(anyList());
    verify(librarySetRepository, times(1)).deleteAll(anyList());
  }

  @Test
  void testDeleteTestLibrariesNoLibrarySets() {
    when(actionLogRepository.findAllActionLogs()).thenReturn(List.of(actionLog));
    doNothing().when(actionLogRepository).removeActionsByUsers(anyList(), any());

    when(librarySetActionLogRepository.findAll()).thenReturn(List.of(librarySetActionLog));
    doNothing().when(librarySetActionLogRepository).removeActionsByUsers(anyList(), any());

    when(librarySetRepository.findAll()).thenReturn(Collections.emptyList());

    changeUnit.deleteTestLibraries(
        cqlLibraryRepository,
        librarySetRepository,
        actionLogRepository,
        librarySetActionLogRepository);

    verify(actionLogRepository, times(1)).findAllActionLogs();
    verify(actionLogRepository, times(1)).removeActionsByUsers(anyList(), any());

    verify(cqlLibraryRepository, times(0)).deleteAll(anyList());
    verify(librarySetRepository, times(0)).deleteAll(anyList());
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
    when(actionLogRepository.saveAllActionLogs(anyList())).thenReturn(List.of(actionLog));
    when(librarySetActionLogRepository.saveAll(anyList())).thenReturn(List.of(librarySetActionLog));
    when(cqlLibraryRepository.saveAll(anyList())).thenReturn(List.of(cqlLibrary));
    when(librarySetRepository.saveAll(anyList())).thenReturn(List.of(librarySet));

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
