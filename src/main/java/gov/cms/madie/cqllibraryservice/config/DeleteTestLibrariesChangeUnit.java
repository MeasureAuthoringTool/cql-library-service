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
  // reserved LibrarySets and CqlLibraries (cqlLibraryName: 'SDEFHIR4', 'StatusNew')
  private final List<String> WHITE_LISTED_LIBRARYSET_IDS =
      Arrays.asList("0839c438-a145-4c01-8444-c725e58b4c2f", "1214294e-9ed3-406e-b08f-6996d671a0e9");

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

    List<LibrarySet> librarySets = librarySetRepository.findAll();
    filteredLibrarySets =
        librarySets.stream()
            .filter(
                librarySet ->
                    userSet.contains(librarySet.getOwner())
                        && !WHITE_LISTED_LIBRARYSET_IDS.contains(librarySet.getLibrarySetId()))
            .toList();
    if (CollectionUtils.isNotEmpty(filteredLibrarySets)) {
      List<String> filteredLibrarySetIds =
          filteredLibrarySets.stream().map(LibrarySet::getLibrarySetId).toList();
      filteredLibraries = cqlLibraryRepository.findByLibrarySetIdIn(filteredLibrarySetIds);

      if (CollectionUtils.isNotEmpty(filteredLibraries)) {
        List<String> filteredLibraryIds =
            filteredLibraries.stream().map(CqlLibrary::getId).toList();

        deleteLibraries(cqlLibraryRepository, filteredLibraries);

        deleteActionLogs(actionLogRepository, filteredLibraryIds, filteredLibrarySetIds);
        deleteLibrarySetActionLogs(
            librarySetActionLogRepository, filteredLibraryIds, filteredLibrarySetIds);
      }
      deleteLibrarySets(librarySetRepository, filteredLibrarySets);
    }
  }

  void deleteLibraries(
      CqlLibraryRepository cqlLibraryRepository, List<CqlLibrary> filteredLibraries) {
    cqlLibraryRepository.deleteAll(filteredLibraries);
    log.info("Deleted Libraries: {}", filteredLibraries.size());
  }

  void deleteActionLogs(
      ActionLogRepository actionLogRepository,
      List<String> filteredLibraryIds,
      List<String> filteredLibrarySetIds) {

    List<LibraryActionLog> actionLogs = actionLogRepository.findAllActionLogs();
    log.info("ActionLog total = {}", actionLogs.size());

    filteredActionLogs =
        actionLogs.stream()
            .filter(
                log ->
                    log.getActions().stream()
                                .allMatch(
                                    action ->
                                        users.contains(
                                            action.getPerformedBy())) // for orphaned ActionLogs
                            && !filteredLibraryIds.contains(log.getTargetId())
                            && !filteredLibrarySetIds.contains(log.getTargetId())
                            && !WHITE_LISTED_LIBRARYSET_IDS.contains(log.getTargetId())
                        || filteredLibraryIds.contains(log.getTargetId())
                        || filteredLibrarySetIds.contains(log.getTargetId()))
            .toList();

    if (CollectionUtils.isNotEmpty(filteredActionLogs)) {
      log.info("ActionLogs to be deleted = {}", filteredActionLogs.size());
      List<String> targetIds =
          filteredActionLogs.stream().map(LibraryActionLog::getTargetId).toList();
      actionLogRepository.deleteByTargetIds(targetIds, "actionLog");
    }
  }

  void deleteLibrarySetActionLogs(
      LibrarySetActionLogRepository librarySetActionLogRepository,
      List<String> filteredLibraryIds,
      List<String> filteredLibrarySetIds) {
    List<LibrarySetActionLog> actionLogs = librarySetActionLogRepository.findAll();
    log.info("LibrarySetActionLog total = {}", actionLogs.size());
    filteredLibrarySetActionLogs =
        actionLogs.stream()
            .filter(
                log ->
                    filteredLibraryIds.contains(log.getTargetId())
                        || filteredLibrarySetIds.contains(log.getTargetId()))
            .toList();
    if (CollectionUtils.isNotEmpty(filteredLibrarySetActionLogs)) {
      log.info("LibrarySetActionLogs to be deleted = {}", filteredLibrarySetActionLogs.size());
      librarySetActionLogRepository.deleteByTargetIds(filteredLibraryIds, "librarySetActionLog");
      librarySetActionLogRepository.deleteByTargetIds(filteredLibrarySetIds, "librarySetActionLog");
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
