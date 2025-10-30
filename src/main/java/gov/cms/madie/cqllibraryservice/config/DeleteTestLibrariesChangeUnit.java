package gov.cms.madie.cqllibraryservice.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;

import gov.cms.madie.cqllibraryservice.repositories.ActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.ActionLogRepositoryImpl;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.LibraryActionLog;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.library.CqlLibrary;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "delete_test_libraries", order = "1", author = "madie_dev")
public class DeleteTestLibrariesChangeUnit {
  private final List<String> users =
      Arrays.asList("cvasile", "bwelch", "pvasireddy", "colin.sullivan", "adongare");
  private final Set<String> userSet = new HashSet<>(users);

  private List<LibraryActionLog> filteredActionLogs = new ArrayList<>();
  private List<LibrarySetActionLog> filteredLibrarySetActionLogs = new ArrayList<>();

  private List<LibrarySet> filteredLibrarySets = new ArrayList<>();
  private List<CqlLibrary> filteredLibraries = new ArrayList<>();

  // reserved LibrarySets and CqlLibraries (cqlLibraryName: 'SDEFHIR4')
  private final List<String> WHITE_LISTED_LIBRARYSET_IDS =
      Arrays.asList("0839c438-a145-4c01-8444-c725e58b4c2f");
  private final List<String> WHITE_LISTED_TARGET_IDS =
      Arrays.asList("0839c438-a145-4c01-8444-c725e58b4c2f", "6659f88da7713a45189fa9f6");

  @Execution
  public void deleteTestLibraries(
      CqlLibraryRepository cqlLibraryRepository,
      LibrarySetRepository librarySetRepository,
      ActionLogRepositoryImpl actionLogRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository) {

    deleteActionLogs(actionLogRepository);
    deleteLibrarySetActionLogs(librarySetActionLogRepository);

    List<LibrarySet> librarySets = librarySetRepository.findAll();
    filteredLibrarySets =
        librarySets.stream()
            .filter(
                ls ->
                    userSet.contains(ls.getOwner())
                        && !WHITE_LISTED_LIBRARYSET_IDS.contains(ls.getLibrarySetId()))
            .toList();
    if (CollectionUtils.isNotEmpty(filteredLibrarySets)) {
      List<String> filteredLibrarySetIds =
          filteredLibrarySets.stream().map(LibrarySet::getLibrarySetId).toList();
      filteredLibraries = cqlLibraryRepository.findByLibrarySetIdIn(filteredLibrarySetIds);

      deleteLibraries(cqlLibraryRepository, filteredLibraries);
      deleteLibrarySets(librarySetRepository, filteredLibrarySets);
    }
  }

  void deleteActionLogs(ActionLogRepository actionLogRepository) {
    checkActionLogs(actionLogRepository);
    if (CollectionUtils.isNotEmpty(filteredActionLogs)) {
      log.info("ActionLogs to be deleted: {}", filteredActionLogs.size());
      actionLogRepository.removeActionsByUsers(users, "actionLog");
    }
  }

  void deleteLibrarySetActionLogs(LibrarySetActionLogRepository librarySetActionLogRepository) {
    checkLibrarySetActionLogs(librarySetActionLogRepository);
    if (CollectionUtils.isNotEmpty(filteredLibrarySetActionLogs)) {
      log.info("LibrarySetActionLogs to be deleted: {}", filteredLibrarySetActionLogs.size());
      librarySetActionLogRepository.removeActionsByUsers(users, "librarySetActionLog");
    }
  }

  // for logging and setting roll back data
  void checkActionLogs(ActionLogRepository actionLogRepository) {
    List<LibraryActionLog> actionLogs = actionLogRepository.findAllActionLogs();
    log.info("ActionLog total = {}", actionLogs.size());

    filteredActionLogs =
        actionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                            .allMatch(action -> users.contains(action.getPerformedBy()))
                        && !WHITE_LISTED_TARGET_IDS.contains(log.getTargetId()))
            .toList();
  }

  // for logging and setting roll back data
  void checkLibrarySetActionLogs(LibrarySetActionLogRepository librarySetActionLogRepository) {
    List<LibrarySetActionLog> actionLogs = librarySetActionLogRepository.findAll();
    log.info("LibrarySetActionLog total: {}", actionLogs.size());
    filteredLibrarySetActionLogs =
        actionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                            .allMatch(action -> userSet.contains(action.getPerformedBy()))
                        && !WHITE_LISTED_TARGET_IDS.contains(log.getTargetId()))
            .toList();
  }

  void deleteLibraries(
      CqlLibraryRepository cqlLibraryRepository, List<CqlLibrary> filteredLibraries) {
    if (CollectionUtils.isNotEmpty(filteredLibraries)) {
      cqlLibraryRepository.deleteAll(filteredLibraries);
      log.info("Deleted Libraries: {}", filteredLibraries.size());
    }
  }

  void deleteLibrarySets(
      LibrarySetRepository librarySetRepository, List<LibrarySet> filteredLibrarySets) {
    librarySetRepository.deleteAll(filteredLibrarySets);
    log.info("Deleted LibrarySets: {}", filteredLibrarySets.size());
  }

  @RollbackExecution
  public void rollbackExecution(
      CqlLibraryRepository cqlLibraryRepository,
      LibrarySetRepository librarySetRepository,
      ActionLogRepositoryImpl actionLogRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository) {
    log.info("rollbackExecution started");

    rollBackActionLogs(actionLogRepository);
    rollBackLibrarySetActionLog(librarySetActionLogRepository);
    rollBackCqlLibraries(cqlLibraryRepository);
    rollBackCqlLibrarySets(librarySetRepository);
  }

  int rollBackActionLogs(ActionLogRepositoryImpl actionLogRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredActionLogs)) {
      List<LibraryActionLog> saved =
          (List<LibraryActionLog>) actionLogRepository.saveAllActionLogs(filteredActionLogs);
      size = saved != null ? saved.size() : 0;
      log.info("Roll back ActionLog: {}", size);
    }
    return size;
  }

  int rollBackLibrarySetActionLog(LibrarySetActionLogRepository librarySetActionLogRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredLibrarySetActionLogs)) {
      List<LibrarySetActionLog> saved =
          librarySetActionLogRepository.saveAll(filteredLibrarySetActionLogs);
      size = saved != null ? saved.size() : 0;
      log.info("Roll back LibrarySetActionLog: {}", size);
    }
    return size;
  }

  int rollBackCqlLibraries(CqlLibraryRepository cqlLibraryRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredLibraries)) {
      List<CqlLibrary> saved = cqlLibraryRepository.saveAll(filteredLibraries);
      size = saved != null ? saved.size() : 0;
      log.info("Roll back CqlLibrary: {}", size);
    }
    return size;
  }

  int rollBackCqlLibrarySets(LibrarySetRepository librarySetRepository) {
    int size = 0;
    if (CollectionUtils.isNotEmpty(filteredLibrarySets)) {
      List<LibrarySet> saved = librarySetRepository.saveAll(filteredLibrarySets);
      size = saved != null ? saved.size() : 0;
      log.info("Roll back LibrarySet: {}", size);
    }
    return size;
  }
}
