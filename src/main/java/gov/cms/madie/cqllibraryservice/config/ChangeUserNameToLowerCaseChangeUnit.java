package gov.cms.madie.cqllibraryservice.config;

import java.util.ArrayList;
import java.util.List;

import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.LibraryActionLog;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.library.LibrarySet;

import org.apache.commons.collections4.CollectionUtils;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import gov.cms.madie.models.library.CqlLibrary;

@Slf4j
@Data
@ChangeUnit(id = "change_username_to_lower_case", order = "1", author = "madie_dev")
public class ChangeUserNameToLowerCaseChangeUnit {
  private List<CqlLibrary> originalLibraries = new ArrayList<>();
  private List<CqlLibrary> updatedLibraries = new ArrayList<>();
  private List<LibrarySet> originalLibrarySets = new ArrayList<>();
  private List<LibrarySet> updatedLibrarySets = new ArrayList<>();
  private List<LibraryActionLog> originalLibraryActionLogs = new ArrayList<>();
  private List<LibraryActionLog> updatedLibraryActionLogs = new ArrayList<>();
  private List<LibrarySetActionLog> originalLibrarySetActionLogs = new ArrayList<>();
  private List<LibrarySetActionLog> updatedLibrarySetActionLogs = new ArrayList<>();
  private List<CqlLibraryLock> originalLibraryLocks = new ArrayList<>();
  private List<CqlLibraryLock> updatedLibraryLocks = new ArrayList<>();

  @Execution
  public void changeAllUserNamesToLowerCase(
      CqlLibraryRepository cqlLibraryRepository,
      LibrarySetRepository librarySetRepository,
      ActionLogRepositoryImpl actionLogRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository,
      CqlLibraryLockRepository cqlLibraryLockRepository) {
    updateLibraries(cqlLibraryRepository);
    updatedLibrarySets(librarySetRepository);
    updateLibraryActionLogs(actionLogRepository);
    updateLibrarySetActionLogs(librarySetActionLogRepository);
    updateLibraryLocks(cqlLibraryLockRepository);
  }

