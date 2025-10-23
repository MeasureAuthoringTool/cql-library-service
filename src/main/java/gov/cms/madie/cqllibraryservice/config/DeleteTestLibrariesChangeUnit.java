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
import gov.cms.madie.models.common.ActionLog;
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

  @Execution
  public void deleteTestLibraries(
      CqlLibraryRepository cqlLibraryRepository,
      LibrarySetRepository librarySetRepository,
      ActionLogRepositoryImpl actionLogRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository) {

    // 1. delete ActionLogs and CqlLibraryLogs
    deleteActionLogs(actionLogRepository);
    deleteLibrarySetActionLogs(librarySetActionLogRepository);

    // 2. delete CqlLibrary and LibrarySet
    List<LibrarySet> librarySets = librarySetRepository.findAll();
    filteredLibrarySets =
        librarySets.stream().filter(ms -> userSet.contains(ms.getOwner())).toList();
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
    actionLogRepository.removeActionsByUsers(users, ActionLog.class);
  }

  void deleteLibrarySetActionLogs(LibrarySetActionLogRepository librarySetActionLogRepository) {
    checkLibrarySetActionLogs(librarySetActionLogRepository);
    librarySetActionLogRepository.removeActionsByUsers(users, LibrarySetActionLog.class);
  }

  // for logging purpose
  void checkActionLogs(ActionLogRepository actionLogRepository) {
    List<LibraryActionLog> actionLogs = actionLogRepository.findAllActionLogs();
    log.info("ActionLog total = " + actionLogs.size());

    filteredActionLogs =
        actionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                        .allMatch(action -> users.contains(action.getPerformedBy())))
            .toList();
    log.info("filteredActionLogs size  = " + filteredActionLogs.size());
  }

  // for logging purpose
  void checkLibrarySetActionLogs(LibrarySetActionLogRepository librarySetActionLogRepository) {
    List<LibrarySetActionLog> actionLogs = librarySetActionLogRepository.findAll();
    log.info("LibrarySetActionLog total = " + actionLogs.size());
    filteredLibrarySetActionLogs =
        actionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                        .allMatch(action -> userSet.contains(action.getPerformedBy())))
            .toList();
    log.info("filteredLibrarySetActionLogs size  = " + filteredLibrarySetActionLogs.size());
  }

  void deleteLibraries(
      CqlLibraryRepository cqlLibraryRepository, List<CqlLibrary> filteredLibraries) {
    if (CollectionUtils.isNotEmpty(filteredLibraries)) {
      cqlLibraryRepository.deleteAll(filteredLibraries);
      log.info("Deleted Libraries: " + filteredLibraries.size());
    }
  }

  void deleteLibrarySets(
      LibrarySetRepository librarySetRepository, List<LibrarySet> filteredLibrarySets) {
    librarySetRepository.deleteAll(filteredLibrarySets);
    log.info("Deleted LibrarySets: " + filteredLibrarySets.size());
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
    if (CollectionUtils.isNotEmpty(filteredActionLogs)) {
      List<LibraryActionLog> saved =
          (List<LibraryActionLog>) actionLogRepository.saveAllActionLogs(filteredActionLogs);
      log.info("Roll back ActionLog: " + (saved != null ? saved.size() : " null"));
      return saved != null ? saved.size() : 0;
    }
    return 0;
  }

  int rollBackLibrarySetActionLog(LibrarySetActionLogRepository librarySetActionLogRepository) {
    if (CollectionUtils.isNotEmpty(filteredLibrarySetActionLogs)) {
      List<LibrarySetActionLog> saved =
          librarySetActionLogRepository.saveAll(filteredLibrarySetActionLogs);
      log.info("Roll back LibrarySetActionLog: " + (saved != null ? saved.size() : " null"));
      return saved != null ? saved.size() : 0;
    }
    return 0;
  }

  int rollBackCqlLibraries(CqlLibraryRepository cqlLibraryRepository) {
    if (CollectionUtils.isNotEmpty(filteredLibraries)) {
      List<CqlLibrary> saved = cqlLibraryRepository.saveAll(filteredLibraries);
      log.info("Roll back CqlLibrary: " + (saved != null ? saved.size() : " null"));
      return saved != null ? saved.size() : 0;
    }
    return 0;
  }

  int rollBackCqlLibrarySets(LibrarySetRepository librarySetRepository) {
    if (CollectionUtils.isNotEmpty(filteredLibrarySets)) {
      List<LibrarySet> saved = librarySetRepository.saveAll(filteredLibrarySets);
      log.info("Roll back LibrarySet: " + (saved != null ? saved.size() : " null"));
      return saved != null ? saved.size() : 0;
    }
    return 0;
  }
}
