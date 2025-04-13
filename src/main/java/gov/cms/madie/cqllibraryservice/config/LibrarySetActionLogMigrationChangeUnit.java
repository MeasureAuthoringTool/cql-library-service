package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionLog;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.library.LibrarySet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ChangeUnit(id = "data_migration_libraryset_action_log", order = "1", author = "madie_dev")
public class LibrarySetActionLogMigrationChangeUnit {
  List<ActionLog> actionLogsToBeMigrated = new ArrayList<>();
  List<String> librarySetActionLogIds = new ArrayList<>();

  @Execution
  public void migrateLibrarySetActionLog(
      LibrarySetRepository librarySetRepository,
      CqlLibraryActionLogRepository cqlLibraryHistoryRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository) {

    List<ActionLog> actionsLogs = cqlLibraryHistoryRepository.findAll();
    List<String> actionLogIdsToBeMigrated = new ArrayList<>();
    List<LibrarySetActionLog> librarySetActionLogs = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(actionsLogs)) {
      actionsLogs.stream()
          .forEach(
              actionLog -> {
                String targetId = actionLog.getTargetId();
                Optional<LibrarySet> librarySetOpt = librarySetRepository.findById(targetId);
                if (librarySetOpt.isPresent()) {
                  // record the migrated records for delete and rollback:
                  actionLogsToBeMigrated.add(actionLog);
                  actionLogIdsToBeMigrated.add(actionLog.getId());

                  // get the LibrarySetActionLog data ready:
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

                  LibrarySetActionLog librarySetActionLog =
                      LibrarySetActionLog.builder()
                          .id(actionLog.getId())
                          .targetId(librarySetOpt.get().getLibrarySetId())
                          .actions(accessControlActions)
                          .build();

                  librarySetActionLogs.add(librarySetActionLog);
                  librarySetActionLogIds.add(actionLog.getId());
                }
              });

      // add LibrarySetActionLog first
      if (CollectionUtils.isNotEmpty(librarySetActionLogs)) {
        librarySetActionLogRepository.saveAll(librarySetActionLogs);
      }
      // delete from LibraryActionLog
      if (com.nimbusds.oauth2.sdk.util.CollectionUtils.isNotEmpty(actionLogIdsToBeMigrated)) {
        cqlLibraryHistoryRepository.deleteAllById(actionLogIdsToBeMigrated);
      }
    }
  }

  @RollbackExecution
  public void rollbackExecution(
      CqlLibraryActionLogRepository cqlLibraryHistoryRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository) {
    log.debug("Entering rollbackExecution()");

    if (CollectionUtils.isNotEmpty(actionLogsToBeMigrated)) {
      cqlLibraryHistoryRepository.saveAll(actionLogsToBeMigrated);
    }

    if (CollectionUtils.isNotEmpty(librarySetActionLogIds)) {
      librarySetActionLogRepository.deleteAllById(librarySetActionLogIds);
    }
  }
}