  void updateLibraries(CqlLibraryRepository cqlLibraryRepository) {
    originalLibraries = cqlLibraryRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalLibraries)) {
      log.info("originalLibraries: " + originalLibraries.size());
      for (CqlLibrary library : originalLibraries) {
        boolean isUpdated = false;
        if (library.getCreatedBy() != null
            && library.getCreatedBy().chars().anyMatch(Character::isUpperCase)) {
          library.setCreatedBy(library.getCreatedBy().toLowerCase());
          isUpdated = true;
        }
        if (library.getLastModifiedBy() != null
            && library.getLastModifiedBy().chars().anyMatch(Character::isUpperCase)) {
          library.setLastModifiedBy(library.getLastModifiedBy().toLowerCase());
          isUpdated = true;
        }
        if (isUpdated) {
          updatedLibraries.add(library);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedLibraries)) {
        log.info("updatedLibraries: " + updatedLibraries.size());
        cqlLibraryRepository.saveAll(updatedLibraries);
      }
    }
  }

  void updatedLibrarySets(LibrarySetRepository librarySetRepository) {
    originalLibrarySets = librarySetRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalLibrarySets)) {
      log.info("originalLibrarySets: " + originalLibrarySets.size());
      for (LibrarySet librarySet : originalLibrarySets) {
        boolean isUpdated = false;
        if (librarySet.getOwner() != null
            && librarySet.getOwner().chars().anyMatch(Character::isUpperCase)) {
          librarySet.setOwner(librarySet.getOwner().toLowerCase());
          isUpdated = true;
        }
        if (CollectionUtils.isNotEmpty(librarySet.getAcls())) {
          List<AclSpecification> updatedAcls = new ArrayList<>();
          boolean aclUpdated = false;
          for (AclSpecification acl : librarySet.getAcls()) {
            String userId = acl.getUserId();
            if (userId.chars().anyMatch(Character::isUpperCase)) {
              acl.setUserId(userId.toLowerCase());
              updatedAcls.add(acl);
              aclUpdated = true;
              isUpdated = true;
            } else {
              updatedAcls.add(acl);
            }
          }
          if (aclUpdated) {
            librarySet.setAcls(updatedAcls);
          }
        }
        if (isUpdated) {
          updatedLibrarySets.add(librarySet);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedLibrarySets)) {
        log.info("updatedMeasureSets: " + updatedLibrarySets.size());
        librarySetRepository.saveAll(updatedLibrarySets);
      }
    }
  }

  void updateLibraryActionLogs(ActionLogRepository actionLogRepository) {
    originalLibraryActionLogs = actionLogRepository.findAllActionLogs();
    if (CollectionUtils.isNotEmpty(originalLibraryActionLogs)) {
      log.info("originalLibraryActionLogs: " + originalLibraryActionLogs.size());
      for (LibraryActionLog actionLog : originalLibraryActionLogs) {
        boolean isUpdated = false;
        List<Action> actions = actionLog.getActions();
        List<Action> updatedActions = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(actions)) {
          for (Action action : actions) {
            if (action.getPerformedBy() != null
                && action.getPerformedBy().chars().anyMatch(Character::isUpperCase)) {
              action.setPerformedBy(action.getPerformedBy().toLowerCase());
              updatedActions.add(action);
              isUpdated = true;
            } else {
              updatedActions.add(action);
            }
          }
          if (isUpdated) {
            actionLog.setActions(updatedActions);
          }
        }
        if (isUpdated) {
          updatedLibraryActionLogs.add(actionLog);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedLibraryActionLogs)) {
        log.info("updatedLibraryActionLogs: " + updatedLibraryActionLogs.size());
        actionLogRepository.updateAllActionLogs(updatedLibraryActionLogs);
      }
    }
  }

  void updateLibrarySetActionLogs(LibrarySetActionLogRepository librarySetActionLogRepository) {
    originalLibrarySetActionLogs = librarySetActionLogRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalLibrarySetActionLogs)) {
      log.info("originalLibrarySetActionLogs: " + originalLibrarySetActionLogs.size());
      for (LibrarySetActionLog actionLog : originalLibrarySetActionLogs) {
        boolean isUpdated = false;
        List<AccessControlAction> actions = actionLog.getActions();
        List<AccessControlAction> updatedActions = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(actions)) {
          for (AccessControlAction action : actions) {
            if (action.getPerformedBy() != null
                && action.getPerformedBy().chars().anyMatch(Character::isUpperCase)) {
              action.setPerformedBy(action.getPerformedBy().toLowerCase());
              updatedActions.add(action);
              isUpdated = true;
            } else {
              updatedActions.add(action);
            }
          }
          if (isUpdated) {
            actionLog.setActions(updatedActions);
          }
        }
        if (isUpdated) {
          updatedLibrarySetActionLogs.add(actionLog);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedLibrarySetActionLogs)) {
        log.info("updatedLibrarySetActionLogs: " + updatedLibrarySetActionLogs.size());
        librarySetActionLogRepository.saveAll(updatedLibrarySetActionLogs);
      }
    }
  }

  void updateLibraryLocks(CqlLibraryLockRepository cqlLibraryLockRepository) {
    originalLibraryLocks = cqlLibraryLockRepository.findAll();
    if (CollectionUtils.isNotEmpty(originalLibraryLocks)) {
      log.info("originalLibraryLocks: " + originalLibraryLocks.size());
      for (CqlLibraryLock libraryLock : originalLibraryLocks) {
        boolean isUpdated = false;
        if (libraryLock.getLockedBy() != null
            && libraryLock.getLockedBy().chars().anyMatch(Character::isUpperCase)) {
          libraryLock.setLockedBy(libraryLock.getLockedBy().toLowerCase());
          isUpdated = true;
        }
        if (isUpdated) {
          updatedLibraryLocks.add(libraryLock);
        }
      }
      if (CollectionUtils.isNotEmpty(updatedLibraryLocks)) {
        log.info("updatedLibraryLocks: " + updatedLibraryLocks.size());
        cqlLibraryLockRepository.saveAll(updatedLibraryLocks);
      }
    }
  }

  @RollbackExecution
  public void rollbackChanges(
      CqlLibraryRepository cqlLibraryRepository,
      LibrarySetRepository librarySetRepository,
      ActionLogRepository actionLogRepository,
      LibrarySetActionLogRepository librarySetActionLogRepository,
      CqlLibraryLockRepository cqlLibraryLockRepository) {
    if (CollectionUtils.isNotEmpty(originalLibraries)) {
      cqlLibraryRepository.saveAll(originalLibraries);
    }
    if (CollectionUtils.isNotEmpty(originalLibrarySets)) {
      librarySetRepository.saveAll(originalLibrarySets);
    }
    if (CollectionUtils.isNotEmpty(originalLibraryActionLogs)) {
      actionLogRepository.updateAllActionLogs(originalLibraryActionLogs);
    }
    if (CollectionUtils.isNotEmpty(originalLibrarySetActionLogs)) {
      librarySetActionLogRepository.saveAll(originalLibrarySetActionLogs);
    }
    if (CollectionUtils.isNotEmpty(originalLibraryLocks)) {
      cqlLibraryLockRepository.saveAll(originalLibraryLocks);
    }
  }
}
